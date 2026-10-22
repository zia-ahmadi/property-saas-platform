package com.example.property_core.billing;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.example.property_core.common.Page;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class BillingController {
    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/invoices")
    public ResponseEntity<Page<Invoice>> listInvoices(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID leaseId,
            @RequestParam(defaultValue = "false") boolean overdueOnly) {
        return ResponseEntity.ok(billingService.listInvoices(status, leaseId, overdueOnly, page, size));
    }

    @PostMapping(path = "/invoices", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Invoice> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        Invoice invoice = billingService.createInvoice(request.leaseId(), request.amount(), request.dueDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
    }

    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<Invoice> getInvoice(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(billingService.getInvoice(invoiceId));
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    public ResponseEntity<Page<Payment>> listInvoicePayments(
            @PathVariable UUID invoiceId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(billingService.listInvoicePayments(invoiceId, page, size));
    }

    @PostMapping(path = "/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Payment> recordPayment(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        BillingService.RecordedPayment result = billingService.recordPayment(
                idempotencyKey, request.invoiceId(), request.amount(), request.paymentMethod());
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result.payment());
    }

    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<Payment> getPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(billingService.getPayment(paymentId));
    }
}
