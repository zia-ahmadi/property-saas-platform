package com.example.property_core.lease;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Lease(
        UUID id,
        UUID renterId,
        UUID unitId,
        LeaseStatus status,
        long version,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal monthlyRent,
        UUID renewedFromLeaseId,
        Instant createdAt,
        Instant updatedAt) {
}
