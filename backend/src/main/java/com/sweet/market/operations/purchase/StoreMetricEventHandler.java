package com.sweet.market.operations.purchase;

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
public class StoreMetricEventHandler implements OperationalEventHandler {

    private static final String PROJECTION_NAME = "store-commerce-metrics";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StoreMetricEventHandler(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String projectionName() {
        return PROJECTION_NAME;
    }

    @Override
    public boolean supports(OperationalEventType eventType, int schemaVersion) {
        return schemaVersion == 1 && (eventType == OperationalEventType.PURCHASE_OUTCOME
                || eventType == OperationalEventType.ORDER_STATUS_CHANGED);
    }

    @Override
    public void handle(long generationId, OperationalEvent event) {
        PurchaseOutcomePayload payload = payload(event);
        if (event.eventType() == OperationalEventType.ORDER_STATUS_CHANGED
                && !isMetricStatus(payload.result())) {
            return;
        }
        Counters counters = counters(event.eventType(), payload);
        jdbcTemplate.update("""
                INSERT INTO store_metric_hourly (
                    generation_id, bucket_start, store_id, outcome_reason,
                    order_success_count, purchase_failure_count, reservation_failure_count,
                    promotion_applied_amount, promotion_realized_amount,
                    promotion_canceled_amount, promotion_refunded_amount,
                    coupon_applied_amount, coupon_realized_amount,
                    coupon_canceled_amount, coupon_refunded_amount
                ) VALUES (
                    :generationId, :bucketStart, :storeId, :reason,
                    :orderSuccess, :purchaseFailure, :reservationFailure,
                    :promotionApplied, :promotionRealized,
                    :promotionCanceled, :promotionRefunded,
                    :couponApplied, :couponRealized, :couponCanceled, :couponRefunded
                )
                ON CONFLICT (generation_id, bucket_start, store_id, outcome_reason)
                DO UPDATE SET
                    order_success_count = store_metric_hourly.order_success_count + EXCLUDED.order_success_count,
                    purchase_failure_count = store_metric_hourly.purchase_failure_count + EXCLUDED.purchase_failure_count,
                    reservation_failure_count = store_metric_hourly.reservation_failure_count + EXCLUDED.reservation_failure_count,
                    promotion_applied_amount = store_metric_hourly.promotion_applied_amount + EXCLUDED.promotion_applied_amount,
                    promotion_realized_amount = store_metric_hourly.promotion_realized_amount + EXCLUDED.promotion_realized_amount,
                    promotion_canceled_amount = store_metric_hourly.promotion_canceled_amount + EXCLUDED.promotion_canceled_amount,
                    promotion_refunded_amount = store_metric_hourly.promotion_refunded_amount + EXCLUDED.promotion_refunded_amount,
                    coupon_applied_amount = store_metric_hourly.coupon_applied_amount + EXCLUDED.coupon_applied_amount,
                    coupon_realized_amount = store_metric_hourly.coupon_realized_amount + EXCLUDED.coupon_realized_amount,
                    coupon_canceled_amount = store_metric_hourly.coupon_canceled_amount + EXCLUDED.coupon_canceled_amount,
                    coupon_refunded_amount = store_metric_hourly.coupon_refunded_amount + EXCLUDED.coupon_refunded_amount,
                    updated_at = CURRENT_TIMESTAMP
                """, parameters(generationId, event, payload, counters));
    }

    private MapSqlParameterSource parameters(
            long generationId,
            OperationalEvent event,
            PurchaseOutcomePayload payload,
            Counters counters
    ) {
        return new MapSqlParameterSource()
                .addValue("generationId", generationId)
                .addValue("bucketStart", Timestamp.from(event.occurredAt()
                        .atZone(KST).truncatedTo(ChronoUnit.HOURS).toInstant()))
                .addValue("storeId", payload.storeId())
                .addValue("reason", payload.reason().name())
                .addValue("orderSuccess", counters.orderSuccess())
                .addValue("purchaseFailure", counters.purchaseFailure())
                .addValue("reservationFailure", counters.reservationFailure())
                .addValue("promotionApplied", counters.promotionApplied())
                .addValue("promotionRealized", counters.promotionRealized())
                .addValue("promotionCanceled", counters.promotionCanceled())
                .addValue("promotionRefunded", counters.promotionRefunded())
                .addValue("couponApplied", counters.couponApplied())
                .addValue("couponRealized", counters.couponRealized())
                .addValue("couponCanceled", counters.couponCanceled())
                .addValue("couponRefunded", counters.couponRefunded());
    }

    private Counters counters(OperationalEventType eventType, PurchaseOutcomePayload payload) {
        if (eventType == OperationalEventType.PURCHASE_OUTCOME) {
            if ("SUCCESS".equals(payload.result())) {
                return new Counters(1, 0, 0,
                        payload.promotionDiscountAmount(), 0, 0, 0,
                        payload.couponDiscountAmount(), 0, 0, 0);
            }
            int reservationFailure = payload.reason() == PurchaseOutcomeReason.SOLD_OUT
                    || payload.reason() == PurchaseOutcomeReason.PRODUCT_UNAVAILABLE ? 1 : 0;
            return new Counters(0, 1, reservationFailure, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        return switch (payload.result()) {
            case "CONFIRMED" -> new Counters(0, 0, 0, 0,
                    payload.promotionDiscountAmount(), 0, 0, 0,
                    payload.couponDiscountAmount(), 0, 0);
            case "CANCELED" -> new Counters(0, 0, 0, 0, 0,
                    payload.promotionDiscountAmount(), 0, 0, 0,
                    payload.couponDiscountAmount(), 0);
            case "REFUNDED" -> new Counters(0, 0, 0, 0, 0, 0,
                    payload.promotionDiscountAmount(), 0, 0, 0,
                    payload.couponDiscountAmount());
            default -> throw new IllegalArgumentException("Unsupported order status: " + payload.result());
        };
    }

    private PurchaseOutcomePayload payload(OperationalEvent event) {
        try {
            return objectMapper.treeToValue(event.payload(), PurchaseOutcomePayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize purchase outcome payload", exception);
        }
    }

    private boolean isMetricStatus(String status) {
        return "CONFIRMED".equals(status) || "CANCELED".equals(status) || "REFUNDED".equals(status);
    }

    private record Counters(
            long orderSuccess, long purchaseFailure, long reservationFailure,
            long promotionApplied, long promotionRealized, long promotionCanceled, long promotionRefunded,
            long couponApplied, long couponRealized, long couponCanceled, long couponRefunded
    ) { }
}
