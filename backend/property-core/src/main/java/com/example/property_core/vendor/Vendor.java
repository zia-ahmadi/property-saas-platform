package com.example.property_core.vendor;

import java.time.Instant;
import java.util.UUID;

public record Vendor(
        UUID id,
        String name,
        String serviceType,
        String email,
        String phone,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
