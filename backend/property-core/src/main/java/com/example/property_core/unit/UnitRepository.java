package com.example.property_core.unit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                SELECT id, organization_id, property_id, name, status, bedrooms, bathrooms, monthly_rent, created_at, updated_at
                FROM units
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", orgId)
                .query((resultSet, rowNumber) -> new Unit(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getObject("property_id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("status"),
                        (Integer) resultSet.getObject("bedrooms"),
                        resultSet.getObject("bathrooms", BigDecimal.class),
                        resultSet.getObject("monthly_rent", BigDecimal.class),
                        resultSet.getObject("created_at", Instant.class),
                        resultSet.getObject("updated_at", Instant.class)))
                .optional();
    }

    /**
     * Find all units for the current organization.
     */
    public List<Unit> findAll() {
        UUID orgId = currentOrganizationId();
        return jdbcClient.sql("""
                SELECT id, organization_id, property_id, name, status, bedrooms, bathrooms, monthly_rent, created_at, updated_at
                FROM units
                WHERE organization_id = :organizationId
                ORDER BY property_id, name
                """)
                .param("organizationId", orgId)
                .query((resultSet, rowNumber) -> new Unit(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getObject("property_id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("status"),
                        (Integer) resultSet.getObject("bedrooms"),
                        resultSet.getObject("bathrooms", BigDecimal.class),
                        resultSet.getObject("monthly_rent", BigDecimal.class),
                        resultSet.getObject("created_at", Instant.class),
                        resultSet.getObject("updated_at", Instant.class)))
                .list();
    }

    /**
     * Find all units for a specific property, scoped to the current organization.
     */
    public List<Unit> findByPropertyId(UUID propertyId) {
        UUID orgId = currentOrganizationId();
        return jdbcClient.sql("""
                SELECT id, organization_id, property_id, name, status, bedrooms, bathrooms, monthly_rent, created_at, updated_at
                FROM units
                WHERE property_id = :propertyId AND organization_id = :organizationId
                ORDER BY name
                """)
                .param("propertyId", propertyId)
                .param("organizationId", orgId)
                .query((resultSet, rowNumber) -> new Unit(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getObject("property_id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("status"),
                        (Integer) resultSet.getObject("bedrooms"),
                        resultSet.getObject("bathrooms", BigDecimal.class),
                        resultSet.getObject("monthly_rent", BigDecimal.class),
                        resultSet.getObject("created_at", Instant.class),
                        resultSet.getObject("updated_at", Instant.class)))
                .list();
    }

    /**
     * Create a new unit for the current organization.
     */
    public Unit create(UUID propertyId, String name, String status, Integer bedrooms, BigDecimal bathrooms, BigDecimal monthlyRent) {
        UUID orgId = currentOrganizationId();
        UUID id = jdbcClient.sql("""
                INSERT INTO units (organization_id, property_id, name, status, bedrooms, bathrooms, monthly_rent)
                VALUES (:organizationId, :propertyId, :name, :status, :bedrooms, :bathrooms, :monthlyRent)
                RETURNING id
                """)
                .param("organizationId", orgId)
                .param("propertyId", propertyId)
                .param("name", name)
                .param("status", status)
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
    public Optional<Unit> update(UUID id, String name, String status, Integer bedrooms, BigDecimal bathrooms, BigDecimal monthlyRent) {
        UUID orgId = currentOrganizationId();
        int updated = jdbcClient.sql("""
                UPDATE units
                SET name = :name, status = :status, bedrooms = :bedrooms, bathrooms = :bathrooms, monthly_rent = :monthlyRent, updated_at = CURRENT_TIMESTAMP
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
