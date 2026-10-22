package com.example.property_core.unit;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
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
import com.example.property_core.property.Property;
import com.example.property_core.property.PropertyNotFoundException;
import com.example.property_core.property.PropertyRepository;
import com.example.property_core.property.PropertyService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UnitIntegrationTest {

    @Autowired
    private UnitService unitService;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private UUID organizationId;
    private UUID userId;
    private Property property;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .param("id", organizationId)
                .param("name", "Integration Test Organization")
                .update();
        SecurityContextHolder.getContext().setAuthentication(authentication(userId, organizationId));

        property = propertyService.createProperty("Test Property", "123 Test Street");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsUnitForProperty() {
        Unit unit = unitService.createUnit(
                property.id(),
                "Unit 1A",
                2,
                new BigDecimal("2.5"),
                new BigDecimal("1500.00"));

        assertNotNull(unit.id());
        assertEquals(property.id(), unit.propertyId());
        assertEquals("Unit 1A", unit.name());
        assertEquals(2, unit.bedrooms());
        assertEquals(new BigDecimal("2.5"), unit.bathrooms());
        assertEquals(new BigDecimal("1500.00"), unit.monthlyRent());
        assertEquals("AVAILABLE", unit.status());
    }

    @Test
    void rejectsCreatingUnitForNonExistentProperty() {
        assertThrows(PropertyNotFoundException.class, () -> {
            unitService.createUnit(
                    UUID.randomUUID(),
                    "Unit 1A",
                    2,
                    new BigDecimal("2.5"),
                    new BigDecimal("1500.00"));
        });
    }

    @Test
    void listsUnitsForPropertyWithPagination() {
        unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));
        unitService.createUnit(property.id(), "Unit 1B", 1, new BigDecimal("1.0"), new BigDecimal("1200.00"));
        unitService.createUnit(property.id(), "Unit 1C", 3, new BigDecimal("2.0"), new BigDecimal("2000.00"));

        Page<Unit> page1 = unitService.getUnitsByProperty(property.id(), 0, 2, null);
        assertEquals(2, page1.content().size());
        assertEquals(3, page1.totalElements());
        assertEquals(2, page1.totalPages());

        Page<Unit> page2 = unitService.getUnitsByProperty(property.id(), 1, 2, null);
        assertEquals(1, page2.content().size());
        assertEquals(3, page2.totalElements());
    }

    @Test
    void filtersUnitsByStatus() {
        Unit unit1 = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));
        Unit unit2 = unitService.createUnit(property.id(), "Unit 1B", 1, new BigDecimal("1.0"), new BigDecimal("1200.00"));

        unitService.updateUnit(unit1.id(), null, UnitStatus.OCCUPIED, null, null, null);

        Page<Unit> availableUnits = unitService.getUnitsByProperty(property.id(), 0, 10, UnitStatus.AVAILABLE);
        Page<Unit> occupiedUnits = unitService.getUnitsByProperty(property.id(), 0, 10, UnitStatus.OCCUPIED);

        assertEquals(1, availableUnits.totalElements());
        assertEquals(1, occupiedUnits.totalElements());
        assertEquals("Unit 1B", availableUnits.content().getFirst().name());
        assertEquals("Unit 1A", occupiedUnits.content().getFirst().name());
    }

    @Test
    void getsUnitById() {
        Unit created = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        Unit retrieved = unitService.getUnit(created.id());

        assertEquals(created.id(), retrieved.id());
        assertEquals(created.propertyId(), retrieved.propertyId());
        assertEquals("Unit 1A", retrieved.name());
    }

    @Test
    void throwsNotFoundWhenGettingNonExistentUnit() {
        assertThrows(UnitService.UnitNotFoundException.class, () -> {
            unitService.getUnit(UUID.randomUUID());
        });
    }

    @Test
    void updatesUnitPartially() {
        Unit created = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        Unit updated = unitService.updateUnit(created.id(), "Unit 1B", null, null, null, null);

        assertEquals("Unit 1B", updated.name());
        assertEquals(2, updated.bedrooms()); // unchanged
        assertEquals(new BigDecimal("2.5"), updated.bathrooms()); // unchanged
    }

    @Test
    void updatesUnitStatus() {
        Unit created = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        Unit updated = unitService.updateUnit(created.id(), null, UnitStatus.OCCUPIED, null, null, null);

        assertEquals("OCCUPIED", updated.status());
    }

    @Test
    void updatesAllUnitFields() {
        Unit created = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        Unit updated = unitService.updateUnit(
                created.id(),
                "Unit 1B",
                UnitStatus.MAINTENANCE,
                3,
                new BigDecimal("3.0"),
                new BigDecimal("2000.00"));

        assertEquals("Unit 1B", updated.name());
        assertEquals("MAINTENANCE", updated.status());
        assertEquals(3, updated.bedrooms());
        assertEquals(new BigDecimal("3.0"), updated.bathrooms());
        assertEquals(new BigDecimal("2000.00"), updated.monthlyRent());
    }

    @Test
    void rejectsUpdateWithNoFields() {
        Unit created = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        assertThrows(IllegalArgumentException.class, () -> {
            unitService.updateUnit(created.id(), null, null, null, null, null);
        });
    }

    @Test
    void rejectsUpdateWithBlankName() {
        Unit created = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        assertThrows(IllegalArgumentException.class, () -> {
            unitService.updateUnit(created.id(), "   ", null, null, null, null);
        });
    }

    @Test
    void doesNotExposeUnitsAcrossOrganizations() {
        // Create unit in Organization A
        Unit created = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        // Switch to Organization B
        UUID otherOrganizationId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .param("id", otherOrganizationId)
                .param("name", "Other Organization")
                .update();
        SecurityContextHolder.getContext().setAuthentication(authentication(otherUserId, otherOrganizationId));

        // Try to access unit from Organization A
        assertThrows(UnitService.UnitNotFoundException.class, () -> {
            unitService.getUnit(created.id());
        });

        // Try to update unit from Organization A
        assertThrows(UnitService.UnitNotFoundException.class, () -> {
            unitService.updateUnit(created.id(), "New Name", null, null, null, null);
        });
    }

    @Test
    void doesNotListUnitsFromOtherOrganizationProperties() {
        // Create property and unit in Organization A
        Property orgAProperty = propertyService.createProperty("Org A Property", "Address A");
        Unit orgAUnit = unitService.createUnit(orgAProperty.id(), "Unit A1", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        // Switch to Organization B
        UUID otherOrganizationId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .param("id", otherOrganizationId)
                .param("name", "Other Organization")
                .update();
        SecurityContextHolder.getContext().setAuthentication(authentication(otherUserId, otherOrganizationId));

        // Create property in Organization B with same ID as Organization A's property (should be separate due to tenant scoping)
        Property orgBProperty = propertyService.createProperty("Org B Property", "Address B");

        // List units for Organization B's property - should not include Organization A's units
        Page<Unit> units = unitService.getUnitsByProperty(orgBProperty.id(), 0, 10, null);
        assertEquals(0, units.totalElements());
    }

    @Test
    void repositoryPreventsCrossOrganizationUnitAccess() {
        // Create unit in Organization A
        Unit created = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        // Switch to Organization B
        UUID otherOrganizationId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .param("id", otherOrganizationId)
                .param("name", "Other Organization")
                .update();
        SecurityContextHolder.getContext().setAuthentication(authentication(otherUserId, otherOrganizationId));

        // Repository should return empty when trying to find unit from other organization
        assertTrue(unitRepository.findById(created.id()).isEmpty());
    }

    @Test
    void repositoryDeleteScopesByOrganization() {
        // Create unit in Organization A
        Unit created = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        // Switch to Organization B
        UUID otherOrganizationId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .param("id", otherOrganizationId)
                .param("name", "Other Organization")
                .update();
        SecurityContextHolder.getContext().setAuthentication(authentication(otherUserId, otherOrganizationId));

        // Try to delete unit from Organization A - should fail (unit not found in their context)
        boolean deleted = unitRepository.delete(created.id());
        assertFalse(deleted);

        // Switch back to Organization A
        SecurityContextHolder.getContext().setAuthentication(authentication(userId, organizationId));

        // Now the delete should succeed
        deleted = unitRepository.delete(created.id());
        assertTrue(deleted);
    }

    @Test
    void rejectsCreatingUnitForPropertyFromOtherOrganization() {
        // Create property in Organization A
        Property orgAProperty = propertyService.createProperty("Org A Property", "Address A");

        // Switch to Organization B
        UUID otherOrganizationId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .param("id", otherOrganizationId)
                .param("name", "Other Organization")
                .update();
        SecurityContextHolder.getContext().setAuthentication(authentication(otherUserId, otherOrganizationId));

        // Try to create unit for Organization A's property
        assertThrows(PropertyNotFoundException.class, () -> {
            unitService.createUnit(orgAProperty.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));
        });
    }

    @Test
    void databaseConstraintPreventsCrossOrganizationUnitPropertyReference() {
        // This test verifies the database-level constraint prevents cross-organization references
        // Even if application logic is bypassed, the database should reject it

        UUID otherOrganizationId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .param("id", otherOrganizationId)
                .param("name", "Other Organization")
                .update();

        // Try to insert a unit with organization_id from Org B but property_id from Org A
        // This should fail due to the composite foreign key constraint
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            jdbcClient.sql("""
                    INSERT INTO units (organization_id, property_id, name, monthly_rent)
                    VALUES (:organizationId, :propertyId, 'Unit X', 1000.00)
                    """)
                    .param("organizationId", otherOrganizationId)
                    .param("propertyId", property.id())
                    .update();
        });
    }

    @Test
    void databaseConstraintPreventsDeletingPropertyWithUnits() {
        // Create a unit for the property
        unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        // Try to delete the property - should fail due to ON DELETE RESTRICT
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            jdbcClient.sql("DELETE FROM properties WHERE id = :id")
                    .param("id", property.id())
                    .update();
        });
    }

    @Test
    void databaseConstraintPreventsDeletingUnitWithLeases() {
        // Create a unit
        Unit unit = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        // Create a renter
        UUID renterId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO renters (organization_id, id, full_name, email)
                VALUES (:organizationId, :id, 'John Doe', 'john@example.com')
                """)
                .param("organizationId", organizationId)
                .param("id", renterId)
                .update();

        // Create a lease for the unit
        UUID leaseId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO leases (organization_id, id, renter_id, unit_id, status, start_date, end_date, monthly_rent)
                VALUES (:organizationId, :id, :renterId, :unitId, 'ACTIVE', CURRENT_DATE, CURRENT_DATE + INTERVAL '1 year', 1500.00)
                """)
                .param("organizationId", organizationId)
                .param("id", leaseId)
                .param("renterId", renterId)
                .param("unitId", unit.id())
                .update();

        // Try to delete the unit - should fail due to ON DELETE RESTRICT
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            jdbcClient.sql("DELETE FROM units WHERE id = :id")
                    .param("id", unit.id())
                    .update();
        });
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
