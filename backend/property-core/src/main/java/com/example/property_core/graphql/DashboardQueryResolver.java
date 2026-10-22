package com.example.property_core.graphql;

import java.time.Instant;
import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import com.example.property_core.tenant.TenantContext;

@Controller
public class DashboardQueryResolver {
    private final DashboardRepository repository;

    public DashboardQueryResolver(DashboardRepository repository) {
        this.repository = repository;
    }

    @QueryMapping
    public PortfolioDashboard portfolioDashboard() {
        DashboardRepository.DashboardCounts counts = repository.counts();
        int units = Math.toIntExact(counts.units());
        int occupiedUnits = Math.toIntExact(counts.occupiedUnits());
        List<MaintenanceAlert> alerts = repository.maintenanceAlerts(null, null);
        return new PortfolioDashboard(
                TenantContext.getCurrentOrganizationId(),
                Math.toIntExact(counts.properties()),
                units,
                occupiedUnits,
                units == 0 ? 0.0 : (double) occupiedUnits / units,
                counts.outstanding().doubleValue(),
                Math.toIntExact(counts.overdueInvoices()),
                alerts,
                Instant.now().toString());
    }

    @QueryMapping
    public List<MaintenanceAlert> maintenanceAlerts(
            @Argument String status, @Argument String priority) {
        return repository.maintenanceAlerts(status, priority);
    }
}
