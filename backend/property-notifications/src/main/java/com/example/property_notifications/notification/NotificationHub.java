package com.example.property_notifications.notification;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.property_notifications.events.DomainEvent;

@Component
public class NotificationHub {
    private static final Logger logger = LoggerFactory.getLogger(NotificationHub.class);
    private final ConcurrentHashMap<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID tenantId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(tenantId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(tenantId, emitter));
        emitter.onTimeout(() -> remove(tenantId, emitter));
        emitter.onError(exception -> remove(tenantId, emitter));
        return emitter;
    }

    public void broadcast(DomainEvent event) {
        Set<SseEmitter> tenantEmitters = emitters.get(event.organizationId());
        if (tenantEmitters == null) return;
        tenantEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.eventType())
                        .data(event.data()));
            } catch (Exception exception) {
                logger.debug("Removing disconnected notification stream for tenant {}", event.organizationId(), exception);
                remove(event.organizationId(), emitter);
            }
        });
    }

    @Scheduled(fixedDelay = 25000)
    public void keepAlive() {
        emitters.forEach((tenantId, tenantEmitters) -> tenantEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception exception) {
                logger.debug("Removing disconnected notification stream for tenant {}", tenantId, exception);
                remove(tenantId, emitter);
            }
        }));
    }

    private void remove(UUID tenantId, SseEmitter emitter) {
        Set<SseEmitter> tenantEmitters = emitters.get(tenantId);
        if (tenantEmitters != null) {
            tenantEmitters.remove(emitter);
            if (tenantEmitters.isEmpty()) emitters.remove(tenantId, tenantEmitters);
        }
    }
}
