package com.example.property_core.property;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for Property Management.
 * Tests the service layer with tenant isolation.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PropertyIntegrationTest {

    @Autowired
    private PropertyService propertyService;

    private UUID organizationId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        userId = UUID.randomUUID();

        // Create a mock JWT for testing
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .claim("sub", userId.toString())
                .claim("tenantId", organizationId.toString())
                .claim("email", "test@example.com")
                .claim("fullName", "Test User")
                .claim("roles", List.of("ADMIN"))
                .build();

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void testCreateProperty() {
        Property property = propertyService.createProperty(
                "Sunset Apartments",
                "123 Main Street, City, State 12345",
                PropertyStatus.ACTIVE);

        assertNotNull(property);
        assertNotNull(property.id());
        assertEquals("Sunset Apartments", property.name());
        assertEquals("123 Main Street, City, State 12345", property.address());
        assertEquals("ACTIVE", property.status());
        assertEquals(organizationId, property.organizationId());
    }

    @Test
    void testCreatePropertyWithNullStatus() {
        Property property = propertyService.createProperty(
                "Test Property",
                "Test Address",
                null);

        assertNotNull(property);
        assertEquals("ACTIVE", property.status()); // Should default to ACTIVE
    }

    @Test
    void testCreatePropertyWithInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> {
            propertyService.createProperty("", "Test Address", PropertyStatus.ACTIVE);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            propertyService.createProperty("A", "Test Address", PropertyStatus.ACTIVE); // Too short
        });

        assertThrows(IllegalArgumentException.class, () -> {
            propertyService.createProperty("Test", "", PropertyStatus.ACTIVE);
        });
    }

    @Test
    void testListProperties() {
        propertyService.createProperty("Property A", "Address A", PropertyStatus.ACTIVE);
        propertyService.createProperty("Property B", "Address B", PropertyStatus.INACTIVE);

        List<Property> properties = propertyService.getAllProperties();

        assertEquals(2, properties.size());
    }

    @Test
    void testListPropertiesFilteredByStatus() {
        propertyService.createProperty("Active Property", "Address A", PropertyStatus.ACTIVE);
        propertyService.createProperty("Inactive Property", "Address B", PropertyStatus.INACTIVE);

        List<Property> activeProperties = propertyService.getAllProperties("ACTIVE");

        assertEquals(1, activeProperties.size());
        assertEquals("Active Property", activeProperties.get(0).name());
    }

    @Test
    void testGetProperty() {
        Property created = propertyService.createProperty(
                "Test Property",
                "Test Address",
                PropertyStatus.ACTIVE);

        Property found = propertyService.getProperty(created.id());

        assertNotNull(found);
        assertEquals(created.id(), found.id());
        assertEquals("Test Property", found.name());
    }

    @Test
    void testGetPropertyNotFound() {
        UUID nonExistentId = UUID.randomUUID();

        assertThrows(PropertyNotFoundException.class, () -> {
            propertyService.getProperty(nonExistentId);
        });
    }

    @Test
    void testUpdateProperty() {
        Property created = propertyService.createProperty(
                "Original Name",
                "Original Address",
                PropertyStatus.ACTIVE);

        Property updated = propertyService.updateProperty(
                created.id(),
                "Updated Name",
                "Updated Address",
                PropertyStatus.INACTIVE);

        assertNotNull(updated);
        assertEquals("Updated Name", updated.name());
        assertEquals("Updated Address", updated.address());
        assertEquals("INACTIVE", updated.status());
    }

    @Test
    void testUpdatePropertyNotFound() {
        UUID nonExistentId = UUID.randomUUID();

        assertThrows(PropertyNotFoundException.class, () -> {
            propertyService.updateProperty(
                    nonExistentId,
                    "Name",
                    "Address",
                    PropertyStatus.ACTIVE);
        });
    }

    @Test
    void testArchiveProperty() {
        Property created = propertyService.createProperty(
                "To Archive",
                "Archive Address",
                PropertyStatus.ACTIVE);

        Property archived = propertyService.archiveProperty(created.id());

        assertNotNull(archived);
        assertEquals("INACTIVE", archived.status());
    }

    @Test
    void testDeleteProperty() {
        Property created = propertyService.createProperty(
                "To Delete",
                "Delete Address",
                PropertyStatus.ACTIVE);

        propertyService.deleteProperty(created.id());

        assertThrows(PropertyNotFoundException.class, () -> {
            propertyService.getProperty(created.id());
        });
    }

    @Test
    void testTenantIsolation() {
        // Create a property in the current organization
        Property property = propertyService.createProperty(
                "Org A Property",
                "Address A",
                PropertyStatus.ACTIVE);

        // Try to access it with a different organization context
        UUID otherOrgId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Jwt otherJwt = Jwt.withTokenValue("other-token")
                .header("alg", "HS256")
                .claim("sub", otherUserId.toString())
                .claim("tenantId", otherOrgId.toString())
                .claim("email", "other@example.com")
                .claim("fullName", "Other User")
                .claim("roles", List.of("ADMIN"))
                .build();

        JwtAuthenticationToken otherAuth = new JwtAuthenticationToken(otherJwt);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(otherAuth);

        // The property should not be found in the other organization's context
        assertThrows(PropertyNotFoundException.class, () -> {
            propertyService.getProperty(property.id());
        });

        // Restore original context
        Jwt originalJwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .claim("sub", userId.toString())
                .claim("tenantId", organizationId.toString())
                .claim("email", "test@example.com")
                .claim("fullName", "Test User")
                .claim("roles", List.of("ADMIN"))
                .build();

        JwtAuthenticationToken originalAuth = new JwtAuthenticationToken(originalJwt);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(originalAuth);
    }
}
