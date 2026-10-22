package com.example.property_core.vendor;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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
@RequestMapping(path = "/api/v1/vendors", produces = MediaType.APPLICATION_JSON_VALUE)
public class VendorController {
    private final VendorService service;

    public VendorController(VendorService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Vendor> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Boolean active) {
        return service.list(active, page, size);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Vendor create(@Valid @RequestBody CreateVendorRequest request) {
        return service.create(request);
    }

    @GetMapping("/{vendorId}")
    public Vendor get(@PathVariable UUID vendorId) {
        return service.get(vendorId);
    }

    @PatchMapping(path = "/{vendorId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Vendor update(@PathVariable UUID vendorId, @Valid @RequestBody UpdateVendorRequest request) {
        return service.update(vendorId, request);
    }
}
