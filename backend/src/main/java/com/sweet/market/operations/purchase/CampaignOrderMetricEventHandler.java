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
import java.util.Map;

@Component
public class CampaignOrderMetricEventHandler implements OperationalEventHandler {

    private static final String PROJECTION_NAME = "campaign-order-metrics";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CampaignOrderMetricEventHandler(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
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
        if (payload.promotionCampaignId() != null) {
            upsert(generationId, event, payload, "PROMOTION", payload.promotionCampaignId(),
                    new CampaignOwner("STORE", payload.storeId()));
        }
        if (payload.couponCampaignId() != null) {
            upsert(generationId, event, payload, "COUPON", payload.couponCampaignId(),
                    couponOwner(payload.couponCampaignId()));
        }
    }

    private void upsert(
            long generationId,
            OperationalEvent event,
            PurchaseOutcomePayload payload,
            String kind,
            long campaignId,
            CampaignOwner owner
    ) {
        Amounts amounts = amounts(event.eventType(), payload, kind);
        jdbcTemplate.update("""
                INSERT INTO campaign_metric_hourly (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason,
                    order_success_count, purchase_failure_count,
                    promotion_applied_amount, promotion_realized_amount,
                    promotion_canceled_amount, promotion_refunded_amount,
                    coupon_applied_amount, coupon_realized_amount,
                    coupon_canceled_amount, coupon_refunded_amount
                ) VALUES (
                    :generationId, :bucketStart, :storeId, :kind,
                    :campaignId, :ownerType, :ownerStoreId, :outcomeReason,
                    :orderSuccess, :purchaseFailure, :promotionApplied, :promotionRealized,
                    :promotionCanceled, :promotionRefunded,
                    :couponApplied, :couponRealized, :couponCanceled, :couponRefunded
                )
                ON CONFLICT (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason
                ) DO UPDATE SET
                    order_success_count = campaign_metric_hourly.order_success_count + EXCLUDED.order_success_count,
                    purchase_failure_count = campaign_metric_hourly.purchase_failure_count + EXCLUDED.purchase_failure_count,
                    promotion_applied_amount = campaign_metric_hourly.promotion_applied_amount + EXCLUDED.promotion_applied_amount,
                    promotion_realized_amount = campaign_metric_hourly.promotion_realized_amount + EXCLUDED.promotion_realized_amount,
                    promotion_canceled_amount = campaign_metric_hourly.promotion_canceled_amount + EXCLUDED.promotion_canceled_amount,
                    promotion_refunded_amount = campaign_metric_hourly.promotion_refunded_amount + EXCLUDED.promotion_refunded_amount,
                    coupon_applied_amount = campaign_metric_hourly.coupon_applied_amount + EXCLUDED.coupon_applied_amount,
                    coupon_realized_amount = campaign_metric_hourly.coupon_realized_amount + EXCLUDED.coupon_realized_amount,
                    coupon_canceled_amount = campaign_metric_hourly.coupon_canceled_amount + EXCLUDED.coupon_canceled_amount,
                    coupon_refunded_amount = campaign_metric_hourly.coupon_refunded_amount + EXCLUDED.coupon_refunded_amount,
                    updated_at = CURRENT_TIMESTAMP
                """, new MapSqlParameterSource()
                .addValue("generationId", generationId)
                .addValue("bucketStart", Timestamp.from(event.occurredAt()
                        .atZone(KST).truncatedTo(ChronoUnit.HOURS).toInstant()))
                .addValue("storeId", payload.storeId())
                .addValue("kind", kind)
                .addValue("campaignId", campaignId)
                .addValue("ownerType", owner.type())
                .addValue("ownerStoreId", owner.storeId())
                .addValue("outcomeReason", "FAILURE".equals(payload.result())
                        ? payload.reason().name() : "NONE")
                .addValue("orderSuccess", amounts.orderSuccess())
                .addValue("purchaseFailure", amounts.purchaseFailure())
                .addValue("promotionApplied", amounts.promotionApplied())
                .addValue("promotionRealized", amounts.promotionRealized())
                .addValue("promotionCanceled", amounts.promotionCanceled())
                .addValue("promotionRefunded", amounts.promotionRefunded())
                .addValue("couponApplied", amounts.couponApplied())
                .addValue("couponRealized", amounts.couponRealized())
                .addValue("couponCanceled", amounts.couponCanceled())
                .addValue("couponRefunded", amounts.couponRefunded()));
    }

    private Amounts amounts(OperationalEventType eventType, PurchaseOutcomePayload payload, String kind) {
        long promotion = "PROMOTION".equals(kind) ? payload.promotionDiscountAmount() : 0L;
        long coupon = "COUPON".equals(kind) ? payload.couponDiscountAmount() : 0L;
        if (eventType == OperationalEventType.PURCHASE_OUTCOME) {
            return "SUCCESS".equals(payload.result())
                    ? new Amounts(1, 0, promotion, 0, 0, 0, coupon, 0, 0, 0)
                    : new Amounts(0, 1, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        return switch (payload.result()) {
            case "CONFIRMED" -> new Amounts(0, 0, 0, promotion, 0, 0, 0, coupon, 0, 0);
            case "CANCELED" -> new Amounts(0, 0, 0, 0, promotion, 0, 0, 0, coupon, 0);
            case "REFUNDED" -> new Amounts(0, 0, 0, 0, 0, promotion, 0, 0, 0, coupon);
            default -> throw new IllegalArgumentException("Unsupported order status: " + payload.result());
        };
    }

    private CampaignOwner couponOwner(long campaignId) {
        var owners = jdbcTemplate.query("""
                SELECT owner_type, COALESCE(store_id, 0) AS owner_store_id
                FROM coupon_campaigns WHERE id = :campaignId
                """, Map.of("campaignId", campaignId),
                (resultSet, rowNumber) -> new CampaignOwner(
                        resultSet.getString("owner_type"), resultSet.getLong("owner_store_id")));
        return owners.isEmpty() ? new CampaignOwner("PLATFORM", 0L) : owners.getFirst();
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

    private record CampaignOwner(String type, long storeId) { }

    private record Amounts(
            long orderSuccess,
            long purchaseFailure,
            long promotionApplied, long promotionRealized, long promotionCanceled, long promotionRefunded,
            long couponApplied, long couponRealized, long couponCanceled, long couponRefunded
    ) { }
}
