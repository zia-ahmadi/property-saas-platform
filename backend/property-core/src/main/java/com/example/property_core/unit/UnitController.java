package com.example.property_core.unit;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.property_core.common.Page;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class UnitController {
    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping("/properties/{propertyId}/units")
    public Page<Unit> listUnits(
            @PathVariable UUID propertyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) UnitStatus status) {
        return unitService.getUnitsByProperty(propertyId, page, size, status);
    }

    @PostMapping(path = "/properties/{propertyId}/units", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Unit createUnit(
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateUnitRequest request) {
        return unitService.createUnit(
                propertyId, request.name(), request.bedrooms(), request.bathrooms(), request.monthlyRent());
    }

    @GetMapping("/units/{unitId}")
    public Unit getUnit(@PathVariable UUID unitId) {
        return unitService.getUnit(unitId);
    }

    @PatchMapping(path = "/units/{unitId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Unit updateUnit(
            @PathVariable UUID unitId,
            @Valid @RequestBody UpdateUnitRequest request) {
        return unitService.updateUnit(
                unitId,
                request.name(),
                request.status(),
                request.bedrooms(),
                request.bathrooms(),
                request.monthlyRent());
    }

    public record CreateUnitRequest(
            @NotBlank @Size(min = 1, max = 100) String name,
            @Min(0) Integer bedrooms,
            @DecimalMin("0.0") @Digits(integer = 3, fraction = 1) BigDecimal bathrooms,
            @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal monthlyRent) {
    }

    public record UpdateUnitRequest(
            @Size(min = 1, max = 100) String name,
            UnitStatus status,
            @Min(0) Integer bedrooms,
            @DecimalMin("0.0") @Digits(integer = 3, fraction = 1) BigDecimal bathrooms,
            @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal monthlyRent) {
    }
}
