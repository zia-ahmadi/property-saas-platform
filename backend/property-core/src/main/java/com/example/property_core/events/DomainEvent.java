package com.example.property_core.events;

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
    public DomainEvent {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType cannot be null or blank");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt cannot be null");
        if (organizationId == null) throw new IllegalArgumentException("organizationId cannot be null");
        if (data == null) throw new IllegalArgumentException("data cannot be null");
    }

    public static DomainEvent create(String eventType, UUID organizationId, Map<String, Object> data) {
        return new DomainEvent(
            UUID.randomUUID(),
            eventType,
            Instant.now(),
            organizationId,
            Map.copyOf(data)
        );
    }
}
