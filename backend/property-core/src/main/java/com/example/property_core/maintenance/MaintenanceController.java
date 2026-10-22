package com.example.property_core.maintenance;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.example.property_core.common.IfMatchVersion;
import com.example.property_core.common.Page;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(path = "/api/v1/maintenance-requests", produces = MediaType.APPLICATION_JSON_VALUE)
public class MaintenanceController {
    private final MaintenanceService service;

    public MaintenanceController(MaintenanceService service) {
        this.service = service;
    }

    @GetMapping
    public Page<MaintenanceItem> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) MaintenanceStatus status,
            @RequestParam(required = false) MaintenancePriority priority,
            @RequestParam(required = false) UUID vendorId) {
        return service.list(status, priority, vendorId, page, size);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceItem create(@Valid @RequestBody CreateMaintenanceRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public MaintenanceItem get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping(path = "/{id}/assign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MaintenanceItem assign(
            @PathVariable UUID id,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody AssignMaintenanceRequest request) {
        return service.assign(id, request.vendorId(), IfMatchVersion.parse(ifMatch));
    }

    @PostMapping("/{id}/start")
    public MaintenanceItem start(@PathVariable UUID id, @RequestHeader("If-Match") String ifMatch) {
        return service.start(id, IfMatchVersion.parse(ifMatch));
    }

    @PostMapping(path = "/{id}/resolve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MaintenanceItem resolve(
            @PathVariable UUID id,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ResolveMaintenanceRequest request) {
        return service.resolve(id, IfMatchVersion.parse(ifMatch), request.resolutionNotes());
    }
}
