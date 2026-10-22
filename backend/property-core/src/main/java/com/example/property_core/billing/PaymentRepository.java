package com.example.property_core.billing;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.example.property_core.common.JdbcTimestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.property_core.tenant.TenantScopedRepository;

@Repository
public class PaymentRepository extends TenantScopedRepository {
    public PaymentRepository(JdbcClient jdbcClient) {
        super(jdbcClient);
    }

    public void lockIdempotencyKey(UUID key) {
        jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
                .param("lockKey", key.toString())
                .query((resultSet, rowNumber) -> resultSet.getObject(1))
                .single();
    }

    public Optional<PaymentWithHash> findByIdempotencyKey(UUID key) {
        return jdbcClient.sql("""
                SELECT id, invoice_id, amount, payment_method, status, created_at, request_hash
                FROM payments
                WHERE organization_id = :organizationId AND idempotency_key = :key
                """)
                .param("organizationId", currentOrganizationId())
                .param("key", key)
                .query((resultSet, rowNumber) -> new PaymentWithHash(
                        mapPayment(resultSet), resultSet.getString("request_hash")))
                .optional();
    }

    public Payment findById(UUID id) {
        return jdbcClient.sql("""
                SELECT id, invoice_id, amount, payment_method, status, created_at
                FROM payments
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .query((resultSet, rowNumber) -> mapPayment(resultSet))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
    }

    public Payment create(
            UUID invoiceId, BigDecimal amount, PaymentMethod method, UUID idempotencyKey, String requestHash) {
        UUID id = jdbcClient.sql("""
                INSERT INTO payments (
                    organization_id, invoice_id, amount, payment_method, status, idempotency_key, request_hash)
                VALUES (:organizationId, :invoiceId, :amount, :method, 'COMPLETED', :key, :requestHash)
                RETURNING id
                """)
                .param("organizationId", currentOrganizationId())
                .param("invoiceId", invoiceId)
                .param("amount", amount)
                .param("method", method.name())
                .param("key", idempotencyKey)
                .param("requestHash", requestHash)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
        return findById(id);
    }

    private Payment mapPayment(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new Payment(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("invoice_id", UUID.class),
                resultSet.getObject("amount", BigDecimal.class),
                PaymentMethod.valueOf(resultSet.getString("payment_method")),
                PaymentStatus.valueOf(resultSet.getString("status")),
                JdbcTimestamps.getInstant(resultSet, "created_at"));
    }

    private Payment mapPayment(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return mapPayment(resultSet);
    }

    public record PaymentWithHash(Payment payment, String requestHash) {
    }
}
