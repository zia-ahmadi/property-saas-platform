package com.example.property_notifications.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.example.property_notifications.notification.NotificationHub;

@Component
public class EventConsumer {
    private static final Logger logger = LoggerFactory.getLogger(EventConsumer.class);
    private final NotificationHub notificationHub;

    public EventConsumer(NotificationHub notificationHub) {
        this.notificationHub = notificationHub;
    }

    @RabbitListener(queues = RabbitMQConfiguration.NOTIFICATIONS_QUEUE)
    public void handleEvent(DomainEvent event) {
        logger.debug("Received event {} for organization {}", event.eventType(), event.organizationId());
        notificationHub.broadcast(event);
    }
}
