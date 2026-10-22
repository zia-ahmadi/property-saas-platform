package com.example.property_core.lease;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.property_core.common.JdbcTimestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.property_core.tenant.TenantScopedRepository;

@Repository
public class LeaseRepository extends TenantScopedRepository {
    public LeaseRepository(JdbcClient jdbcClient) {
        super(jdbcClient);
    }

    public List<Lease> findAll(LeaseStatus status, UUID renterId, UUID unitId, int page, int size) {
        String sql = """
                SELECT id, renter_id, unit_id, status, version, start_date, end_date,
                       monthly_rent, renewed_from_lease_id, created_at, updated_at
                FROM leases
                WHERE organization_id = :organizationId
                """;
        if (status != null) sql += " AND status = :status";
        if (renterId != null) sql += " AND renter_id = :renterId";
        if (unitId != null) sql += " AND unit_id = :unitId";
        sql += " ORDER BY created_at DESC, id LIMIT :size OFFSET :offset";

        var query = jdbcClient.sql(sql)
                .param("organizationId", currentOrganizationId())
                .param("size", size)
                .param("offset", Math.multiplyExact((long) page, size));
        if (status != null) query = query.param("status", status.name());
        if (renterId != null) query = query.param("renterId", renterId);
        if (unitId != null) query = query.param("unitId", unitId);
        return query.query(this::mapLease).list();
    }

    public long count(LeaseStatus status, UUID renterId, UUID unitId) {
        String sql = "SELECT COUNT(*) FROM leases WHERE organization_id = :organizationId";
        if (status != null) sql += " AND status = :status";
        if (renterId != null) sql += " AND renter_id = :renterId";
        if (unitId != null) sql += " AND unit_id = :unitId";
        var query = jdbcClient.sql(sql).param("organizationId", currentOrganizationId());
        if (status != null) query = query.param("status", status.name());
        if (renterId != null) query = query.param("renterId", renterId);
        if (unitId != null) query = query.param("unitId", unitId);
        return query.query(Long.class).single();
    }

    public Optional<Lease> findById(UUID id) {
        return jdbcClient.sql("""
                SELECT id, renter_id, unit_id, status, version, start_date, end_date,
                       monthly_rent, renewed_from_lease_id, created_at, updated_at
                FROM leases
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .query(this::mapLease)
                .optional();
    }

    public boolean renterExists(UUID renterId) {
        return jdbcClient.sql("""
                SELECT EXISTS (SELECT 1 FROM renters
                WHERE id = :renterId AND organization_id = :organizationId)
                """)
                .param("renterId", renterId)
                .param("organizationId", currentOrganizationId())
                .query(Boolean.class)
                .single();
    }

    public Optional<String> unitStatus(UUID unitId) {
        return jdbcClient.sql("""
                SELECT status FROM units
                WHERE id = :unitId AND organization_id = :organizationId
                FOR UPDATE
                """)
                .param("unitId", unitId)
                .param("organizationId", currentOrganizationId())
                .query(String.class)
                .optional();
    }

    public boolean hasOpenLease(UUID unitId) {
        return jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM leases
                    WHERE organization_id = :organizationId AND unit_id = :unitId
                      AND status IN ('DRAFT', 'ACTIVE')
                )
                """)
                .param("organizationId", currentOrganizationId())
                .param("unitId", unitId)
                .query(Boolean.class)
                .single();
    }

    public boolean hasDraftLease(UUID unitId) {
        return jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM leases
                    WHERE organization_id = :organizationId AND unit_id = :unitId
                      AND status = 'DRAFT'
                )
                """)
                .param("organizationId", currentOrganizationId())
                .param("unitId", unitId)
                .query(Boolean.class)
                .single();
    }

    public Lease create(
            UUID renterId, UUID unitId, LocalDate startDate, LocalDate endDate,
            BigDecimal monthlyRent, UUID renewedFromLeaseId) {
        UUID id = jdbcClient.sql("""
                INSERT INTO leases (
                    organization_id, renter_id, unit_id, status, start_date, end_date,
                    monthly_rent, renewed_from_lease_id)
                VALUES (:organizationId, :renterId, :unitId, 'DRAFT', :startDate, :endDate,
                    :monthlyRent, :renewedFromLeaseId)
                RETURNING id
                """)
                .param("organizationId", currentOrganizationId())
                .param("renterId", renterId)
                .param("unitId", unitId)
                .param("startDate", startDate)
                .param("endDate", endDate)
                .param("monthlyRent", monthlyRent)
                .param("renewedFromLeaseId", renewedFromLeaseId)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
        return findById(id).orElseThrow(() -> new IllegalStateException("Created lease was not found"));
    }

    public int terminateForRenewal(UUID originalLeaseId, UUID renewalLeaseId) {
        return jdbcClient.sql("""
                UPDATE leases
                SET status = 'TERMINATED',
                    termination_reason = :reason,
                    version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :originalLeaseId
                  AND organization_id = :organizationId
                  AND status = 'ACTIVE'
                """)
                .param("reason", "Renewed by lease " + renewalLeaseId)
                .param("originalLeaseId", originalLeaseId)
                .param("organizationId", currentOrganizationId())
                .update();
    }

    public int transition(UUID id, LeaseStatus from, LeaseStatus to, long expectedVersion, String reason) {
        return jdbcClient.sql("""
                UPDATE leases
                SET status = :toStatus,
                    termination_reason = COALESCE(:reason, termination_reason),
                    version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND organization_id = :organizationId
                  AND status = :fromStatus AND version = :expectedVersion
                """)
                .param("toStatus", to.name())
                .param("reason", reason)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .param("fromStatus", from.name())
                .param("expectedVersion", expectedVersion)
                .update();
    }

    public void updateUnitStatus(UUID unitId, String status) {
        jdbcClient.sql("""
                UPDATE units SET status = :status, updated_at = CURRENT_TIMESTAMP
                WHERE id = :unitId AND organization_id = :organizationId
                """)
                .param("status", status)
                .param("unitId", unitId)
                .param("organizationId", currentOrganizationId())
                .update();
    }

    private Lease mapLease(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new Lease(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("renter_id", UUID.class),
                resultSet.getObject("unit_id", UUID.class),
                LeaseStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                resultSet.getObject("start_date", LocalDate.class),
                resultSet.getObject("end_date", LocalDate.class),
                resultSet.getObject("monthly_rent", BigDecimal.class),
                resultSet.getObject("renewed_from_lease_id", UUID.class),
                JdbcTimestamps.getInstant(resultSet, "created_at"),
                JdbcTimestamps.getInstant(resultSet, "updated_at"));
    }
}
