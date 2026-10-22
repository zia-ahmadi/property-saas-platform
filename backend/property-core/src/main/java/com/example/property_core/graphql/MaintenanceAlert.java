package com.example.property_core.graphql;

import java.util.UUID;

public record MaintenanceAlert(
        UUID id,
        String priority,
        String status,
        String unitName,
        String propertyName,
        String description,
        String requestedDate) {
}
