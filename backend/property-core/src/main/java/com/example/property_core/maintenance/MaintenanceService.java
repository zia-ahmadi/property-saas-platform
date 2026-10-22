package com.example.property_core.maintenance;

import java.util.UUID;

import com.example.property_core.common.Page;
import com.example.property_core.exception.ApiException;
import com.example.property_core.notification.NotificationPublisher;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {
    private final MaintenanceRepository repository;
    private final NotificationPublisher notificationPublisher;

    public MaintenanceService(MaintenanceRepository repository, NotificationPublisher notificationPublisher) {
        this.repository = repository;
        this.notificationPublisher = notificationPublisher;
    }

    public Page<MaintenanceItem> list(
            MaintenanceStatus status, MaintenancePriority priority, UUID vendorId, int page, int size) {
        long total = repository.count(status, priority, vendorId);
        return new Page<>(repository.findAll(status, priority, vendorId, page, size), page, size, total,
                (int) Math.ceil((double) total / size));
    }

    public MaintenanceItem get(UUID id) {
        return repository.findById(id).orElseThrow(this::notFound);
    }

    @Transactional
    public MaintenanceItem create(CreateMaintenanceRequest request) {
        if (!repository.unitExists(request.unitId()) || !repository.renterExists(request.renterId())) {
            throw notFound();
        }
        MaintenanceItem created = repository.create(request);
        publishStatusChange(created);
        return created;
    }

    @Transactional
    public MaintenanceItem assign(UUID id, UUID vendorId, long version) {
        MaintenanceItem current = get(id);
        checkVersion(current, version);
        requireStatus(current, MaintenanceStatus.SUBMITTED);
        if (!repository.activeVendorExists(vendorId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Active vendor not found");
        }
        if (repository.assign(id, vendorId, version) != 1) {
            throw preconditionFailed();
        }
        MaintenanceItem updated = get(id);
        publishStatusChange(updated);
        return updated;
    }

    @Transactional
    public MaintenanceItem start(UUID id, long version) {
        MaintenanceItem current = get(id);
        checkVersion(current, version);
        requireStatus(current, MaintenanceStatus.ASSIGNED);
        if (repository.start(id, version) != 1) {
            throw preconditionFailed();
        }
        MaintenanceItem updated = get(id);
        publishStatusChange(updated);
        return updated;
    }

    @Transactional
    public MaintenanceItem resolve(UUID id, long version, String notes) {
        MaintenanceItem current = get(id);
        checkVersion(current, version);
        requireStatus(current, MaintenanceStatus.IN_PROGRESS);
        if (repository.resolve(id, version, notes) != 1) {
            throw preconditionFailed();
        }
        MaintenanceItem updated = get(id);
        publishStatusChange(updated);
        return updated;
    }

    private void publishStatusChange(MaintenanceItem item) {
        notificationPublisher.publish("MAINTENANCE_STATUS_CHANGED", java.util.Map.of(
                "requestId", item.id(), "status", item.status().name(), "version", item.version()));
    }

    private void checkVersion(MaintenanceItem item, long expectedVersion) {
        if (item.version() != expectedVersion) throw preconditionFailed();
    }

    private void requireStatus(MaintenanceItem item, MaintenanceStatus expected) {
        if (item.status() != expected) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Maintenance request must be " + expected + " to perform this transition");
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Maintenance request, unit, or renter not found");
    }

    private ApiException preconditionFailed() {
        return new ApiException(HttpStatus.PRECONDITION_FAILED,
                "Maintenance request version does not match the current version");
    }
}
