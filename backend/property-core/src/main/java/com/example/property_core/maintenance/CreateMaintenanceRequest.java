package com.example.property_core.maintenance;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMaintenanceRequest(
        @NotNull UUID unitId,
        @NotNull UUID renterId,
        @NotBlank @Size(min = 5, max = 2000) String description,
        @NotNull MaintenancePriority priority) {
}
