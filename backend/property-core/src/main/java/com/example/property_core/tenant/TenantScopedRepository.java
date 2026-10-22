package com.example.property_core.tenant;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Base repository for tenant-scoped entities.
 * All queries automatically include organization_id to enforce tenant isolation.
 */
public abstract class TenantScopedRepository {

    protected final JdbcClient jdbcClient;

    protected TenantScopedRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Returns the current organization ID from the security context.
     * This ensures all repository operations are scoped to the authenticated tenant.
     */
    protected UUID currentOrganizationId() {
        return TenantContext.getCurrentOrganizationId();
    }

    /**
     * Validates that an entity belongs to the current organization.
     * @throws IllegalStateException if the entity belongs to a different organization
     */
    protected void validateOrganizationAccess(UUID entityOrganizationId) {
        UUID currentOrgId = currentOrganizationId();
        if (!entityOrganizationId.equals(currentOrgId)) {
            throw new IllegalStateException(
                    "Entity belongs to organization " + entityOrganizationId +
                    " but authenticated user is in organization " + currentOrgId);
        }
    }
}
