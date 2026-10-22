package com.example.property_core.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        UUID invoiceId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        Instant createdAt) {
}
