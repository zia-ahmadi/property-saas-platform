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

## 3. Contract gaps and implementation decisions

1. **Role `TENANT` conflicts with the agreed product behavior.** The OpenAPI enum allows
   `ADMIN`, `MANAGER`, `TENANT`, but renters do not have login accounts. The migration
   retains the enum value for contract compatibility, but the application should not assign
   `TENANT` to renter contacts. Prefer changing the API role enum to `ADMIN` and `MANAGER`
   unless there is another intended staff use for `TENANT`.
2. **Invoice `version`.** The OpenAPI Invoice response exposes the database version. It
   increments when an invoice is fully paid or changes from `ISSUED` to `OVERDUE`.
3. **Money types.** OpenAPI uses JSON `number`/`double` for rents and invoice/payment amounts.
   The database uses exact `numeric(12,2)` for financial correctness. Consider changing the
   API to a decimal-safe representation or document rounding and currency rules.
4. **Currency is unspecified.** No currency field appears in the current contracts. The
   migration does not assume one. Decide whether each organization has a single configured
   currency or each invoice carries a currency before implementing billing.
5. **Payment status and invoice status consistency.** Payment creation records a completed
   payment. Only `COMPLETED` payments count toward the balance; the service locks the invoice
   while checking the balance and recording payment. Failed/refunded payment workflows are
   not exposed by the current contract.
6. **Unit occupancy consistency.** Lease activation/termination and renewal activation update
   unit occupancy in the same transaction as the lease transition. The unit PATCH contract
   also allows an explicit status change, so clients should avoid overriding occupancy while
   an active lease exists.
7. **Lease overlap rule.** A unit may have one active lease and one renewal draft at a
   time. Normal lease creation rejects either an active or draft lease; renewals are the
   explicit exception. Future date-range overlaps are otherwise validated by service
   workflows rather than a PostgreSQL exclusion constraint.
8. **Invoice amount and renter relationship.** Invoice includes both `leaseId` and `renterId`.
   A composite foreign key enforces that the renter is the one attached to the lease.
9. **Invoice due-date validation.** Manual invoice creation rejects a due date that is not
   in the future. This is application validation because the rule depends on the current date.
10. **Recurring period definition.** Recurring invoices use calendar-month periods. The
    period is the first through last day of the month, and the invoice is due on the last
    day of that month.
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
- Use version-conditional SQL updates for lease and maintenance transitions, and map the
  contract's quoted `If-Match` value to the stored version. Invoice state changes increment
  its version inside the transaction.
- Generate recurring invoices inside a transaction and rely on the unique period index to
  prevent duplicates across retries.
- GraphQL dashboard aggregates must always be organization-scoped and should use `ISSUED`
  and `OVERDUE` invoice statuses for the contract's outstanding amount.
- `maintenanceAlerts` defaults to unresolved statuses when no status filter is provided.
- Integration tests cover tenant-scoped property access, same-key payment replay and changed
  payload rejection, overpayment rejection, invoice settlement/overdue transitions,
  recurring invoice deduplication, and maintenance workflow version conflicts.

## 5. Remaining product decisions

The REST and GraphQL contracts are implemented against the schema. Before production, decide
whether staff may use the `TENANT` role and define the organization currency; neither is
specified by the current API contract. Cross-request concurrent-payment load testing and
multi-instance SSE delivery are also outside the current in-process implementation.
