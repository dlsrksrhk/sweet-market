package com.sweet.market.operations.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InventoryPressureMaintenanceSchedulerTest {

    @Test
    void 재고압력_유지보수를_서울시간대_스케줄로_실행한다() throws NoSuchMethodException {
        InventoryPressureMaintenanceService maintenanceService = mock(InventoryPressureMaintenanceService.class);
        InventoryPressureMaintenanceScheduler scheduler =
                new InventoryPressureMaintenanceScheduler(maintenanceService);

        scheduler.refresh();

        verify(maintenanceService).refresh(any(Instant.class));
        Method method = InventoryPressureMaintenanceScheduler.class.getMethod("refresh");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        assertThat(scheduled.cron()).isEqualTo(
                "${market.operations.inventory-pressure-maintenance.cron:0 10 * * * *}");
    }
}
