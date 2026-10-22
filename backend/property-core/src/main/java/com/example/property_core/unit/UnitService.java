package com.example.property_core.unit;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.example.property_core.common.Page;
import com.example.property_core.property.PropertyNotFoundException;
import com.example.property_core.property.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnitService {

    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;

    public UnitService(UnitRepository unitRepository, PropertyRepository propertyRepository) {
        this.unitRepository = unitRepository;
        this.propertyRepository = propertyRepository;
    }

    public Page<Unit> getUnitsByProperty(UUID propertyId, int page, int size, UnitStatus status) {
        if (propertyRepository.findById(propertyId).isEmpty()) {
            throw new PropertyNotFoundException(propertyId);
        }
        String statusValue = status == null ? null : status.name();
        List<Unit> units = unitRepository.findByPropertyId(propertyId, page, size, statusValue);
        long totalElements = unitRepository.countByPropertyId(propertyId, statusValue);
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new Page<>(units, page, size, totalElements, totalPages);
    }

    @Transactional
    public Unit createUnit(
            UUID propertyId, String name, Integer bedrooms, BigDecimal bathrooms, BigDecimal monthlyRent) {
        if (propertyRepository.findById(propertyId).isEmpty()) {
            throw new PropertyNotFoundException(propertyId);
        }
        return unitRepository.create(propertyId, name.trim(), bedrooms, bathrooms, monthlyRent);
    }

    @Transactional
    public Unit updateUnit(
            UUID id, String name, UnitStatus status, Integer bedrooms, BigDecimal bathrooms, BigDecimal monthlyRent) {
        if (name == null && status == null && bedrooms == null && bathrooms == null && monthlyRent == null) {
            throw new IllegalArgumentException("At least one unit field must be provided");
        }
        Unit existing = unitRepository.findById(id)
                .orElseThrow(() -> new UnitNotFoundException(id));
        if (name != null && name.isBlank()) {
            throw new IllegalArgumentException("Unit name cannot be blank");
        }
        return unitRepository.update(
                        id,
                        name == null ? null : name.trim(),
                        status == null ? null : status.name(),
                        bedrooms,
                        bathrooms,
                        monthlyRent)
                .orElseThrow(() -> new UnitNotFoundException(existing.id()));
    }

    public Unit getUnit(UUID id) {
        return unitRepository.findById(id).orElseThrow(() -> new UnitNotFoundException(id));
    }

    public static class UnitNotFoundException extends RuntimeException {
        public UnitNotFoundException(UUID id) {
            super("Unit not found");
        }
    }
}
