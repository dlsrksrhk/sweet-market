package com.sweet.market.operations.admin;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.operations.api.DashboardPeriod;
import com.sweet.market.operations.api.DashboardPeriodResolver;
import com.sweet.market.operations.projection.OperationalProjectionRepository;
import com.sweet.market.operations.store.DiscountAmountSummary;
import com.sweet.market.operations.store.OutcomeReasonCount;
import com.sweet.market.operations.store.StoreCampaignAuditResponse;
import com.sweet.market.operations.store.StoreCampaignMetricResponse;
import com.sweet.market.operations.store.StoreInventoryPressureResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
public class AdminOperationsDashboardQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DashboardPeriodResolver periodResolver;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OperationalProjectionRepository projectionRepository;
    private final Clock clock;

    @Autowired
    public AdminOperationsDashboardQueryService(
            DashboardPeriodResolver periodResolver,
            NamedParameterJdbcTemplate jdbcTemplate,
            OperationalProjectionRepository projectionRepository
    ) {
        this(periodResolver, jdbcTemplate, projectionRepository, Clock.systemUTC());
    }

    AdminOperationsDashboardQueryService(
            DashboardPeriodResolver periodResolver,
            NamedParameterJdbcTemplate jdbcTemplate,
            OperationalProjectionRepository projectionRepository,
            Clock clock
    ) {
        this.periodResolver = periodResolver;
        this.jdbcTemplate = jdbcTemplate;
        this.projectionRepository = projectionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminOperationsDashboardResponse dashboard(
            String preset, LocalDate from, LocalDate to, Long storeId
    ) {
        Instant generatedAt = clock.instant();
        DashboardPeriod period = periodResolver.resolve(preset, from, to, generatedAt);
        ActiveGeneration generation = activeGeneration();
        MapSqlParameterSource parameters = periodParameters(generation.id(), period)
                .addValue("storeId", storeId, Types.BIGINT);
        PlatformTotals platformTotals = jdbcTemplate.queryForObject("""
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
                       COALESCE(SUM(sold_out_transition_count), 0) AS sold_out_transition_count
                FROM store_metric_hourly
                WHERE generation_id = :generationId
                  AND bucket_start >= :fromInclusive
                  AND bucket_start < :toExclusive
                  AND (:storeId IS NULL OR store_id = :storeId)
                """, parameters, (resultSet, rowNumber) -> new PlatformTotals(
                resultSet.getLong("order_success_count"),
                resultSet.getLong("purchase_failure_count"),
                discounts(resultSet, "promotion"),
                discounts(resultSet, "coupon"),
                resultSet.getLong("sold_out_transition_count")));
        CampaignTotals campaignTotals = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(claim_success_count), 0) AS claim_success_count,
                       COALESCE(SUM(redemption_success_count), 0) AS redemption_success_count
                FROM campaign_metric_hourly
                WHERE generation_id = :generationId
                  AND bucket_start >= :fromInclusive
                  AND bucket_start < :toExclusive
                  AND (:storeId IS NULL
                       OR campaign_owner_store_id = :storeId
                       OR commerce_store_id = :storeId)
                """, parameters, (resultSet, rowNumber) -> new CampaignTotals(
                resultSet.getLong("claim_success_count"),
                resultSet.getLong("redemption_success_count")));
        AttentionTotals attentionTotals = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE low_stock = TRUE) AS low_stock_count
                FROM inventory_pressure_projection
                WHERE generation_id = :generationId
                  AND (:storeId IS NULL OR store_id = :storeId)
                """, parameters, (resultSet, rowNumber) -> new AttentionTotals(
                resultSet.getLong("low_stock_count")));
        long auditCount = count("""
                SELECT COUNT(*) FROM campaign_audit_projection
                WHERE generation_id = :generationId
                  AND occurred_at >= :fromInclusive
                  AND occurred_at < :toExclusive
                  AND (:storeId IS NULL OR owner_store_id = :storeId)
                """, parameters);
        List<OutcomeReasonCount> leadingReasons = jdbcTemplate.query("""
                SELECT reason, SUM(failure_count) AS failure_count
                FROM (
                    SELECT outcome_reason AS reason,
                           SUM(purchase_failure_count) AS failure_count
                    FROM store_metric_hourly
                    WHERE generation_id = :generationId
                      AND bucket_start >= :fromInclusive
                      AND bucket_start < :toExclusive
                      AND (:storeId IS NULL OR store_id = :storeId)
                      AND outcome_reason <> 'NONE'
                    GROUP BY outcome_reason
                    UNION ALL
                    SELECT outcome_reason AS reason,
                           SUM(claim_failure_count + redemption_failure_count) AS failure_count
                    FROM campaign_metric_hourly
                    WHERE generation_id = :generationId
                      AND bucket_start >= :fromInclusive
                      AND bucket_start < :toExclusive
                      AND (:storeId IS NULL
                           OR campaign_owner_store_id = :storeId
                           OR commerce_store_id = :storeId)
                      AND outcome_reason <> 'NONE'
                    GROUP BY outcome_reason
                ) failures
                GROUP BY reason
                HAVING SUM(failure_count) > 0
                ORDER BY failure_count DESC, reason ASC
                LIMIT 5
                """, parameters, (resultSet, rowNumber) -> new OutcomeReasonCount(
                resultSet.getString("reason"), resultSet.getLong("failure_count")));
        long lagSeconds = Math.max(0, Duration.between(generation.updatedAt(), generatedAt).getSeconds());
        return new AdminOperationsDashboardResponse(
                storeId,
                period,
                generatedAt,
                generation.updatedAt(),
                lagSeconds,
                generation.trackingStartedAt(),
                campaignTotals.claimSuccessCount(),
                campaignTotals.redemptionSuccessCount(),
                platformTotals.orderSuccessCount(),
                platformTotals.purchaseFailureCount(),
                platformTotals.promotionDiscounts(),
                platformTotals.couponDiscounts(),
                attentionTotals.lowStockCount(),
                platformTotals.soldOutTransitionCount(),
                auditCount,
                leadingReasons,
                projectionRepository.health(generatedAt));
    }

    @Transactional(readOnly = true)
    public Page<StoreCampaignMetricResponse> campaigns(
            String preset, LocalDate from, LocalDate to, Long storeId,
            String ownerType, String campaignKind, String campaignStatus,
            int page, int size
    ) {
        DashboardPeriod period = periodResolver.resolve(preset, from, to, clock.instant());
        PageRequest pageRequest = pageRequest(page, size);
        ActiveGeneration generation = activeGeneration();
        MapSqlParameterSource parameters = periodParameters(generation.id(), period)
                .addValue("storeId", storeId, Types.BIGINT)
                .addValue("ownerType", normalize(ownerType), Types.VARCHAR)
                .addValue("campaignKind", normalize(campaignKind), Types.VARCHAR)
                .addValue("campaignStatus", normalize(campaignStatus), Types.VARCHAR)
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
                      AND (:storeId IS NULL
                           OR metric.campaign_owner_store_id = :storeId
                           OR metric.commerce_store_id = :storeId)
                      AND (:ownerType IS NULL OR metric.campaign_owner_type = :ownerType)
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
                    WHERE (:campaignStatus IS NULL OR
                           CASE WHEN metrics.campaign_kind = 'PROMOTION'
                                THEN promotion.lifecycle_status
                                ELSE coupon.lifecycle_status END = :campaignStatus)
                )
                """;
        List<StoreCampaignMetricResponse> content = jdbcTemplate.query(rowsCte + """
                SELECT * FROM rows
                ORDER BY latest_bucket_start DESC, campaign_id DESC,
                         campaign_kind DESC, campaign_owner_type DESC, campaign_owner_store_id DESC
                LIMIT :limit OFFSET :offset
                """, parameters, (resultSet, rowNumber) -> campaignMetric(resultSet));
        long total = count(rowsCte + "SELECT COUNT(*) FROM rows", parameters);
        return new PageImpl<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public Page<AdminOperationsDashboardResponse.OutcomeResponse> outcomes(
            String preset, LocalDate from, LocalDate to, Long storeId,
            String ownerType, String campaignKind, Long productId, String reason,
            int page, int size
    ) {
        DashboardPeriod period = periodResolver.resolve(preset, from, to, clock.instant());
        PageRequest pageRequest = pageRequest(page, size);
        ActiveGeneration generation = activeGeneration();
        MapSqlParameterSource parameters = periodParameters(generation.id(), period)
                .addValue("storeId", storeId, Types.BIGINT)
                .addValue("ownerType", normalize(ownerType), Types.VARCHAR)
                .addValue("campaignKind", normalize(campaignKind), Types.VARCHAR)
                .addValue("productId", productId, Types.BIGINT)
                .addValue("reason", normalize(reason), Types.VARCHAR)
                .addValue("limit", pageRequest.getPageSize())
                .addValue("offset", pageRequest.getOffset());
        String rowsCte = """
                WITH rows AS (
                    SELECT 'CAMPAIGN'::varchar AS outcome_type,
                           MAX(bucket_start) AS latest_bucket_start,
                           commerce_store_id AS store_id,
                           campaign_kind, campaign_id,
                           campaign_owner_type AS owner_type,
                           campaign_owner_store_id AS owner_store_id,
                           NULL::bigint AS product_id,
                           outcome_reason,
                           SUM(claim_success_count + redemption_success_count + order_success_count) AS success_count,
                           SUM(claim_failure_count + redemption_failure_count + purchase_failure_count) AS failure_count,
                           0::bigint AS reservation_failure_count
                    FROM campaign_metric_hourly
                    WHERE generation_id = :generationId
                      AND bucket_start >= :fromInclusive
                      AND bucket_start < :toExclusive
                      AND :productId IS NULL
                      AND (:storeId IS NULL
                           OR campaign_owner_store_id = :storeId
                           OR commerce_store_id = :storeId)
                      AND (:ownerType IS NULL OR campaign_owner_type = :ownerType)
                      AND (:campaignKind IS NULL OR campaign_kind = :campaignKind)
                      AND (:reason IS NULL OR outcome_reason = :reason)
                    GROUP BY commerce_store_id, campaign_kind, campaign_id,
                             campaign_owner_type, campaign_owner_store_id, outcome_reason
                    UNION ALL
                    SELECT 'PURCHASE'::varchar AS outcome_type,
                           MAX(bucket_start) AS latest_bucket_start,
                           store_id,
                           NULL::varchar AS campaign_kind, NULL::bigint AS campaign_id,
                           NULL::varchar AS owner_type, NULL::bigint AS owner_store_id,
                           NULL::bigint AS product_id,
                           outcome_reason,
                           SUM(order_success_count) AS success_count,
                           SUM(purchase_failure_count) AS failure_count,
                           SUM(reservation_failure_count) AS reservation_failure_count
                    FROM store_metric_hourly
                    WHERE generation_id = :generationId
                      AND bucket_start >= :fromInclusive
                      AND bucket_start < :toExclusive
                      AND :ownerType IS NULL AND :campaignKind IS NULL AND :productId IS NULL
                      AND (:storeId IS NULL OR store_id = :storeId)
                      AND (:reason IS NULL OR outcome_reason = :reason)
                    GROUP BY store_id, outcome_reason
                    UNION ALL
                    SELECT 'INVENTORY'::varchar AS outcome_type,
                           MAX(bucket_start) AS latest_bucket_start,
                           store_id,
                           NULL::varchar AS campaign_kind, NULL::bigint AS campaign_id,
                           NULL::varchar AS owner_type, NULL::bigint AS owner_store_id,
                           product_id,
                           'RESERVATION_FAILED'::varchar AS outcome_reason,
                           0::bigint AS success_count,
                           SUM(failure_count) AS failure_count,
                           SUM(failure_count) AS reservation_failure_count
                    FROM inventory_failure_hourly
                    WHERE generation_id = :generationId
                      AND bucket_start >= :fromInclusive
                      AND bucket_start < :toExclusive
                      AND :ownerType IS NULL AND :campaignKind IS NULL
                      AND (:storeId IS NULL OR store_id = :storeId)
                      AND (:productId IS NULL OR product_id = :productId)
                      AND (:reason IS NULL OR :reason = 'RESERVATION_FAILED')
                    GROUP BY store_id, product_id
                )
                """;
        List<AdminOperationsDashboardResponse.OutcomeResponse> content = jdbcTemplate.query(rowsCte + """
                SELECT * FROM rows
                ORDER BY latest_bucket_start DESC, outcome_type ASC,
                         campaign_id DESC NULLS LAST, product_id DESC NULLS LAST,
                         store_id DESC, outcome_reason DESC
                LIMIT :limit OFFSET :offset
                """, parameters, (resultSet, rowNumber) -> outcome(resultSet));
        long total = count(rowsCte + "SELECT COUNT(*) FROM rows", parameters);
        return new PageImpl<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public Page<StoreInventoryPressureResponse> inventoryPressure(
            String preset, LocalDate from, LocalDate to, Long storeId, Long productId,
            boolean attentionOnly, int page, int size
    ) {
        DashboardPeriod period = periodResolver.resolve(preset, from, to, clock.instant());
        PageRequest pageRequest = pageRequest(page, size);
        ActiveGeneration generation = activeGeneration();
        MapSqlParameterSource parameters = periodParameters(generation.id(), period)
                .addValue("storeId", storeId, Types.BIGINT)
                .addValue("productId", productId, Types.BIGINT)
                .addValue("attentionOnly", attentionOnly)
                .addValue("limit", pageRequest.getPageSize())
                .addValue("offset", pageRequest.getOffset());
        String rowsCte = """
                WITH failures AS (
                    SELECT product_id, SUM(failure_count) AS failure_count
                    FROM inventory_failure_hourly
                    WHERE generation_id = :generationId
                      AND bucket_start >= :fromInclusive
                      AND bucket_start < :toExclusive
                      AND (:storeId IS NULL OR store_id = :storeId)
                      AND (:productId IS NULL OR product_id = :productId)
                    GROUP BY product_id
                ), rows AS (
                    SELECT pressure.product_id, pressure.sales_policy,
                           pressure.available_quantity, pressure.low_stock,
                           pressure.last_sold_out_at,
                           COALESCE(failures.failure_count, 0) AS reservation_failure_count,
                           pressure.last_reservation_failure_at, pressure.updated_at
                    FROM inventory_pressure_projection pressure
                    LEFT JOIN failures ON failures.product_id = pressure.product_id
                    WHERE pressure.generation_id = :generationId
                      AND (:storeId IS NULL OR pressure.store_id = :storeId)
                      AND (:productId IS NULL OR pressure.product_id = :productId)
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
        long total = count(rowsCte + "SELECT COUNT(*) FROM rows", parameters);
        return new PageImpl<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public Page<StoreCampaignAuditResponse> audits(
            String preset, LocalDate from, LocalDate to, Long storeId,
            String ownerType, String campaignKind, int page, int size
    ) {
        DashboardPeriod period = periodResolver.resolve(preset, from, to, clock.instant());
        PageRequest pageRequest = pageRequest(page, size);
        ActiveGeneration generation = activeGeneration();
        MapSqlParameterSource parameters = periodParameters(generation.id(), period)
                .addValue("storeId", storeId, Types.BIGINT)
                .addValue("ownerType", normalize(ownerType), Types.VARCHAR)
                .addValue("campaignKind", normalize(campaignKind), Types.VARCHAR)
                .addValue("limit", pageRequest.getPageSize())
                .addValue("offset", pageRequest.getOffset());
        String predicate = """
                FROM campaign_audit_projection
                WHERE generation_id = :generationId
                  AND occurred_at >= :fromInclusive
                  AND occurred_at < :toExclusive
                  AND (:storeId IS NULL OR owner_store_id = :storeId)
                  AND (:ownerType IS NULL OR owner_type = :ownerType)
                  AND (:campaignKind IS NULL OR campaign_kind = :campaignKind)
                """;
        List<StoreCampaignAuditResponse> content = jdbcTemplate.query("""
                SELECT id, event_id, campaign_kind, campaign_id, owner_type,
                       owner_store_id, actor_member_id, command, occurred_at,
                       aggregate_version, before_summary::text AS before_summary,
                       after_summary::text AS after_summary
                """ + predicate + """
                ORDER BY occurred_at DESC, aggregate_version DESC NULLS LAST, event_id DESC
                LIMIT :limit OFFSET :offset
                """, parameters, (resultSet, rowNumber) -> campaignAudit(resultSet));
        long total = count("SELECT COUNT(*) " + predicate, parameters);
        return new PageImpl<>(content, pageRequest, total);
    }

    private ActiveGeneration activeGeneration() {
        List<ActiveGeneration> generations = jdbcTemplate.query("""
                SELECT generation.id, generation.tracking_started_at,
                       COALESCE(receipt.processed_at, generation.activated_at,
                                generation.cutoff_at) AS projection_updated_at
                FROM projection_generations generation
                LEFT JOIN LATERAL (
                    SELECT processed_at
                    FROM projection_event_receipts
                    WHERE generation_id = generation.id
                    ORDER BY processed_at DESC
                    LIMIT 1
                ) receipt ON TRUE
                WHERE generation.status = 'ACTIVE'
                """, new MapSqlParameterSource(), (resultSet, rowNumber) -> new ActiveGeneration(
                resultSet.getLong("id"),
                instant(resultSet, "tracking_started_at"),
                instant(resultSet, "projection_updated_at")));
        if (generations.size() != 1) {
            throw new IllegalStateException("Active projection generation is not available");
        }
        return generations.getFirst();
    }

    private MapSqlParameterSource periodParameters(long generationId, DashboardPeriod period) {
        return new MapSqlParameterSource()
                .addValue("generationId", generationId)
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

    private StoreCampaignMetricResponse campaignMetric(ResultSet resultSet) throws SQLException {
        String kind = resultSet.getString("campaign_kind");
        long campaignId = resultSet.getLong("campaign_id");
        String ownerType = resultSet.getString("campaign_owner_type");
        long ownerStoreId = resultSet.getLong("campaign_owner_store_id");
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

    private AdminOperationsDashboardResponse.OutcomeResponse outcome(ResultSet resultSet) throws SQLException {
        String type = resultSet.getString("outcome_type");
        Long storeId = nullableLong(resultSet, "store_id");
        String campaignKind = resultSet.getString("campaign_kind");
        Long campaignId = nullableLong(resultSet, "campaign_id");
        String ownerType = resultSet.getString("owner_type");
        Long ownerStoreId = nullableLong(resultSet, "owner_store_id");
        Long productId = nullableLong(resultSet, "product_id");
        String reason = resultSet.getString("outcome_reason");
        String id = type + ":" + storeId + ":" + campaignKind + ":" + campaignId
                + ":" + ownerType + ":" + ownerStoreId + ":" + productId + ":" + reason;
        return new AdminOperationsDashboardResponse.OutcomeResponse(
                id, type, instant(resultSet, "latest_bucket_start"), storeId,
                campaignKind, campaignId, ownerType,
                ownerStoreId != null && ownerStoreId == 0 ? null : ownerStoreId,
                productId, reason,
                resultSet.getLong("success_count"),
                resultSet.getLong("failure_count"),
                resultSet.getLong("reservation_failure_count"));
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

    private static DiscountAmountSummary discounts(ResultSet resultSet, String prefix) throws SQLException {
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

    private record AttentionTotals(long lowStockCount) {
    }

    private record PlatformTotals(
            long orderSuccessCount,
            long purchaseFailureCount,
            DiscountAmountSummary promotionDiscounts,
            DiscountAmountSummary couponDiscounts,
            long soldOutTransitionCount
    ) {
    }
}
