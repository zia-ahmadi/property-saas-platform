package com.example.property_core.notification;

import java.util.Map;
import java.util.UUID;

public record NotificationEvent(UUID tenantId, String name, Map<String, Object> data) {
}
