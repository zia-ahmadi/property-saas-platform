# Entity relationship diagram

```mermaid
erDiagram
    ORGANIZATIONS ||--o{ USERS : employs
    USERS ||--o{ USER_ROLES : has
    ORGANIZATIONS ||--o{ PROPERTIES : owns
    PROPERTIES ||--o{ UNITS : contains
    ORGANIZATIONS ||--o{ RENTERS : manages
    RENTERS ||--o{ LEASES : signs
    UNITS ||--o{ LEASES : leased_through
    LEASES ||--o{ INVOICES : billed_by
    RENTERS ||--o{ INVOICES : billed_to
    INVOICES ||--o{ PAYMENTS : receives
    ORGANIZATIONS ||--o{ VENDORS : registers
    UNITS ||--o{ MAINTENANCE_REQUESTS : has
    RENTERS ||--o{ MAINTENANCE_REQUESTS : requests
    VENDORS o|--o{ MAINTENANCE_REQUESTS : assigned_to
    LEASES o|--o{ LEASES : renewed_from

    ORGANIZATIONS {
        uuid id PK
        varchar name
        timestamptz created_at
        timestamptz updated_at
    }
    USERS {
        uuid id PK
        uuid organization_id FK
        varchar email UK
        varchar password_hash
        varchar full_name
        boolean active
    }
    USER_ROLES {
        uuid organization_id PK,FK
        uuid user_id PK,FK
        varchar role PK
    }
    PROPERTIES {
        uuid id PK
        uuid organization_id FK
        varchar name
        varchar address
        varchar status
    }
    UNITS {
        uuid id PK
        uuid organization_id FK
        uuid property_id FK
        varchar name
        varchar status
        numeric monthly_rent
    }
    RENTERS {
        uuid id PK
        uuid organization_id FK
        varchar full_name
        varchar email
        varchar phone
    }
    LEASES {
        uuid id PK
        uuid organization_id FK
        uuid renter_id FK
        uuid unit_id FK
        uuid renewed_from_lease_id FK
        varchar status
        bigint version
        date start_date
        date end_date
        numeric monthly_rent
    }
    INVOICES {
        uuid id PK
        uuid organization_id FK
        uuid lease_id FK
        uuid renter_id FK
        numeric amount
        date due_date
        varchar status
        date billing_period_start
        date billing_period_end
        bigint version
    }
    PAYMENTS {
        uuid id PK
        uuid organization_id FK
        uuid invoice_id FK
        numeric amount
        uuid idempotency_key UK
        varchar request_hash
        varchar status
    }
    VENDORS {
        uuid id PK
        uuid organization_id FK
        varchar name
        varchar service_type
        boolean active
    }
    MAINTENANCE_REQUESTS {
        uuid id PK
        uuid organization_id FK
        uuid unit_id FK
        uuid renter_id FK
        uuid vendor_id FK
        varchar priority
        varchar status
        bigint version
    }
```

`organization_id` is included on each organization-owned record. Relationships between
organization-owned tables use composite foreign keys `(organization_id, id)` so that a
record in one organization cannot reference a record owned by another organization.
