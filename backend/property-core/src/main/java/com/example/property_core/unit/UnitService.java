package com.example.property_core.unit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnitService {

    private final UnitRepository unitRepository;

    public UnitService(UnitRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    /**
     * Get all units for the current organization.
     */
    public List<Unit> getAllUnits() {
        return unitRepository.findAll();
    }

    /**
     * Get a unit by ID, scoped to the current organization.
     */
    public Optional<Unit> getUnit(UUID id) {
        return unitRepository.findById(id);
    }

    /**
     * Get all units for a specific property, scoped to the current organization.
     */
    public List<Unit> getUnitsByProperty(UUID propertyId) {
        return unitRepository.findByPropertyId(propertyId);
    }

    /**
     * Create a new unit for the current organization.
     */
    @Transactional
    public Unit createUnit(UUID propertyId, String name, String status, Integer bedrooms, BigDecimal bathrooms, BigDecimal monthlyRent) {
        return unitRepository.create(propertyId, name, status, bedrooms, bathrooms, monthlyRent);
    }

    /**
     * Update a unit, scoped to the current organization.
     */
    @Transactional
    public Optional<Unit> updateUnit(UUID id, String name, String status, Integer bedrooms, BigDecimal bathrooms, BigDecimal monthlyRent) {
        return unitRepository.update(id, name, status, bedrooms, bathrooms, monthlyRent);
    }

    /**
     * Delete a unit, scoped to the current organization.
     */
    @Transactional
    public boolean deleteUnit(UUID id) {
        return unitRepository.delete(id);
    }
}
