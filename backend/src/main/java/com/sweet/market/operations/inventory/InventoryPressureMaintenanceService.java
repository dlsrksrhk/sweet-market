package com.sweet.market.operations.inventory;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class InventoryPressureMaintenanceService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryPressureMaintenanceService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void refresh(Instant now) {
        Instant cutoff = now.minus(90, ChronoUnit.DAYS);
        jdbcTemplate.update("""
                UPDATE inventory_pressure_projection pressure
                SET recent_reservation_failure_count = COALESCE((
                    SELECT SUM(failure.failure_count)
                    FROM inventory_failure_hourly failure
                    WHERE failure.generation_id = pressure.generation_id
                      AND failure.product_id = pressure.product_id
                      AND failure.bucket_start >= :cutoff
                ), 0)
                """, Map.of("cutoff", Timestamp.from(cutoff)));
        jdbcTemplate.update("""
                DELETE FROM inventory_failure_hourly WHERE bucket_start < :cutoff
                """, Map.of("cutoff", Timestamp.from(cutoff)));
    }
}
