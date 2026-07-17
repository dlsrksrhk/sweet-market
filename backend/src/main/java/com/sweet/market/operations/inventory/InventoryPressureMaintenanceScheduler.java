package com.sweet.market.operations.inventory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InventoryPressureMaintenanceScheduler {

    private final InventoryPressureMaintenanceService maintenanceService;

    public InventoryPressureMaintenanceScheduler(InventoryPressureMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Scheduled(
            cron = "${market.operations.inventory-pressure-maintenance.cron:0 10 * * * *}",
            zone = "Asia/Seoul"
    )
    public void refresh() {
        maintenanceService.refresh(Instant.now());
    }
}
