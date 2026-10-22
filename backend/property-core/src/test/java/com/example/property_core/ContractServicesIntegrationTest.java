package com.example.property_core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
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

import com.example.property_core.billing.BillingMaintenanceRepository;
import com.example.property_core.billing.BillingService;
import com.example.property_core.billing.InvoiceStatus;
import com.example.property_core.billing.PaymentMethod;
import com.example.property_core.billing.PaymentStatus;
import com.example.property_core.exception.ApiException;
import com.example.property_core.graphql.DashboardRepository;
import com.example.property_core.maintenance.AssignMaintenanceRequest;
import com.example.property_core.maintenance.CreateMaintenanceRequest;
import com.example.property_core.maintenance.MaintenancePriority;
import com.example.property_core.maintenance.MaintenanceService;
import com.example.property_core.maintenance.MaintenanceStatus;
import com.example.property_core.vendor.CreateVendorRequest;
import com.example.property_core.vendor.UpdateVendorRequest;
import com.example.property_core.vendor.VendorService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContractServicesIntegrationTest {
    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private BillingService billingService;

    @Autowired
    private BillingMaintenanceRepository billingMaintenanceRepository;

    @Autowired
    private VendorService vendorService;

    @Autowired
    private MaintenanceService maintenanceService;

    @Autowired
    private DashboardRepository dashboardRepository;

    private UUID organizationId;
    private UUID unitId;
    private UUID renterId;
    private UUID leaseId;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        Jwt.withTokenValue("integration-token")
                                .header("alg", "HS256")
                                .subject(userId.toString())
                                .claim("tenantId", organizationId.toString())
                                .build(),
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        jdbcClient.sql("INSERT INTO organizations (id, name) VALUES (:id, 'Contract Integration')")
                .param("id", organizationId)
                .update();
        UUID propertyId = jdbcClient.sql("""
                INSERT INTO properties (organization_id, name, address)
                VALUES (:organizationId, 'Integration Property', 'Test Address')
                RETURNING id
                """)
                .param("organizationId", organizationId)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
        renterId = jdbcClient.sql("""
                INSERT INTO renters (organization_id, full_name, email)
                VALUES (:organizationId, 'Test Renter', :email)
                RETURNING id
                """)
                .param("organizationId", organizationId)
                .param("email", UUID.randomUUID() + "@example.test")
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
        unitId = jdbcClient.sql("""
                INSERT INTO units (organization_id, property_id, name, status, monthly_rent)
                VALUES (:organizationId, :propertyId, 'Unit 1', 'OCCUPIED', 1000.00)
                RETURNING id
                """)
                .param("organizationId", organizationId)
                .param("propertyId", propertyId)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
        leaseId = jdbcClient.sql("""
                INSERT INTO leases (
                    organization_id, renter_id, unit_id, status, start_date, end_date, monthly_rent)
                VALUES (:organizationId, :renterId, :unitId, 'ACTIVE', '2025-01-01', '2035-12-31', 1000.00)
                RETURNING id
                """)
                .param("organizationId", organizationId)
                .param("renterId", renterId)
                .param("unitId", unitId)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .single();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsPaymentsIdempotentlyAndSettlesInvoice() {
        var invoice = billingService.createInvoice(
                leaseId, new BigDecimal("100.00"), LocalDate.now(ZoneOffset.UTC).plusDays(5));
        UUID key = UUID.randomUUID();

        var first = billingService.recordPayment(key, invoice.id(), new BigDecimal("45.00"), PaymentMethod.CASH);
        var replay = billingService.recordPayment(key, invoice.id(), new BigDecimal("45.00"), PaymentMethod.CASH);

        assertFalse(first.replay());
        assertTrue(replay.replay());
        assertEquals(first.payment().id(), replay.payment().id());
        assertEquals(PaymentStatus.COMPLETED, billingService.getPayment(first.payment().id()).status());
        assertThrows(ApiException.class, () -> billingService.recordPayment(
                key, invoice.id(), new BigDecimal("46.00"), PaymentMethod.CASH));
        assertThrows(ApiException.class, () -> billingService.recordPayment(
                UUID.randomUUID(), invoice.id(), new BigDecimal("56.00"), PaymentMethod.CASH));

        billingService.recordPayment(
                UUID.randomUUID(), invoice.id(), new BigDecimal("55.00"), PaymentMethod.BANK_TRANSFER);
        assertEquals(InvoiceStatus.PAID, billingService.getInvoice(invoice.id()).status());
    }

    @Test
    void generatesRecurringInvoicesOncePerCalendarMonth() {
        YearMonth period = YearMonth.of(2030, 1);

        int firstGeneration = billingMaintenanceRepository.generateMonthlyInvoices(
                period.atDay(1), period.atEndOfMonth());
        int duplicateGeneration = billingMaintenanceRepository.generateMonthlyInvoices(
                period.atDay(1), period.atEndOfMonth());

        assertEquals(1, firstGeneration);
        assertEquals(0, duplicateGeneration);
        assertEquals(1, billingService.listInvoices(
                null, leaseId, false, 0, 20).totalElements());
    }

    @Test
    void marksPastDueIssuedInvoicesAsOverdue() {
        var invoice = billingService.createInvoice(
                leaseId, new BigDecimal("80.00"), LocalDate.now(ZoneOffset.UTC).plusDays(1));
        LocalDate asOf = LocalDate.now(ZoneOffset.UTC).plusDays(2);

        assertEquals(1, billingMaintenanceRepository.markOverdue(asOf));
        assertEquals(InvoiceStatus.OVERDUE, billingService.getInvoice(invoice.id()).status());
    }

    @Test
    void managesVendorsAndMaintenanceWorkflowWithVersionChecks() {
        var vendor = vendorService.create(new CreateVendorRequest(
                "Reliable Repairs", "Plumbing", "repairs@example.test", "555-0100"));
        UpdateVendorRequest clearEmail = new UpdateVendorRequest();
        clearEmail.setEmail(null);
        assertNull(vendorService.update(vendor.id(), clearEmail).email());
        var request = maintenanceService.create(new CreateMaintenanceRequest(
                unitId, renterId, "Leaking kitchen pipe", MaintenancePriority.HIGH));

        var assigned = maintenanceService.assign(request.id(), vendor.id(), 0);
        assertEquals(MaintenanceStatus.ASSIGNED, assigned.status());
        assertEquals(1, assigned.version());
        assertThrows(ApiException.class, () -> maintenanceService.start(request.id(), 0));
        var started = maintenanceService.start(request.id(), 1);
        var resolved = maintenanceService.resolve(request.id(), started.version(), "Pipe replaced");

        assertEquals(MaintenanceStatus.RESOLVED, resolved.status());
        assertEquals(vendor.id(), resolved.vendorId());
    }

    @Test
    void dashboardReturnsTenantScopedAggregatesAndAlerts() {
        var invoice = billingService.createInvoice(
                leaseId, new BigDecimal("120.00"), LocalDate.now(ZoneOffset.UTC).plusDays(5));
        maintenanceService.create(new CreateMaintenanceRequest(
                unitId, renterId, "Broken front door lock", MaintenancePriority.EMERGENCY));

        var counts = dashboardRepository.counts();
        var alerts = dashboardRepository.maintenanceAlerts(null, null);

        assertEquals(1, counts.properties());
        assertEquals(1, counts.units());
        assertEquals(1, counts.occupiedUnits());
        assertEquals(0, counts.outstanding().compareTo(invoice.amount()));
        assertEquals(1, alerts.size());
        assertEquals("Unit 1", alerts.getFirst().unitName());
    }
}
