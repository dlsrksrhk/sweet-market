package com.sweet.market.operations.projection;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Repository
public class ProjectionBootstrapRepository {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate repeatableReadTransaction;

    public ProjectionBootstrapRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            DataSource dataSource
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.repeatableReadTransaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        this.repeatableReadTransaction.setIsolationLevel(
                TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public ProjectionBootstrapSnapshot createBuildingAndPopulate(
            Instant cutoff,
            Instant trackingStartedAt
    ) {
        return repeatableReadTransaction.execute(status -> populate(cutoff, trackingStartedAt));
    }

    private ProjectionBootstrapSnapshot populate(Instant cutoff, Instant trackingStartedAt) {
        long highWaterId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM operational_event_outbox",
                Map.of(), Long.class);
        long generationId = jdbcTemplate.queryForObject("""
                INSERT INTO projection_generations (
                    status, cutoff_at, tracking_started_at, bootstrap_high_water_id
                ) VALUES ('BUILDING', :cutoff, :trackingStartedAt, :highWaterId)
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("cutoff", Timestamp.from(cutoff))
                .addValue("trackingStartedAt", Timestamp.from(trackingStartedAt))
                .addValue("highWaterId", highWaterId), Long.class);

        MapSqlParameterSource parameters = bootstrapParameters(generationId, cutoff);
        recordVisibleOutboxEvents(parameters, highWaterId);
        populateCreatedOrderStoreMetrics(parameters);
        populateConfirmedOrderStoreMetrics(parameters);
        populateCreatedOrderCampaignMetrics(parameters);
        populateConfirmedOrderCampaignMetrics(parameters);
        populateCouponClaims(parameters);
        populateInventoryPressure(parameters);
        return new ProjectionBootstrapSnapshot(generationId, cutoff, highWaterId);
    }

    private void recordVisibleOutboxEvents(
            MapSqlParameterSource parameters,
            long highWaterId
    ) {
        parameters.addValue("highWaterId", highWaterId);
        jdbcTemplate.update("""
                INSERT INTO projection_event_receipts (
                    generation_id, projection_name, event_id, processed_at
                )
                SELECT :generationId, 'bootstrap-outbox-visibility', event_id, :cutoff
                FROM operational_event_outbox
                WHERE id <= :highWaterId
                """, parameters);
    }

    private MapSqlParameterSource bootstrapParameters(long generationId, Instant cutoff) {
        LocalDateTime cutoffLocal = cutoff.atZone(SEOUL).toLocalDateTime();
        LocalDateTime windowStartLocal = cutoff.atZone(SEOUL)
                .minusDays(89)
                .toLocalDate()
                .atStartOfDay();
        return new MapSqlParameterSource()
                .addValue("generationId", generationId)
                .addValue("cutoff", Timestamp.from(cutoff))
                .addValue("windowStart", Timestamp.from(
                        windowStartLocal.atZone(SEOUL).toInstant()))
                .addValue("cutoffLocal", Timestamp.valueOf(cutoffLocal))
                .addValue("windowStartLocal", Timestamp.valueOf(windowStartLocal));
    }

    private void populateCreatedOrderStoreMetrics(MapSqlParameterSource parameters) {
        jdbcTemplate.update("""
                INSERT INTO store_metric_hourly (
                    generation_id, bucket_start, store_id, outcome_reason,
                    order_success_count, promotion_applied_amount, coupon_applied_amount
                )
                SELECT :generationId,
                       date_trunc('hour', o.ordered_at) AT TIME ZONE 'Asia/Seoul',
                       p.store_id, 'NONE', COUNT(*),
                       SUM(o.promotion_discount_amount), SUM(o.coupon_discount_amount)
                FROM orders o
                JOIN products p ON p.id = o.product_id
                WHERE o.ordered_at >= :windowStartLocal
                  AND o.ordered_at < :cutoffLocal
                GROUP BY date_trunc('hour', o.ordered_at), p.store_id
                """, parameters);
    }

    private void populateConfirmedOrderStoreMetrics(MapSqlParameterSource parameters) {
        jdbcTemplate.update("""
                INSERT INTO store_metric_hourly (
                    generation_id, bucket_start, store_id, outcome_reason,
                    promotion_realized_amount, coupon_realized_amount
                )
                SELECT :generationId,
                       date_trunc('hour', o.confirmed_at) AT TIME ZONE 'Asia/Seoul',
                       p.store_id, 'NONE',
                       SUM(o.promotion_discount_amount), SUM(o.coupon_discount_amount)
                FROM orders o
                JOIN products p ON p.id = o.product_id
                WHERE o.confirmed_at >= :windowStartLocal
                  AND o.confirmed_at < :cutoffLocal
                GROUP BY date_trunc('hour', o.confirmed_at), p.store_id
                ON CONFLICT (generation_id, bucket_start, store_id, outcome_reason)
                DO UPDATE SET
                    promotion_realized_amount = EXCLUDED.promotion_realized_amount,
                    coupon_realized_amount = EXCLUDED.coupon_realized_amount,
                    updated_at = CURRENT_TIMESTAMP
                """, parameters);
    }

    private void populateCreatedOrderCampaignMetrics(MapSqlParameterSource parameters) {
        jdbcTemplate.update("""
                INSERT INTO campaign_metric_hourly (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id,
                    outcome_reason, order_success_count, promotion_applied_amount
                )
                SELECT :generationId,
                       date_trunc('hour', o.ordered_at) AT TIME ZONE 'Asia/Seoul',
                       p.store_id, 'PROMOTION', o.promotion_campaign_id,
                       'STORE', p.store_id, 'NONE', COUNT(*),
                       SUM(o.promotion_discount_amount)
                FROM orders o
                JOIN products p ON p.id = o.product_id
                WHERE o.promotion_campaign_id IS NOT NULL
                  AND o.ordered_at >= :windowStartLocal
                  AND o.ordered_at < :cutoffLocal
                GROUP BY date_trunc('hour', o.ordered_at), p.store_id,
                         o.promotion_campaign_id
                """, parameters);
        jdbcTemplate.update("""
                INSERT INTO campaign_metric_hourly (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id,
                    outcome_reason, order_success_count, coupon_applied_amount
                )
                SELECT :generationId,
                       date_trunc('hour', o.ordered_at) AT TIME ZONE 'Asia/Seoul',
                       p.store_id, 'COUPON', mc.coupon_campaign_id,
                       cc.owner_type, COALESCE(cc.store_id, 0), 'NONE', COUNT(*),
                       SUM(o.coupon_discount_amount)
                FROM orders o
                JOIN products p ON p.id = o.product_id
                JOIN member_coupons mc ON mc.id = o.member_coupon_id
                JOIN coupon_campaigns cc ON cc.id = mc.coupon_campaign_id
                WHERE o.ordered_at >= :windowStartLocal
                  AND o.ordered_at < :cutoffLocal
                GROUP BY date_trunc('hour', o.ordered_at), p.store_id,
                         mc.coupon_campaign_id, cc.owner_type, COALESCE(cc.store_id, 0)
                """, parameters);
    }

    private void populateConfirmedOrderCampaignMetrics(MapSqlParameterSource parameters) {
        jdbcTemplate.update("""
                INSERT INTO campaign_metric_hourly (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id,
                    outcome_reason, promotion_realized_amount
                )
                SELECT :generationId,
                       date_trunc('hour', o.confirmed_at) AT TIME ZONE 'Asia/Seoul',
                       p.store_id, 'PROMOTION', o.promotion_campaign_id,
                       'STORE', p.store_id, 'NONE', SUM(o.promotion_discount_amount)
                FROM orders o
                JOIN products p ON p.id = o.product_id
                WHERE o.promotion_campaign_id IS NOT NULL
                  AND o.confirmed_at >= :windowStartLocal
                  AND o.confirmed_at < :cutoffLocal
                GROUP BY date_trunc('hour', o.confirmed_at), p.store_id,
                         o.promotion_campaign_id
                ON CONFLICT (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason
                ) DO UPDATE SET
                    promotion_realized_amount = EXCLUDED.promotion_realized_amount,
                    updated_at = CURRENT_TIMESTAMP
                """, parameters);
        jdbcTemplate.update("""
                INSERT INTO campaign_metric_hourly (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id,
                    outcome_reason, coupon_realized_amount
                )
                SELECT :generationId,
                       date_trunc('hour', o.confirmed_at) AT TIME ZONE 'Asia/Seoul',
                       p.store_id, 'COUPON', mc.coupon_campaign_id,
                       cc.owner_type, COALESCE(cc.store_id, 0), 'NONE',
                       SUM(o.coupon_discount_amount)
                FROM orders o
                JOIN products p ON p.id = o.product_id
                JOIN member_coupons mc ON mc.id = o.member_coupon_id
                JOIN coupon_campaigns cc ON cc.id = mc.coupon_campaign_id
                WHERE o.confirmed_at >= :windowStartLocal
                  AND o.confirmed_at < :cutoffLocal
                GROUP BY date_trunc('hour', o.confirmed_at), p.store_id,
                         mc.coupon_campaign_id, cc.owner_type, COALESCE(cc.store_id, 0)
                ON CONFLICT (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason
                ) DO UPDATE SET
                    coupon_realized_amount = EXCLUDED.coupon_realized_amount,
                    updated_at = CURRENT_TIMESTAMP
                """, parameters);
    }

    private void populateCouponClaims(MapSqlParameterSource parameters) {
        jdbcTemplate.update("""
                INSERT INTO campaign_metric_hourly (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id,
                    outcome_reason, claim_success_count
                )
                SELECT :generationId,
                       date_trunc('hour', mc.issued_at AT TIME ZONE 'Asia/Seoul')
                           AT TIME ZONE 'Asia/Seoul',
                       COALESCE(cc.store_id, 0), 'COUPON', mc.coupon_campaign_id,
                       cc.owner_type, COALESCE(cc.store_id, 0), 'NONE', COUNT(*)
                FROM member_coupons mc
                JOIN coupon_campaigns cc ON cc.id = mc.coupon_campaign_id
                WHERE mc.issued_at >= :windowStart
                  AND mc.issued_at < :cutoff
                GROUP BY date_trunc('hour', mc.issued_at AT TIME ZONE 'Asia/Seoul'),
                         mc.coupon_campaign_id, cc.owner_type, COALESCE(cc.store_id, 0)
                ON CONFLICT (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason
                ) DO UPDATE SET
                    claim_success_count = campaign_metric_hourly.claim_success_count
                        + EXCLUDED.claim_success_count,
                    updated_at = CURRENT_TIMESTAMP
                """, parameters);
    }

    private void populateInventoryPressure(MapSqlParameterSource parameters) {
        jdbcTemplate.update("""
                INSERT INTO inventory_pressure_projection (
                    generation_id, product_id, store_id, sales_policy,
                    available_quantity, low_stock, recent_reservation_failure_count,
                    aggregate_version, updated_at
                )
                SELECT :generationId, p.id, p.store_id, p.sales_policy,
                       CASE WHEN p.sales_policy = 'STOCK_MANAGED'
                            THEN i.total_quantity - i.reserved_quantity END,
                       CASE WHEN p.sales_policy = 'STOCK_MANAGED'
                            THEN i.total_quantity - i.reserved_quantity <= 5
                            ELSE FALSE END,
                       0,
                       CASE WHEN p.sales_policy = 'STOCK_MANAGED'
                            THEN COALESCE(i.version, p.version)
                            ELSE p.version END,
                       :cutoff
                FROM products p
                LEFT JOIN inventories i ON i.product_id = p.id
                """, parameters);
    }
}
