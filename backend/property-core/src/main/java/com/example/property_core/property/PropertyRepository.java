package com.example.property_core.property;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.property_core.common.JdbcTimestamps;
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
     * Includes computed unitCount and occupiedUnits.
     */
    public Optional<Property> findById(UUID id) {
        UUID orgId = currentOrganizationId();
        return jdbcClient.sql("""
                SELECT p.id, p.name, p.address, p.status, p.created_at, p.updated_at,
                       COALESCE(COUNT(DISTINCT u.id), 0) as unit_count,
                       COALESCE(COUNT(DISTINCT CASE WHEN u.status = 'OCCUPIED' THEN u.id END), 0) as occupied_units
                FROM properties p
                LEFT JOIN units u ON p.id = u.property_id AND p.organization_id = u.organization_id
                WHERE p.id = :id AND p.organization_id = :organizationId
                GROUP BY p.id, p.name, p.address, p.status, p.created_at, p.updated_at
                """)
                .param("id", id)
                .param("organizationId", orgId)
                .query((resultSet, rowNumber) -> new Property(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("address"),
                        resultSet.getString("status"),
                        resultSet.getInt("unit_count"),
                        resultSet.getInt("occupied_units"),
                        JdbcTimestamps.getInstant(resultSet, "created_at"),
                        JdbcTimestamps.getInstant(resultSet, "updated_at")))
                .optional();
    }

    /**
     * Find all properties for the current organization with pagination.
     * Includes computed unitCount and occupiedUnits.
     */
    public List<Property> findAll(int page, int size, String sort, String status) {
        UUID orgId = currentOrganizationId();
        long offset = Math.multiplyExact((long) page, size);

        String sql = """
                SELECT p.id, p.name, p.address, p.status, p.created_at, p.updated_at,
                       COALESCE(COUNT(DISTINCT u.id), 0) as unit_count,
                       COALESCE(COUNT(DISTINCT CASE WHEN u.status = 'OCCUPIED' THEN u.id END), 0) as occupied_units
                FROM properties p
                LEFT JOIN units u ON p.id = u.property_id AND p.organization_id = u.organization_id
                WHERE p.organization_id = :organizationId
                """;

        if (status != null && !status.isEmpty()) {
            sql += " AND p.status = :status";
        }

        sql += " GROUP BY p.id, p.name, p.address, p.status, p.created_at, p.updated_at";

        sql += " ORDER BY " + orderBy(sort);

        sql += " LIMIT :size OFFSET :offset";

        var query = jdbcClient.sql(sql)
                .param("organizationId", orgId)
                .param("size", size)
                .param("offset", offset);

        if (status != null && !status.isEmpty()) {
            query = query.param("status", status);
        }

        return query.query((resultSet, rowNumber) -> new Property(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("address"),
                resultSet.getString("status"),
                resultSet.getInt("unit_count"),
                resultSet.getInt("occupied_units"),
                JdbcTimestamps.getInstant(resultSet, "created_at"),
                JdbcTimestamps.getInstant(resultSet, "updated_at")))
                .list();
    }

    /**
     * Count total properties for the current organization, optionally filtered by status.
     */
    public long count(String status) {
        UUID orgId = currentOrganizationId();
        String sql = """
                SELECT COUNT(*)
                FROM properties
                WHERE organization_id = :organizationId
                """;

        if (status != null && !status.isEmpty()) {
            sql += " AND status = :status";
        }

        var query = jdbcClient.sql(sql).param("organizationId", orgId);
        if (status != null && !status.isEmpty()) {
            query = query.param("status", status);
        }

        return query.query(Long.class).single();
    }

    private String orderBy(String sort) {
        if (sort == null || sort.isBlank()) {
            return "p.name ASC, p.id ASC";
        }

        String[] parts = sort.split(",", -1);
        String column = switch (parts[0].trim()) {
            case "name" -> "p.name";
            case "address" -> "p.address";
            case "status" -> "p.status";
            case "createdAt" -> "p.created_at";
            case "updatedAt" -> "p.updated_at";
            case "unitCount" -> "unit_count";
            case "occupiedUnits" -> "occupied_units";
            default -> throw new IllegalArgumentException("Unsupported property sort field");
        };
        if (parts.length > 2 || (parts.length == 2
                && !parts[1].trim().equalsIgnoreCase("asc")
                && !parts[1].trim().equalsIgnoreCase("desc"))) {
            throw new IllegalArgumentException("Sort direction must be asc or desc");
        }
        String direction = parts.length == 2 && parts[1].trim().equalsIgnoreCase("desc")
                ? "DESC"
                : "ASC";
        return column + " " + direction + ", p.id ASC";
    }

    /**
     * Check if a property has active units or leases.
     * Returns true if the property has units that are OCCUPIED or has active leases.
     */
    public boolean hasActiveDependencies(UUID id) {
        UUID orgId = currentOrganizationId();
        
        Integer units = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM units WHERE property_id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", orgId)
                .query(Integer.class)
                .single();
        
        if (units != null && units > 0) {
            return true;
        }

        Integer activeLeases = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM leases
                WHERE unit_id IN (
                    SELECT id FROM units WHERE property_id = :id AND organization_id = :organizationId
                ) AND organization_id = :organizationId AND status = 'ACTIVE'
                """)
                .param("id", id)
                .param("organizationId", orgId)
                .query(Integer.class)
                .single();
        
        return activeLeases != null && activeLeases > 0;
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
