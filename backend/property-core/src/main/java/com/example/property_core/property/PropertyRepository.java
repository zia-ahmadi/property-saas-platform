package com.example.property_core.property;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.property_core.tenant.TenantScopedRepository;

@Repository
public class PropertyRepository extends TenantScopedRepository {

    public PropertyRepository(JdbcClient jdbcClient) {
        super(jdbcClient);
    }

    /**
     * Find a property by ID, scoped to the current organization.
     */
    public Optional<Property> findById(UUID id) {
        UUID orgId = currentOrganizationId();
        return jdbcClient.sql("""
                SELECT id, organization_id, name, address, status, created_at, updated_at
                FROM properties
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", orgId)
                .query((resultSet, rowNumber) -> new Property(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("address"),
                        resultSet.getString("status"),
                        resultSet.getObject("created_at", Instant.class),
                        resultSet.getObject("updated_at", Instant.class)))
                .optional();
    }

    /**
     * Find all properties for the current organization.
     */
    public List<Property> findAll() {
        UUID orgId = currentOrganizationId();
        return jdbcClient.sql("""
                SELECT id, organization_id, name, address, status, created_at, updated_at
                FROM properties
                WHERE organization_id = :organizationId
                ORDER BY name
                """)
                .param("organizationId", orgId)
                .query((resultSet, rowNumber) -> new Property(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("address"),
                        resultSet.getString("status"),
                        resultSet.getObject("created_at", Instant.class),
                        resultSet.getObject("updated_at", Instant.class)))
                .list();
    }

    /**
     * Find all properties for the current organization, optionally filtered by status.
     */
    public List<Property> findAll(String status) {
        UUID orgId = currentOrganizationId();
        String sql = """
                SELECT id, organization_id, name, address, status, created_at, updated_at
                FROM properties
                WHERE organization_id = :organizationId
                """;

        if (status != null && !status.isEmpty()) {
            sql += " AND status = :status";
        }

        sql += " ORDER BY name";

        var query = jdbcClient.sql(sql).param("organizationId", orgId);
        if (status != null && !status.isEmpty()) {
            query = query.param("status", status);
        }

        return query.query((resultSet, rowNumber) -> new Property(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("address"),
                resultSet.getString("status"),
                resultSet.getObject("created_at", Instant.class),
                resultSet.getObject("updated_at", Instant.class)))
                .list();
    }

    /**
     * Create a new property for the current organization.
     */
    public Property create(String name, String address, String status) {
        UUID orgId = currentOrganizationId();
        UUID id = jdbcClient.sql("""
                INSERT INTO properties (organization_id, name, address, status)
                VALUES (:organizationId, :name, :address, :status)
                RETURNING id
                """)
                .param("organizationId", orgId)
                .param("name", name)
                .param("address", address)
                .param("status", status)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
        return findById(id).orElseThrow(() -> new IllegalStateException("Failed to create property"));
    }

    /**
     * Update a property, scoped to the current organization.
     */
    public Optional<Property> update(UUID id, String name, String address, String status) {
        UUID orgId = currentOrganizationId();
        int updated = jdbcClient.sql("""
                UPDATE properties
                SET name = :name, address = :address, status = :status, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", orgId)
                .param("name", name)
                .param("address", address)
                .param("status", status)
                .update();

        if (updated == 0) {
            return Optional.empty();
        }

        return findById(id);
    }

    /**
     * Delete a property, scoped to the current organization.
     * Returns true if the property was deleted, false if it didn't exist or belonged to another organization.
     */
    public boolean delete(UUID id) {
        UUID orgId = currentOrganizationId();
        int deleted = jdbcClient.sql("""
                DELETE FROM properties
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", orgId)
                .update();
        return deleted > 0;
    }
}
