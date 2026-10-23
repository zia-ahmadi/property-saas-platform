package com.example.property_notifications.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DomainEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID organizationId,
    Map<String, Object> data
) {
}
