package com.example.property_core.unit.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUnitRequest(
        @NotBlank @Size(max = 100) String name,
        String status,
        Integer bedrooms,
        BigDecimal bathrooms,
        BigDecimal monthlyRent) {
}
