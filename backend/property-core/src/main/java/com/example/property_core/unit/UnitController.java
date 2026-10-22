package com.example.property_core.unit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Valid;

import com.example.property_core.unit.api.UpdateUnitRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Units API controller with tenant isolation enforcement.
 * All operations are automatically scoped to the authenticated user's organization.
 */
@RestController
@RequestMapping(path = "/api/v1/units", produces = MediaType.APPLICATION_JSON_VALUE)
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    public ResponseEntity<List<Unit>> listUnits() {
        List<Unit> units = unitService.getAllUnits();
        return ResponseEntity.ok(units);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Unit> getUnit(@PathVariable UUID id) {
        Optional<Unit> unit = unitService.getUnit(id);
        return unit.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Unit> updateUnit(@PathVariable UUID id, @Valid @RequestBody UpdateUnitRequest request) {
        Optional<Unit> updated = unitService.updateUnit(
                id,
                request.name(),
                request.status() != null ? request.status() : "AVAILABLE",
                request.bedrooms(),
                request.bathrooms(),
                request.monthlyRent());
        return updated.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
