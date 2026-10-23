package com.example.property_core.platformadmin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.example.property_core.common.Page;
import com.example.property_core.platformadmin.PlatformAdminRepository.OrganizationSummary;
import com.example.property_core.platformadmin.PlatformAdminRepository.PlatformUserSummary;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformAdminService {
    private static final Set<String> ORGANIZATION_ROLES = Set.of("ADMIN", "MANAGER", "TENANT");

    private final PlatformAdminRepository repository;
    private final Set<String> allowedEmails;
    private final String bootstrapToken;

    public PlatformAdminService(
            PlatformAdminRepository repository,
            @Value("${app.platform-admin.allowed-emails:}") String allowedEmails,
            @Value("${app.platform-admin.bootstrap-token:}") String bootstrapToken) {
        this.repository = repository;
        this.allowedEmails = Arrays.stream(allowedEmails.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .map(PlatformAdminService::normalizeEmail)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.bootstrapToken = bootstrapToken;
    }

    public Page<OrganizationSummary> listOrganizations(int page, int size) {
        return repository.listOrganizations(page, size);
    }

    public Page<PlatformUserSummary> listUsers(UUID organizationId, int page, int size) {
        return repository.listUsers(organizationId, page, size);
    }

    @Transactional
    public void renameOrganization(UUID organizationId, String name) {
        if (!repository.renameOrganization(organizationId, name.trim())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
    }

    @Transactional
    public void setUserActive(UUID userId, boolean active) {
        if (repository.findOrganizationIdByUserId(userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (!active && repository.isPlatformAdmin(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Platform-admin accounts cannot be disabled from organization management");
        }
        repository.setUserActive(userId, active);
    }

    @Transactional
    public void setOrganizationRole(UUID userId, String role) {
        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
        if (!ORGANIZATION_ROLES.contains(normalizedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported organization role");
        }
        UUID organizationId = repository.findOrganizationIdByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        repository.replaceOrganizationRole(organizationId, userId, normalizedRole);
    }

    @Transactional
    public void bootstrap(String email, String providedToken) {
        String normalizedEmail = normalizeEmail(email);
        if (!allowedEmails.contains(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email is not allowlisted for bootstrap");
        }
        if (bootstrapToken == null || bootstrapToken.length() < 32) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Platform-admin bootstrap is not configured");
        }
        if (providedToken == null || !MessageDigest.isEqual(
                bootstrapToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bootstrap token");
        }
        if (!repository.userExistsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Register an account with the allowlisted email before bootstrapping");
        }
        repository.addPlatformAdminByEmail(normalizedEmail);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
