package com.example.property_core.property.api;

import com.example.property_core.property.PropertyStatus;

import jakarta.validation.constraints.Size;

public record UpdatePropertyRequest(
        @Size(min = 1, max = 200) String name,
        @Size(min = 1, max = 500) String address,
        PropertyStatus status) {
}
