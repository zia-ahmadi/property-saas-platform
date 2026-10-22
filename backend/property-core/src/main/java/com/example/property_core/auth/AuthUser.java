package com.example.property_core.auth;

import java.util.List;
import java.util.UUID;

public record AuthUser(
        UUID id,
        UUID organizationId,
        String email,
        String passwordHash,
        String fullName,
        boolean active,
        List<String> roles) {

    public AuthUser {
        roles = List.copyOf(roles);
    }

    public AuthUser withRoles(List<String> userRoles) {
        return new AuthUser(id, organizationId, email, passwordHash, fullName, active, userRoles);
    }
}
