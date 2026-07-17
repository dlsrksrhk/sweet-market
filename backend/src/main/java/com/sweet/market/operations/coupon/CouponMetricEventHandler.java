package com.sweet.market.operations.coupon;

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
public class CouponMetricEventHandler implements OperationalEventHandler {

    private static final String PROJECTION_NAME = "coupon-campaign-metrics";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CouponMetricEventHandler(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String projectionName() {
        return PROJECTION_NAME;
    }

    @Override
    public boolean supports(OperationalEventType eventType, int schemaVersion) {
        return schemaVersion == 1 && (eventType == OperationalEventType.COUPON_CLAIM_OUTCOME
                || eventType == OperationalEventType.COUPON_REDEMPTION_OUTCOME);
    }

    @Override
    public void handle(long generationId, OperationalEvent event) {
        CouponOutcomePayload payload = payload(event);
        Counters counters = counters(payload);
        jdbcTemplate.update("""
                INSERT INTO campaign_metric_hourly (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id,
                    outcome_reason, claim_success_count, claim_failure_count,
                    redemption_success_count, redemption_failure_count
                ) VALUES (
                    :generationId, :bucketStart, :commerceStoreId, 'COUPON',
                    :campaignId, :ownerType, :ownerStoreId,
                    :outcomeReason, :claimSuccess, :claimFailure,
                    :redemptionSuccess, :redemptionFailure
                )
                ON CONFLICT (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason
                ) DO UPDATE SET
                    claim_success_count = campaign_metric_hourly.claim_success_count
                        + EXCLUDED.claim_success_count,
                    claim_failure_count = campaign_metric_hourly.claim_failure_count
                        + EXCLUDED.claim_failure_count,
                    redemption_success_count = campaign_metric_hourly.redemption_success_count
                        + EXCLUDED.redemption_success_count,
                    redemption_failure_count = campaign_metric_hourly.redemption_failure_count
                        + EXCLUDED.redemption_failure_count,
                    updated_at = CURRENT_TIMESTAMP
                """, new MapSqlParameterSource()
                .addValue("generationId", generationId)
                .addValue("bucketStart", Timestamp.from(event.occurredAt()
                        .atZone(KST).truncatedTo(ChronoUnit.HOURS).toInstant()))
                .addValue("commerceStoreId", payload.commerceStoreId() == null
                        ? 0L : payload.commerceStoreId())
                .addValue("campaignId", payload.campaignId())
                .addValue("ownerType", payload.ownerType())
                .addValue("ownerStoreId", payload.ownerStoreId() == null
                        ? 0L : payload.ownerStoreId())
                .addValue("outcomeReason", payload.reason().name())
                .addValue("claimSuccess", counters.claimSuccess())
                .addValue("claimFailure", counters.claimFailure())
                .addValue("redemptionSuccess", counters.redemptionSuccess())
                .addValue("redemptionFailure", counters.redemptionFailure()));
    }

    private CouponOutcomePayload payload(OperationalEvent event) {
        try {
            return objectMapper.treeToValue(event.payload(), CouponOutcomePayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize coupon outcome payload", exception);
        }
    }

    private Counters counters(CouponOutcomePayload payload) {
        return switch (payload.outcomeType() + ":" + payload.result()) {
            case "CLAIM:SUCCESS" -> new Counters(1, 0, 0, 0);
            case "CLAIM:FAILURE" -> new Counters(0, 1, 0, 0);
            case "REDEMPTION:SUCCESS" -> new Counters(0, 0, 1, 0);
            case "REDEMPTION:FAILURE" -> new Counters(0, 0, 0, 1);
            default -> throw new IllegalArgumentException(
                    "Unsupported coupon outcome: " + payload.outcomeType() + ":" + payload.result());
        };
    }

    private record Counters(
            int claimSuccess,
            int claimFailure,
            int redemptionSuccess,
            int redemptionFailure
    ) {
    }
}
