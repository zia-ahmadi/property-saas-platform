package com.example.property_core.property;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.example.property_core.property.api.CreatePropertyRequest;
import com.example.property_core.property.api.UpdatePropertyRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Properties API controller with tenant isolation enforcement.
 * All operations are automatically scoped to the authenticated user's organization.
 */
@RestController
@RequestMapping(path = "/api/v1/properties", produces = MediaType.APPLICATION_JSON_VALUE)
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    /**
     * GET /api/v1/properties
     * List all properties for the current organization.
     * Query parameter 'status' can be used to filter by status (ACTIVE, INACTIVE).
     */
    @GetMapping
    public ResponseEntity<List<Property>> listProperties(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status) {
        List<Property> properties = status != null
                ? propertyService.getAllProperties(status)
                : propertyService.getAllProperties();
        return ResponseEntity.ok(properties);
    }

    /**
     * POST /api/v1/properties
     * Create a new property for the current organization.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Property> createProperty(@Valid @RequestBody CreatePropertyRequest request) {
        Property property = propertyService.createProperty(
                request.name(),
                request.address(),
                request.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(property);
    }

    /**
     * GET /api/v1/properties/{id}
     * Get a specific property by ID, scoped to the current organization.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Property> getProperty(@PathVariable UUID id) {
        Property property = propertyService.getProperty(id);
        return ResponseEntity.ok(property);
    }

    /**
     * PUT /api/v1/properties/{id}
     * Update a property, scoped to the current organization.
     */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Property> updateProperty(@PathVariable UUID id, @Valid @RequestBody UpdatePropertyRequest request) {
        Property updated = propertyService.updateProperty(
                id,
                request.name(),
                request.address(),
                request.status());
        return ResponseEntity.ok(updated);
    }

    /**
     * PATCH /api/v1/properties/{id}/archive
     * Archive (soft delete) a property by setting status to INACTIVE.
     */
    @PatchMapping("/{id}/archive")
    public ResponseEntity<Property> archiveProperty(@PathVariable UUID id) {
        Property archived = propertyService.archiveProperty(id);
        return ResponseEntity.ok(archived);
    }

    /**
     * DELETE /api/v1/properties/{id}
     * Permanently delete a property, scoped to the current organization.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProperty(@PathVariable UUID id) {
        propertyService.deleteProperty(id);
        return ResponseEntity.noContent().build();
    }
}
