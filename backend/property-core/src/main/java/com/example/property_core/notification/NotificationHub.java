package com.example.property_core.notification;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    public void broadcast(NotificationEvent event) {
        Set<SseEmitter> tenantEmitters = emitters.get(event.tenantId());
        if (tenantEmitters == null) return;
        tenantEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.name())
                        .data(event.data(), MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException exception) {
                logger.debug("Removing disconnected notification stream for tenant {}", event.tenantId(), exception);
                remove(event.tenantId(), emitter);
            }
        });
    }

    @Scheduled(fixedDelay = 25000)
    public void keepAlive() {
        emitters.forEach((tenantId, tenantEmitters) -> tenantEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException exception) {
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
