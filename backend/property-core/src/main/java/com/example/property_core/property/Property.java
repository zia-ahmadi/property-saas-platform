package com.example.property_core.property;

import java.time.Instant;
import java.util.UUID;

public record Property(
        UUID id,
        UUID organizationId,
        String name,
        String address,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
