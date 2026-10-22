package com.example.property_core.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.property_core.common.JdbcTimestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.property_core.exception.ApiException;
import com.example.property_core.tenant.TenantScopedRepository;

import org.springframework.http.HttpStatus;

@Repository
public class InvoiceRepository extends TenantScopedRepository {
    public InvoiceRepository(JdbcClient jdbcClient) {
        super(jdbcClient);
    }

    public List<Invoice> findAll(InvoiceStatus status, UUID leaseId, boolean overdueOnly, int page, int size) {
        String sql = """
                SELECT id, lease_id, renter_id, amount, due_date, status, paid_at,
                       version, created_at, updated_at
                FROM invoices
                WHERE organization_id = :organizationId
                """;
        if (status != null) sql += " AND status = :status";
        if (leaseId != null) sql += " AND lease_id = :leaseId";
        if (overdueOnly) sql += " AND status = 'OVERDUE'";
        sql += " ORDER BY due_date DESC, id LIMIT :size OFFSET :offset";
        var query = jdbcClient.sql(sql)
                .param("organizationId", currentOrganizationId())
                .param("size", size)
                .param("offset", Math.multiplyExact((long) page, size));
        if (status != null) query = query.param("status", status.name());
        if (leaseId != null) query = query.param("leaseId", leaseId);
        return query.query(this::mapInvoice).list();
    }

    public long count(InvoiceStatus status, UUID leaseId, boolean overdueOnly) {
        String sql = "SELECT COUNT(*) FROM invoices WHERE organization_id = :organizationId";
        if (status != null) sql += " AND status = :status";
        if (leaseId != null) sql += " AND lease_id = :leaseId";
        if (overdueOnly) sql += " AND status = 'OVERDUE'";
        var query = jdbcClient.sql(sql).param("organizationId", currentOrganizationId());
        if (status != null) query = query.param("status", status.name());
        if (leaseId != null) query = query.param("leaseId", leaseId);
        return query.query(Long.class).single();
    }

    public Optional<Invoice> findById(UUID id) {
        return jdbcClient.sql("""
                SELECT id, lease_id, renter_id, amount, due_date, status, paid_at,
                       version, created_at, updated_at
                FROM invoices
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .query(this::mapInvoice)
                .optional();
    }

    public Invoice createManual(UUID leaseId, BigDecimal amount, LocalDate dueDate) {
        UUID id = jdbcClient.sql("""
                INSERT INTO invoices (organization_id, lease_id, renter_id, amount, due_date, status)
                SELECT l.organization_id, l.id, l.renter_id, :amount, :dueDate, 'ISSUED'
                FROM leases l
                WHERE l.id = :leaseId AND l.organization_id = :organizationId AND l.status = 'ACTIVE'
                RETURNING id
                """)
                .param("leaseId", leaseId)
                .param("organizationId", currentOrganizationId())
                .param("amount", amount)
                .param("dueDate", dueDate)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Lease is no longer active"));
        return findById(id).orElseThrow(() -> new IllegalStateException("Created invoice was not found"));
    }

    public boolean exists(UUID id) {
        return jdbcClient.sql("""
                SELECT EXISTS (SELECT 1 FROM invoices
                WHERE id = :id AND organization_id = :organizationId)
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .query(Boolean.class)
                .single();
    }

    public Invoice lockById(UUID id) {
        return jdbcClient.sql("""
                SELECT id, lease_id, renter_id, amount, due_date, status, paid_at,
                       version, created_at, updated_at
                FROM invoices
                WHERE id = :id AND organization_id = :organizationId
                FOR UPDATE
                """)
                .param("id", id)
                .param("organizationId", currentOrganizationId())
                .query(this::mapInvoice)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
    }

    public BigDecimal completedPaymentsTotal(UUID invoiceId) {
        return jdbcClient.sql("""
                SELECT COALESCE(SUM(amount), 0)
                FROM payments
                WHERE organization_id = :organizationId AND invoice_id = :invoiceId
                  AND status = 'COMPLETED'
                """)
                .param("organizationId", currentOrganizationId())
                .param("invoiceId", invoiceId)
                .query(BigDecimal.class)
                .single();
    }

    public void markPaid(UUID invoiceId) {
        jdbcClient.sql("""
                UPDATE invoices
                SET status = 'PAID', paid_at = CURRENT_TIMESTAMP,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", invoiceId)
                .param("organizationId", currentOrganizationId())
                .update();
    }

    public List<Payment> findPayments(UUID invoiceId, int page, int size) {
        if (!exists(invoiceId)) {
            throw new IllegalArgumentException("Invoice not found");
        }
        return jdbcClient.sql("""
                SELECT id, invoice_id, amount, payment_method, status, created_at
                FROM payments
                WHERE organization_id = :organizationId AND invoice_id = :invoiceId
                ORDER BY created_at DESC, id LIMIT :size OFFSET :offset
                """)
                .param("organizationId", currentOrganizationId())
                .param("invoiceId", invoiceId)
                .param("size", size)
                .param("offset", Math.multiplyExact((long) page, size))
                .query(this::mapPayment)
                .list();
    }

    public long countPayments(UUID invoiceId) {
        if (!exists(invoiceId)) {
            throw new IllegalArgumentException("Invoice not found");
        }
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM payments
                WHERE organization_id = :organizationId AND invoice_id = :invoiceId
                """)
                .param("organizationId", currentOrganizationId())
                .param("invoiceId", invoiceId)
                .query(Long.class)
                .single();
    }

    private Invoice mapInvoice(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new Invoice(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("lease_id", UUID.class),
                resultSet.getObject("renter_id", UUID.class),
                resultSet.getObject("amount", BigDecimal.class),
                resultSet.getObject("due_date", LocalDate.class),
                InvoiceStatus.valueOf(resultSet.getString("status")),
                JdbcTimestamps.getInstant(resultSet, "paid_at"),
                resultSet.getLong("version"),
                JdbcTimestamps.getInstant(resultSet, "created_at"),
                JdbcTimestamps.getInstant(resultSet, "updated_at"));
    }

    private Payment mapPayment(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new Payment(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("invoice_id", UUID.class),
                resultSet.getObject("amount", BigDecimal.class),
                PaymentMethod.valueOf(resultSet.getString("payment_method")),
                PaymentStatus.valueOf(resultSet.getString("status")),
                JdbcTimestamps.getInstant(resultSet, "created_at"));
    }
}
