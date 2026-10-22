package com.example.property_core.tenant;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.example.property_core.common.Page;
import com.example.property_core.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {
    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Page<Tenant> list(String search, int page, int size) {
        List<Tenant> content = tenantRepository.findAll(search, page, size);
        long totalElements = tenantRepository.count(search);
        return new Page<>(content, page, size, totalElements, (int) Math.ceil((double) totalElements / size));
    }

    public Tenant get(UUID id) {
        return tenantRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public Tenant create(String fullName, String email, String phone) {
        return tenantRepository.create(fullName.trim(), normalizeEmail(email), trimToNull(phone));
    }

    @Transactional
    public Tenant update(UUID id, String fullName, String email, String phone) {
        if (fullName == null && email == null && phone == null) {
            throw new IllegalArgumentException("At least one tenant field must be provided");
        }
        if (fullName != null && fullName.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be blank");
        }
        return tenantRepository.update(
                        id,
                        fullName == null ? null : fullName.trim(),
                        email == null ? null : normalizeEmail(email),
                        phone == null ? null : trimToNull(phone))
                .orElseThrow(() -> notFound(id));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null ? null : value.trim();
    }

    private ApiException notFound(UUID id) {
        return new ApiException(HttpStatus.NOT_FOUND, "Tenant record not found");
    }
}
