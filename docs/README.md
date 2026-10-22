# Property Management SaaS — Database Design

This package contains a **reviewable initial database design** for the existing
`property-saas-platform` repository. It is based on the current REST OpenAPI contract,
the GraphQL dashboard schema, and the decision that only organization staff have login
accounts; renters are contact records.

## Files

- `db/migration/V1__create_core_schema.sql` — PostgreSQL 15 / Flyway initial schema.
- `docs/diagrams/er-diagram.md` — entity relationship diagram (Mermaid).
- `docs/adr/ADR-001-database-and-tenancy.md` — key design decisions and rationale.
- `docs/DATABASE-DESIGN-NOTES.md` — mapping, constraints, and contract gaps to resolve.

## Where to put them

Copy the files into the same paths in the repository root:

```text
property-saas-platform/
├── backend/
│   └── property-core/
│       └── src/main/resources/
│           └── db/migration/
├── docs/
│   ├── adr/
│   ├── diagrams/
│   └── DATABASE-DESIGN-NOTES.md
```

**Important:** Move/copy `db/migration/V1__create_core_schema.sql` to
`backend/property-core/src/main/resources/db/migration/V1__create_core_schema.sql`
so Spring Boot's Flyway auto-configuration can discover it. The archive keeps it in
a short top-level `db/migration/` path for clarity.

## Before running the migration

1. Confirm the contract gaps listed in `docs/DATABASE-DESIGN-NOTES.md`.
2. Ensure the local database is disposable/empty. This migration creates tables and
   constraints; do not run it against a database containing important data.
3. Verify Flyway is configured and enabled in `backend/property-core`.
4. Run the application or `mvn flyway:migrate` only after reviewing the SQL and the
   contract gaps. The SQL is not automatically applied by this download.

## Core modeling decisions

- PostgreSQL is the database; Flyway owns schema changes.
- `organizations.id` is the organization/tenant ID placed in the JWT.
- Every organization-owned table stores `organization_id`.
- Composite foreign keys include `organization_id` to prevent cross-organization
  relationships at the database level, not only in application code.
- Staff user accounts are separate from renter contact records.
- UUID primary keys, UTC-aware timestamps (`timestamptz`), and `numeric` for money.
- Payment idempotency is persisted per organization and key.
- Recurring invoices have a lease and billing period, with a uniqueness constraint
  to prevent duplicate generation for the same lease and period.
- Dashboard totals are computed from source tables; no separate dashboard table is
  introduced at this stage.

This is a first schema proposal for review, not a claim that the database has already
been installed or that every OpenAPI inconsistency has been resolved.
