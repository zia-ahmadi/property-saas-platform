package com.example.property_core.platformadmin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import com.example.property_core.common.Page;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformAdminRepository {
    private final JdbcClient jdbcClient;

    public PlatformAdminRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Page<OrganizationSummary> listOrganizations(int page, int size) {
        long total = jdbcClient.sql("SELECT COUNT(*) FROM organizations")
                .query(Long.class)
                .single();
        List<OrganizationSummary> organizations = jdbcClient.sql("""
                SELECT o.id, o.name, o.created_at,
                       COUNT(u.id) AS user_count,
                       COUNT(u.id) FILTER (WHERE u.active) AS active_user_count
                FROM organizations o
                LEFT JOIN users u ON u.organization_id = o.id
                GROUP BY o.id, o.name, o.created_at
                ORDER BY o.name, o.id
                LIMIT :size OFFSET :offset
                """)
                .param("size", size)
                .param("offset", (long) page * size)
                .query((resultSet, rowNumber) -> new OrganizationSummary(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getLong("user_count"),
                        resultSet.getLong("active_user_count")))
                .list();
        return new Page<>(organizations, page, size, total, (int) Math.ceil((double) total / size));
    }

    public boolean renameOrganization(UUID organizationId, String name) {
        return jdbcClient.sql("""
                UPDATE organizations
                SET name = :name, updated_at = CURRENT_TIMESTAMP
                WHERE id = :organizationId
                """)
                .param("name", name)
                .param("organizationId", organizationId)
                .update() > 0;
    }

    public Page<PlatformUserSummary> listUsers(UUID organizationId, int page, int size) {
        String filter = organizationId == null ? "" : "WHERE u.organization_id = :organizationId";
        JdbcClient.StatementSpec countQuery = jdbcClient.sql(
                "SELECT COUNT(*) FROM users u " + filter);
        if (organizationId != null) countQuery = countQuery.param("organizationId", organizationId);
        long total = countQuery.query(Long.class).single();

        JdbcClient.StatementSpec usersQuery = jdbcClient.sql("""
                SELECT u.id, u.organization_id, o.name AS organization_name,
                       u.email, u.full_name, u.active,
                       COALESCE(string_agg(ur.role, ',' ORDER BY ur.role), '') AS roles,
                       EXISTS (SELECT 1 FROM platform_admins pa WHERE pa.user_id = u.id) AS platform_admin
                FROM users u
                JOIN organizations o ON o.id = u.organization_id
                LEFT JOIN user_roles ur
                    ON ur.organization_id = u.organization_id AND ur.user_id = u.id
                """ + filter + """
                GROUP BY u.id, u.organization_id, o.name, u.email, u.full_name, u.active
                ORDER BY u.email, u.id
                LIMIT :size OFFSET :offset
                """)
                .param("size", size)
                .param("offset", (long) page * size);
        if (organizationId != null) usersQuery = usersQuery.param("organizationId", organizationId);

        List<PlatformUserSummary> users = usersQuery.query((resultSet, rowNumber) -> {
            String roles = resultSet.getString("roles");
            return new PlatformUserSummary(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("organization_id", UUID.class),
                    resultSet.getString("organization_name"),
                    resultSet.getString("email"),
                    resultSet.getString("full_name"),
                    resultSet.getBoolean("active"),
                    roles.isEmpty() ? List.of() : List.of(roles.split(",")),
                    resultSet.getBoolean("platform_admin"));
        }).list();
        return new Page<>(users, page, size, total, (int) Math.ceil((double) total / size));
    }

    public Optional<UUID> findOrganizationIdByUserId(UUID userId) {
        return jdbcClient.sql("SELECT organization_id FROM users WHERE id = :userId")
                .param("userId", userId)
                .query(UUID.class)
                .optional();
    }

    public boolean isPlatformAdmin(UUID userId) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM platform_admins WHERE user_id = :userId)")
                .param("userId", userId)
                .query(Boolean.class)
                .single();
    }

    public boolean setUserActive(UUID userId, boolean active) {
        return jdbcClient.sql("""
                UPDATE users
                SET active = :active, updated_at = CURRENT_TIMESTAMP
                WHERE id = :userId
                """)
                .param("active", active)
                .param("userId", userId)
                .update() > 0;
    }

    public void replaceOrganizationRole(UUID organizationId, UUID userId, String role) {
        jdbcClient.sql("""
                DELETE FROM user_roles
                WHERE organization_id = :organizationId AND user_id = :userId
                """)
                .param("organizationId", organizationId)
                .param("userId", userId)
                .update();
        jdbcClient.sql("""
                INSERT INTO user_roles (organization_id, user_id, role)
                VALUES (:organizationId, :userId, :role)
                """)
                .param("organizationId", organizationId)
                .param("userId", userId)
                .param("role", role)
                .update();
    }

    public boolean addPlatformAdminByEmail(String email) {
        return jdbcClient.sql("""
                INSERT INTO platform_admins (user_id)
                SELECT id FROM users WHERE email = :email
                ON CONFLICT (user_id) DO NOTHING
                """)
                .param("email", email)
                .update() > 0;
    }

    public boolean userExistsByEmail(String email) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM users WHERE email = :email)")
                .param("email", email)
                .query(Boolean.class)
                .single();
    }

    public record OrganizationSummary(
            UUID id, String name, Instant createdAt, long userCount, long activeUserCount) {
    }

    public record PlatformUserSummary(
            UUID id,
            UUID organizationId,
            String organizationName,
            String email,
            String fullName,
            boolean active,
            List<String> roles,
            boolean platformAdmin) {
        public PlatformUserSummary {
            roles = List.copyOf(roles);
        }
    }
}
