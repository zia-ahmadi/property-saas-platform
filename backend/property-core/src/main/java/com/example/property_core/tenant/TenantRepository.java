package com.example.property_core.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.property_core.common.JdbcTimestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository extends TenantScopedRepository {
    public TenantRepository(JdbcClient jdbcClient) {
        super(jdbcClient);
    }

    public List<Tenant> findAll(String search, int page, int size) {
        String sql = """
                SELECT id, full_name, email, phone, created_at, updated_at
                FROM renters
                WHERE organization_id = :organizationId
                """;
        if (search != null && !search.isBlank()) {
            sql += " AND (full_name ILIKE :search OR email ILIKE :search)";
        }
        sql += " ORDER BY full_name, id LIMIT :size OFFSET :offset";

        var query = jdbcClient.sql(sql)
                .param("organizationId", currentOrganizationId())
                .param("size", size)
                .param("offset", Math.multiplyExact((long) page, size));
        if (search != null && !search.isBlank()) {
            query = query.param("search", "%" + search.trim() + "%");
        }
        return query.query(this::mapTenant).list();
    }

    public long count(String search) {
        String sql = "SELECT COUNT(*) FROM renters WHERE organization_id = :organizationId";
        if (search != null && !search.isBlank()) {
            sql += " AND (full_name ILIKE :search OR email ILIKE :search)";
        }
        var query = jdbcClient.sql(sql).param("organizationId", currentOrganizationId());
        if (search != null && !search.isBlank()) {
            query = query.param("search", "%" + search.trim() + "%");
        }
        return query.query(Long.class).single();
    }

    public Optional<Tenant> findById(UUID id) {
        return jdbcClient.sql("""
                SELECT id, full_name, email, phone, created_at, updated_at
                FROM renters
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .query(this::mapTenant)
                .optional();
    }

    public Tenant create(String fullName, String email, String phone) {
        UUID id = jdbcClient.sql("""
                INSERT INTO renters (organization_id, full_name, email, phone)
                VALUES (:organizationId, :fullName, :email, :phone)
                RETURNING id
                """)
                .param("organizationId", currentOrganizationId())
                .param("fullName", fullName)
                .param("email", email)
                .param("phone", phone)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
        return findById(id).orElseThrow(() -> new IllegalStateException("Created renter was not found"));
    }

    public Optional<Tenant> update(UUID id, String fullName, String email, String phone) {
        int updated = jdbcClient.sql("""
                UPDATE renters
                SET full_name = COALESCE(:fullName, full_name),
                    email = COALESCE(:email, email),
                    phone = COALESCE(:phone, phone),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .param("fullName", fullName)
                .param("email", email)
                .param("phone", phone)
                .update();
        return updated == 0 ? Optional.empty() : findById(id);
    }

    private Tenant mapTenant(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new Tenant(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("full_name"),
                resultSet.getString("email"),
                resultSet.getString("phone"),
                JdbcTimestamps.getInstant(resultSet, "created_at"),
                JdbcTimestamps.getInstant(resultSet, "updated_at"));
    }
}
