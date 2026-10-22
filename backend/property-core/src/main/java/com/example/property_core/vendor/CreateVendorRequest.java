package com.example.property_core.vendor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVendorRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 100) String serviceType,
        @Email @Size(max = 320) String email,
        @Size(max = 40) String phone) {
}
