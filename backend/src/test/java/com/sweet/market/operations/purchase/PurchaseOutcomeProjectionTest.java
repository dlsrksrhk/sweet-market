package com.sweet.market.operations.purchase;

import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.coupon.application.CouponDiscountQuote;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.operations.event.OperationalEventRecorder;
import com.sweet.market.operations.event.JdbcOperationalEventRecorder;
import com.sweet.market.operations.event.OperationalEventType;
import com.sweet.market.operations.projection.OperationalProjectionCoordinator;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.application.PaymentFailureCompensationService;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.promotion.application.PromotionPrice;
import com.sweet.market.purchase.application.ProductReservationService;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@TestPropertySource(properties = "market.operations-projector.enabled=false")
class PurchaseOutcomeProjectionTest extends IntegrationTestSupport {

    private static final Instant ORDERED_AT = Instant.parse("2026-07-17T01:15:00Z");

    @Autowired private PurchaseOutcomeEventFactory eventFactory;
    @Autowired private OperationalEventRecorder eventRecorder;
    @Autowired private OperationalProjectionCoordinator coordinator;
    @Autowired private MemberRepository memberRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private InventoryService inventoryService;
    @Autowired private ProductReservationService productReservationService;
    @Autowired private PaymentFailureCompensationService compensationService;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoSpyBean private JdbcOperationalEventRecorder jdbcOperationalEventRecorder;

    @BeforeEach
    void 구매_성과_projection_테이블을_준비한다() {
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
                    claim_success_count BIGINT NOT NULL DEFAULT 0, claim_failure_count BIGINT NOT NULL DEFAULT 0,
                    redemption_success_count BIGINT NOT NULL DEFAULT 0, redemption_failure_count BIGINT NOT NULL DEFAULT 0,
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
                TRUNCATE TABLE campaign_metric_hourly, store_metric_hourly,
                    projection_event_receipts, operational_event_outbox, projection_generations
                    RESTART IDENTITY CASCADE
                """);
        jdbcTemplate.update("""
                INSERT INTO projection_generations (status, cutoff_at, tracking_started_at, activated_at)
                VALUES ('ACTIVE', ?, ?, ?)
                """, Timestamp.from(ORDERED_AT), Timestamp.from(ORDERED_AT), Timestamp.from(ORDERED_AT));
    }

    @Test
    void 주문_생성은_적용_할인액과_주문수를_한번_집계한다() {
        eventRecorder.record(eventFactory.orderCreated(
                11L, 21L, 31L, 101L, 202L, 1_500L, 700L, ORDERED_AT));

        projectWithDuplicateDelivery();

        assertStoreMetric("NONE", "order_success_count", 1L);
        assertStoreMetric("NONE", "promotion_applied_amount", 1_500L);
        assertStoreMetric("NONE", "coupon_applied_amount", 700L);
        assertCampaignMetric("PROMOTION", 101L, "order_success_count", 1L);
        assertCampaignMetric("PROMOTION", 101L, "promotion_applied_amount", 1_500L);
        assertCampaignMetric("COUPON", 202L, "order_success_count", 1L);
        assertCampaignMetric("COUPON", 202L, "coupon_applied_amount", 700L);
    }

    @Test
    void 구매확정은_실현_할인액을_확정시각_버킷에_집계한다() {
        Instant confirmedAt = ORDERED_AT.plusSeconds(3_600);
        eventRecorder.record(eventFactory.orderStatusChanged(
                "CONFIRMED", 11L, 21L, 31L, 101L, 202L, 1_500L, 700L, confirmedAt));

        project();

        assertThat(metricAt("store_metric_hourly", "promotion_realized_amount", confirmedAt, "store_id", 21L))
                .isEqualTo(1_500L);
        assertThat(metricAt("store_metric_hourly", "coupon_realized_amount", confirmedAt, "store_id", 21L))
                .isEqualTo(700L);
        assertThat(metricAt("campaign_metric_hourly", "promotion_realized_amount", confirmedAt, "campaign_id", 101L))
                .isEqualTo(1_500L);
        assertThat(metricAt("campaign_metric_hourly", "coupon_realized_amount", confirmedAt, "campaign_id", 202L))
                .isEqualTo(700L);
    }

    @Test
    void 취소와_환불은_각각_별도_할인액으로_집계한다() {
        eventRecorder.record(eventFactory.orderStatusChanged(
                "CANCELED", 11L, 21L, 31L, 101L, 202L, 1_500L, 700L, ORDERED_AT));
        eventRecorder.record(eventFactory.orderStatusChanged(
                "REFUNDED", 12L, 21L, 32L, 101L, 202L, 900L, 300L, ORDERED_AT));

        project();

        assertStoreMetric("NONE", "promotion_canceled_amount", 1_500L);
        assertStoreMetric("NONE", "coupon_canceled_amount", 700L);
        assertStoreMetric("NONE", "promotion_refunded_amount", 900L);
        assertStoreMetric("NONE", "coupon_refunded_amount", 300L);
        assertStoreMetric("NONE", "promotion_applied_amount", 0L);
        assertStoreMetric("NONE", "promotion_realized_amount", 0L);
    }

    @Test
    void 품절_경쟁_패배는_SOLD_OUT으로_집계하고_재고를_음수로_만들지_않는다() throws Exception {
        long productId = createStockProduct("outcome-race", 3);
        List<String> tokens = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> accessToken("outcome-race-buyer-" + index + "@example.com", "구매자" + index))
                .toList();
        ExecutorService executor = Executors.newFixedThreadPool(tokens.size());
        CountDownLatch ready = new CountDownLatch(tokens.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> attempts = tokens.stream()
                    .map(token -> executor.submit(() -> purchaseAfterBarrier(token, productId, ready, start)))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = attempts.stream().map(this::await).toList();
            assertThat(statuses).filteredOn(status -> status == 201).hasSize(3);
            assertThat(statuses).filteredOn(status -> status == 409).hasSize(7);
        } finally {
            executor.shutdownNow();
        }

        project();

        assertThat(availableQuantity(productId)).isZero();
        assertStoreMetric("NONE", "order_success_count", 3L);
        assertStoreMetric("SOLD_OUT", "reservation_failure_count", 7L);
    }

    @Test
    void 멱등_재요청은_구매성과_event를_다시_발행하지_않는다() throws Exception {
        long productId = createStockProduct("outcome-replay", 2);
        String token = accessToken("outcome-replay-buyer@example.com", "구매자");
        String idempotencyKey = UUID.randomUUID().toString();

        int first = purchase(token, productId, idempotencyKey);
        int replay = purchase(token, productId, idempotencyKey);

        assertThat(first).isEqualTo(201);
        assertThat(replay).isEqualTo(201);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM operational_event_outbox
                WHERE event_type = 'PURCHASE_OUTCOME' AND payload ->> 'result' = 'SUCCESS'
                """, Long.class)).isOne();
    }

    @Test
    void 프로모션과_쿠폰이_적용된_품절실패를_각_campaign에_집계한다() throws Exception {
        CampaignFailureFixture fixture = createCampaignFailureFixture();
        jdbcTemplate.execute("TRUNCATE TABLE operational_event_outbox RESTART IDENTITY CASCADE");

        int status = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"memberCouponId\":%d}".formatted(
                                fixture.productId(), fixture.memberCouponId())))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(409);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT store_id, payload ->> 'reason' AS reason,
                       (payload ->> 'promotionCampaignId')::bigint AS promotion_campaign_id,
                       (payload ->> 'couponCampaignId')::bigint AS coupon_campaign_id
                FROM operational_event_outbox
                WHERE event_type = 'PURCHASE_OUTCOME' AND payload ->> 'result' = 'FAILURE'
                """)).containsEntry("store_id", fixture.storeId())
                .containsEntry("reason", "SOLD_OUT")
                .containsEntry("promotion_campaign_id", fixture.promotionCampaignId())
                .containsEntry("coupon_campaign_id", fixture.couponCampaignId());

        project();

        assertCampaignFailure("PROMOTION", fixture.promotionCampaignId(), fixture.storeId());
        assertCampaignFailure("COUPON", fixture.couponCampaignId(), fixture.storeId());
    }

    @Test
    void 결제실패_보상은_재고와_쿠폰을_한번만_복구한다() {
        CompensationFixture fixture = createCompensationFixture();
        jdbcTemplate.execute("TRUNCATE TABLE operational_event_outbox RESTART IDENTITY CASCADE");

        compensationService.compensate(fixture.orderId());
        compensationService.compensate(fixture.orderId());

        assertThat(availableQuantity(fixture.productId())).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM inventory_adjustments
                WHERE order_id = ? AND change_type = 'RELEASE'
                """, Long.class, fixture.orderId())).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM coupon_reservations WHERE order_id = ?",
                String.class, fixture.orderId())).isEqualTo("RELEASED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM operational_event_outbox
                WHERE event_type = 'PURCHASE_OUTCOME'
                  AND payload ->> 'reason' = 'PAYMENT_FAILED'
                """, Long.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM operational_event_outbox
                WHERE event_type = 'ORDER_STATUS_CHANGED'
                  AND payload ->> 'result' = 'CANCELED'
                """, Long.class)).isOne();

        project();

        assertStoreMetric("PAYMENT_FAILED", "purchase_failure_count", 1L);
        assertStoreMetric("PAYMENT_FAILED", "order_success_count", 0L);
        assertCampaignMetric("COUPON", fixture.campaignId(), "coupon_canceled_amount", 1_000L);
    }

    @Test
    void 취소_outbox_저장에_실패하면_결제실패_보상전체를_롤백한다() {
        CompensationFixture fixture = createCompensationFixture();
        jdbcTemplate.execute("TRUNCATE TABLE operational_event_outbox RESTART IDENTITY CASCADE");
        doThrow(new DataIntegrityViolationException("취소 outbox 저장 실패"))
                .when(jdbcOperationalEventRecorder)
                .record(argThat(event -> event.eventType() == OperationalEventType.ORDER_STATUS_CHANGED
                        && "CANCELED".equals(event.payload().path("result").asText())));

        assertThatThrownBy(() -> compensationService.compensate(fixture.orderId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(availableQuantity(fixture.productId())).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, fixture.orderId()))
                .isEqualTo("CREATED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM coupon_reservations WHERE order_id = ?",
                String.class, fixture.orderId())).isEqualTo("RESERVED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM inventory_adjustments
                WHERE order_id = ? AND change_type = 'RELEASE'
                """, Long.class, fixture.orderId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operational_event_outbox", Long.class)).isZero();
    }

    private CompensationFixture createCompensationFixture() {
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create(
                    "outcome-compensation-seller@example.com", "encoded", "판매자"));
            Member buyer = memberRepository.save(Member.create(
                    "outcome-compensation-buyer@example.com", "encoded", "구매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "결제 실패 상점", "소개"));
            Product product = productRepository.save(Product.create(
                    store, "결제 실패 상품", "설명", 10_000L,
                    ProductSalesPolicy.STOCK_MANAGED, 5, 5));
            inventoryService.initialize(product, 5, seller.getId());
            long campaignId = insertCouponCampaign();
            long memberCouponId = insertMemberCoupon(buyer.getId(), campaignId);
            Order order = orderRepository.saveAndFlush(Order.create(
                    buyer, product, PromotionPrice.withoutPromotion(10_000L),
                    new CouponDiscountQuote(memberCouponId, 1_000L, 9_000L)));
            productReservationService.reserve(order);
            jdbcTemplate.update("""
                    INSERT INTO coupon_reservations (
                        member_coupon_id, order_id, status, reserved_at, expires_at
                    ) VALUES (?, ?, 'RESERVED', current_timestamp, current_timestamp + interval '30 minutes')
                    """, memberCouponId, order.getId());
            return new CompensationFixture(order.getId(), product.getId(), campaignId);
        });
    }

    private long insertCouponCampaign() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO coupon_campaigns (
                    version, owner_type, scope, discount_type, discount_value,
                    max_discount_amount, minimum_purchase_amount, stackable, title,
                    issue_starts_at, issue_ends_at, validity_type, validity_days,
                    lifecycle_status, issued_count, created_at, updated_at
                ) VALUES (
                    0, 'PLATFORM', 'ALL_PRODUCTS', 'FIXED_AMOUNT', 1000,
                    NULL, 0, TRUE, '결제 실패 쿠폰', current_timestamp - interval '1 day',
                    current_timestamp + interval '1 day', 'DAYS_FROM_ISSUANCE', 7,
                    'SCHEDULED', 1, current_timestamp, current_timestamp
                ) RETURNING id
                """, Long.class);
    }

    private long insertMemberCoupon(long buyerId, long campaignId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_coupons (
                    member_id, coupon_campaign_id, issued_at, valid_until,
                    discount_type, discount_value, max_discount_amount,
                    minimum_purchase_amount, scope, stackable, status
                ) VALUES (
                    ?, ?, current_timestamp, current_timestamp + interval '7 days',
                    'FIXED_AMOUNT', 1000, NULL, 0, 'ALL_PRODUCTS', TRUE, 'ISSUED'
                ) RETURNING id
                """, Long.class, buyerId, campaignId);
    }

    private CampaignFailureFixture createCampaignFailureFixture() {
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create(
                    "campaign-failure-seller@example.com", "encoded", "판매자"));
            Member buyer = memberRepository.save(Member.create(
                    "campaign-failure-buyer@example.com", "encoded", "구매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "실패 집계 상점", "소개"));
            Product product = productRepository.save(Product.create(
                    store, "품절 상품", "설명", 10_000L,
                    ProductSalesPolicy.STOCK_MANAGED, 5, 0));
            inventoryService.initialize(product, 0, seller.getId());
            jdbcTemplate.update("UPDATE stores SET type = 'BUSINESS', status = 'ACTIVE' WHERE id = ?", store.getId());
            long promotionCampaignId = jdbcTemplate.queryForObject("""
                    INSERT INTO promotion_campaigns (
                        version, store_id, scope, discount_type, discount_value, priority, title,
                        start_at, end_at, lifecycle_status, created_at, updated_at
                    ) VALUES (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', 1500, 10, '품절 프로모션',
                        current_timestamp - interval '1 minute', current_timestamp + interval '1 day',
                        'SCHEDULED', current_timestamp, current_timestamp)
                    RETURNING id
                    """, Long.class, store.getId());
            long couponCampaignId = insertCouponCampaign();
            long memberCouponId = insertMemberCoupon(buyer.getId(), couponCampaignId);
            String token = jwtProvider.createAccessToken(buyer.getId(), buyer.getEmail(), buyer.getRole());
            return new CampaignFailureFixture(
                    token, store.getId(), product.getId(), promotionCampaignId,
                    couponCampaignId, memberCouponId);
        });
    }

    private long createStockProduct(String prefix, int stock) {
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create(
                    prefix + "-seller@example.com", "encoded", "판매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "성과 상점", "소개"));
            Product product = productRepository.save(Product.create(
                    store, "성과 재고 상품", "설명", 10_000L,
                    ProductSalesPolicy.STOCK_MANAGED, stock, stock));
            inventoryService.initialize(product, stock, seller.getId());
            return product.getId();
        });
    }

    private String accessToken(String email, String nickname) {
        Member buyer = memberRepository.save(Member.create(email, "encoded", nickname));
        return jwtProvider.createAccessToken(buyer.getId(), buyer.getEmail(), buyer.getRole());
    }

    private int purchaseAfterBarrier(String token, long productId, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("동시 구매 시작 시간이 초과되었습니다.");
        }
        return mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d}".formatted(productId)))
                .andReturn().getResponse().getStatus();
    }

    private int purchase(String token, long productId, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d}".formatted(productId)))
                .andReturn().getResponse().getStatus();
    }

    private int await(Future<Integer> attempt) {
        try {
            return attempt.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private int availableQuantity(long productId) {
        return jdbcTemplate.queryForObject("""
                SELECT total_quantity - reserved_quantity FROM inventories WHERE product_id = ?
                """, Integer.class, productId);
    }

    private void project() {
        coordinator.projectNextBatch(Instant.now().plusSeconds(2), 100);
    }

    private void projectWithDuplicateDelivery() {
        Instant now = Instant.now().plusSeconds(2);
        coordinator.projectNextBatch(now, 100);
        jdbcTemplate.update("""
                UPDATE operational_event_outbox SET delivery_state = 'RETRY', next_attempt_at = ?
                WHERE event_type IN ('PURCHASE_OUTCOME', 'ORDER_STATUS_CHANGED')
                """, Timestamp.from(now));
        coordinator.projectNextBatch(now, 100);
    }

    private void assertStoreMetric(String reason, String column, long expected) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(%s), 0) FROM store_metric_hourly
                WHERE outcome_reason = ?
                """.formatted(column), Long.class, reason);
        assertThat(value).isEqualTo(expected);
    }

    private void assertCampaignMetric(String kind, long campaignId, String column, long expected) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(%s), 0) FROM campaign_metric_hourly
                WHERE campaign_kind = ? AND campaign_id = ? AND outcome_reason = 'NONE'
                """.formatted(column), Long.class, kind, campaignId);
        assertThat(value).isEqualTo(expected);
    }

    private void assertCampaignFailure(String kind, long campaignId, long storeId) {
        assertThat(jdbcTemplate.queryForMap("""
                SELECT commerce_store_id, purchase_failure_count, order_success_count, outcome_reason
                FROM campaign_metric_hourly
                WHERE campaign_kind = ? AND campaign_id = ?
                """, kind, campaignId)).containsEntry("commerce_store_id", storeId)
                .containsEntry("purchase_failure_count", 1L)
                .containsEntry("order_success_count", 0L)
                .containsEntry("outcome_reason", "SOLD_OUT");
    }

    private long metricAt(String table, String column, Instant instant, String idColumn, long id) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(%s), 0) FROM %s
                WHERE %s = ? AND bucket_start = ?
                """.formatted(column, table, idColumn), Long.class, id,
                Timestamp.from(instant.truncatedTo(java.time.temporal.ChronoUnit.HOURS)));
    }

    private record CompensationFixture(long orderId, long productId, long campaignId) { }

    private record CampaignFailureFixture(
            String token,
            long storeId,
            long productId,
            long promotionCampaignId,
            long couponCampaignId,
            long memberCouponId
    ) { }
}
