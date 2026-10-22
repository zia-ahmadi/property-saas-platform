package com.example.property_core.lease;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.property_core.common.IfMatchVersion;
import com.example.property_core.common.Page;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping(path = "/api/v1/leases", produces = MediaType.APPLICATION_JSON_VALUE)
public class LeaseController {
    private final LeaseService leaseService;

    public LeaseController(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @GetMapping
    public Page<LeaseResponse> listLeases(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) LeaseStatus status,
            @RequestParam(required = false) UUID renterId,
            @RequestParam(required = false) UUID unitId) {
        return leaseService.list(status, renterId, unitId, page, size).map(LeaseController::response);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LeaseResponse createLease(@Valid @RequestBody CreateLeaseRequest request) {
        return response(leaseService.create(
                request.renterId(), request.unitId(), request.startDate(), request.endDate(), request.monthlyRent()));
    }

    @GetMapping("/{id}")
    public LeaseResponse getLease(@PathVariable UUID id) {
        return response(leaseService.get(id));
    }

    @PostMapping("/{id}/activate")
    public LeaseResponse activateLease(
            @PathVariable UUID id, @RequestHeader("If-Match") String ifMatch) {
        return response(leaseService.activate(id, IfMatchVersion.parse(ifMatch)));
    }

    @PostMapping(path = "/{id}/terminate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LeaseResponse terminateLease(
            @PathVariable UUID id,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody(required = false) TerminateLeaseRequest request) {
        return response(leaseService.terminate(
                id, IfMatchVersion.parse(ifMatch), request == null ? null : request.reason()));
    }

    @PostMapping(path = "/{id}/renew", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LeaseResponse renewLease(
            @PathVariable UUID id,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody RenewLeaseRequest request) {
        return response(leaseService.renew(
                id,
                IfMatchVersion.parse(ifMatch),
                request.startDate(),
                request.endDate(),
                request.monthlyRent()));
    }

    private static LeaseResponse response(Lease lease) {
        return new LeaseResponse(
                lease.id(),
                lease.renterId(),
                lease.unitId(),
                lease.status(),
                lease.version(),
                lease.startDate(),
                lease.endDate(),
                lease.monthlyRent(),
                lease.createdAt(),
                lease.updatedAt());
    }

    public record LeaseResponse(
            UUID id,
            UUID renterId,
            UUID unitId,
            LeaseStatus status,
            long version,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal monthlyRent,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateLeaseRequest(
            @NotNull UUID renterId,
            @NotNull UUID unitId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal monthlyRent) {
    }

    public record RenewLeaseRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal monthlyRent) {
    }

    public record TerminateLeaseRequest(@Size(max = 500) String reason) {
    }
}
