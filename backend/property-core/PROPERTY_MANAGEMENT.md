# Property Management Feature

## Overview

The Property Management feature provides full CRUD operations for managing properties within an organization. All operations are automatically scoped to the authenticated user's organization (tenant isolation).

## Endpoints

### List Properties
```
GET /api/v1/properties
```

**Query Parameters:**
- `status` (optional): Filter by status (`ACTIVE`, `INACTIVE`)

**Response:** Array of Property objects

**Example:**
```bash
curl -X GET http://localhost:8080/api/v1/properties \
  -H "Authorization: Bearer <JWT>"

curl -X GET http://localhost:8080/api/v1/properties?status=ACTIVE \
  -H "Authorization: Bearer <JWT>"
```

### Create Property
```
POST /api/v1/properties
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "Sunset Apartments",
  "address": "123 Main Street, City, State 12345",
  "status": "ACTIVE"
}
```

**Validation Rules:**
- `name`: Required, 2-200 characters
- `address`: Required, max 500 characters
- `status`: Optional, defaults to `ACTIVE` if not provided

**Response:** Created Property object (201 Created)

**Example:**
```bash
curl -X POST http://localhost:8080/api/v1/properties \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Sunset Apartments",
    "address": "123 Main Street, City, State 12345",
    "status": "ACTIVE"
  }'
```

### Get Property
```
GET /api/v1/properties/{id}
```

**Response:** Property object (200 OK)

**Error:** 404 Not Found if property doesn't exist or belongs to another organization

**Example:**
```bash
curl -X GET http://localhost:8080/api/v1/properties/{id} \
  -H "Authorization: Bearer <JWT>"
```

### Update Property
```
PUT /api/v1/properties/{id}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "Updated Name",
  "address": "Updated Address",
  "status": "INACTIVE"
}
```

**Response:** Updated Property object (200 OK)

**Error:** 404 Not Found if property doesn't exist or belongs to another organization

**Example:**
```bash
curl -X PUT http://localhost:8080/api/v1/properties/{id} \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Name",
    "address": "Updated Address",
    "status": "INACTIVE"
  }'
```

### Archive Property (Soft Delete)
```
PATCH /api/v1/properties/{id}/archive
```

**Description:** Sets property status to `INACTIVE` instead of permanently deleting it. This preserves historical data while marking the property as archived.

**Response:** Archived Property object (200 OK)

**Error:** 404 Not Found if property doesn't exist or belongs to another organization

**Example:**
```bash
curl -X PATCH http://localhost:8080/api/v1/properties/{id}/archive \
  -H "Authorization: Bearer <JWT>"
```

### Delete Property
```
DELETE /api/v1/properties/{id}
```

**Description:** Permanently deletes the property. Use with caution - this operation cannot be undone.

**Response:** 204 No Content

**Error:** 404 Not Found if property doesn't exist or belongs to another organization

**Example:**
```bash
curl -X DELETE http://localhost:8080/api/v1/properties/{id} \
  -H "Authorization: Bearer <JWT>"
```

## Data Model

### Property Object
```json
{
  "id": "uuid",
  "organizationId": "uuid",
  "name": "string",
  "address": "string",
  "status": "ACTIVE | INACTIVE",
  "createdAt": "ISO-8601 timestamp",
  "updatedAt": "ISO-8601 timestamp"
}
```

### PropertyStatus Enum
- `ACTIVE`: Property is active and available for use
- `INACTIVE`: Property is archived or inactive

## Error Responses

### Validation Error (400 Bad Request)
```json
{
  "timestamp": "2024-01-01T00:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": {
    "name": "Property name must be at least 2 characters",
    "address": "Property address cannot be empty"
  }
}
```

### Not Found (404 Not Found)
```json
{
  "timestamp": "2024-01-01T00:00:00Z",
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Property not found with id: {id}"
}
```

### Bad Request (400 Bad Request)
```json
{
  "timestamp": "2024-01-01T00:00:00Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Property name cannot be empty"
}
```

## Tenant Isolation

All Property Management operations automatically enforce tenant isolation:

1. **Organization Context**: Derived from JWT `tenantId` claim
2. **Query Scoping**: All database queries include `organization_id` filter
3. **Cross-Organization Prevention**: Users cannot access properties from other organizations
4. **Database-Level Protection**: Composite foreign keys prevent cross-organization relationships

## Implementation Details

### Layer Architecture

**Controller Layer** (`PropertyController`)
- Handles HTTP requests/responses
- Validates request bodies
- Maps to service layer methods
- Returns appropriate HTTP status codes

**Service Layer** (`PropertyService`)
- Business logic and validation
- Transaction management
- Exception handling
- Delegates to repository

**Repository Layer** (`PropertyRepository`)
- Database operations
- Automatic organization scoping
- Extends `TenantScopedRepository`

### Key Classes

- `Property` - Domain model
- `PropertyStatus` - Status enum
- `PropertyNotFoundException` - Custom exception
- `CreatePropertyRequest` - API request model
- `UpdatePropertyRequest` - API request model
- `GlobalExceptionHandler` - Centralized error handling

## Testing

### Integration Tests

Run the integration tests:

```bash
./mvnw test -Dtest=PropertyIntegrationTest
```

**Test Coverage:**
- Create property with valid data
- Create property with invalid data (validation)
- List all properties
- List properties filtered by status
- Get specific property
- Get non-existent property (404)
- Update property
- Archive property (soft delete)
- Delete property
- Tenant isolation (cross-organization access prevention)

## Security Considerations

1. **Authentication Required**: All endpoints require valid JWT authentication
2. **Authorization**: All operations scoped to user's organization
3. **Input Validation**: All inputs validated at both controller and service levels
4. **SQL Injection Prevention**: Parameterized queries used throughout
5. **Cross-Organization Access**: Prevented at both application and database levels

## Best Practices

1. **Use Archive Before Delete**: Prefer `PATCH /archive` over `DELETE` to preserve historical data
2. **Filter by Status**: Use the `status` query parameter to filter active vs inactive properties
3. **Handle 404 Errors**: Always handle `PropertyNotFoundException` in client code
4. **Validate Input**: Let the service layer handle validation, don't bypass it
5. **Transaction Safety**: Write operations are transactional - they either succeed completely or fail completely

## Future Enhancements

- Add pagination to list endpoint
- Add search functionality (name, address)
- Add sorting options
- Add bulk operations (bulk create, bulk update)
- Add property metadata/custom fields
- Add property images/media
- Add property ownership history audit trail
