package com.example.property_core.platformadmin;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.property_core.common.Page;
import com.example.property_core.platformadmin.PlatformAdminRepository.OrganizationSummary;
import com.example.property_core.platformadmin.PlatformAdminRepository.PlatformUserSummary;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(path = "/api/v1/platform-admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class PlatformAdminController {
    private final PlatformAdminService service;

    public PlatformAdminController(PlatformAdminService service) {
        this.service = service;
    }

    @PostMapping(path = "/bootstrap", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bootstrap(
            @Valid @RequestBody BootstrapRequest request,
            @RequestHeader("X-Platform-Admin-Bootstrap-Token") String bootstrapToken) {
        service.bootstrap(request.email(), bootstrapToken);
    }

    @GetMapping("/organizations")
    public Page<OrganizationSummary> listOrganizations(
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listOrganizations(page, size);
    }

    @PatchMapping(path = "/organizations/{organizationId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void renameOrganization(
            @PathVariable UUID organizationId,
            @Valid @RequestBody RenameOrganizationRequest request) {
        service.renameOrganization(organizationId, request.name());
    }

    @GetMapping("/users")
    public Page<PlatformUserSummary> listUsers(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listUsers(organizationId, page, size);
    }

    @PatchMapping(path = "/users/{userId}/active", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setUserActive(@PathVariable UUID userId, @Valid @RequestBody ActiveRequest request) {
        service.setUserActive(userId, request.active());
    }

    @PutMapping(path = "/users/{userId}/role", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setOrganizationRole(@PathVariable UUID userId, @Valid @RequestBody RoleRequest request) {
        service.setOrganizationRole(userId, request.role());
    }

    public record BootstrapRequest(@NotBlank @Email String email) {
    }

    public record RenameOrganizationRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    public record RoleRequest(@NotBlank String role) {
    }
}
