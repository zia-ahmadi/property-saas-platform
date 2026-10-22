package com.example.property_core.property.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePropertyRequest(
        @NotBlank @Size(min = 1, max = 200) String name,
        @NotBlank @Size(min = 1, max = 500) String address) {
}
