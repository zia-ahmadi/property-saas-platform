package com.example.property_core.events;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.example.property_core.tenant.TenantContext;

@Component
public class EventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String eventType, Map<String, Object> data) {
        try {
            UUID organizationId = TenantContext.getCurrentOrganizationId();
            DomainEvent event = DomainEvent.create(eventType, organizationId, data);
            rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.DOMAIN_EVENTS_EXCHANGE,
                "",
                event
            );
            logger.debug("Published event {} for organization {}", eventType, organizationId);
        } catch (Exception e) {
            logger.error("Failed to publish event {}", eventType, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
