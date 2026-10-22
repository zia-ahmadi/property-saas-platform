package com.example.property_core.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Invoice(
        UUID id,
        UUID leaseId,
        UUID renterId,
        BigDecimal amount,
        LocalDate dueDate,
        InvoiceStatus status,
        Instant paidAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
