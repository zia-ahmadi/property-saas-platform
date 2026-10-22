package com.example.property_core.notification;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.property_core.tenant.TenantContext;

@Component
public class NotificationPublisher {
    private final ApplicationEventPublisher eventPublisher;

    public NotificationPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(String name, Map<String, Object> data) {
        eventPublisher.publishEvent(
                new NotificationEvent(TenantContext.getCurrentOrganizationId(), name, Map.copyOf(data)));
    }
}
