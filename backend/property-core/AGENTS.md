# Property Core - Agent Instructions

## Project Overview

Property Core is a Spring Boot backend service for a Property Management SaaS platform. It uses PostgreSQL with Flyway, JWT authentication, and implements organization-level multi-tenancy.

## Architecture

### Technology Stack
- **Framework**: Spring Boot 4.1.1
- **Java Version**: 21
- **Database**: PostgreSQL 15+ with Flyway migrations
- **Security**: Spring Security with JWT (HS256)
- **ORM**: Spring Data JPA with JDBC Client for direct SQL
- **API**: REST (OpenAPI-generated) + GraphQL (read-only dashboard)
- **Build**: Maven

### Key Directories
- `src/main/java/com/example/property_core/` - Application code
  - `auth/` - Authentication (JWT, login, register)
  - `tenant/` - Tenant isolation utilities
  - `property/` - Property management
  - `unit/` - Unit management
  - `graphql/` - GraphQL resolvers
- `src/main/resources/db/migration/` - Flyway migrations
- `src/main/resources/graphql/` - GraphQL schema
- `api-contracts/` - OpenAPI and GraphQL contract definitions

## Tenant Isolation (Critical)

### Design Principle
Users can only access organizations where they have valid membership. Organization context is derived from the JWT, never from request input.

### Implementation Pattern

1. **Use TenantContext** to get the current organization ID:
   ```java
   UUID orgId = TenantContext.getCurrentOrganizationId();
   ```

2. **Extend TenantScopedRepository** for all entity repositories:
   ```java
   @Repository
   public class MyEntityRepository extends TenantScopedRepository {
       public Optional<MyEntity> findById(UUID id) {
           UUID orgId = currentOrganizationId(); // From base class
           return jdbcClient.sql("""
                   SELECT ... FROM my_entity
                   WHERE id = :id AND organization_id = :organizationId
                   """)
                   .param("id", id)
                   .param("organizationId", orgId)
                   .query(...);
       }
   }
   ```

3. **Never accept organization_id from request parameters** - This is a security violation.

4. **Every query must include organization_id** - No exceptions.

### References
- `TENANT_ISOLATION.md` - Detailed implementation guide
- `ADR-001-database-and-tenancy.md` - Architecture decision record
- `DATABASE-DESIGN-NOTES.md` - Design gaps and considerations

## Build & Run

### Start PostgreSQL
```bash
cd /mnt/recovery/7th\ sems/enterprise/final\ project/v1/property-saas-platform
docker compose up -d postgres
```

### Set JWT Secret
```bash
export JWT_SECRET="$(openssl rand -base64 32)"
```

### Run Application
```bash
cd backend/property-core
./mvnw spring-boot:run
```

### Run Tests
```bash
./mvnw test
```

### Build
```bash
./mvnw clean package
```

## Configuration

### Environment Variables
- `DB_URL` - PostgreSQL connection URL (default: jdbc:postgresql://localhost:5432/property_db)
- `DB_USERNAME` - Database username (default: admin)
- `DB_PASSWORD` - Database password (default: admin)
- `JWT_SECRET` - Base64-encoded JWT signing key (required, min 32 bytes)
- `JWT_ISSUER` - JWT issuer (default: https://property-core.local)
- `JWT_ACCESS_TOKEN_TTL_SECONDS` - Token lifetime (default: 3600)

### Configuration File
`src/main/resources/application.properties`

## API Endpoints

### Authentication
- `POST /api/v1/auth/register` - Register new organization and admin user
- `POST /api/v1/auth/login` - Login and get JWT
- `GET /api/v1/auth/me` - Get current user info

### Properties (Implemented)
- `GET /api/v1/properties` - List properties (tenant-scoped)
- `POST /api/v1/properties` - Create property (tenant-scoped)
- `GET /api/v1/properties/{id}` - Get property (tenant-scoped)
- `PATCH /api/v1/properties/{id}` - Update property (tenant-scoped)
- `DELETE /api/v1/properties/{id}` - Delete property (tenant-scoped)

### Units (Implemented)
- `GET /api/v1/units/{id}` - Get unit (tenant-scoped)
- `PATCH /api/v1/units/{id}` - Update unit (tenant-scoped)

### Other Endpoints (Not Yet Implemented)
- Leases, Invoices, Payments, Vendors, Maintenance Requests, Tenants, Notifications
- OpenAPI-generated stubs exist in `target/generated-sources/openapi/`

### GraphQL
- `POST /graphql` - Query dashboard (tenant-scoped)
  - `portfolioDashboard` - Organization dashboard summary
  - `maintenanceAlerts` - Maintenance alerts with filters

## Database Schema

### Key Tables
- `organizations` - Tenant organizations
- `users` - Staff accounts (with organization_id)
- `user_roles` - User roles per organization
- `properties` - Properties (organization-scoped)
- `units` - Units (organization-scoped, belongs to property)
- `renters` - Renter contacts (organization-scoped)
- `leases` - Leases (organization-scoped)
- `invoices` - Invoices (organization-scoped)
- `payments` - Payments (organization-scoped)
- `vendors` - Vendors (organization-scoped)
- `maintenance_requests` - Maintenance requests (organization-scoped)

### Migration
Initial schema: `src/main/resources/db/migration/V1__create_core_schema.sql`

## Code Conventions

### Repository Pattern
- Use JdbcClient for direct SQL queries
- Always scope queries by organization_id
- Use parameterized queries to prevent SQL injection
- Return domain model objects (not database entities)

### Service Layer
- Use `@Transactional` for write operations
- Delegate to repositories for data access
- Handle business logic and validation
- Don't handle tenant scoping (repositories do that)

### Controller Layer
- Implement OpenAPI-generated interfaces
- Map domain models to API models
- Return appropriate HTTP status codes
- Don't handle tenant scoping (repositories do that)

### Error Handling
- Use `@ControllerAdvice` for global exception handling
- Return appropriate HTTP status codes
- Log errors appropriately
- Don't expose sensitive information

## OpenAPI Generation

**Note**: The OpenAPI generator is currently disabled in pom.xml due to compatibility issues with Jakarta EE (it generates javax.* packages instead of jakarta.*). 

Custom API models are created in:
- `property/api/` - Property API request/response models
- `unit/api/` - Unit API request/response models

Controllers are implemented manually to enforce tenant isolation without relying on generated stubs. When the OpenAPI generator is updated to support Jakarta EE, it can be re-enabled in pom.xml.

## Important Notes

1. **Security First**: Always consider tenant isolation when adding new features
2. **Database-First**: The schema is the source of truth; use composite foreign keys
3. **No Magic Values**: Never hardcode organization IDs or accept them from requests
4. **Test Isolation**: Always test cross-organization access scenarios
5. **Documentation**: Update this file and TENANT_ISOLATION.md when making changes

## Known Issues / TODO

- Complete implementation of all entity controllers (Leases, Invoices, etc.)
- Implement full GraphQL dashboard with real data aggregation
- Add comprehensive integration tests for all endpoints
- Implement updated_at triggers or Spring Data auditing
- Resolve contract gaps documented in DATABASE-DESIGN-NOTES.md
