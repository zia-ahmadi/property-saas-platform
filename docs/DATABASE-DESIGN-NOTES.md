# Database design notes and contract review

## 1. Mapping from API to tables

| Contract resource | Table | Notes |
|---|---|---|
| Registration organization | `organizations` | `organizations.id` becomes JWT `tenantId`. |
| Auth user/current user | `users` | Staff account, password hash only; never store plaintext password. |
| Auth roles | `user_roles` | One or more roles per staff user. |
| Property | `properties` | Organization-scoped. |
| Unit | `units` | Belongs to a property in the same organization. |
| API `Tenant` | `renters` | Contact record, not a login account. |
| Lease | `leases` | Links renter and unit; stores lifecycle status/version. |
| Invoice | `invoices` | Links lease and renter; recurring period fields support idempotent job. |
| Payment | `payments` | Organization-scoped idempotency key and request hash. |
| Vendor | `vendors` | Organization-scoped maintenance provider. |
| Maintenance request | `maintenance_requests` | Links unit, renter and optional vendor. |

## 2. Tenancy and security

- `tenantId` in the API means the **organization ID**, not the renter.
- Do not accept `organization_id`/`tenantId` from request bodies or query parameters.
- Scope every database lookup by the authenticated organization.
- Composite foreign keys protect relationships against accidental cross-organization IDs.
- The current login contract takes only email/password, so the proposed design makes user email
  globally unique and assumes each user belongs to one organization. Supporting the same
  user in multiple organizations would require an organization-selection/login contract change.

## 3. Contract gaps requiring a deliberate decision

1. **Role `TENANT` conflicts with the agreed product behavior.** The OpenAPI enum allows
   `ADMIN`, `MANAGER`, `TENANT`, but renters do not have login accounts. The migration
   retains the enum value for contract compatibility, but the application should not assign
   `TENANT` to renter contacts. Prefer changing the API role enum to `ADMIN` and `MANAGER`
   unless there is another intended staff use for `TENANT`.
2. **Invoice `version` mismatch.** The OpenAPI overview says mutable workflow entities
   including `Invoice` expose a `version`, but the Invoice response schema does not list it
   and no invoice transition endpoints are defined. The database includes `version` to
   support the overview, but align the OpenAPI response/behavior before implementing.
3. **Money types.** OpenAPI uses JSON `number`/`double` for rents and invoice/payment amounts.
   The database uses exact `numeric(12,2)` for financial correctness. Consider changing the
   API to a decimal-safe representation or document rounding and currency rules.
4. **Currency is unspecified.** No currency field appears in the current contracts. The
   migration does not assume one. Decide whether each organization has a single configured
   currency or each invoice carries a currency before implementing billing.
5. **Payment status and invoice status consistency.** Define how `PENDING`, `FAILED`, and
   `REFUNDED` payments affect the invoice balance and `PAID` status. Only successful,
   non-refunded payments should count toward settled balance, subject to the final business
   rule. Prevent overpayment with a transaction/locking strategy, not just a pre-check.
6. **Unit occupancy consistency.** The API exposes a writable-looking unit status including
   `OCCUPIED`, while leases also have an `ACTIVE` status. Define whether occupancy is derived
   from active leases or maintained explicitly. If both are stored, keep them synchronized
   transactionally.
7. **Lease overlap rule.** The migration prevents more than one `ACTIVE` lease per unit, but
   does not prevent overlapping date ranges among DRAFT/future leases. Decide whether future
   reservations may overlap and whether scheduled lease activation is needed.
8. **Invoice amount and renter relationship.** Invoice includes both `leaseId` and `renterId`.
   The service must ensure the renter is the renter attached to the referenced lease; this
   invariant is not fully enforced by the current FK layout.
9. **Invoice due-date validation.** The OpenAPI says manual invoice due date must be in the
   future. This is application/business validation; it is not enforced by a static database
   check because it depends on the current date and job behavior.
10. **Recurring period definition.** The schema supports a billing period, but the contract
    does not specify whether periods follow calendar months, lease anniversary dates, or
    another cycle. Choose one rule and use it consistently in the scheduled job.
11. **Updated timestamps.** PostgreSQL defaults populate creation timestamps, but `updated_at`
    needs application auditing (`@PreUpdate`/Spring Data auditing) or a database trigger.
12. **Deletion/retention.** The schema uses restrictive foreign keys for business history.
    Define whether records are archived instead of deleted, especially for invoices and payments.

## 4. Implementation guardrails

- `Idempotency-Key` is required for payment creation. Compute a canonical request hash from
  invoice ID, amount and payment method; if the key exists with the same hash, return the
  original payment, otherwise return conflict.
- For overpayment protection, lock the invoice row or use an equivalent concurrency-safe
  transaction while calculating remaining balance and recording the payment.
- Use JPA `@Version` for lease, maintenance request, and invoice optimistic locking, and map
  the contract's `If-Match` value to the stored version.
- Generate recurring invoices inside a transaction and rely on the unique period index to
  prevent duplicates across retries.
- GraphQL dashboard aggregates must always be organization-scoped and should use `ISSUED`
  and `OVERDUE` invoice statuses for the contract's outstanding amount.
- `maintenanceAlerts` defaults to unresolved statuses when no status filter is provided.
- Add integration tests for cross-organization access, duplicate payment keys, concurrent
  payments, duplicate recurring invoice generation, and optimistic-lock conflicts.

## 5. Suggested next step

Review the contract gaps above, especially role `TENANT`, currency, payment/invoice status
rules, and recurring billing period definition. Once confirmed, the schema can be refined
and the first Flyway migration can be treated as implementation-ready.
