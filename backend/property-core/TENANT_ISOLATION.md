# Tenant Isolation Implementation

## Overview

This document describes the tenant isolation enforcement implemented in the property-core backend. The system ensures that users can only access organizations where they have valid membership, following the ADR-001 database and tenancy design.

## Architecture

### 1. Tenant Context (`TenantContext`)

The `TenantContext` utility class extracts the organization ID (tenantId) from the authenticated JWT token:

```java
UUID organizationId = TenantContext.getCurrentOrganizationId();
```

- Automatically reads the `tenantId` claim from the JWT in the security context
- Throws `IllegalStateException` if no authentication is present
- Provides optional access via `getOrganizationIdIfAvailable()`
- Also provides access to the current user ID via `getCurrentUserId()`

### 2. Tenant-Scoped Repository Pattern (`TenantScopedRepository`)

Base repository class that enforces organization scoping for all database operations:

```java
public abstract class TenantScopedRepository {
    protected UUID currentOrganizationId() {
        return TenantContext.getCurrentOrganizationId();
    }

    protected void validateOrganizationAccess(UUID entityOrganizationId) {
        // Throws if entity belongs to different organization
    }
}
```

All business entity repositories extend this base class.

### 3. Repository Implementation

Repositories automatically scope all queries by organization:

```java
public Optional<Property> findById(UUID id) {
    UUID orgId = currentOrganizationId();
    return jdbcClient.sql("""
            SELECT ... FROM properties
            WHERE id = :id AND organization_id = :organizationId
            """)
            .param("id", id)
            .param("organizationId", orgId)
            .query(...);
}
```

Key points:
- Every query includes `organization_id = :organizationId`
- The organization ID is obtained from the security context, never from request input
- Cross-organization access is prevented at the database level

### 4. Service Layer

Services delegate to repositories which automatically enforce tenant isolation:

```java
@Service
public class PropertyService {
    private final PropertyRepository propertyRepository;

    public Optional<Property> getProperty(UUID id) {
        return propertyRepository.findById(id); // Automatically scoped
    }
}
```

### 5. Controller Layer

Controllers don't need to handle tenant scoping - it's handled by the repository layer:

```java
@RestController
public class PropertyController implements PropertiesApi {
    @Override
    public ResponseEntity<Property> getProperty(UUID propertyId) {
        // No tenant ID parameter needed - enforced by repository
        return propertyService.getProperty(propertyId);
    }
}
```

### 6. GraphQL Resolvers

GraphQL queries also use the tenant context:

```java
@Controller
public class DashboardQueryResolver {
    @QueryMapping
    public PortfolioDashboard portfolioDashboard() {
        UUID organizationId = TenantContext.getCurrentOrganizationId();
        // Query scoped to authenticated organization
    }
}
```

## Security Guarantees

### Database-Level Protection

The database schema includes composite foreign keys to prevent cross-organization references:

```sql
CONSTRAINT fk_units_property
    FOREIGN KEY (organization_id, property_id)
    REFERENCES properties(organization_id, id) ON DELETE RESTRICT
```

This ensures that even if application logic fails, the database rejects cross-organization relationships.

### Application-Level Protection

1. **Never accept organization_id from request input** - Always derive from JWT
2. **Every query includes organization_id** - No global queries possible
3. **Validation on access** - `validateOrganizationAccess()` checks entity ownership
4. **JWT-based authentication** - All endpoints require valid bearer token

## Implementation Status

### Completed

- ✅ `TenantContext` utility for extracting organization ID from JWT
- ✅ `TenantScopedRepository` base class
- ✅ `PropertyRepository` with tenant-scoped queries
- ✅ `PropertyService` with tenant isolation
- ✅ `PropertyController` implementing PropertiesApi
- ✅ `UnitRepository` with tenant-scoped queries
- ✅ `UnitService` with tenant isolation
- ✅ `UnitController` implementing UnitsApi
- ✅ GraphQL resolver skeleton with tenant context
- ✅ Integration tests for tenant isolation

### Pending (Future Work)

- Leases service and controller
- Invoices service and controller
- Payments service and controller
- Vendors service and controller
- Maintenance requests service and controller
- Tenants (renters) service and controller
- Notifications service and controller
- Full GraphQL dashboard implementation with real data aggregation
- Additional integration tests for cross-organization access scenarios
- Re-enable OpenAPI generator with Jakarta EE configuration

## Testing

Run the tenant isolation tests:

```bash
./mvnw test -Dtest=TenantIsolationTest
```

## Usage Example

### Creating a Property

```bash
# Register and get JWT
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "organizationName": "Acme Properties",
    "email": "admin@acme.com",
    "password": "SecurePass123",
    "fullName": "Admin User"
  }'

# Use JWT to create property (automatically scoped to Acme's organization)
curl -X POST http://localhost:8080/api/v1/properties \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Sunset Apartments",
    "address": "123 Main St",
    "status": "ACTIVE"
  }'
```

### Cross-Organization Access Prevention

If a user from Organization B tries to access a property from Organization A:

```bash
# User from Org B tries to access Org A's property
curl -X GET http://localhost:8080/api/v1/properties/<org-a-property-id> \
  -H "Authorization: Bearer <org-b-user-jwt>"

# Result: 404 Not Found (property doesn't exist in their organization context)
```

The database query includes `organization_id = org-b-id`, so the property from Org A is never returned.

## Important Notes

1. **Never disable tenant scoping** - All queries must include organization_id
2. **Never pass organization_id from request** - Always use TenantContext
3. **Test cross-organization scenarios** - Verify users cannot access other organizations
4. **Review generated controllers** - OpenAPI-generated stubs should be replaced with tenant-aware implementations
5. **GraphQL schema notes** - The schema states "Clients must not supply a tenantId argument" - this is enforced by the backend

## References

- ADR-001: PostgreSQL relational model and organization-level tenancy
- DATABASE-DESIGN-NOTES.md: Detailed design decisions and gaps
- V1__create_core_schema.sql: Database schema with composite foreign keys
