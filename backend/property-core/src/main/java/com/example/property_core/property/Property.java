package com.example.property_core.property;

import java.time.Instant;
import java.util.UUID;

public record Property(
        UUID id,
        String name,
        String address,
        String status,
        int unitCount,
        int occupiedUnits,
        Instant createdAt,
        Instant updatedAt) {
}
