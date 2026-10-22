package com.example.property_core.tenant;

import java.time.Instant;
import java.util.UUID;

public record Tenant(UUID id, String fullName, String email, String phone, Instant createdAt, Instant updatedAt) {
}
