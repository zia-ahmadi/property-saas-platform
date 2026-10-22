package com.example.property_core.property;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.property_core.common.Page;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    /**
     * Get paginated properties for the current organization.
     */
    public Page<Property> getProperties(int page, int size, String sort, String status) {
        List<Property> properties = propertyRepository.findAll(page, size, sort, status);
        long totalElements = propertyRepository.count(status);
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new Page<>(properties, page, size, totalElements, totalPages);
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
    public Property createProperty(String name, String address) {
        validatePropertyData(name, address);
        return propertyRepository.create(name, address, PropertyStatus.ACTIVE.name());
    }

    /**
     * Update a property, scoped to the current organization.
     * @throws PropertyNotFoundException if property doesn't exist or belongs to another organization
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public Property updateProperty(UUID id, String name, String address, PropertyStatus status) {
        if (name != null) validatePropertyData(name, address != null ? address : "");
        if (address != null) validatePropertyData(name != null ? name : "", address);
        
        Property existing = getProperty(id);
        String nameValue = name != null ? name : existing.name();
        String addressValue = address != null ? address : existing.address();
        String statusValue = status != null ? status.name() : existing.status();
        
        return propertyRepository.update(id, nameValue, addressValue, statusValue)
                .orElseThrow(() -> new PropertyNotFoundException(id));
    }

    /**
     * Delete a property, scoped to the current organization.
     * @throws PropertyNotFoundException if property doesn't exist or belongs to another organization
     * @throws PropertyConflictException if property has active units or leases
     */
    @Transactional
    public void deleteProperty(UUID id) {
        Property property = getProperty(id);
        
        if (propertyRepository.hasActiveDependencies(id)) {
            throw new PropertyConflictException(
                    "Cannot delete property with active units or leases. " +
                    "Property must have no occupied units and no active leases.");
        }
        
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
        if (name.length() < 1) {
            throw new IllegalArgumentException("Property name must be at least 1 character");
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
