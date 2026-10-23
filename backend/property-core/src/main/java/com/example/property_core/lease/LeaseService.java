package com.example.property_core.lease;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.property_core.common.Page;
import com.example.property_core.exception.ApiException;
import com.example.property_core.events.EventPublisher;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaseService {
    private final LeaseRepository leaseRepository;
    private final EventPublisher eventPublisher;

    public LeaseService(LeaseRepository leaseRepository, EventPublisher eventPublisher) {
        this.leaseRepository = leaseRepository;
        this.eventPublisher = eventPublisher;
    }

    public Page<Lease> list(LeaseStatus status, UUID renterId, UUID unitId, int page, int size) {
        List<Lease> content = leaseRepository.findAll(status, renterId, unitId, page, size);
        long totalElements = leaseRepository.count(status, renterId, unitId);
        return new Page<>(content, page, size, totalElements, (int) Math.ceil((double) totalElements / size));
    }

    public Lease get(UUID id) {
        return leaseRepository.findById(id).orElseThrow(() -> notFound());
    }

    @Transactional
    public Lease create(UUID renterId, UUID unitId, LocalDate startDate, LocalDate endDate, BigDecimal rent) {
        validateTerm(startDate, endDate, rent);
        if (!leaseRepository.renterExists(renterId)) {
            throw notFound();
        }
        leaseRepository.unitStatus(unitId).orElseThrow(this::notFound);
        if (leaseRepository.hasOpenLease(unitId)) {
            throw conflict("Unit already has an active or draft lease");
        }
        return createDraft(renterId, unitId, startDate, endDate, rent, null);
    }

    @Transactional
    public Lease activate(UUID id, long expectedVersion) {
        Lease lease = get(id);
        checkVersion(lease, expectedVersion);
        requireStatus(lease, LeaseStatus.DRAFT);
        String unitStatus = leaseRepository.unitStatus(lease.unitId()).orElseThrow(this::notFound);

        if (lease.renewedFromLeaseId() != null) {
            Lease original = get(lease.renewedFromLeaseId());
            if (original.status() != LeaseStatus.ACTIVE || !original.unitId().equals(lease.unitId())) {
                throw conflict("The lease being renewed is no longer active for this unit");
            }
            if (leaseRepository.terminateForRenewal(original.id(), lease.id()) != 1) {
                throw conflict("The active lease changed while the renewal was being activated");
            }
        } else if (!"AVAILABLE".equals(unitStatus)) {
            throw conflict("Unit is not available");
        }

        if (leaseRepository.transition(id, LeaseStatus.DRAFT, LeaseStatus.ACTIVE, expectedVersion, null) != 1) {
            throw preconditionFailed();
        }
        leaseRepository.updateUnitStatus(lease.unitId(), "OCCUPIED");
        eventPublisher.publish("LEASE_ACTIVATED", java.util.Map.of(
                "leaseId", lease.id(), "unitId", lease.unitId()));
        return get(id);
    }

    @Transactional
    public Lease terminate(UUID id, long expectedVersion, String reason) {
        Lease lease = get(id);
        checkVersion(lease, expectedVersion);
        requireStatus(lease, LeaseStatus.ACTIVE);
        leaseRepository.unitStatus(lease.unitId()).orElseThrow(this::notFound);
        if (leaseRepository.transition(id, LeaseStatus.ACTIVE, LeaseStatus.TERMINATED, expectedVersion, reason) != 1) {
            throw preconditionFailed();
        }
        leaseRepository.updateUnitStatus(lease.unitId(), "AVAILABLE");
        return get(id);
    }

    @Transactional
    public Lease renew(
            UUID id, long expectedVersion, LocalDate startDate, LocalDate endDate, BigDecimal rent) {
        Lease original = get(id);
        checkVersion(original, expectedVersion);
        requireStatus(original, LeaseStatus.ACTIVE);
        validateTerm(startDate, endDate, rent);
        if (!startDate.isAfter(original.endDate())) {
            throw new IllegalArgumentException("Renewal start date must be after the current lease end date");
        }
        if (leaseRepository.unitStatus(original.unitId()).isEmpty()) {
            throw notFound();
        }
        if (leaseRepository.hasDraftLease(original.unitId())) {
            throw conflict("Unit already has a draft renewal");
        }
        return createDraft(original.renterId(), original.unitId(), startDate, endDate, rent, original.id());
    }

    private Lease createDraft(
            UUID renterId, UUID unitId, LocalDate startDate, LocalDate endDate,
            BigDecimal rent, UUID renewedFromLeaseId) {
        try {
            return leaseRepository.create(renterId, unitId, startDate, endDate, rent, renewedFromLeaseId);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("A conflicting lease already exists for this unit");
        }
    }

    private void validateTerm(LocalDate startDate, LocalDate endDate, BigDecimal rent) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Lease end date must be on or after its start date");
        }
        if (rent == null || rent.signum() < 0 || rent.scale() > 2 || rent.precision() > 12) {
            throw new IllegalArgumentException("Monthly rent must be a non-negative amount with at most 2 decimals");
        }
    }

    private void checkVersion(Lease lease, long expectedVersion) {
        if (lease.version() != expectedVersion) {
            throw preconditionFailed();
        }
    }

    private void requireStatus(Lease lease, LeaseStatus expected) {
        if (lease.status() != expected) {
            throw conflict("Lease must be " + expected + " to perform this transition");
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Lease, renter, or unit not found");
    }

    private ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, detail);
    }

    private ApiException preconditionFailed() {
        return new ApiException(HttpStatus.PRECONDITION_FAILED, "Lease version does not match the current version");
    }
}
