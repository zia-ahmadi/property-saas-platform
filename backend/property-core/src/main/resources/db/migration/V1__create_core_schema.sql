-- PostgreSQL 15+ | Flyway
-- Every organization-owned relationship includes organization_id to prevent
-- cross-organization references at the database level.

CREATE TABLE organizations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_users_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT
);

CREATE TABLE user_roles (
    organization_id UUID NOT NULL,
    user_id         UUID NOT NULL,
    role            VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (organization_id, user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('ADMIN', 'MANAGER', 'TENANT')),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (organization_id, user_id)
        REFERENCES users(organization_id, id) ON DELETE CASCADE
);

CREATE TABLE properties (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    name            VARCHAR(200) NOT NULL,
    address         VARCHAR(500) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_properties_org_id UNIQUE (organization_id, id),
    CONSTRAINT ck_properties_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_properties_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT
);

CREATE TABLE units (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    property_id     UUID NOT NULL,
    name            VARCHAR(100) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    bedrooms        INTEGER,
    bathrooms       NUMERIC(4,1),
    monthly_rent    NUMERIC(12,2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_units_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_units_property_name UNIQUE (organization_id, property_id, name),
    CONSTRAINT ck_units_status CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE')),
    CONSTRAINT ck_units_bedrooms CHECK (bedrooms IS NULL OR bedrooms >= 0),
    CONSTRAINT ck_units_bathrooms CHECK (bathrooms IS NULL OR bathrooms >= 0),
    CONSTRAINT ck_units_monthly_rent CHECK (monthly_rent >= 0),
    CONSTRAINT fk_units_property
        FOREIGN KEY (organization_id, property_id)
        REFERENCES properties(organization_id, id) ON DELETE RESTRICT
);

-- The API calls these Tenant records; they are renter contacts, not login accounts.
CREATE TABLE renters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    email           VARCHAR(320) NOT NULL,
    phone           VARCHAR(40),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_renters_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_renters_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT
);

CREATE INDEX ix_renters_org_name ON renters(organization_id, full_name);
CREATE INDEX ix_renters_org_email ON renters(organization_id, email);

CREATE TABLE leases (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL,
    renter_id            UUID NOT NULL,
    unit_id              UUID NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version              BIGINT NOT NULL DEFAULT 0,
    start_date           DATE NOT NULL,
    end_date             DATE NOT NULL,
    monthly_rent         NUMERIC(12,2) NOT NULL,
    termination_reason   VARCHAR(500),
    renewed_from_lease_id UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_leases_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_leases_org_id_renter UNIQUE (organization_id, id, renter_id),
    CONSTRAINT ck_leases_status CHECK (status IN ('DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED')),
    CONSTRAINT ck_leases_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_leases_rent CHECK (monthly_rent >= 0),
    CONSTRAINT ck_leases_version CHECK (version >= 0),
    CONSTRAINT fk_leases_renter
        FOREIGN KEY (organization_id, renter_id)
        REFERENCES renters(organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_leases_unit
        FOREIGN KEY (organization_id, unit_id)
        REFERENCES units(organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_leases_renewed_from
        FOREIGN KEY (organization_id, renewed_from_lease_id)
        REFERENCES leases(organization_id, id) ON DELETE RESTRICT
);

-- The API rejects creating a draft when a unit already has a draft or active lease.
CREATE UNIQUE INDEX uq_leases_one_open_per_unit
    ON leases(organization_id, unit_id)
    WHERE status IN ('DRAFT', 'ACTIVE');
CREATE INDEX ix_leases_org_status ON leases(organization_id, status);
CREATE INDEX ix_leases_org_renter ON leases(organization_id, renter_id);
CREATE INDEX ix_leases_org_unit_dates ON leases(organization_id, unit_id, start_date, end_date);

CREATE TABLE invoices (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL,
    lease_id             UUID NOT NULL,
    renter_id            UUID NOT NULL,
    amount               NUMERIC(12,2) NOT NULL,
    due_date             DATE NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    paid_at              TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0,
    -- Null for manual invoices; recurring jobs populate both period columns.
    billing_period_start DATE,
    billing_period_end   DATE,
    description          VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_invoices_org_id UNIQUE (organization_id, id),
    CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'OVERDUE', 'CANCELLED')),
    CONSTRAINT ck_invoices_amount CHECK (amount >= 0),
    CONSTRAINT ck_invoices_version CHECK (version >= 0),
    CONSTRAINT ck_invoices_billing_period CHECK (
        (billing_period_start IS NULL AND billing_period_end IS NULL)
        OR
        (billing_period_start IS NOT NULL AND billing_period_end IS NOT NULL
         AND billing_period_end >= billing_period_start)
    ),
    CONSTRAINT fk_invoices_lease_renter
        FOREIGN KEY (organization_id, lease_id, renter_id)
        REFERENCES leases(organization_id, id, renter_id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_invoices_recurring_period
    ON invoices(organization_id, lease_id, billing_period_start, billing_period_end)
    WHERE billing_period_start IS NOT NULL;
CREATE INDEX ix_invoices_org_status_due ON invoices(organization_id, status, due_date);
CREATE INDEX ix_invoices_org_lease ON invoices(organization_id, lease_id);
CREATE INDEX ix_invoices_org_renter ON invoices(organization_id, renter_id);

CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    invoice_id      UUID NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    payment_method  VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    idempotency_key UUID NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payments_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_payments_idempotency UNIQUE (organization_id, idempotency_key),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (payment_method IN ('CREDIT_CARD', 'BANK_TRANSFER', 'CASH')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    CONSTRAINT fk_payments_invoice
        FOREIGN KEY (organization_id, invoice_id)
        REFERENCES invoices(organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_payments_org_invoice ON payments(organization_id, invoice_id, created_at);

CREATE TABLE vendors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    name            VARCHAR(120) NOT NULL,
    service_type    VARCHAR(100) NOT NULL,
    email           VARCHAR(320),
    phone           VARCHAR(40),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_vendors_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_vendors_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT
);

CREATE INDEX ix_vendors_org_active ON vendors(organization_id, active);

CREATE TABLE maintenance_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL,
    unit_id          UUID NOT NULL,
    renter_id        UUID NOT NULL,
    description      VARCHAR(2000) NOT NULL,
    priority         VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    version          BIGINT NOT NULL DEFAULT 0,
    vendor_id        UUID,
    requested_date   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolution_notes VARCHAR(2000),
    resolved_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_maintenance_org_id UNIQUE (organization_id, id),
    CONSTRAINT ck_maintenance_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'EMERGENCY')),
    CONSTRAINT ck_maintenance_status CHECK (status IN ('SUBMITTED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED')),
    CONSTRAINT ck_maintenance_version CHECK (version >= 0),
    CONSTRAINT fk_maintenance_unit
        FOREIGN KEY (organization_id, unit_id)
        REFERENCES units(organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_renter
        FOREIGN KEY (organization_id, renter_id)
        REFERENCES renters(organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_vendor
        FOREIGN KEY (organization_id, vendor_id)
        REFERENCES vendors(organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_maintenance_org_status_priority
    ON maintenance_requests(organization_id, status, priority);
CREATE INDEX ix_maintenance_org_unit ON maintenance_requests(organization_id, unit_id);
CREATE INDEX ix_maintenance_org_vendor ON maintenance_requests(organization_id, vendor_id);

-- updated_at is populated on insert. Application auditing should maintain it on update.
