package com.sweet.market.operations;

import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.coupon.domain.CouponCampaignOwnerType;
import com.sweet.market.coupon.domain.CouponDiscountType;
import com.sweet.market.coupon.domain.CouponScope;
import com.sweet.market.coupon.domain.CouponValidityType;
import com.sweet.market.coupon.repository.CouponCampaignRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.operations.coupon.CouponOutcomeEventFactory;
import com.sweet.market.operations.event.OperationalEventRecorder;
import com.sweet.market.operations.projection.OperationalProjectionCoordinator;
import com.sweet.market.operations.purchase.PurchaseOutcomeEventFactory;
import com.sweet.market.operations.purchase.PurchaseOutcomeReason;
import com.sweet.market.operations.store.StoreOperationsDashboardQueryService;
import com.sweet.market.operations.store.StoreOperationsDashboardResponse;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "market.operations-projector.enabled=false")
class OperationsDashboardReconciliationTest extends IntegrationTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant TRACKING_STARTED_AT = kst(2026, 7, 17, 0, 0);
    private static final LocalDate FIXTURE_DATE = LocalDate.of(2026, 7, 17);

    @Autowired private PurchaseOutcomeEventFactory purchaseEventFactory;
    @Autowired private CouponOutcomeEventFactory couponEventFactory;
    @Autowired private OperationalEventRecorder eventRecorder;
    @Autowired private OperationalProjectionCoordinator projectionCoordinator;
    @Autowired private StoreOperationsDashboardQueryService dashboardQueryService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreMembershipRepository storeMembershipRepository;
    @Autowired private CouponCampaignRepository couponCampaignRepository;

    @BeforeEach
    void 운영_대시보드_projection_테이블을_준비한다() {
        createProjectionTables();
        jdbcTemplate.execute("""
                TRUNCATE TABLE inventory_failure_hourly, inventory_pressure_projection,
                    campaign_metric_hourly, store_metric_hourly, projection_event_receipts,
                    operational_event_outbox, projection_generations RESTART IDENTITY CASCADE
                """);
        jdbcTemplate.update("""
                INSERT INTO projection_generations (
                    status, cutoff_at, tracking_started_at, bootstrap_high_water_id, activated_at
                ) VALUES ('ACTIVE', ?, ?, 0, ?)
                """, timestamp(TRACKING_STARTED_AT), timestamp(TRACKING_STARTED_AT),
                timestamp(TRACKING_STARTED_AT));
    }

    @AfterEach
    void 운영_대시보드_projection_fixture를_정리한다() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE inventory_failure_hourly, inventory_pressure_projection,
                    campaign_metric_hourly, store_metric_hourly, projection_event_receipts,
                    projection_generations RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void projection_합계가_원본_fixture와_정확히_일치한다() {
        ReconciliationFixture fixture = createFixture();
        recordSourceEvents(fixture);
        int recordedEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operational_event_outbox", Integer.class);
        Instant projectionTime = kst(2026, 7, 17, 12, 0);
        jdbcTemplate.update(
                "UPDATE operational_event_outbox SET next_attempt_at = ?",
                timestamp(projectionTime));

        assertThat(projectionCoordinator.projectNextBatch(projectionTime, 100))
                .isEqualTo(recordedEventCount);

        StoreOperationsDashboardResponse storeOverview = overview(fixture.owner(), fixture.store(), FIXTURE_DATE);
        assertSourceTotals(storeOverview, fixture.sourceTotals());
        assertThat(storeOverview.claimSuccessCount()).isEqualTo(1L);
        assertThat(storeOverview.redemptionSuccessCount()).isEqualTo(fixture.orders().size());
        assertThat(storeOverview.leadingFailureReasons())
                .extracting(reason -> Map.entry(reason.reason(), reason.count()))
                .containsExactlyInAnyOrder(
                        Map.entry("PAYMENT_FAILED", 1L),
                        Map.entry("SOLD_OUT", 1L));

        assertPlatformCouponAttribution(fixture);
        assertStoreIsolation(fixture);
        assertTrackingStartedAtSemantics(fixture);

        long receiptCountBeforeReplay = receiptCount();
        jdbcTemplate.update("""
                UPDATE operational_event_outbox
                SET delivery_state = 'RETRY', next_attempt_at = ?
                """, timestamp(projectionTime));

        assertThat(projectionCoordinator.projectNextBatch(projectionTime, 100))
                .isEqualTo(recordedEventCount);
        assertSourceTotals(overview(fixture.owner(), fixture.store(), FIXTURE_DATE), fixture.sourceTotals());
        assertThat(receiptCount()).isEqualTo(receiptCountBeforeReplay);
    }

    private ReconciliationFixture createFixture() {
        Member owner = memberRepository.save(Member.create(
                "reconciliation-owner@example.com", "encoded", "정산 소유자"));
        Store store = saveBusinessStore(owner, "정산 상점");
        Member foreignOwner = memberRepository.save(Member.create(
                "reconciliation-foreign@example.com", "encoded", "외부 소유자"));
        Store foreignStore = saveBusinessStore(foreignOwner, "외부 상점");
        CouponCampaign storeCoupon = saveCoupon(CouponCampaignOwnerType.STORE, store, "상점 쿠폰");
        CouponCampaign platformCoupon = saveCoupon(CouponCampaignOwnerType.PLATFORM, null, "플랫폼 쿠폰");

        List<OrderFixture> orders = List.of(
                new OrderFixture(101L, 1_001L, storeCoupon, 1_000L, 100L,
                        List.of("CONFIRMED"), false),
                new OrderFixture(102L, 1_002L, platformCoupon, 2_000L, 200L,
                        List.of("CANCELED"), false),
                new OrderFixture(103L, 1_003L, storeCoupon, 3_000L, 300L,
                        List.of("CONFIRMED", "REFUNDED"), false),
                new OrderFixture(104L, 1_004L, platformCoupon, 4_000L, 400L,
                        List.of("CANCELED"), true));
        SourceTotals totals = new SourceTotals(
                orders.size(),
                sum(orders, null, OrderFixture::promotionDiscount),
                sum(orders, "CONFIRMED", OrderFixture::promotionDiscount),
                sum(orders, "CANCELED", OrderFixture::promotionDiscount),
                sum(orders, "REFUNDED", OrderFixture::promotionDiscount),
                sum(orders, null, OrderFixture::couponDiscount),
                sum(orders, "CONFIRMED", OrderFixture::couponDiscount),
                sum(orders, "CANCELED", OrderFixture::couponDiscount),
                sum(orders, "REFUNDED", OrderFixture::couponDiscount),
                2L);
        return new ReconciliationFixture(
                owner, store, foreignOwner, foreignStore, storeCoupon, platformCoupon, orders, totals);
    }

    private void recordSourceEvents(ReconciliationFixture fixture) {
        eventRecorder.record(couponEventFactory.claimSucceeded(
                fixture.storeCoupon(), kst(2026, 7, 17, 8, 55)));
        eventRecorder.record(couponEventFactory.claimSucceeded(
                fixture.platformCoupon(), kst(2026, 7, 17, 8, 56)));

        int minute = 0;
        for (OrderFixture order : fixture.orders()) {
            Instant orderedAt = kst(2026, 7, 17, 9, minute++);
            eventRecorder.record(purchaseEventFactory.orderCreated(
                    order.orderId(), fixture.store().getId(), order.productId(), 901L,
                    order.coupon().getId(), order.promotionDiscount(), order.couponDiscount(), orderedAt));
            eventRecorder.record(couponEventFactory.redemptionSucceeded(
                    order.coupon(), fixture.store().getId(), order.orderId(), order.couponDiscount(), orderedAt));
            if (order.paymentFailed()) {
                eventRecorder.record(purchaseEventFactory.paymentFailed(
                        order.orderId(), fixture.store().getId(), order.productId(), 901L,
                        order.coupon().getId(), order.promotionDiscount(), order.couponDiscount(),
                        orderedAt.plusSeconds(30)));
            }
            int statusOffset = 1;
            for (String status : order.statuses()) {
                eventRecorder.record(purchaseEventFactory.orderStatusChanged(
                        status, order.orderId(), fixture.store().getId(), order.productId(), 901L,
                        order.coupon().getId(), order.promotionDiscount(), order.couponDiscount(),
                        orderedAt.plusSeconds(60L * statusOffset++)));
            }
        }

        eventRecorder.record(purchaseEventFactory.purchaseFailed(
                fixture.store().getId(), 1_005L, 901L, fixture.platformCoupon().getId(),
                PurchaseOutcomeReason.SOLD_OUT, kst(2026, 7, 17, 10, 0)));

        Instant foreignOrderedAt = kst(2026, 7, 17, 11, 0);
        eventRecorder.record(purchaseEventFactory.orderCreated(
                201L, fixture.foreignStore().getId(), 2_001L, 902L,
                fixture.platformCoupon().getId(), 9_000L, 9_000L, foreignOrderedAt));
        eventRecorder.record(couponEventFactory.redemptionSucceeded(
                fixture.platformCoupon(), fixture.foreignStore().getId(), 201L, 9_000L, foreignOrderedAt));
    }

    private void assertSourceTotals(StoreOperationsDashboardResponse overview, SourceTotals source) {
        assertThat(overview.orderSuccessCount()).isEqualTo(source.createdOrders());
        assertThat(overview.promotionDiscounts().applied()).isEqualTo(source.promotionApplied());
        assertThat(overview.promotionDiscounts().realized()).isEqualTo(source.promotionConfirmed());
        assertThat(overview.promotionDiscounts().canceled()).isEqualTo(source.promotionCanceled());
        assertThat(overview.promotionDiscounts().refunded()).isEqualTo(source.promotionRefunded());
        assertThat(overview.couponDiscounts().applied()).isEqualTo(source.couponApplied());
        assertThat(overview.couponDiscounts().realized()).isEqualTo(source.couponConfirmed());
        assertThat(overview.couponDiscounts().canceled()).isEqualTo(source.couponCanceled());
        assertThat(overview.couponDiscounts().refunded()).isEqualTo(source.couponRefunded());
        assertThat(overview.purchaseFailureCount()).isEqualTo(source.recordedPurchaseFailures());
    }

    private void assertPlatformCouponAttribution(ReconciliationFixture fixture) {
        Map<String, Object> attribution = jdbcTemplate.queryForMap("""
                SELECT campaign_owner_type, campaign_owner_store_id, commerce_store_id,
                       COALESCE(SUM(coupon_applied_amount), 0) AS coupon_applied_amount
                FROM campaign_metric_hourly
                WHERE campaign_kind = 'COUPON' AND campaign_id = ? AND commerce_store_id = ?
                GROUP BY campaign_owner_type, campaign_owner_store_id, commerce_store_id
                """, fixture.platformCoupon().getId(), fixture.store().getId());

        assertThat(attribution)
                .containsEntry("campaign_owner_type", "PLATFORM")
                .containsEntry("campaign_owner_store_id", 0L)
                .containsEntry("commerce_store_id", fixture.store().getId());
        assertThat(((Number) attribution.get("coupon_applied_amount")).longValue()).isEqualTo(600L);
    }

    private void assertStoreIsolation(ReconciliationFixture fixture) {
        StoreOperationsDashboardResponse foreignOverview = overview(
                fixture.foreignOwner(), fixture.foreignStore(), FIXTURE_DATE);
        assertThat(foreignOverview.orderSuccessCount()).isOne();
        assertThat(foreignOverview.purchaseFailureCount()).isZero();
        assertThat(foreignOverview.promotionDiscounts().applied()).isEqualTo(9_000L);
        assertThat(foreignOverview.couponDiscounts().applied()).isEqualTo(9_000L);
        assertThat(foreignOverview.redemptionSuccessCount()).isOne();
    }

    private void assertTrackingStartedAtSemantics(ReconciliationFixture fixture) {
        StoreOperationsDashboardResponse measured = overview(fixture.owner(), fixture.store(), FIXTURE_DATE);
        StoreOperationsDashboardResponse beforeTracking = overview(
                fixture.owner(), fixture.store(), LocalDate.of(2026, 7, 16));

        assertThat(measured.trackingStartedAt()).isEqualTo(TRACKING_STARTED_AT);
        assertThat(beforeTracking.trackingStartedAt()).isEqualTo(TRACKING_STARTED_AT);
        assertThat(beforeTracking.period().toExclusive()).isEqualTo(TRACKING_STARTED_AT);
        assertThat(beforeTracking.orderSuccessCount()).isZero();
        assertThat(beforeTracking.purchaseFailureCount()).isZero();
        assertThat(beforeTracking.promotionDiscounts().applied()).isZero();
        assertThat(beforeTracking.couponDiscounts().applied()).isZero();
    }

    private StoreOperationsDashboardResponse overview(Member member, Store store, LocalDate date) {
        return dashboardQueryService.dashboard(member.getId(), store.getId(), null, date, date);
    }

    private Store saveBusinessStore(Member owner, String name) {
        Store store = Store.applyBusiness(owner, name, "소개", name + " 법인", "123-45-67890");
        store.approve();
        store = storeRepository.save(store);
        storeMembershipRepository.save(StoreMembership.createOwner(store, owner));
        return store;
    }

    private CouponCampaign saveCoupon(CouponCampaignOwnerType ownerType, Store store, String title) {
        return couponCampaignRepository.save(CouponCampaign.create(
                ownerType, store, CouponScope.ALL_PRODUCTS, CouponDiscountType.FIXED_AMOUNT,
                1_000L, null, 0L, true, title, title,
                kst(2026, 7, 1, 0, 0), kst(2026, 7, 31, 23, 59),
                CouponValidityType.COMMON_EXPIRY, kst(2026, 8, 31, 23, 59), null, List.of()));
    }

    private long receiptCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM projection_event_receipts", Long.class);
    }

    private static long sum(
            List<OrderFixture> orders,
            String status,
            java.util.function.ToLongFunction<OrderFixture> amount
    ) {
        return orders.stream()
                .filter(order -> status == null || order.statuses().contains(status))
                .mapToLong(amount)
                .sum();
    }

    private static Instant kst(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, KST).toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private void createProjectionTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_generations (
                    id BIGSERIAL PRIMARY KEY, status VARCHAR(20) NOT NULL,
                    cutoff_at TIMESTAMPTZ NOT NULL, tracking_started_at TIMESTAMPTZ NOT NULL,
                    bootstrap_high_water_id BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    activated_at TIMESTAMPTZ, retired_at TIMESTAMPTZ
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_event_receipts (
                    id BIGSERIAL PRIMARY KEY,
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    projection_name VARCHAR(80) NOT NULL, event_id UUID NOT NULL,
                    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (generation_id, projection_name, event_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS store_metric_hourly (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    bucket_start TIMESTAMPTZ NOT NULL, store_id BIGINT NOT NULL,
                    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
                    order_success_count BIGINT NOT NULL DEFAULT 0,
                    purchase_failure_count BIGINT NOT NULL DEFAULT 0,
                    reservation_failure_count BIGINT NOT NULL DEFAULT 0,
                    promotion_applied_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_applied_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    sold_out_transition_count BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (generation_id, bucket_start, store_id, outcome_reason)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS campaign_metric_hourly (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    bucket_start TIMESTAMPTZ NOT NULL, commerce_store_id BIGINT NOT NULL DEFAULT 0,
                    campaign_kind VARCHAR(20) NOT NULL, campaign_id BIGINT NOT NULL,
                    campaign_owner_type VARCHAR(20) NOT NULL,
                    campaign_owner_store_id BIGINT NOT NULL DEFAULT 0,
                    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
                    claim_success_count BIGINT NOT NULL DEFAULT 0,
                    claim_failure_count BIGINT NOT NULL DEFAULT 0,
                    redemption_success_count BIGINT NOT NULL DEFAULT 0,
                    redemption_failure_count BIGINT NOT NULL DEFAULT 0,
                    order_success_count BIGINT NOT NULL DEFAULT 0,
                    purchase_failure_count BIGINT NOT NULL DEFAULT 0,
                    promotion_applied_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_applied_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (generation_id, bucket_start, commerce_store_id, campaign_kind,
                        campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS inventory_pressure_projection (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    product_id BIGINT NOT NULL, store_id BIGINT NOT NULL,
                    sales_policy VARCHAR(20) NOT NULL, available_quantity INTEGER,
                    low_stock BOOLEAN NOT NULL, last_sold_out_at TIMESTAMPTZ,
                    recent_reservation_failure_count BIGINT NOT NULL DEFAULT 0,
                    last_reservation_failure_at TIMESTAMPTZ, aggregate_version BIGINT NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (generation_id, product_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS inventory_failure_hourly (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    bucket_start TIMESTAMPTZ NOT NULL, product_id BIGINT NOT NULL,
                    store_id BIGINT NOT NULL, failure_count BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (generation_id, bucket_start, product_id)
                )
                """);
    }

    private record ReconciliationFixture(
            Member owner,
            Store store,
            Member foreignOwner,
            Store foreignStore,
            CouponCampaign storeCoupon,
            CouponCampaign platformCoupon,
            List<OrderFixture> orders,
            SourceTotals sourceTotals
    ) { }

    private record OrderFixture(
            long orderId,
            long productId,
            CouponCampaign coupon,
            long promotionDiscount,
            long couponDiscount,
            List<String> statuses,
            boolean paymentFailed
    ) { }

    private record SourceTotals(
            long createdOrders,
            long promotionApplied,
            long promotionConfirmed,
            long promotionCanceled,
            long promotionRefunded,
            long couponApplied,
            long couponConfirmed,
            long couponCanceled,
            long couponRefunded,
            long recordedPurchaseFailures
    ) { }
}
