package com.example.property_core.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {
    private final JdbcClient jdbcClient;

    public AuthRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<AuthUser> findByEmail(String email) {
        Optional<AuthUser> user = jdbcClient.sql("""
                SELECT id, organization_id, email, password_hash, full_name, active
                FROM users
                WHERE email = :email
                """)
                .param("email", email)
                .query((resultSet, rowNumber) -> new AuthUser(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getString("email"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("full_name"),
                        resultSet.getBoolean("active"),
                        List.of()))
                .optional();

        return user.map(account -> {
            List<String> roles = jdbcClient.sql("""
                    SELECT role
                    FROM user_roles
                    WHERE organization_id = :organizationId AND user_id = :userId
                    ORDER BY role
                    """)
                    .param("organizationId", account.organizationId())
                    .param("userId", account.id())
                    .query(String.class)
                    .list();
            boolean platformAdmin = jdbcClient.sql("""
                    SELECT EXISTS (
                        SELECT 1 FROM platform_admins WHERE user_id = :userId
                    )
                    """)
                    .param("userId", account.id())
                    .query(Boolean.class)
                    .single();
            if (platformAdmin) {
                roles = new java.util.ArrayList<>(roles);
                roles.add("PLATFORM_ADMIN");
            }
            return account.withRoles(roles);
        });
    }

    public boolean existsByEmail(String email) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM users WHERE email = :email)")
                .param("email", email)
                .query(Boolean.class)
                .single();
    }

    public UUID createOrganization(String name) {
        return jdbcClient.sql("""
                INSERT INTO organizations (name)
                VALUES (:name)
                RETURNING id
                """)
                .param("name", name)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
    }

    public UUID createUser(UUID organizationId, String email, String passwordHash, String fullName) {
        return jdbcClient.sql("""
                INSERT INTO users (organization_id, email, password_hash, full_name)
                VALUES (:organizationId, :email, :passwordHash, :fullName)
                RETURNING id
                """)
                .param("organizationId", organizationId)
                .param("email", email)
                .param("passwordHash", passwordHash)
                .param("fullName", fullName)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
    }

    public void assignRole(UUID organizationId, UUID userId, String role) {
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

}
