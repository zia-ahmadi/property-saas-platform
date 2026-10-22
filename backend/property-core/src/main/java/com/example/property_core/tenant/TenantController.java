package com.example.property_core.tenant;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.example.property_core.common.Page;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(path = "/api/v1/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
public class TenantController {
    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public Page<Tenant> listTenants(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String q) {
        return tenantService.list(q, page, size);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Tenant createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return tenantService.create(request.fullName(), request.email(), request.phone());
    }

    @GetMapping("/{id}")
    public Tenant getTenant(@PathVariable UUID id) {
        return tenantService.get(id);
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Tenant updateTenant(@PathVariable UUID id, @Valid @RequestBody UpdateTenantRequest request) {
        return tenantService.update(id, request.fullName(), request.email(), request.phone());
    }

    public record CreateTenantRequest(
            @NotBlank @Size(min = 1, max = 120) String fullName,
            @NotBlank @Email @Size(max = 320) String email,
            @Size(max = 40) String phone) {
    }

    public record UpdateTenantRequest(
            @Size(min = 1, max = 120) String fullName,
            @Email @Size(max = 320) String email,
            @Size(max = 40) String phone) {
    }
}
