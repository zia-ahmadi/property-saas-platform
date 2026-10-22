# ADR-001: PostgreSQL relational model and organization-level tenancy

- **Status:** Proposed for approval
- **Date:** 2026-10-22

## Context

The repository's core backend uses Spring Boot, Spring Data JPA and Flyway. The local
Compose file provisions PostgreSQL. The REST contract describes organization registration,
properties, units, renter contacts, leases, invoices, payments, vendors, and maintenance
requests. GraphQL is a read-only dashboard projection. The product decision is that only
organization staff have login accounts; renters are contact records.

## Decisions

1. Use PostgreSQL with Flyway-managed SQL migrations.
2. Use one shared database and schema with a required `organization_id` on every
   organization-owned entity.
3. Derive organization context from the authenticated JWT, never from request input.
4. Add composite foreign keys to enforce same-organization relationships at the database
   level in addition to scoped application queries.
5. Store staff credentials in `users`; store renters separately in `renters`.
6. Use UUID identifiers, `timestamptz` for instants, `date` for lease/invoice calendar
   dates, and `numeric(12,2)` for money.
7. Persist payment idempotency keys and a request fingerprint so the same key can be
   replayed safely and conflicting payload reuse can be rejected.
8. Add a unique recurring billing period per lease to make scheduled invoice generation
   idempotent.
9. Keep the GraphQL dashboard as a query/read model derived from operational tables; do
   not store dashboard totals as a separate source of truth.

## Consequences

- Application queries must always be organization-scoped.
- Every repository/service path must use the organization ID from the security context.
- Deleting business records is restricted where financial or operational history depends
  on them; use status changes/archive semantics instead.
- API and schema contract gaps listed in `DATABASE-DESIGN-NOTES.md` must be resolved
  before treating the initial migration as final.
