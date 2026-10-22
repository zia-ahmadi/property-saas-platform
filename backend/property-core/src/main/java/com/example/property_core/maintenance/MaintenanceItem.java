package com.example.property_core.maintenance;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceItem(
        UUID id,
        UUID unitId,
        UUID renterId,
        String description,
        MaintenancePriority priority,
        MaintenanceStatus status,
        long version,
        UUID vendorId,
        Instant requestedDate,
        Instant createdAt,
        Instant updatedAt) {
}
