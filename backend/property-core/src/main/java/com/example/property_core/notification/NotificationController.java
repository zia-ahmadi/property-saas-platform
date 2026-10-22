package com.example.property_core.notification;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.property_core.tenant.TenantContext;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationHub hub;

    public NotificationController(NotificationHub hub) {
        this.hub = hub;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return hub.subscribe(TenantContext.getCurrentOrganizationId());
    }
}
