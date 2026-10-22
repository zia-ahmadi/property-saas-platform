package com.example.property_core.property.api;

import com.example.property_core.property.PropertyStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePropertyRequest(
        @NotBlank @Size(min = 2, max = 200) String name,
        @NotBlank @Size(max = 500) String address,
        PropertyStatus status) {
}
