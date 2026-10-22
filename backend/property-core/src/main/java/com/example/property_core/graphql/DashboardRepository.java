package com.example.property_core.graphql;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.example.property_core.common.JdbcTimestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.example.property_core.tenant.TenantScopedRepository;

@Repository
public class DashboardRepository extends TenantScopedRepository {
    public DashboardRepository(JdbcClient jdbcClient) {
        super(jdbcClient);
    }

    public DashboardCounts counts() {
        return jdbcClient.sql("""
                SELECT
                    (SELECT COUNT(*) FROM properties WHERE organization_id = :organizationId) AS properties,
                    (SELECT COUNT(*) FROM units WHERE organization_id = :organizationId) AS units,
                    (SELECT COUNT(*) FROM units WHERE organization_id = :organizationId AND status = 'OCCUPIED')
                        AS occupied_units,
                    (SELECT COALESCE(SUM(GREATEST(
                        i.amount - COALESCE((
                            SELECT SUM(p.amount) FROM payments p
                            WHERE p.organization_id = i.organization_id
                              AND p.invoice_id = i.id AND p.status = 'COMPLETED'
                        ), 0), 0)), 0)
                     FROM invoices i
                     WHERE i.organization_id = :organizationId AND i.status IN ('ISSUED', 'OVERDUE'))
                        AS outstanding,
                    (SELECT COUNT(*) FROM invoices
                     WHERE organization_id = :organizationId AND status = 'OVERDUE') AS overdue_count
                """)
                .param("organizationId", currentOrganizationId())
                .query((resultSet, rowNumber) -> new DashboardCounts(
                        resultSet.getLong("properties"),
                        resultSet.getLong("units"),
                        resultSet.getLong("occupied_units"),
                        resultSet.getBigDecimal("outstanding"),
                        resultSet.getLong("overdue_count")))
                .single();
    }

    public List<MaintenanceAlert> maintenanceAlerts(String status, String priority) {
        String sql = """
                SELECT mr.id, mr.priority, mr.status, u.name AS unit_name, p.name AS property_name,
                       mr.description, mr.requested_date
                FROM maintenance_requests mr
                JOIN units u ON u.organization_id = mr.organization_id AND u.id = mr.unit_id
                JOIN properties p ON p.organization_id = u.organization_id AND p.id = u.property_id
                WHERE mr.organization_id = :organizationId
                """ + (status == null ? " AND mr.status <> 'RESOLVED'" : " AND mr.status = :status")
                + (priority == null ? "" : " AND mr.priority = :priority")
                + " ORDER BY CASE mr.priority WHEN 'EMERGENCY' THEN 0 WHEN 'HIGH' THEN 1 "
                + "WHEN 'MEDIUM' THEN 2 ELSE 3 END, mr.requested_date DESC, mr.id";
        var query = jdbcClient.sql(sql).param("organizationId", currentOrganizationId());
        if (status != null) query = query.param("status", status);
        if (priority != null) query = query.param("priority", priority);
        return query.query((resultSet, rowNumber) -> new MaintenanceAlert(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("priority"),
                resultSet.getString("status"),
                resultSet.getString("unit_name"),
                resultSet.getString("property_name"),
                resultSet.getString("description"),
                JdbcTimestamps.getInstant(resultSet, "requested_date").toString()))
                .list();
    }

    public record DashboardCounts(
            long properties,
            long units,
            long occupiedUnits,
            BigDecimal outstanding,
            long overdueInvoices) {
    }
}
