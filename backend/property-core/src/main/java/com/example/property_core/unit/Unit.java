package com.example.property_core.unit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Unit(
        UUID id,
        UUID organizationId,
        UUID propertyId,
        String name,
        String status,
        Integer bedrooms,
        BigDecimal bathrooms,
        BigDecimal monthlyRent,
        Instant createdAt,
        Instant updatedAt) {
}
