package com.example.property_core.vendor;

import java.util.UUID;

import com.example.property_core.common.Page;
import com.example.property_core.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class VendorService {
    private final VendorRepository repository;

    public VendorService(VendorRepository repository) {
        this.repository = repository;
    }

    public Page<Vendor> list(Boolean active, int page, int size) {
        long total = repository.count(active);
        return new Page<>(repository.findAll(active, page, size), page, size, total,
                (int) Math.ceil((double) total / size));
    }

    public Vendor get(UUID id) {
        return repository.findById(id).orElseThrow(this::notFound);
    }

    public Vendor create(CreateVendorRequest request) {
        return repository.create(
                request.name().trim(), request.serviceType().trim(), request.email(), request.phone());
    }

    public Vendor update(UUID id, UpdateVendorRequest request) {
        if (request.isEmpty()) {
            throw new IllegalArgumentException("At least one vendor field must be provided");
        }
        return repository.update(id, request).orElseThrow(this::notFound);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Vendor not found");
    }
}
