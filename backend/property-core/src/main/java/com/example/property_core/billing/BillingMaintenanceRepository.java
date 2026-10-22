package com.example.property_core.billing;

import java.time.LocalDate;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BillingMaintenanceRepository {
    private final JdbcClient jdbcClient;

    public BillingMaintenanceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int generateMonthlyInvoices(LocalDate periodStart, LocalDate periodEnd) {
        return jdbcClient.sql("""
                INSERT INTO invoices (
                    organization_id, lease_id, renter_id, amount, due_date, status,
                    billing_period_start, billing_period_end, description)
                SELECT organization_id, id, renter_id, monthly_rent, :periodEnd, 'ISSUED',
                       :periodStart, :periodEnd, 'Monthly rent'
                FROM leases
                WHERE status = 'ACTIVE'
                  AND monthly_rent > 0
                  AND start_date <= :periodEnd
                  AND end_date >= :periodStart
                ON CONFLICT (organization_id, lease_id, billing_period_start, billing_period_end)
                    WHERE billing_period_start IS NOT NULL
                DO NOTHING
                """)
                .param("periodStart", periodStart)
                .param("periodEnd", periodEnd)
                .update();
    }

    public int markOverdue(LocalDate asOf) {
        return jdbcClient.sql("""
                UPDATE invoices
                SET status = 'OVERDUE', version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE status = 'ISSUED' AND due_date < :asOf
                """)
                .param("asOf", asOf)
                .update();
    }
}
