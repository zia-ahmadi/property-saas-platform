package com.example.property_core.property;

import java.util.UUID;

import jakarta.validation.Valid;

import com.example.property_core.common.Page;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Properties API controller with tenant isolation enforcement.
 * All operations are automatically scoped to the authenticated user's organization.
 * Implements OpenAPI contract at /api-contracts/rest/openapi.yaml
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
     * List properties with pagination and filtering.
     * OpenAPI: listProperties
     */
    @GetMapping
    public ResponseEntity<Page<Property>> listProperties(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status) {
        
        // Validate pagination parameters
        if (page < 0) page = 0;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        
        Page<Property> properties = propertyService.getProperties(page, size, sort, status);
        return ResponseEntity.ok(properties);
    }

    /**
     * POST /api/v1/properties
     * Create a new property.
     * OpenAPI: createProperty
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Property> createProperty(@Valid @RequestBody CreatePropertyRequest request) {
        Property property = propertyService.createProperty(request.name(), request.address());
        return ResponseEntity.status(HttpStatus.CREATED).body(property);
    }

    /**
     * GET /api/v1/properties/{id}
     * Get a property by id.
     * OpenAPI: getProperty
     */
    @GetMapping("/{id}")
    public ResponseEntity<Property> getProperty(@PathVariable UUID id) {
        Property property = propertyService.getProperty(id);
        return ResponseEntity.ok(property);
    }

    /**
     * PATCH /api/v1/properties/{id}
     * Update a property.
     * OpenAPI: updateProperty
     */
    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Property> updateProperty(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePropertyRequest request) {
        Property updated = propertyService.updateProperty(
                id,
                request.name(),
                request.address(),
                request.status());
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/v1/properties/{id}
     * Delete a property.
     * Fails with 409 if the property still has active units or leases.
     * OpenAPI: deleteProperty
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProperty(@PathVariable UUID id) {
        propertyService.deleteProperty(id);
        return ResponseEntity.noContent().build();
    }
}
