package com.example.property_core.graphql;

import java.util.List;
import java.util.UUID;

public record PortfolioDashboard(
        UUID tenantId,
        int totalProperties,
        int totalUnits,
        int occupiedUnits,
        double occupancyRate,
        double totalOutstandingAmount,
        int overdueInvoiceCount,
        List<MaintenanceAlert> activeMaintenanceRequests,
        String generatedAt) {
}
