package com.sweet.market.operations.store;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.operations.api.DashboardPeriod;
import com.sweet.market.operations.api.DashboardPeriodResolver;
import com.sweet.market.store.application.StoreAccessService;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class StoreOperationsDashboardQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StoreAccessService storeAccessService;
    private final DashboardPeriodResolver periodResolver;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public StoreOperationsDashboardQueryService(
            StoreAccessService storeAccessService,
            DashboardPeriodResolver periodResolver,
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this(storeAccessService, periodResolver, jdbcTemplate, Clock.systemUTC());
    }

    StoreOperationsDashboardQueryService(
            StoreAccessService storeAccessService,
            DashboardPeriodResolver periodResolver,
            NamedParameterJdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.storeAccessService = storeAccessService;
        this.periodResolver = periodResolver;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StoreOperationsDashboardResponse dashboard(
            Long memberId, Long storeId, String preset, LocalDate from, LocalDate to
    ) {
        Store store = storeAccessService.requireOperator(memberId, storeId);
        Instant generatedAt = clock.instant();
        DashboardPeriod period = periodResolver.resolve(preset, from, to, generatedAt);
        ActiveGeneration generation = activeGeneration();
        MapSqlParameterSource parameters = periodParameters(generation.id(), storeId, period);

        StoreTotals storeTotals = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(order_success_count), 0) AS order_success_count,
                       COALESCE(SUM(purchase_failure_count), 0) AS purchase_failure_count,
                       COALESCE(SUM(promotion_applied_amount), 0) AS promotion_applied_amount,
                       COALESCE(SUM(promotion_realized_amount), 0) AS promotion_realized_amount,
                       COALESCE(SUM(promotion_canceled_amount), 0) AS promotion_canceled_amount,
                       COALESCE(SUM(promotion_refunded_amount), 0) AS promotion_refunded_amount,
                       COALESCE(SUM(coupon_applied_amount), 0) AS coupon_applied_amount,
                       COALESCE(SUM(coupon_realized_amount), 0) AS coupon_realized_amount,
                       COALESCE(SUM(coupon_canceled_amount), 0) AS coupon_canceled_amount,
                       COALESCE(SUM(coupon_refunded_amount), 0) AS coupon_refunded_amount,
                       COALESCE(SUM(sold_out_transition_count), 0) AS sold_out_transition_count,
                       (
                           SELECT COALESCE(COUNT(*), 0)
                           FROM inventory_pressure_projection pressure
                           WHERE pressure.generation_id = :generationId
                             AND pressure.store_id = :storeId
                             AND pressure.low_stock = TRUE
                       ) AS low_stock_count
                FROM store_metric_hourly
                WHERE generation_id = :generationId
                  AND store_id = :storeId
                  AND bucket_start >= :fromInclusive
                  AND bucket_start < :toExclusive
                """, parameters, (resultSet, rowNumber) -> storeTotals(resultSet));
        CampaignTotals campaignTotals = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(claim_success_count), 0) AS claim_success_count,
                       COALESCE(SUM(redemption_success_count), 0) AS redemption_success_count
                FROM campaign_metric_hourly
                WHERE generation_id = :generationId
                  AND bucket_start >= :fromInclusive
                  AND bucket_start < :toExclusive
                  AND (campaign_owner_store_id = :storeId OR commerce_store_id = :storeId)
                """, parameters, (resultSet, rowNumber) -> new CampaignTotals(
                resultSet.getLong("claim_success_count"),
                resultSet.getLong("redemption_success_count")));
        List<OutcomeReasonCount> leadingReasons = jdbcTemplate.query("""
                SELECT reason, COALESCE(SUM(failure_count), 0) AS failure_count
                FROM (
                    SELECT outcome_reason AS reason,
                           COALESCE(SUM(purchase_failure_count), 0) AS failure_count
                    FROM store_metric_hourly
                    WHERE generation_id = :generationId
                      AND store_id = :storeId
                      AND bucket_start >= :fromInclusive
                      AND bucket_start < :toExclusive
                      AND outcome_reason <> 'NONE'
                    GROUP BY outcome_reason
                    UNION ALL
                    SELECT outcome_reason AS reason,
                           COALESCE(SUM(claim_failure_count + redemption_failure_count), 0) AS failure_count
                    FROM campaign_metric_hourly
                    WHERE generation_id = :generationId
                      AND bucket_start >= :fromInclusive
                      AND bucket_start < :toExclusive
                      AND (campaign_owner_store_id = :storeId OR commerce_store_id = :storeId)
                      AND outcome_reason <> 'NONE'
                    GROUP BY outcome_reason
                ) failures
                GROUP BY reason
                HAVING COALESCE(SUM(failure_count), 0) > 0
                ORDER BY failure_count DESC, reason ASC
                LIMIT 5
                """, parameters, (resultSet, rowNumber) -> new OutcomeReasonCount(
                resultSet.getString("reason"), resultSet.getLong("failure_count")));

        long lagSeconds = Math.max(0, Duration.between(generation.updatedAt(), generatedAt).getSeconds());
        return new StoreOperationsDashboardResponse(
                storeId,
                store.getPublicName(),
                period,
                generatedAt,
                generation.updatedAt(),
                lagSeconds,
                generation.trackingStartedAt(),
                campaignTotals.claimSuccessCount(),
                campaignTotals.redemptionSuccessCount(),
                storeTotals.orderSuccessCount(),
                storeTotals.purchaseFailureCount(),
                storeTotals.promotionDiscounts(),
                storeTotals.couponDiscounts(),
                storeTotals.lowStockCount(),
                storeTotals.soldOutTransitionCount(),
                leadingReasons
        );
    }

    @Transactional(readOnly = true)
    public Page<StoreCampaignMetricResponse> campaigns(
            Long memberId, Long storeId, String preset, LocalDate from, LocalDate to,
            String campaignKind, String status, int page, int size
    ) {
        Store store = storeAccessService.requireOperator(memberId, storeId);
        DashboardPeriod period = periodResolver.resolve(preset, from, to, clock.instant());
        PageRequest pageRequest = pageRequest(page, size);
        if (store.getType() == StoreType.PERSONAL) {
            return Page.empty(pageRequest);
        }
        ActiveGeneration generation = activeGeneration();
        MapSqlParameterSource parameters = periodParameters(generation.id(), storeId, period)
                .addValue("campaignKind", normalize(campaignKind), Types.VARCHAR)
                .addValue("status", normalize(status), Types.VARCHAR)
                .addValue("limit", pageRequest.getPageSize())
                .addValue("offset", pageRequest.getOffset());
        String rowsCte = """
                WITH metrics AS (
                    SELECT MAX(metric.bucket_start) AS latest_bucket_start,
                           metric.campaign_kind, metric.campaign_id,
                           metric.campaign_owner_type, metric.campaign_owner_store_id,
                           COALESCE(SUM(metric.claim_success_count), 0) AS claim_success_count,
                           COALESCE(SUM(metric.claim_failure_count), 0) AS claim_failure_count,
                           COALESCE(SUM(metric.redemption_success_count), 0) AS redemption_success_count,
                           COALESCE(SUM(metric.redemption_failure_count), 0) AS redemption_failure_count,
                           COALESCE(SUM(metric.order_success_count), 0) AS order_success_count,
                           COALESCE(SUM(metric.purchase_failure_count), 0) AS purchase_failure_count,
                           COALESCE(SUM(metric.promotion_applied_amount), 0) AS promotion_applied_amount,
                           COALESCE(SUM(metric.promotion_realized_amount), 0) AS promotion_realized_amount,
                           COALESCE(SUM(metric.promotion_canceled_amount), 0) AS promotion_canceled_amount,
                           COALESCE(SUM(metric.promotion_refunded_amount), 0) AS promotion_refunded_amount,
                           COALESCE(SUM(metric.coupon_applied_amount), 0) AS coupon_applied_amount,
                           COALESCE(SUM(metric.coupon_realized_amount), 0) AS coupon_realized_amount,
                           COALESCE(SUM(metric.coupon_canceled_amount), 0) AS coupon_canceled_amount,
                           COALESCE(SUM(metric.coupon_refunded_amount), 0) AS coupon_refunded_amount
                    FROM campaign_metric_hourly metric
                    WHERE metric.generation_id = :generationId
                      AND metric.bucket_start >= :fromInclusive
                      AND metric.bucket_start < :toExclusive
                      AND (metric.campaign_owner_store_id = :storeId OR metric.commerce_store_id = :storeId)
                      AND (:campaignKind IS NULL OR metric.campaign_kind = :campaignKind)
                    GROUP BY metric.campaign_kind, metric.campaign_id,
                             metric.campaign_owner_type, metric.campaign_owner_store_id
                ), rows AS (
                    SELECT metrics.*,
                           CASE WHEN metrics.campaign_kind = 'PROMOTION'
                                THEN promotion.lifecycle_status
                                ELSE coupon.lifecycle_status END AS campaign_status
                    FROM metrics
                    LEFT JOIN promotion_campaigns promotion
                      ON metrics.campaign_kind = 'PROMOTION' AND promotion.id = metrics.campaign_id
                    LEFT JOIN coupon_campaigns coupon
                      ON metrics.campaign_kind = 'COUPON' AND coupon.id = metrics.campaign_id
                    WHERE (:status IS NULL OR
                           CASE WHEN metrics.campaign_kind = 'PROMOTION'
                                THEN promotion.lifecycle_status
                                ELSE coupon.lifecycle_status END = :status)
                )
                """;
        List<StoreCampaignMetricResponse> content = jdbcTemplate.query(rowsCte + """
                SELECT * FROM rows
                ORDER BY latest_bucket_start DESC, campaign_id DESC,
                         campaign_kind DESC, campaign_owner_type DESC, campaign_owner_store_id DESC
                LIMIT :limit OFFSET :offset
                """, parameters, (resultSet, rowNumber) -> campaignMetric(resultSet));
        long total = count(rowsCte + "SELECT COALESCE(COUNT(*), 0) FROM rows", parameters);
        return new PageImpl<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public Page<StoreCouponOutcomeResponse> couponOutcomes(
            Long memberId, Long storeId, String preset, LocalDate from, LocalDate to,
            String reason, int page, int size
    ) {
        storeAccessService.requireOperator(memberId, storeId);
        DashboardPeriod period = periodResolver.resolve(preset, from, to, clock.instant());
        PageRequest pageRequest = pageRequest(page, size);
        ActiveGeneration generation = activeGeneration();
        MapSqlParameterSource parameters = periodParameters(generation.id(), storeId, period)
                .addValue("reason", normalize(reason), Types.VARCHAR)
                .addValue("limit", pageRequest.getPageSize())
                .addValue("offset", pageRequest.getOffset());
        String rowsCte = """
                WITH rows AS (
                    SELECT MAX(bucket_start) AS latest_bucket_start,
                           campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason,
                           COALESCE(SUM(claim_success_count), 0) AS claim_success_count,
                           COALESCE(SUM(claim_failure_count), 0) AS claim_failure_count,
                           COALESCE(SUM(redemption_success_count), 0) AS redemption_success_count,
                           COALESCE(SUM(redemption_failure_count), 0) AS redemption_failure_count,
                           COALESCE(SUM(coupon_applied_amount), 0) AS coupon_applied_amount,
                           COALESCE(SUM(coupon_realized_amount), 0) AS coupon_realized_amount,
                           COALESCE(SUM(coupon_canceled_amount), 0) AS coupon_canceled_amount,
                           COALESCE(SUM(coupon_refunded_amount), 0) AS coupon_refunded_amount
                    FROM campaign_metric_hourly
                    WHERE generation_id = :generationId
                      AND bucket_start >= :fromInclusive
                      AND bucket_start < :toExclusive
                      AND campaign_kind = 'COUPON'
                      AND (campaign_owner_store_id = :storeId OR commerce_store_id = :storeId)
                      AND (:reason IS NULL OR outcome_reason = :reason)
                    GROUP BY campaign_id, campaign_owner_type,
                             campaign_owner_store_id, outcome_reason
                )
                """;
        List<StoreCouponOutcomeResponse> content = jdbcTemplate.query(rowsCte + """
                SELECT * FROM rows
                ORDER BY latest_bucket_start DESC, campaign_id DESC,
                         outcome_reason DESC, campaign_owner_type DESC, campaign_owner_store_id DESC
                LIMIT :limit OFFSET :offset
                """, parameters, (resultSet, rowNumber) -> couponOutcome(resultSet));
        long total = count(rowsCte + "SELECT COALESCE(COUNT(*), 0) FROM rows", parameters);
        return new PageImpl<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public Page<StoreInventoryPressureResponse> inventoryPressure(
            Long memberId, Long storeId, String preset, LocalDate from, LocalDate to,
            boolean attentionOnly, int page, int size
    ) {
        storeAccessService.requireOperator(memberId, storeId);
        DashboardPeriod period = periodResolver.resolve(preset, from, to, clock.instant());
        PageRequest pageRequest = pageRequest(page, size);
        ActiveGeneration generation = activeGeneration();
        MapSqlParameterSource parameters = periodParameters(generation.id(), storeId, period)
                .addValue("attentionOnly", attentionOnly)
                .addValue("limit", pageRequest.getPageSize())
                .addValue("offset", pageRequest.getOffset());
        String rowsCte = """
                WITH failures AS (
                    SELECT product_id,
                           COALESCE(SUM(failure_count), 0) AS failure_count,
                           MAX(bucket_start) AS last_failure_at
                    FROM inventory_failure_hourly
                    WHERE generation_id = :generationId
                      AND store_id = :storeId
                      AND bucket_start >= :fromInclusive
                      AND bucket_start < :toExclusive
                    GROUP BY product_id
                ), rows AS (
                    SELECT pressure.product_id, pressure.sales_policy,
                           pressure.available_quantity, pressure.low_stock,
                           pressure.last_sold_out_at,
                           COALESCE(failures.failure_count, 0) AS reservation_failure_count,
                           failures.last_failure_at AS last_reservation_failure_at,
                           pressure.updated_at
                    FROM inventory_pressure_projection pressure
                    LEFT JOIN failures ON failures.product_id = pressure.product_id
                    WHERE pressure.generation_id = :generationId
                      AND pressure.store_id = :storeId
                      AND (
                          :attentionOnly = FALSE
                          OR pressure.low_stock = TRUE
                          OR COALESCE(failures.failure_count, 0) > 0
                          OR (pressure.last_sold_out_at >= :fromInclusive
                              AND pressure.last_sold_out_at < :toExclusive)
                      )
                )
                """;
        List<StoreInventoryPressureResponse> content = jdbcTemplate.query(rowsCte + """
                SELECT * FROM rows
                ORDER BY low_stock DESC, reservation_failure_count DESC,
                         updated_at DESC, product_id DESC
                LIMIT :limit OFFSET :offset
                """, parameters, (resultSet, rowNumber) -> inventoryPressure(resultSet));
        long total = count(rowsCte + "SELECT COALESCE(COUNT(*), 0) FROM rows", parameters);
        return new PageImpl<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public Page<StorePurchaseOutcomeResponse> purchaseOutcomes(
            Long memberId, Long storeId, String preset, LocalDate from, LocalDate to,
            String reason, int page, int size
    ) {
        storeAccessService.requireOperator(memberId, storeId);
        DashboardPeriod period = periodResolver.resolve(preset, from, to, clock.instant());
        PageRequest pageRequest = pageRequest(page, size);
        ActiveGeneration generation = activeGeneration();
        MapSqlParameterSource parameters = periodParameters(generation.id(), storeId, period)
                .addValue("reason", normalize(reason), Types.VARCHAR)
                .addValue("limit", pageRequest.getPageSize())
                .addValue("offset", pageRequest.getOffset());
        String rowsCte = """
                WITH rows AS (
                    SELECT MAX(bucket_start) AS latest_bucket_start, outcome_reason,
                           COALESCE(SUM(order_success_count), 0) AS order_success_count,
                           COALESCE(SUM(purchase_failure_count), 0) AS purchase_failure_count,
                           COALESCE(SUM(reservation_failure_count), 0) AS reservation_failure_count
                    FROM store_metric_hourly
                    WHERE generation_id = :generationId
                      AND store_id = :storeId
                      AND bucket_start >= :fromInclusive
                      AND bucket_start < :toExclusive
                      AND (:reason IS NULL OR outcome_reason = :reason)
                    GROUP BY outcome_reason
                )
                """;
        List<StorePurchaseOutcomeResponse> content = jdbcTemplate.query(rowsCte + """
                SELECT * FROM rows
                ORDER BY latest_bucket_start DESC, outcome_reason DESC
                LIMIT :limit OFFSET :offset
                """, parameters, (resultSet, rowNumber) -> new StorePurchaseOutcomeResponse(
                resultSet.getString("outcome_reason"),
                instant(resultSet, "latest_bucket_start"),
                resultSet.getString("outcome_reason"),
                resultSet.getLong("order_success_count"),
                resultSet.getLong("purchase_failure_count"),
                resultSet.getLong("reservation_failure_count")));
        long total = count(rowsCte + "SELECT COALESCE(COUNT(*), 0) FROM rows", parameters);
        return new PageImpl<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public Page<StoreCampaignAuditResponse> campaignAudits(
            Long memberId, Long storeId, String preset, LocalDate from, LocalDate to,
            String campaignKind, String command, int page, int size
    ) {
        storeAccessService.requireOperator(memberId, storeId);
        DashboardPeriod period = periodResolver.resolve(preset, from, to, clock.instant());
        PageRequest pageRequest = pageRequest(page, size);
        ActiveGeneration generation = activeGeneration();
        MapSqlParameterSource parameters = periodParameters(generation.id(), storeId, period)
                .addValue("campaignKind", normalize(campaignKind), Types.VARCHAR)
                .addValue("command", normalize(command), Types.VARCHAR)
                .addValue("limit", pageRequest.getPageSize())
                .addValue("offset", pageRequest.getOffset());
        String predicate = """
                FROM campaign_audit_projection
                WHERE generation_id = :generationId
                  AND owner_store_id = :storeId
                  AND occurred_at >= :fromInclusive
                  AND occurred_at < :toExclusive
                  AND (:campaignKind IS NULL OR campaign_kind = :campaignKind)
                  AND (:command IS NULL OR command = :command)
                """;
        List<StoreCampaignAuditResponse> content = jdbcTemplate.query("""
                SELECT id, event_id, campaign_kind, campaign_id, owner_type,
                       owner_store_id, actor_member_id, command, occurred_at,
                       aggregate_version, before_summary::text AS before_summary,
                       after_summary::text AS after_summary
                """ + predicate + """
                ORDER BY occurred_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """, parameters, (resultSet, rowNumber) -> campaignAudit(resultSet));
        long total = count("SELECT COALESCE(COUNT(*), 0) " + predicate, parameters);
        return new PageImpl<>(content, pageRequest, total);
    }

    private ActiveGeneration activeGeneration() {
        List<ActiveGeneration> generations = jdbcTemplate.query("""
                SELECT generation.id, generation.tracking_started_at,
                       COALESCE(MAX(receipt.processed_at), generation.activated_at,
                                generation.cutoff_at) AS projection_updated_at
                FROM projection_generations generation
                LEFT JOIN projection_event_receipts receipt
                  ON receipt.generation_id = generation.id
                WHERE generation.status = 'ACTIVE'
                GROUP BY generation.id, generation.tracking_started_at,
                         generation.activated_at, generation.cutoff_at
                """, new MapSqlParameterSource(), (resultSet, rowNumber) -> new ActiveGeneration(
                resultSet.getLong("id"),
                instant(resultSet, "tracking_started_at"),
                instant(resultSet, "projection_updated_at")));
        if (generations.size() != 1) {
            throw new IllegalStateException("Active projection generation is not available");
        }
        return generations.getFirst();
    }

    private MapSqlParameterSource periodParameters(long generationId, long storeId, DashboardPeriod period) {
        return new MapSqlParameterSource()
                .addValue("generationId", generationId)
                .addValue("storeId", storeId)
                .addValue("fromInclusive", Timestamp.from(period.fromInclusive()))
                .addValue("toExclusive", Timestamp.from(period.toExclusive()));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
    }

    private long count(String sql, MapSqlParameterSource parameters) {
        Long count = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        return count == null ? 0 : count;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private StoreTotals storeTotals(ResultSet resultSet) throws SQLException {
        return new StoreTotals(
                resultSet.getLong("order_success_count"),
                resultSet.getLong("purchase_failure_count"),
                discounts(resultSet, "promotion"),
                discounts(resultSet, "coupon"),
                resultSet.getLong("sold_out_transition_count"),
                resultSet.getLong("low_stock_count"));
    }

    private StoreCampaignMetricResponse campaignMetric(ResultSet resultSet) throws SQLException {
        long ownerStoreId = resultSet.getLong("campaign_owner_store_id");
        String kind = resultSet.getString("campaign_kind");
        long campaignId = resultSet.getLong("campaign_id");
        String ownerType = resultSet.getString("campaign_owner_type");
        return new StoreCampaignMetricResponse(
                kind + ":" + campaignId + ":" + ownerType + ":" + ownerStoreId,
                instant(resultSet, "latest_bucket_start"),
                kind,
                campaignId,
                ownerType,
                ownerStoreId == 0 ? null : ownerStoreId,
                resultSet.getString("campaign_status"),
                resultSet.getLong("claim_success_count"),
                resultSet.getLong("claim_failure_count"),
                resultSet.getLong("redemption_success_count"),
                resultSet.getLong("redemption_failure_count"),
                resultSet.getLong("order_success_count"),
                resultSet.getLong("purchase_failure_count"),
                discounts(resultSet, "promotion"),
                discounts(resultSet, "coupon"));
    }

    private StoreCouponOutcomeResponse couponOutcome(ResultSet resultSet) throws SQLException {
        long campaignId = resultSet.getLong("campaign_id");
        long ownerStoreId = resultSet.getLong("campaign_owner_store_id");
        String ownerType = resultSet.getString("campaign_owner_type");
        String reason = resultSet.getString("outcome_reason");
        return new StoreCouponOutcomeResponse(
                campaignId + ":" + ownerType + ":" + ownerStoreId + ":" + reason,
                instant(resultSet, "latest_bucket_start"),
                campaignId,
                ownerType,
                ownerStoreId == 0 ? null : ownerStoreId,
                reason,
                resultSet.getLong("claim_success_count"),
                resultSet.getLong("claim_failure_count"),
                resultSet.getLong("redemption_success_count"),
                resultSet.getLong("redemption_failure_count"),
                discounts(resultSet, "coupon"));
    }

    private StoreInventoryPressureResponse inventoryPressure(ResultSet resultSet) throws SQLException {
        return new StoreInventoryPressureResponse(
                resultSet.getLong("product_id"),
                resultSet.getString("sales_policy"),
                nullableInteger(resultSet, "available_quantity"),
                resultSet.getBoolean("low_stock"),
                nullableInstant(resultSet, "last_sold_out_at"),
                resultSet.getLong("reservation_failure_count"),
                nullableInstant(resultSet, "last_reservation_failure_at"),
                instant(resultSet, "updated_at"));
    }

    private StoreCampaignAuditResponse campaignAudit(ResultSet resultSet) throws SQLException {
        long ownerStoreId = resultSet.getLong("owner_store_id");
        return new StoreCampaignAuditResponse(
                resultSet.getLong("id"),
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("campaign_kind"),
                resultSet.getLong("campaign_id"),
                resultSet.getString("owner_type"),
                ownerStoreId == 0 ? null : ownerStoreId,
                resultSet.getLong("actor_member_id"),
                resultSet.getString("command"),
                instant(resultSet, "occurred_at"),
                nullableLong(resultSet, "aggregate_version"),
                resultSet.getString("before_summary"),
                resultSet.getString("after_summary"));
    }

    private DiscountAmountSummary discounts(ResultSet resultSet, String prefix) throws SQLException {
        return new DiscountAmountSummary(
                resultSet.getLong(prefix + "_applied_amount"),
                resultSet.getLong(prefix + "_realized_amount"),
                resultSet.getLong(prefix + "_canceled_amount"),
                resultSet.getLong(prefix + "_refunded_amount"));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private record ActiveGeneration(long id, Instant trackingStartedAt, Instant updatedAt) {
    }

    private record CampaignTotals(long claimSuccessCount, long redemptionSuccessCount) {
    }

    private record StoreTotals(
            long orderSuccessCount,
            long purchaseFailureCount,
            DiscountAmountSummary promotionDiscounts,
            DiscountAmountSummary couponDiscounts,
            long soldOutTransitionCount,
            long lowStockCount
    ) {
    }
}
