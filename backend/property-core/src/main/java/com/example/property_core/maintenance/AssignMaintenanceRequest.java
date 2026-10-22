package com.example.property_core.maintenance;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AssignMaintenanceRequest(@NotNull UUID vendorId) {
}
