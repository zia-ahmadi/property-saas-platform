package com.example.property_core.tenant;

import java.util.UUID;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Provides access to the current tenant (organization) context from the authenticated JWT.
 * All application queries must be scoped by the organization ID retrieved from this context.
 */
public final class TenantContext {

    private TenantContext() {
    }

    /**
     * Returns the organization ID (tenantId) from the authenticated JWT.
     * @throws IllegalStateException if no authentication is present or the claim is missing
     */
    public static UUID getCurrentOrganizationId() {
        return getOrganizationIdFromAuthentication()
                .orElseThrow(() -> new IllegalStateException("No authenticated user or tenantId claim found"));
    }

    /**
     * Returns the organization ID (tenantId) from the authenticated JWT, if present.
     */
    public static Optional<UUID> getOrganizationIdIfAvailable() {
        return getOrganizationIdFromAuthentication();
    }

    private static Optional<UUID> getOrganizationIdFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String tenantId = jwt.getClaimAsString("tenantId");
            if (tenantId != null) {
                try {
                    return Optional.of(UUID.fromString(tenantId));
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("Invalid tenantId format in JWT: " + tenantId, e);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Returns the user ID from the authenticated JWT, if present.
     */
    public static Optional<UUID> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null) {
                try {
                    return Optional.of(UUID.fromString(subject));
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("Invalid subject format in JWT: " + subject, e);
                }
            }
        }

        return Optional.empty();
    }
}
