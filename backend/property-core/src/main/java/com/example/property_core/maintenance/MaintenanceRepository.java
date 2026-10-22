package com.example.property_core.maintenance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.property_core.common.JdbcTimestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.property_core.tenant.TenantScopedRepository;

@Repository
public class MaintenanceRepository extends TenantScopedRepository {
    public MaintenanceRepository(JdbcClient jdbcClient) {
        super(jdbcClient);
    }

    public List<MaintenanceItem> findAll(
            MaintenanceStatus status, MaintenancePriority priority, UUID vendorId, int page, int size) {
        String sql = """
                SELECT id, unit_id, renter_id, description, priority, status, version,
                       vendor_id, requested_date, created_at, updated_at
                FROM maintenance_requests
                WHERE organization_id = :organizationId
                """ + (status == null ? "" : " AND status = :status")
                + (priority == null ? "" : " AND priority = :priority")
                + (vendorId == null ? "" : " AND vendor_id = :vendorId")
                + " ORDER BY requested_date DESC, id LIMIT :size OFFSET :offset";
        var query = jdbcClient.sql(sql)
                .param("organizationId", currentOrganizationId())
                .param("size", size)
                .param("offset", Math.multiplyExact((long) page, size));
        if (status != null) query = query.param("status", status.name());
        if (priority != null) query = query.param("priority", priority.name());
        if (vendorId != null) query = query.param("vendorId", vendorId);
        return query.query(this::mapItem).list();
    }

    public long count(MaintenanceStatus status, MaintenancePriority priority, UUID vendorId) {
        String sql = "SELECT COUNT(*) FROM maintenance_requests WHERE organization_id = :organizationId"
                + (status == null ? "" : " AND status = :status")
                + (priority == null ? "" : " AND priority = :priority")
                + (vendorId == null ? "" : " AND vendor_id = :vendorId");
        var query = jdbcClient.sql(sql).param("organizationId", currentOrganizationId());
        if (status != null) query = query.param("status", status.name());
        if (priority != null) query = query.param("priority", priority.name());
        if (vendorId != null) query = query.param("vendorId", vendorId);
        return query.query(Long.class).single();
    }

    public Optional<MaintenanceItem> findById(UUID id) {
        return jdbcClient.sql("""
                SELECT id, unit_id, renter_id, description, priority, status, version,
                       vendor_id, requested_date, created_at, updated_at
                FROM maintenance_requests
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .query(this::mapItem)
                .optional();
    }

    public boolean unitExists(UUID unitId) {
        return jdbcClient.sql("""
                SELECT EXISTS (SELECT 1 FROM units
                WHERE id = :id AND organization_id = :organizationId)
                """)
                .param("id", unitId)
                .param("organizationId", currentOrganizationId())
                .query(Boolean.class)
                .single();
    }

    public boolean renterExists(UUID renterId) {
        return jdbcClient.sql("""
                SELECT EXISTS (SELECT 1 FROM renters
                WHERE id = :id AND organization_id = :organizationId)
                """)
                .param("id", renterId)
                .param("organizationId", currentOrganizationId())
                .query(Boolean.class)
                .single();
    }

    public boolean activeVendorExists(UUID vendorId) {
        return jdbcClient.sql("""
                SELECT EXISTS (SELECT 1 FROM vendors
                WHERE id = :id AND organization_id = :organizationId AND active = TRUE)
                """)
                .param("id", vendorId)
                .param("organizationId", currentOrganizationId())
                .query(Boolean.class)
                .single();
    }

    public MaintenanceItem create(CreateMaintenanceRequest request) {
        UUID id = jdbcClient.sql("""
                INSERT INTO maintenance_requests (organization_id, unit_id, renter_id, description, priority)
                VALUES (:organizationId, :unitId, :renterId, :description, :priority)
                RETURNING id
                """)
                .param("organizationId", currentOrganizationId())
                .param("unitId", request.unitId())
                .param("renterId", request.renterId())
                .param("description", request.description().trim())
                .param("priority", request.priority().name())
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
        return findById(id).orElseThrow(() -> new IllegalStateException("Created maintenance request was not found"));
    }

    public int assign(UUID id, UUID vendorId, long expectedVersion) {
        return jdbcClient.sql("""
                UPDATE maintenance_requests
                SET status = 'ASSIGNED', vendor_id = :vendorId,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND organization_id = :organizationId
                  AND status = 'SUBMITTED' AND version = :version
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .param("vendorId", vendorId)
                .param("version", expectedVersion)
                .update();
    }

    public int start(UUID id, long expectedVersion) {
        return transition(id, "ASSIGNED", "IN_PROGRESS", expectedVersion);
    }

    public int resolve(UUID id, long expectedVersion, String notes) {
        return jdbcClient.sql("""
                UPDATE maintenance_requests
                SET status = 'RESOLVED', resolution_notes = :notes, resolved_at = CURRENT_TIMESTAMP,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND organization_id = :organizationId
                  AND status = 'IN_PROGRESS' AND version = :version
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .param("notes", notes)
                .param("version", expectedVersion)
                .update();
    }

    private int transition(UUID id, String from, String to, long expectedVersion) {
        return jdbcClient.sql("""
                UPDATE maintenance_requests
                SET status = :to, version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND organization_id = :organizationId
                  AND status = :from AND version = :version
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .param("from", from)
                .param("to", to)
                .param("version", expectedVersion)
                .update();
    }

    private MaintenanceItem mapItem(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new MaintenanceItem(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("unit_id", UUID.class),
                resultSet.getObject("renter_id", UUID.class),
                resultSet.getString("description"),
                MaintenancePriority.valueOf(resultSet.getString("priority")),
                MaintenanceStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                resultSet.getObject("vendor_id", UUID.class),
                JdbcTimestamps.getInstant(resultSet, "requested_date"),
                JdbcTimestamps.getInstant(resultSet, "created_at"),
                JdbcTimestamps.getInstant(resultSet, "updated_at"));
    }
}
