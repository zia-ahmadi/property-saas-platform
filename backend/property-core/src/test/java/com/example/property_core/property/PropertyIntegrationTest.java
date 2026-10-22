package com.example.property_core.property;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.property_core.common.Page;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PropertyIntegrationTest {
    @Autowired
    private PropertyService propertyService;

    @Autowired
    private JdbcClient jdbcClient;

    private UUID organizationId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .param("id", organizationId)
                .param("name", "Integration Test Organization")
                .update();
        SecurityContextHolder.getContext().setAuthentication(authentication(userId, organizationId));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsPropertyForCurrentOrganization() {
        Property property = propertyService.createProperty(
                "Sunset Apartments", "123 Main Street, City, State 12345");

        assertNotNull(property.id());
        assertEquals("Sunset Apartments", property.name());
        assertEquals("123 Main Street, City, State 12345", property.address());
        assertEquals("ACTIVE", property.status());
    }

    @Test
    void rejectsInvalidPropertyData() {
        assertThrows(IllegalArgumentException.class, () -> propertyService.createProperty("", "Address"));
        assertThrows(IllegalArgumentException.class, () -> propertyService.createProperty("Property", " "));
    }

    @Test
    void listsAndFiltersPropertiesWithPagination() {
        propertyService.createProperty("Active Property", "Address A");
        propertyService.updateProperty(
                propertyService.createProperty("Inactive Property", "Address B").id(),
                null,
                null,
                PropertyStatus.INACTIVE);

        Page<Property> all = propertyService.getProperties(0, 1, "name,asc", null);
        Page<Property> active = propertyService.getProperties(0, 20, null, "ACTIVE");

        assertEquals(2, all.totalElements());
        assertEquals(2, all.totalPages());
        assertEquals(1, all.content().size());
        assertEquals(1, active.totalElements());
        assertEquals("Active Property", active.content().getFirst().name());
    }

    @Test
    void getsUpdatesAndDeletesProperties() {
        Property created = propertyService.createProperty("Test Property", "Test Address");
        Property updated = propertyService.updateProperty(
                created.id(), "Updated Property", "Updated Address", PropertyStatus.INACTIVE);

        assertEquals("Updated Property", updated.name());
        assertEquals("INACTIVE", updated.status());
        propertyService.deleteProperty(created.id());
        assertThrows(PropertyNotFoundException.class, () -> propertyService.getProperty(created.id()));
    }

    @Test
    void doesNotExposePropertiesAcrossOrganizations() {
        Property created = propertyService.createProperty("Tenant A Property", "Address");
        UUID otherOrganizationId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(authentication(UUID.randomUUID(), otherOrganizationId));

        assertThrows(PropertyNotFoundException.class, () -> propertyService.getProperty(created.id()));
    }

    @Test
    void rejectsDeletingPropertyWithUnits() {
        Property property = propertyService.createProperty("Property With Unit", "Address");
        jdbcClient.sql("""
                INSERT INTO units (organization_id, property_id, name, monthly_rent)
                VALUES (:organizationId, :propertyId, 'Unit 1', 1000.00)
                """)
                .param("organizationId", organizationId)
                .param("propertyId", property.id())
                .update();

        assertThrows(PropertyConflictException.class, () -> propertyService.deleteProperty(property.id()));
    }

    private JwtAuthenticationToken authentication(UUID principalId, UUID tenantId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(principalId.toString())
                .claim("tenantId", tenantId.toString())
                .claim("email", "test@example.com")
                .claim("fullName", "Test User")
                .claim("roles", List.of("ADMIN"))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
