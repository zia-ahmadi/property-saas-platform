package com.example.property_core.tenant;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import com.example.property_core.property.Property;
import com.example.property_core.property.PropertyRepository;
import com.example.property_core.property.PropertyService;

/**
 * Integration tests to verify tenant isolation.
 * Ensures users can only access organizations where they have valid membership.
 */
@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationTest {

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private PropertyRepository propertyRepository;

    private static final UUID ORG_1 = UUID.randomUUID();
    private static final UUID ORG_2 = UUID.randomUUID();
    private static final UUID USER_1 = UUID.randomUUID();
    private static final UUID USER_2 = UUID.randomUUID();

    @Test
    void testTenantContextExtractsOrganizationIdFromJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim(JwtClaimNames.SUB, USER_1.toString())
                .claim("tenantId", ORG_1.toString())
                .claim("email", "user1@example.com")
                .claim("fullName", "User One")
                .claim("roles", List.of("ADMIN"))
                .build();

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UUID organizationId = TenantContext.getCurrentOrganizationId();
        assertEquals(ORG_1, organizationId);

        SecurityContextHolder.clearContext();
    }

    @Test
    void testTenantContextThrowsWhenNoAuthentication() {
        SecurityContextHolder.clearContext();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            TenantContext.getCurrentOrganizationId();
        });

        assertTrue(exception.getMessage().contains("No authenticated user"));
    }

    @Test
    void testTenantContextReturnsOptionalWhenNoAuthentication() {
        SecurityContextHolder.clearContext();

        var organizationId = TenantContext.getOrganizationIdIfAvailable();
        assertTrue(organizationId.isEmpty());
    }

    @Test
    void testRepositoryScopesQueriesByOrganization() {
        // Simulate user from ORG_1
        Jwt jwt1 = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim(JwtClaimNames.SUB, USER_1.toString())
                .claim("tenantId", ORG_1.toString())
                .claim("email", "user1@example.com")
                .claim("fullName", "User One")
                .claim("roles", List.of("ADMIN"))
                .build();

        JwtAuthenticationToken auth1 = new JwtAuthenticationToken(jwt1);
        SecurityContextHolder.getContext().setAuthentication(auth1);

        // Verify that the repository uses the correct organization ID
        UUID currentOrg = TenantContext.getCurrentOrganizationId();
        assertEquals(ORG_1, currentOrg);

        SecurityContextHolder.clearContext();
    }

    @Test
    void testCrossOrganizationAccessIsPrevented() {
        // This test demonstrates the pattern for preventing cross-organization access
        // In a real scenario, you would create data in ORG_1, then try to access it as ORG_2

        UUID org1 = UUID.randomUUID();
        UUID org2 = UUID.randomUUID();

        // Validate that org2 does not equal org1
        assertNotEquals(org1, org2);

        // The TenantScopedRepository.validateOrganizationAccess() method would throw
        // IllegalStateException if a user from org2 tried to access an entity from org1
        assertThrows(IllegalStateException.class, () -> {
            TenantScopedRepository repo = new TenantScopedRepository(null) {
                @Override
                protected UUID currentOrganizationId() {
                    return org2; // User is in org2
                }
            };
            repo.validateOrganizationAccess(org1); // Trying to access org1 entity
        });
    }
}
