package com.example.property_core.vendor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.property_core.common.JdbcTimestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.property_core.tenant.TenantScopedRepository;

@Repository
public class VendorRepository extends TenantScopedRepository {
    public VendorRepository(JdbcClient jdbcClient) {
        super(jdbcClient);
    }

    public List<Vendor> findAll(Boolean active, int page, int size) {
        String sql = """
                SELECT id, name, service_type, email, phone, active, created_at, updated_at
                FROM vendors
                WHERE organization_id = :organizationId
                """ + (active == null ? "" : " AND active = :active")
                + " ORDER BY name, id LIMIT :size OFFSET :offset";
        var query = jdbcClient.sql(sql)
                .param("organizationId", currentOrganizationId())
                .param("size", size)
                .param("offset", Math.multiplyExact((long) page, size));
        if (active != null) query = query.param("active", active);
        return query.query(this::mapVendor).list();
    }

    public long count(Boolean active) {
        String sql = "SELECT COUNT(*) FROM vendors WHERE organization_id = :organizationId"
                + (active == null ? "" : " AND active = :active");
        var query = jdbcClient.sql(sql).param("organizationId", currentOrganizationId());
        if (active != null) query = query.param("active", active);
        return query.query(Long.class).single();
    }

    public Optional<Vendor> findById(UUID id) {
        return jdbcClient.sql("""
                SELECT id, name, service_type, email, phone, active, created_at, updated_at
                FROM vendors WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .query(this::mapVendor)
                .optional();
    }

    public Vendor create(String name, String serviceType, String email, String phone) {
        UUID id = jdbcClient.sql("""
                INSERT INTO vendors (organization_id, name, service_type, email, phone)
                VALUES (:organizationId, :name, :serviceType, :email, :phone)
                RETURNING id
                """)
                .param("organizationId", currentOrganizationId())
                .param("name", name)
                .param("serviceType", serviceType)
                .param("email", email)
                .param("phone", phone)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
        return findById(id).orElseThrow(() -> new IllegalStateException("Created vendor was not found"));
    }

    public Optional<Vendor> update(UUID id, UpdateVendorRequest request) {
        var query = jdbcClient.sql("""
                UPDATE vendors
                SET name = CASE WHEN :nameProvided THEN :name ELSE name END,
                    service_type = CASE WHEN :serviceTypeProvided THEN :serviceType ELSE service_type END,
                    email = CASE WHEN :emailProvided THEN :email ELSE email END,
                    phone = CASE WHEN :phoneProvided THEN :phone ELSE phone END,
                    active = CASE WHEN :activeProvided THEN :active ELSE active END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .param("nameProvided", request.nameProvided())
                .param("name", request.name())
                .param("serviceTypeProvided", request.serviceTypeProvided())
                .param("serviceType", request.serviceType())
                .param("emailProvided", request.emailProvided())
                .param("email", request.email())
                .param("phoneProvided", request.phoneProvided())
                .param("phone", request.phone())
                .param("activeProvided", request.activeProvided())
                .param("active", request.active());
        return query.update() == 0 ? Optional.empty() : findById(id);
    }

    private Vendor mapVendor(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new Vendor(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("service_type"),
                resultSet.getString("email"),
                resultSet.getString("phone"),
                resultSet.getBoolean("active"),
                JdbcTimestamps.getInstant(resultSet, "created_at"),
                JdbcTimestamps.getInstant(resultSet, "updated_at"));
    }
}
