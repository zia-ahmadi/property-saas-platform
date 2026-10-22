package com.example.property_core.property;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    /**
     * Get all properties for the current organization.
     */
    public List<Property> getAllProperties() {
        return propertyRepository.findAll();
    }

    /**
     * Get all properties for the current organization, optionally filtered by status.
     */
    public List<Property> getAllProperties(String status) {
        return propertyRepository.findAll(status);
    }

    /**
     * Get a property by ID, scoped to the current organization.
     * @throws PropertyNotFoundException if property doesn't exist or belongs to another organization
     */
    public Property getProperty(UUID id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new PropertyNotFoundException(id));
    }

    /**
     * Create a new property for the current organization.
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public Property createProperty(String name, String address, PropertyStatus status) {
        validatePropertyData(name, address);
        String statusValue = status != null ? status.name() : PropertyStatus.ACTIVE.name();
        return propertyRepository.create(name, address, statusValue);
    }

    /**
     * Update a property, scoped to the current organization.
     * @throws PropertyNotFoundException if property doesn't exist or belongs to another organization
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public Property updateProperty(UUID id, String name, String address, PropertyStatus status) {
        validatePropertyData(name, address);
        String statusValue = status != null ? status.name() : PropertyStatus.ACTIVE.name();
        return propertyRepository.update(id, name, address, statusValue)
                .orElseThrow(() -> new PropertyNotFoundException(id));
    }

    /**
     * Archive (soft delete) a property by setting status to INACTIVE.
     * @throws PropertyNotFoundException if property doesn't exist or belongs to another organization
     */
    @Transactional
    public Property archiveProperty(UUID id) {
        Property property = getProperty(id);
        return updateProperty(id, property.name(), property.address(), PropertyStatus.INACTIVE);
    }

    /**
     * Delete a property, scoped to the current organization.
     * @throws PropertyNotFoundException if property doesn't exist or belongs to another organization
     */
    @Transactional
    public void deleteProperty(UUID id) {
        Property property = getProperty(id);
        boolean deleted = propertyRepository.delete(id);
        if (!deleted) {
            throw new PropertyNotFoundException(id);
        }
    }

    /**
     * Validate property data.
     * @throws IllegalArgumentException if validation fails
     */
    private void validatePropertyData(String name, String address) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Property name cannot be empty");
        }
        if (name.length() < 2) {
            throw new IllegalArgumentException("Property name must be at least 2 characters");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("Property name cannot exceed 200 characters");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Property address cannot be empty");
        }
        if (address.length() > 500) {
            throw new IllegalArgumentException("Property address cannot exceed 500 characters");
        }
    }
}
