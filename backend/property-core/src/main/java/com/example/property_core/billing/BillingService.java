package com.example.property_core.billing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.example.property_core.common.Page;
import com.example.property_core.exception.ApiException;
import com.example.property_core.lease.Lease;
import com.example.property_core.lease.LeaseRepository;
import com.example.property_core.lease.LeaseStatus;
import com.example.property_core.notification.NotificationPublisher;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final LeaseRepository leaseRepository;
    private final NotificationPublisher notificationPublisher;

    public BillingService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            LeaseRepository leaseRepository,
            NotificationPublisher notificationPublisher) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.leaseRepository = leaseRepository;
        this.notificationPublisher = notificationPublisher;
    }

    public Page<Invoice> listInvoices(
            InvoiceStatus status, UUID leaseId, boolean overdueOnly, int page, int size) {
        List<Invoice> content = invoiceRepository.findAll(status, leaseId, overdueOnly, page, size);
        long totalElements = invoiceRepository.count(status, leaseId, overdueOnly);
        return new Page<>(content, page, size, totalElements, (int) Math.ceil((double) totalElements / size));
    }

    public Invoice getInvoice(UUID id) {
        return invoiceRepository.findById(id).orElseThrow(this::invoiceNotFound);
    }

    @Transactional
    public Invoice createInvoice(UUID leaseId, BigDecimal amount, LocalDate dueDate) {
        validateAmount(amount, false);
        if (dueDate == null || !dueDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException("Invoice due date must be in the future");
        }
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lease not found"));
        if (lease.status() != LeaseStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "Invoices can only be created for active leases");
        }
        return invoiceRepository.createManual(leaseId, amount, dueDate);
    }

    public Page<Payment> listInvoicePayments(UUID invoiceId, int page, int size) {
        try {
            List<Payment> content = invoiceRepository.findPayments(invoiceId, page, size);
            long totalElements = invoiceRepository.countPayments(invoiceId);
            return new Page<>(content, page, size, totalElements, (int) Math.ceil((double) totalElements / size));
        } catch (IllegalArgumentException exception) {
            throw invoiceNotFound();
        }
    }

    @Transactional
    public RecordedPayment recordPayment(
            UUID idempotencyKey, UUID invoiceId, BigDecimal amount, PaymentMethod method) {
        validateAmount(amount, true);
        paymentRepository.lockIdempotencyKey(idempotencyKey);
        String requestHash = requestHash(invoiceId, amount, method);

        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!MessageDigest.isEqual(
                    existing.get().requestHash().getBytes(StandardCharsets.US_ASCII),
                    requestHash.getBytes(StandardCharsets.US_ASCII))) {
                throw new ApiException(HttpStatus.CONFLICT, "Idempotency key was reused with a different request");
            }
            return new RecordedPayment(existing.get().payment(), true);
        }

        Invoice invoice;
        try {
            invoice = invoiceRepository.lockById(invoiceId);
        } catch (IllegalArgumentException exception) {
            throw invoiceNotFound();
        }
        if (invoice.status() != InvoiceStatus.ISSUED && invoice.status() != InvoiceStatus.OVERDUE) {
            throw paymentUnprocessable("Invoice is not payable");
        }

        BigDecimal outstanding = invoice.amount().subtract(invoiceRepository.completedPaymentsTotal(invoiceId));
        if (amount.compareTo(outstanding) > 0) {
            throw paymentUnprocessable("Payment exceeds the outstanding invoice balance");
        }

        Payment payment = paymentRepository.create(invoiceId, amount, method, idempotencyKey, requestHash);
        BigDecimal settled = invoiceRepository.completedPaymentsTotal(invoiceId);
        if (settled.compareTo(invoice.amount()) >= 0) {
            invoiceRepository.markPaid(invoiceId);
        }
        notificationPublisher.publish("PAYMENT_RECEIVED", java.util.Map.of(
                "paymentId", payment.id(),
                "invoiceId", invoiceId,
                "amount", payment.amount()));
        return new RecordedPayment(payment, false);
    }

    public Payment getPayment(UUID paymentId) {
        try {
            return paymentRepository.findById(paymentId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payment not found");
        }
    }

    private void validateAmount(BigDecimal amount, boolean mustBePositive) {
        if (amount == null
                || (mustBePositive ? amount.signum() <= 0 : amount.signum() < 0)
                || amount.scale() > 2
                || amount.precision() > 12) {
            throw new IllegalArgumentException(
                    mustBePositive
                            ? "Amount must be positive and have at most 2 decimal places"
                            : "Amount must be non-negative and have at most 2 decimal places");
        }
    }

    private String requestHash(UUID invoiceId, BigDecimal amount, PaymentMethod method) {
        String canonical = invoiceId + "|" + amount.stripTrailingZeros().toPlainString() + "|" + method.name();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ApiException invoiceNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Invoice not found");
    }

    private ApiException paymentUnprocessable(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    public record RecordedPayment(Payment payment, boolean replay) {
    }
}
