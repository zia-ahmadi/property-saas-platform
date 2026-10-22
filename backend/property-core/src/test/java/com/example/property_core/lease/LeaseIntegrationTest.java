package com.example.property_core.lease;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.property_core.common.Page;
import com.example.property_core.exception.ApiException;
import com.example.property_core.property.Property;
import com.example.property_core.property.PropertyService;
import com.example.property_core.unit.Unit;
import com.example.property_core.unit.UnitService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LeaseIntegrationTest {

    @Autowired
    private LeaseService leaseService;

    @Autowired
    private LeaseRepository leaseRepository;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UnitService unitService;

    @Autowired
    private JdbcClient jdbcClient;

    private UUID organizationId;
    private UUID userId;
    private Property property;
    private Unit unit;
    private UUID renterId;

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
        unit = unitService.createUnit(property.id(), "Unit 1A", 2, new BigDecimal("2.5"), new BigDecimal("1500.00"));

        renterId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO renters (organization_id, id, full_name, email)
                VALUES (:organizationId, :id, 'John Doe', 'john@example.com')
                """)
                .param("organizationId", organizationId)
                .param("id", renterId)
                .update();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsLeaseInDraftState() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));

        assertNotNull(lease.id());
        assertEquals(renterId, lease.renterId());
        assertEquals(unit.id(), lease.unitId());
        assertEquals(LeaseStatus.DRAFT, lease.status());
        assertEquals(0, lease.version());
        assertEquals(startDate, lease.startDate());
        assertEquals(endDate, lease.endDate());
        assertEquals(new BigDecimal("1500.00"), lease.monthlyRent());
    }

    @Test
    void rejectsLeaseWithNullDates() {
        assertThrows(IllegalArgumentException.class, () -> {
            leaseService.create(renterId, unit.id(), null, null, new BigDecimal("1500.00"));
        });
    }

    @Test
    void rejectsLeaseWithEndDateBeforeStartDate() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.minusDays(1);

        assertThrows(IllegalArgumentException.class, () -> {
            leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        });
    }

    @Test
    void acceptsLeaseWithSameStartAndEndDate() {
        LocalDate date = LocalDate.now();

        Lease lease = leaseService.create(renterId, unit.id(), date, date, new BigDecimal("1500.00"));

        assertEquals(date, lease.startDate());
        assertEquals(date, lease.endDate());
    }

    @Test
    void rejectsLeaseWithNegativeRent() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        assertThrows(IllegalArgumentException.class, () -> {
            leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("-100.00"));
        });
    }

    @Test
    void rejectsLeaseWithTooManyDecimalPlaces() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        assertThrows(IllegalArgumentException.class, () -> {
            leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.123"));
        });
    }

    @Test
    void rejectsLeaseWithTooHighPrecision() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        assertThrows(IllegalArgumentException.class, () -> {
            leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("999999999999.99"));
        });
    }

    @Test
    void rejectsLeaseForNonExistentRenter() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.create(UUID.randomUUID(), unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.status());
    }

    @Test
    void rejectsLeaseForNonExistentUnit() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.create(renterId, UUID.randomUUID(), startDate, endDate, new BigDecimal("1500.00"));
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.status());
    }

    @Test
    void rejectsLeaseForUnitWithOpenLease() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease firstLease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));

        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1600.00"));
        });
        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertTrue(exception.getMessage().contains("Unit already has an active or draft lease"));
    }

    @Test
    void listsLeasesWithPagination() {
        Unit unit2 = unitService.createUnit(property.id(), "Unit 1B", 1, new BigDecimal("1.0"), new BigDecimal("1200.00"));

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.create(renterId, unit2.id(), startDate.plusYears(1), endDate.plusYears(1), new BigDecimal("1600.00"));

        Page<Lease> page1 = leaseService.list(null, null, null, 0, 1);
        assertEquals(1, page1.content().size());
        assertEquals(2, page1.totalElements());
        assertEquals(2, page1.totalPages());

        Page<Lease> page2 = leaseService.list(null, null, null, 1, 1);
        assertEquals(1, page2.content().size());
    }

    @Test
    void filtersLeasesByStatus() {
        Unit unit2 = unitService.createUnit(property.id(), "Unit 1B", 1, new BigDecimal("1.0"), new BigDecimal("1200.00"));

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease1 = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        Lease lease2 = leaseService.create(renterId, unit2.id(), startDate.plusYears(1), endDate.plusYears(1), new BigDecimal("1600.00"));

        leaseService.activate(lease1.id(), 0);

        Page<Lease> draftLeases = leaseService.list(LeaseStatus.DRAFT, null, null, 0, 10);
        Page<Lease> activeLeases = leaseService.list(LeaseStatus.ACTIVE, null, null, 0, 10);

        assertEquals(1, draftLeases.totalElements());
        assertEquals(1, activeLeases.totalElements());
    }

    @Test
    void filtersLeasesByRenterId() {
        UUID renter2Id = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO renters (organization_id, id, full_name, email)
                VALUES (:organizationId, :id, 'Jane Doe', 'jane@example.com')
                """)
                .param("organizationId", organizationId)
                .param("id", renter2Id)
                .update();

        Unit unit2 = unitService.createUnit(property.id(), "Unit 1B", 1, new BigDecimal("1.0"), new BigDecimal("1200.00"));

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.create(renter2Id, unit2.id(), startDate.plusYears(1), endDate.plusYears(1), new BigDecimal("1600.00"));

        Page<Lease> renter1Leases = leaseService.list(null, renterId, null, 0, 10);
        Page<Lease> renter2Leases = leaseService.list(null, renter2Id, null, 0, 10);

        assertEquals(1, renter1Leases.totalElements());
        assertEquals(1, renter2Leases.totalElements());
    }

    @Test
    void filtersLeasesByUnitId() {
        Unit unit2 = unitService.createUnit(property.id(), "Unit 1B", 1, new BigDecimal("1.0"), new BigDecimal("1200.00"));

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.create(renterId, unit2.id(), startDate.plusYears(1), endDate.plusYears(1), new BigDecimal("1600.00"));

        Page<Lease> unit1Leases = leaseService.list(null, null, unit.id(), 0, 10);
        Page<Lease> unit2Leases = leaseService.list(null, null, unit2.id(), 0, 10);

        assertEquals(1, unit1Leases.totalElements());
        assertEquals(1, unit2Leases.totalElements());
    }

    @Test
    void getsLeaseById() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease created = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));

        Lease retrieved = leaseService.get(created.id());

        assertEquals(created.id(), retrieved.id());
        assertEquals(renterId, retrieved.renterId());
        assertEquals(unit.id(), retrieved.unitId());
    }

    @Test
    void throwsNotFoundWhenGettingNonExistentLease() {
        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.get(UUID.randomUUID());
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.status());
    }

    @Test
    void activatesLeaseAndUpdatesUnitStatus() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));

        Lease activated = leaseService.activate(lease.id(), 0);

        assertEquals(LeaseStatus.ACTIVE, activated.status());
        assertEquals(1, activated.version());

        String unitStatus = leaseRepository.unitStatus(unit.id()).orElseThrow();
        assertEquals("OCCUPIED", unitStatus);
    }

    @Test
    void rejectsActivationWithWrongVersion() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));

        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.activate(lease.id(), 999);
        });
        assertEquals(HttpStatus.PRECONDITION_FAILED, exception.status());
    }

    @Test
    void rejectsActivationOfNonDraftLease() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.activate(lease.id(), 0);

        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.activate(lease.id(), 1);
        });
        assertEquals(HttpStatus.CONFLICT, exception.status());
    }

    @Test
    void rejectsActivationIfUnitNotAvailable() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));

        // Manually set unit to MAINTENANCE
        jdbcClient.sql("UPDATE units SET status = 'MAINTENANCE' WHERE id = :id")
                .param("id", unit.id())
                .update();

        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.activate(lease.id(), 0);
        });
        assertEquals(HttpStatus.CONFLICT, exception.status());
    }

    @Test
    void terminatesLeaseAndUpdatesUnitStatus() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.activate(lease.id(), 0);

        Lease terminated = leaseService.terminate(lease.id(), 1, "Early termination");

        assertEquals(LeaseStatus.TERMINATED, terminated.status());
        assertEquals(2, terminated.version());

        String unitStatus = leaseRepository.unitStatus(unit.id()).orElseThrow();
        assertEquals("AVAILABLE", unitStatus);
    }

    @Test
    void terminatesLeaseWithoutReason() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.activate(lease.id(), 0);

        Lease terminated = leaseService.terminate(lease.id(), 1, null);

        assertEquals(LeaseStatus.TERMINATED, terminated.status());
    }

    @Test
    void rejectsTerminationWithWrongVersion() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.activate(lease.id(), 0);

        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.terminate(lease.id(), 999, "Reason");
        });
        assertEquals(HttpStatus.PRECONDITION_FAILED, exception.status());
    }

    @Test
    void rejectsTerminationOfNonActiveLease() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));

        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.terminate(lease.id(), 0, "Reason");
        });
        assertEquals(HttpStatus.CONFLICT, exception.status());
    }

    @Test
    void renewsLeaseCreatingDraft() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease original = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.activate(original.id(), 0);

        LocalDate renewalStart = endDate.plusDays(1);
        LocalDate renewalEnd = renewalStart.plusMonths(12);

        Lease renewal = leaseService.renew(original.id(), 1, renewalStart, renewalEnd, new BigDecimal("1600.00"));

        assertEquals(LeaseStatus.DRAFT, renewal.status());
        assertEquals(renterId, renewal.renterId());
        assertEquals(unit.id(), renewal.unitId());
        assertEquals(original.id(), renewal.renewedFromLeaseId());
        assertEquals(renewalStart, renewal.startDate());
        assertEquals(renewalEnd, renewal.endDate());
        assertEquals(new BigDecimal("1600.00"), renewal.monthlyRent());

        // Original lease should still be ACTIVE
        Lease originalAfterRenewal = leaseService.get(original.id());
        assertEquals(LeaseStatus.ACTIVE, originalAfterRenewal.status());
    }

    @Test
    void rejectsRenewalWithStartDateBeforeOriginalEndDate() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease original = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.activate(original.id(), 0);

        LocalDate renewalStart = endDate.minusDays(1);
        LocalDate renewalEnd = renewalStart.plusMonths(12);

        assertThrows(IllegalArgumentException.class, () -> {
            leaseService.renew(original.id(), 1, renewalStart, renewalEnd, new BigDecimal("1600.00"));
        });
    }

    @Test
    void rejectsRenewalWithSameStartAsOriginalEndDate() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease original = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.activate(original.id(), 0);

        LocalDate renewalStart = endDate;
        LocalDate renewalEnd = renewalStart.plusMonths(12);

        assertThrows(IllegalArgumentException.class, () -> {
            leaseService.renew(original.id(), 1, renewalStart, renewalEnd, new BigDecimal("1600.00"));
        });
    }

    @Test
    void rejectsRenewalWithWrongVersion() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease original = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.activate(original.id(), 0);

        LocalDate renewalStart = endDate.plusDays(1);
        LocalDate renewalEnd = renewalStart.plusMonths(12);

        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.renew(original.id(), 999, renewalStart, renewalEnd, new BigDecimal("1600.00"));
        });
        assertEquals(HttpStatus.PRECONDITION_FAILED, exception.status());
    }

    @Test
    void rejectsRenewalOfNonActiveLease() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease original = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));

        LocalDate renewalStart = endDate.plusDays(1);
        LocalDate renewalEnd = renewalStart.plusMonths(12);

        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.renew(original.id(), 0, renewalStart, renewalEnd, new BigDecimal("1600.00"));
        });
        assertEquals(HttpStatus.CONFLICT, exception.status());
    }

    @Test
    void rejectsRenewalIfUnitHasDraftLease() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease original = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.activate(original.id(), 0);

        // Create a separate draft lease (simulating another renewal attempt)
        Unit unit2 = unitService.createUnit(property.id(), "Unit 1B", 1, new BigDecimal("1.0"), new BigDecimal("1200.00"));
        leaseService.create(renterId, unit2.id(), startDate, endDate, new BigDecimal("1500.00"));

        // This should work
        LocalDate renewalStart = endDate.plusDays(1);
        LocalDate renewalEnd = renewalStart.plusMonths(12);
        leaseService.renew(original.id(), 1, renewalStart, renewalEnd, new BigDecimal("1600.00"));

        // Try to renew again while there's a draft
        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.renew(original.id(), 1, renewalStart, renewalEnd, new BigDecimal("1700.00"));
        });
        assertEquals(HttpStatus.CONFLICT, exception.status());
    }

    @Test
    void activatesRenewalAndTerminatesOriginal() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease original = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));
        leaseService.activate(original.id(), 0);

        LocalDate renewalStart = endDate.plusDays(1);
        LocalDate renewalEnd = renewalStart.plusMonths(12);

        Lease renewal = leaseService.renew(original.id(), 1, renewalStart, renewalEnd, new BigDecimal("1600.00"));
        Lease activatedRenewal = leaseService.activate(renewal.id(), 0);

        assertEquals(LeaseStatus.ACTIVE, activatedRenewal.status());

        // Original lease should be terminated
        Lease originalAfterActivation = leaseService.get(original.id());
        assertEquals(LeaseStatus.TERMINATED, originalAfterActivation.status());
    }

    @Test
    void databaseConstraintPreventsOverlappingLeases() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        // Create first lease
        Lease firstLease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));

        // Try to create a second lease directly in database (bypassing application logic)
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            jdbcClient.sql("""
                    INSERT INTO leases (organization_id, renter_id, unit_id, status, start_date, end_date, monthly_rent)
                    VALUES (:organizationId, :renterId, :unitId, 'DRAFT', :startDate, :endDate, :monthlyRent)
                    """)
                    .param("organizationId", organizationId)
                    .param("renterId", renterId)
                    .param("unitId", unit.id())
                    .param("startDate", startDate)
                    .param("endDate", endDate)
                    .param("monthlyRent", new BigDecimal("1600.00"))
                    .update();
        });
    }

    @Test
    void tenantIsolationPreventsCrossOrganizationAccess() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease = leaseService.create(renterId, unit.id(), startDate, endDate, new BigDecimal("1500.00"));

        // Switch to other organization
        UUID otherOrganizationId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .param("id", otherOrganizationId)
                .param("name", "Other Organization")
                .update();
        SecurityContextHolder.getContext().setAuthentication(authentication(otherUserId, otherOrganizationId));

        // Try to access lease from other organization
        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.get(lease.id());
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.status());
    }

    @Test
    void tenantIsolationPreventsCreatingLeaseForOtherOrganizationUnit() {
        // Create unit in Organization A
        Unit orgAUnit = unitService.createUnit(property.id(), "Unit 1B", 1, new BigDecimal("1.0"), new BigDecimal("1200.00"));

        // Switch to Organization B
        UUID otherOrganizationId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .param("id", otherOrganizationId)
                .param("name", "Other Organization")
                .update();
        SecurityContextHolder.getContext().setAuthentication(authentication(otherUserId, otherOrganizationId));

        // Create renter in Organization B
        UUID otherRenterId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO renters (organization_id, id, full_name, email)
                VALUES (:organizationId, :id, 'Jane Doe', 'jane@example.com')
                """)
                .param("organizationId", otherOrganizationId)
                .param("id", otherRenterId)
                .update();

        // Try to create lease for Organization A's unit
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(12);

        ApiException exception = assertThrows(ApiException.class, () -> {
            leaseService.create(otherRenterId, orgAUnit.id(), startDate, endDate, new BigDecimal("1500.00"));
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.status());
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
