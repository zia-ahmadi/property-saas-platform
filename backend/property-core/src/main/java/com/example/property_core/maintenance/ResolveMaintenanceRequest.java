package com.example.property_core.maintenance;

import jakarta.validation.constraints.Size;

public record ResolveMaintenanceRequest(@Size(max = 2000) String resolutionNotes) {
}
