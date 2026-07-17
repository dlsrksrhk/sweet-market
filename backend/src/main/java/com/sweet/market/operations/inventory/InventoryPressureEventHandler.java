package com.sweet.market.operations.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventType;
import com.sweet.market.operations.projection.OperationalEventHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Component
public class InventoryPressureEventHandler implements OperationalEventHandler {

    private static final String PROJECTION_NAME = "inventory-pressure";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InventoryPressureEventHandler(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String projectionName() {
        return PROJECTION_NAME;
    }

    @Override
    public boolean supports(OperationalEventType eventType, int schemaVersion) {
        return schemaVersion == 1 && eventType == OperationalEventType.INVENTORY_OUTCOME;
    }

    @Override
    public void handle(long generationId, OperationalEvent event) {
        InventoryOutcomePayload payload = payload(event);
        MapSqlParameterSource parameters = parameters(generationId, event, payload);
        upsertCurrentState(parameters);
        if ("RESERVATION_FAILED".equals(payload.action())) {
            recordFailure(parameters);
        }
        if ("SOLD_OUT".equals(payload.action())) {
            recordSoldOutTransition(parameters);
        }
    }

    private void upsertCurrentState(MapSqlParameterSource parameters) {
        jdbcTemplate.update("""
                INSERT INTO inventory_pressure_projection (
                    generation_id, product_id, store_id, sales_policy, available_quantity,
                    low_stock, last_sold_out_at, recent_reservation_failure_count,
                    aggregate_version, updated_at
                ) VALUES (
                    :generationId, :productId, :storeId, :salesPolicy, :availableQuantity,
                    :lowStock, :soldOutAt, 0, :aggregateVersion, :occurredAt
                )
                ON CONFLICT (generation_id, product_id) DO UPDATE SET
                    store_id = EXCLUDED.store_id,
                    sales_policy = EXCLUDED.sales_policy,
                    available_quantity = EXCLUDED.available_quantity,
                    low_stock = EXCLUDED.low_stock,
                    last_sold_out_at = COALESCE(EXCLUDED.last_sold_out_at,
                        inventory_pressure_projection.last_sold_out_at),
                    aggregate_version = EXCLUDED.aggregate_version,
                    updated_at = EXCLUDED.updated_at
                WHERE EXCLUDED.aggregate_version > inventory_pressure_projection.aggregate_version
                """, parameters);
    }

    private void recordFailure(MapSqlParameterSource parameters) {
        jdbcTemplate.update("""
                UPDATE inventory_pressure_projection
                SET recent_reservation_failure_count = recent_reservation_failure_count + 1,
                    last_reservation_failure_at = GREATEST(
                        COALESCE(last_reservation_failure_at, :occurredAt), :occurredAt)
                WHERE generation_id = :generationId AND product_id = :productId
                """, parameters);
        jdbcTemplate.update("""
                INSERT INTO inventory_failure_hourly (
                    generation_id, bucket_start, product_id, store_id, failure_count
                ) VALUES (:generationId, :bucketStart, :productId, :storeId, 1)
                ON CONFLICT (generation_id, bucket_start, product_id) DO UPDATE SET
                    failure_count = inventory_failure_hourly.failure_count + 1
                """, parameters);
    }

    private void recordSoldOutTransition(MapSqlParameterSource parameters) {
        jdbcTemplate.update("""
                INSERT INTO store_metric_hourly (
                    generation_id, bucket_start, store_id, outcome_reason, sold_out_transition_count
                ) VALUES (:generationId, :bucketStart, :storeId, 'NONE', 1)
                ON CONFLICT (generation_id, bucket_start, store_id, outcome_reason) DO UPDATE SET
                    sold_out_transition_count = store_metric_hourly.sold_out_transition_count + 1,
                    updated_at = CURRENT_TIMESTAMP
                """, parameters);
    }

    private MapSqlParameterSource parameters(
            long generationId,
            OperationalEvent event,
            InventoryOutcomePayload payload
    ) {
        boolean lowStock = "STOCK_MANAGED".equals(payload.salesPolicy())
                && payload.availableQuantity() != null
                && payload.availableQuantity() <= 5;
        Timestamp occurredAt = Timestamp.from(event.occurredAt());
        return new MapSqlParameterSource()
                .addValue("generationId", generationId)
                .addValue("productId", payload.productId())
                .addValue("storeId", payload.storeId())
                .addValue("salesPolicy", payload.salesPolicy())
                .addValue("availableQuantity", payload.availableQuantity())
                .addValue("lowStock", lowStock)
                .addValue("soldOutAt", payload.soldOut() ? occurredAt : null)
                .addValue("aggregateVersion", event.aggregateVersion())
                .addValue("occurredAt", occurredAt)
                .addValue("bucketStart", Timestamp.from(event.occurredAt()
                        .atZone(KST).truncatedTo(ChronoUnit.HOURS).toInstant()));
    }

    private InventoryOutcomePayload payload(OperationalEvent event) {
        try {
            return objectMapper.treeToValue(event.payload(), InventoryOutcomePayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize inventory outcome payload", exception);
        }
    }
}
