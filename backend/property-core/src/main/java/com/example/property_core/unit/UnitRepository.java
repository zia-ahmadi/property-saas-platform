package com.example.property_core.unit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.property_core.common.JdbcTimestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.property_core.tenant.TenantScopedRepository;

@Repository
public class UnitRepository extends TenantScopedRepository {

    public UnitRepository(JdbcClient jdbcClient) {
        super(jdbcClient);
    }

    /**
     * Find a unit by ID, scoped to the current organization.
     */
    public Optional<Unit> findById(UUID id) {
        UUID orgId = currentOrganizationId();
        return jdbcClient.sql("""
                SELECT id, property_id, name, status, bedrooms, bathrooms, monthly_rent, created_at, updated_at
                FROM units
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", orgId)
                .query(this::mapUnit)
                .optional();
    }

    /**
     * Find all units for the current organization.
     */
    public List<Unit> findByPropertyId(UUID propertyId, int page, int size, String status) {
        UUID orgId = currentOrganizationId();
        String sql = """
                SELECT id, property_id, name, status, bedrooms, bathrooms, monthly_rent, created_at, updated_at
                FROM units
                WHERE organization_id = :organizationId AND property_id = :propertyId
                """;
        if (status != null) {
            sql += " AND status = :status";
        }
        sql += " ORDER BY name, id LIMIT :size OFFSET :offset";
        var query = jdbcClient.sql(sql)
                .param("organizationId", orgId)
                .param("propertyId", propertyId)
                .param("size", size)
                .param("offset", Math.multiplyExact((long) page, size));
        if (status != null) {
            query = query.param("status", status);
        }
        return query.query(this::mapUnit).list();
    }

    public long countByPropertyId(UUID propertyId, String status) {
        UUID orgId = currentOrganizationId();
        String sql = """
                SELECT COUNT(*) FROM units
                WHERE organization_id = :organizationId AND property_id = :propertyId
                """;
        if (status != null) {
            sql += " AND status = :status";
        }
        var query = jdbcClient.sql(sql)
                .param("organizationId", orgId)
                .param("propertyId", propertyId);
        if (status != null) {
            query = query.param("status", status);
        }
        return query.query(Long.class).single();
    }

    /**
     * Create a new unit for the current organization.
     */
    public Unit create(UUID propertyId, String name, Integer bedrooms, BigDecimal bathrooms, BigDecimal monthlyRent) {
        UUID orgId = currentOrganizationId();
        UUID id = jdbcClient.sql("""
                INSERT INTO units (organization_id, property_id, name, bedrooms, bathrooms, monthly_rent)
                VALUES (:organizationId, :propertyId, :name, :bedrooms, :bathrooms, :monthlyRent)
                RETURNING id
                """)
                .param("organizationId", orgId)
                .param("propertyId", propertyId)
                .param("name", name)
                .param("bedrooms", bedrooms)
                .param("bathrooms", bathrooms)
                .param("monthlyRent", monthlyRent)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
        return findById(id).orElseThrow(() -> new IllegalStateException("Failed to create unit"));
    }

    /**
     * Update a unit, scoped to the current organization.
     */
    public Optional<Unit> update(
            UUID id, String name, String status, Integer bedrooms, BigDecimal bathrooms, BigDecimal monthlyRent) {
        UUID orgId = currentOrganizationId();
        int updated = jdbcClient.sql("""
                UPDATE units
                SET name = COALESCE(:name, name),
                    status = COALESCE(:status, status),
                    bedrooms = COALESCE(:bedrooms, bedrooms),
                    bathrooms = COALESCE(:bathrooms, bathrooms),
                    monthly_rent = COALESCE(:monthlyRent, monthly_rent),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", orgId)
                .param("name", name)
                .param("status", status)
                .param("bedrooms", bedrooms)
                .param("bathrooms", bathrooms)
                .param("monthlyRent", monthlyRent)
                .update();

        if (updated == 0) {
            return Optional.empty();
        }

        return findById(id);
    }

    private Unit mapUnit(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new Unit(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("property_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("status"),
                (Integer) resultSet.getObject("bedrooms"),
                resultSet.getObject("bathrooms", BigDecimal.class),
                resultSet.getObject("monthly_rent", BigDecimal.class),
                JdbcTimestamps.getInstant(resultSet, "created_at"),
                JdbcTimestamps.getInstant(resultSet, "updated_at"));
    }

    /**
     * Delete a unit, scoped to the current organization.
     * Returns true if the unit was deleted, false if it didn't exist or belonged to another organization.
     */
    public boolean delete(UUID id) {
        UUID orgId = currentOrganizationId();
        int deleted = jdbcClient.sql("""
                DELETE FROM units
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", orgId)
                .update();
        return deleted > 0;
    }
}
