DROP INDEX uq_leases_one_open_per_unit;

CREATE UNIQUE INDEX uq_leases_one_active_per_unit
    ON leases(organization_id, unit_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_leases_one_draft_per_unit
    ON leases(organization_id, unit_id)
    WHERE status = 'DRAFT';
