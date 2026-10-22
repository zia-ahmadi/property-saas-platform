package com.example.property_core.billing;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BillingSchedule {
    private final BillingMaintenanceRepository repository;

    public BillingSchedule(BillingMaintenanceRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 5 0 1 * *", zone = "UTC")
    @Transactional
    public void createMonthlyInvoices() {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        repository.generateMonthlyInvoices(currentMonth.atDay(1), currentMonth.atEndOfMonth());
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "UTC")
    @Transactional
    public void updateOverdueInvoices() {
        repository.markOverdue(LocalDate.now(ZoneOffset.UTC));
    }
}
