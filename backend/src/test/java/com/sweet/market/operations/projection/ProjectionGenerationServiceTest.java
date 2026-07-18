package com.sweet.market.operations.projection;

import com.sweet.market.coupon.application.CouponDiscountQuote;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.promotion.application.PromotionPrice;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = "market.operations-projector.enabled=false")
class ProjectionGenerationServiceTest extends IntegrationTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-17T02:03:04Z");

    @Autowired
    private ProjectionGenerationService service;

    @Autowired
    private ProjectionBootstrapRepository bootstrapRepository;

    @Autowired
    private OperationalProjectionRepository projectionRepository;

    @Autowired
    private OperationalProjectionCoordinator coordinator;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void projection_테이블을_초기화한다() {
        createProjectionTables();
        jdbcTemplate.execute("""
                TRUNCATE TABLE campaign_audit_projection, inventory_failure_hourly,
                               inventory_pressure_projection, campaign_metric_hourly,
                               store_metric_hourly, projection_event_receipts,
                               operational_event_outbox, projection_generations
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void 초기_bootstrap은_cutoff까지_최근90일_성공사실을_집계한다() {
        CommerceFixture fixture = createCommerceFixture();
        Instant windowStart = NOW.atZone(KST)
                .minusDays(89)
                .toLocalDate()
                .atStartOfDay(KST)
                .toInstant();
        createOrder(fixture, NOW.minusSeconds(1_800), NOW.minusSeconds(900), 1_500L, 700L);
        createOrder(fixture, windowStart, null, 0L, 0L);
        createOrder(fixture, windowStart.minusSeconds(1), null, 900L, 0L);
        createOrder(fixture, NOW, null, 900L, 0L);
        insertOutbox("INVENTORY_OUTCOME", NOW.minusSeconds(60), """
                {"action":"INITIALIZE","productId":%d,"storeId":%d,
                 "salesPolicy":"STOCK_MANAGED","availableQuantity":7,"soldOut":false}
                """.formatted(fixture.productId(), fixture.storeId()), fixture.productId(), fixture.storeId());
        long expectedHighWater = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM operational_event_outbox", Long.class);

        ProjectionBootstrapSnapshot snapshot =
                bootstrapRepository.createBuildingAndPopulate(NOW, NOW);

        assertThat(snapshot.cutoff()).isEqualTo(NOW);
        assertThat(snapshot.outboxHighWaterId()).isEqualTo(expectedHighWater);
        assertThat(generation(snapshot.generationId()))
                .containsEntry("status", "BUILDING")
                .containsEntry("cutoff_at", Timestamp.from(NOW))
                .containsEntry("tracking_started_at", Timestamp.from(NOW))
                .containsEntry("bootstrap_high_water_id", expectedHighWater);
        assertThat(metricSum("store_metric_hourly", snapshot.generationId(), "order_success_count"))
                .isEqualTo(2L);
        assertThat(metricSum("store_metric_hourly", snapshot.generationId(), "promotion_applied_amount"))
                .isEqualTo(1_500L);
        assertThat(metricSum("store_metric_hourly", snapshot.generationId(), "promotion_realized_amount"))
                .isEqualTo(1_500L);
        assertThat(metricSum("store_metric_hourly", snapshot.generationId(), "coupon_applied_amount"))
                .isEqualTo(700L);
        assertThat(metricSum("store_metric_hourly", snapshot.generationId(), "coupon_realized_amount"))
                .isEqualTo(700L);
        assertThat(campaignMetricSum(snapshot.generationId(), "PROMOTION", "order_success_count"))
                .isEqualTo(2L);
        assertThat(campaignMetricSum(snapshot.generationId(), "COUPON", "claim_success_count"))
                .isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT commerce_store_id
                FROM campaign_metric_hourly
                WHERE generation_id = ? AND campaign_kind = 'COUPON'
                  AND claim_success_count = 1
                """, Long.class, snapshot.generationId())).isEqualTo(fixture.storeId());
        assertThat(campaignMetricSum(snapshot.generationId(), "COUPON", "order_success_count"))
                .isOne();
        assertThat(jdbcTemplate.queryForMap("""
                SELECT store_id, sales_policy, available_quantity, low_stock,
                       recent_reservation_failure_count
                FROM inventory_pressure_projection
                WHERE generation_id = ? AND product_id = ?
                """, snapshot.generationId(), fixture.productId()))
                .containsEntry("store_id", fixture.storeId())
                .containsEntry("sales_policy", "STOCK_MANAGED")
                .containsEntry("available_quantity", 7)
                .containsEntry("low_stock", false)
                .containsEntry("recent_reservation_failure_count", 0L);
    }

    @Test
    void 애플리케이션_시작은_ACTIVE_generation이_없을때만_초기화한다() throws Exception {
        ProjectionGenerationInitializer initializer = new ProjectionGenerationInitializer(
                service, Clock.fixed(NOW, KST));

        initializer.run(null);
        long firstId = activeGenerationId();
        initializer.run(null);

        assertThat(activeGenerationId()).isEqualTo(firstId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projection_generations", Long.class)).isOne();
    }

    @Test
    void 동시_애플리케이션_시작은_하나의_generation과_같은_tracking_시각을_사용한다() throws Exception {
        Instant firstCutoff = NOW.minusSeconds(60);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection lockHolder = dataSource.getConnection()) {
            advisoryLock(lockHolder, true);
            try {
                Future<Long> first = executor.submit(() ->
                        service.ensureActiveGeneration(firstCutoff));
                awaitColdStartWaiters(1);
                insertOutbox("CAMPAIGN_COMMAND_COMPLETED", NOW.minusSeconds(30), """
                        {"campaignKind":"COUPON","campaignId":77,
                         "ownerType":"STORE","ownerStoreId":10,"actorMemberId":1,
                         "command":"CREATE","beforeSummary":null,"afterSummary":{}}
                        """, 77L, 10L);
                Future<Long> second = executor.submit(() ->
                        service.ensureActiveGeneration(NOW));
                awaitColdStartWaiters(2);

                advisoryLock(lockHolder, false);

                long firstGenerationId = first.get(10, TimeUnit.SECONDS);
                long secondGenerationId = second.get(10, TimeUnit.SECONDS);
                assertThat(secondGenerationId).isEqualTo(firstGenerationId);
                assertThat(activeGenerationId()).isEqualTo(firstGenerationId);
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM projection_generations", Long.class)).isOne();
                assertThat(generation(firstGenerationId).get("tracking_started_at"))
                        .isEqualTo(Timestamp.from(firstCutoff));
                assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM campaign_audit_projection
                        WHERE generation_id = ? AND campaign_id = 77
                        """, Long.class, firstGenerationId)).isOne();
            } finally {
                advisoryLock(lockHolder, false);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void bootstrap_high_water이후_event를_replay해_배포중_변경을_놓치지_않는다() throws Exception {
        long previousId = service.ensureActiveGeneration(NOW.minusSeconds(120));
        CommerceFixture fixture = createCommerceFixture();
        CountDownLatch writerReady = new CountDownLatch(1);
        CountDownLatch appendAfterHighWater = new CountDownLatch(1);
        CountDownLatch afterHighWaterInserted = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<WriterEvents> writer = executor.submit(() -> writeOrderAndEventsWhileHoldingSharedLock(
                    fixture, writerReady, appendAfterHighWater,
                    afterHighWaterInserted, releaseWriter));
            assertThat(writerReady.await(10, TimeUnit.SECONDS)).isTrue();
            insertOutbox("INVENTORY_OUTCOME", NOW.minusSeconds(20), """
                    {"action":"INITIALIZE","productId":%d,"storeId":%d,
                     "salesPolicy":"STOCK_MANAGED","availableQuantity":7,"soldOut":false}
                    """.formatted(fixture.productId(), fixture.storeId()),
                    fixture.productId(), fixture.storeId());

            Future<ProjectionRebuildResult> rebuild = executor.submit(() -> service.rebuild(1L, NOW));
            long buildingId = awaitBuildingGeneration(previousId);
            appendAfterHighWater.countDown();
            assertThat(afterHighWaterInserted.await(10, TimeUnit.SECONDS)).isTrue();
            releaseWriter.countDown();

            WriterEvents events = writer.get(10, TimeUnit.SECONDS);
            ProjectionRebuildResult result = rebuild.get(10, TimeUnit.SECONDS);
            assertThat(result.generationId()).isEqualTo(buildingId);
            assertThat(metricSum("store_metric_hourly", result.generationId(), "order_success_count"))
                    .isEqualTo(2L);
            assertStoreReceipt(result.generationId(), events.lateCommittedEventId());
            assertStoreReceipt(result.generationId(), events.afterHighWaterEventId());
        } finally {
            appendAfterHighWater.countDown();
            releaseWriter.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 재구축_replay는_지원불가_event를_DEAD로_격리하고_다음_event를_반영해_활성화한다() throws Exception {
        long previousId = service.ensureActiveGeneration(NOW.minusSeconds(120));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection lockHolder = dataSource.getConnection()) {
            eventWriterSharedLock(lockHolder, true);
            Future<ProjectionRebuildResult> rebuild = executor.submit(() -> service.rebuild(1L, NOW));
            long buildingId = awaitBuildingGeneration(previousId);
            String unknownPayload = "{\"future\":\"raw-value\"}";
            String purchasePayload = """
                    {"result":"SUCCESS","reason":"NONE","orderId":301,
                     "storeId":10,"productId":20,"promotionCampaignId":null,
                     "couponCampaignId":null,"promotionDiscountAmount":0,
                     "couponDiscountAmount":0}
                    """;
            UUID unknownEventId = insertOutbox(
                    "FUTURE_OPERATIONAL_EVENT", 1, NOW.plusSeconds(1),
                    unknownPayload, 300L, 10L);
            UUID unsupportedEventId = insertOutbox(
                    "PURCHASE_OUTCOME", 2, NOW.plusSeconds(2),
                    purchasePayload, 301L, 10L);
            UUID validEventId = insertOutbox(
                    "PURCHASE_OUTCOME", 1, NOW.plusSeconds(3),
                    purchasePayload, 302L, 10L);

            eventWriterSharedLock(lockHolder, false);
            ProjectionRebuildResult result = rebuild.get(10, TimeUnit.SECONDS);

            assertThat(result.generationId()).isEqualTo(buildingId);
            assertThat(result.status()).isEqualTo("ACTIVE");
            assertThat(activeGenerationId()).isEqualTo(buildingId);
            assertPoisonReplayEvent(unknownEventId, "FUTURE_OPERATIONAL_EVENT", unknownPayload);
            assertPoisonReplayEvent(unsupportedEventId, "PURCHASE_OUTCOME", purchasePayload);
            assertStoreReceipt(buildingId, validEventId);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT processed_at FROM projection_event_receipts
                    WHERE generation_id = ? AND projection_name = 'store-commerce-metrics'
                      AND event_id = ?
                    """, Timestamp.class, buildingId, validEventId)).isEqualTo(Timestamp.from(NOW));
            assertThat(metricSum("store_metric_hourly", buildingId, "order_success_count"))
                    .isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 사용시각없는_과거쿠폰사용과_실패와_감사를_추정하지_않는다() {
        CommerceFixture fixture = createCommerceFixture();
        jdbcTemplate.update(
                "UPDATE member_coupons SET status = 'USED' WHERE id = ?", fixture.memberCouponId());

        long generationId = service.ensureActiveGeneration(NOW);

        assertThat(campaignMetricSum(generationId, "COUPON", "claim_success_count")).isOne();
        assertThat(campaignMetricSum(generationId, "COUPON", "claim_failure_count")).isZero();
        assertThat(campaignMetricSum(generationId, "COUPON", "redemption_success_count")).isZero();
        assertThat(campaignMetricSum(generationId, "COUPON", "redemption_failure_count")).isZero();
        assertThat(metricSum("store_metric_hourly", generationId, "purchase_failure_count")).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM campaign_audit_projection WHERE generation_id = ?
                """, Long.class, generationId)).isZero();
        assertThat(generation(generationId).get("tracking_started_at")).isEqualTo(Timestamp.from(NOW));
    }

    @Test
    void 재구축중에는_기존_ACTIVE_generation을_계속_조회한다() throws Exception {
        long previousId = service.ensureActiveGeneration(NOW.minusSeconds(60));
        CountDownLatch lockReady = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> lockHolder = executor.submit(() -> holdSharedLock(lockReady, releaseLock));
            assertThat(lockReady.await(10, TimeUnit.SECONDS)).isTrue();
            Future<ProjectionRebuildResult> rebuild = executor.submit(() -> service.rebuild(1L, NOW));

            long buildingId = awaitBuildingGeneration(previousId);
            assertThat(activeGenerationId()).isEqualTo(previousId);
            assertThat(generation(buildingId).get("status")).isEqualTo("BUILDING");

            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);
            assertThat(rebuild.get(10, TimeUnit.SECONDS).generationId()).isEqualTo(buildingId);
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 검증된_generation만_원자적으로_ACTIVE로_전환한다() {
        long previousId = service.ensureActiveGeneration(NOW.minusSeconds(60));

        ProjectionRebuildResult result = service.rebuild(9L, NOW);

        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(activeGenerationId()).isEqualTo(result.generationId());
        assertThat(generation(previousId))
                .containsEntry("status", "RETIRED")
                .containsEntry("retired_at", Timestamp.from(result.activatedAt()));
        assertThat(generation(result.generationId()))
                .containsEntry("status", "ACTIVE")
                .containsEntry("activated_at", Timestamp.from(result.activatedAt()));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM projection_generations WHERE status = 'BUILDING'
                """, Long.class)).isZero();
    }

    @Test
    void 재구축_활성화시각은_cutoff가_아닌_실제전환시각이다() {
        service.ensureActiveGeneration(NOW.minusSeconds(60));
        Instant activationTime = NOW.plusSeconds(5);
        ProjectionGenerationService delayedService = new ProjectionGenerationService(
                bootstrapRepository, projectionRepository, coordinator,
                Clock.fixed(activationTime, KST));

        ProjectionRebuildResult result = delayedService.rebuild(9L, NOW);

        assertThat(result.cutoff()).isEqualTo(NOW);
        assertThat(result.activatedAt()).isEqualTo(activationTime).isAfter(result.cutoff());
        assertThat(generation(result.generationId()).get("activated_at"))
                .isEqualTo(Timestamp.from(result.activatedAt()));
    }

    @Test
    void 재구축_실패는_새_generation만_FAILED로_남긴다() {
        long previousId = service.ensureActiveGeneration(NOW.minusSeconds(60));
        insertOutbox("CAMPAIGN_COMMAND_COMPLETED", NOW.minusSeconds(30), "{}", 100L, null);

        assertThatThrownBy(() -> service.rebuild(3L, NOW))
                .isInstanceOf(RuntimeException.class);

        assertThat(activeGenerationId()).isEqualTo(previousId);
        assertThat(generation(previousId).get("status")).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM projection_generations WHERE status = 'FAILED'
                """, Long.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM projection_generations WHERE status = 'BUILDING'
                """, Long.class)).isZero();
    }

    @Test
    @DisplayName("7일지난_오래된_RETIRED_generation을_정리한다")
    void 일주일지난_오래된_RETIRED_generation을_정리한다() {
        long activeId = insertGeneration("ACTIVE", NOW.minusSeconds(86_400), NOW);
        long oldRetiredId = insertGeneration("RETIRED", NOW.minusSeconds(20L * 86_400),
                NOW.minusSeconds(20L * 86_400));
        long newestRetiredId = insertGeneration("RETIRED", NOW.minusSeconds(10L * 86_400),
                NOW.minusSeconds(10L * 86_400));
        UUID receiptEventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO projection_event_receipts (generation_id, projection_name, event_id, processed_at)
                VALUES (?, 'cleanup-test', ?, ?)
                """, oldRetiredId, receiptEventId, Timestamp.from(NOW));
        jdbcTemplate.update("""
                INSERT INTO store_metric_hourly (generation_id, bucket_start, store_id)
                VALUES (?, ?, 1)
                """, oldRetiredId, Timestamp.from(NOW));
        ProjectionGenerationCleanupScheduler scheduler = new ProjectionGenerationCleanupScheduler(
                projectionRepository, Clock.fixed(NOW, KST));

        scheduler.cleanup();

        assertThat(generationIds()).containsExactlyInAnyOrder(activeId, newestRetiredId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM projection_event_receipts WHERE generation_id = ?
                """, Long.class, oldRetiredId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM store_metric_hourly WHERE generation_id = ?
                """, Long.class, oldRetiredId)).isZero();
    }

    private CommerceFixture createCommerceFixture() {
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create(
                    UUID.randomUUID() + "@example.com", "encoded", "판매자"));
            Member buyer = memberRepository.save(Member.create(
                    UUID.randomUUID() + "@example.com", "encoded", "구매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "운영 상점", "소개"));
            Product product = productRepository.saveAndFlush(Product.create(
                    store, "운영 상품", "설명", 10_000L,
                    ProductSalesPolicy.STOCK_MANAGED, 10, 7));
            inventoryService.initialize(product, 7, seller.getId());
            long promotionId = insertPromotionCampaign(store.getId());
            long couponCampaignId = insertCouponCampaign(store.getId());
            long memberCouponId = insertMemberCoupon(
                    buyer.getId(), couponCampaignId, NOW.minusSeconds(3_600));
            return new CommerceFixture(
                    seller.getId(), buyer.getId(), store.getId(), product.getId(),
                    promotionId, couponCampaignId, memberCouponId);
        });
    }

    private long createOrder(
            CommerceFixture fixture,
            Instant orderedAt,
            Instant confirmedAt,
            long promotionDiscount,
            long couponDiscount
    ) {
        return transactionTemplate.execute(status -> {
            Member buyer = memberRepository.findById(fixture.buyerId()).orElseThrow();
            Product product = productRepository.findById(fixture.productId()).orElseThrow();
            PromotionPrice price = new PromotionPrice(
                    10_000L, fixture.promotionId(), "프로모션", promotionDiscount,
                    10_000L - promotionDiscount);
            CouponDiscountQuote coupon = couponDiscount == 0 ? null : new CouponDiscountQuote(
                    fixture.memberCouponId(), couponDiscount,
                    10_000L - promotionDiscount - couponDiscount);
            Order order = orderRepository.saveAndFlush(Order.create(buyer, product, price, coupon));
            jdbcTemplate.update("""
                    UPDATE orders
                    SET ordered_at = ?, confirmed_at = ?, status = ?
                    WHERE id = ?
                    """, LocalDateTime.ofInstant(orderedAt, KST),
                    confirmedAt == null ? null : LocalDateTime.ofInstant(confirmedAt, KST),
                    confirmedAt == null ? "CREATED" : "CONFIRMED", order.getId());
            return order.getId();
        });
    }

    private WriterEvents writeOrderAndEventsWhileHoldingSharedLock(
            CommerceFixture fixture,
            CountDownLatch writerReady,
            CountDownLatch appendAfterHighWater,
            CountDownLatch afterHighWaterInserted,
            CountDownLatch releaseWriter
    ) {
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        return transaction.execute(status -> {
            jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock_shared(310031)", Object.class);
            long orderId = jdbcTemplate.queryForObject("""
                    INSERT INTO orders (
                        buyer_id, product_id, seller_id, list_price,
                        promotion_campaign_id, promotion_discount_amount,
                        member_coupon_id, coupon_discount_amount, final_price,
                        status, ordered_at
                    ) VALUES (?, ?, ?, 10000, NULL, 0, NULL, 0, 10000, 'CREATED', ?)
                    RETURNING id
                    """, Long.class, fixture.buyerId(), fixture.productId(), fixture.sellerId(),
                    LocalDateTime.ofInstant(NOW.minusSeconds(30), KST));
            UUID lateCommittedEventId = insertOutbox("PURCHASE_OUTCOME", NOW.minusSeconds(30), """
                    {"result":"SUCCESS","reason":"NONE","orderId":%d,
                     "storeId":%d,"productId":%d,"promotionCampaignId":null,
                     "couponCampaignId":null,"promotionDiscountAmount":0,
                     "couponDiscountAmount":0}
                    """.formatted(orderId, fixture.storeId(), fixture.productId()),
                    orderId, fixture.storeId());
            writerReady.countDown();
            await(appendAfterHighWater);
            UUID afterHighWaterEventId = insertOutbox("PURCHASE_OUTCOME", NOW.minusSeconds(10), """
                    {"result":"SUCCESS","reason":"NONE","orderId":%d,
                     "storeId":%d,"productId":%d,"promotionCampaignId":null,
                     "couponCampaignId":null,"promotionDiscountAmount":0,
                     "couponDiscountAmount":0}
                    """.formatted(orderId + 1, fixture.storeId(), fixture.productId()),
                    orderId + 1, fixture.storeId());
            afterHighWaterInserted.countDown();
            await(releaseWriter);
            return new WriterEvents(lateCommittedEventId, afterHighWaterEventId);
        });
    }

    private void assertStoreReceipt(long generationId, UUID eventId) {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM projection_event_receipts
                WHERE generation_id = ? AND projection_name = 'store-commerce-metrics'
                  AND event_id = ?
                """, Long.class, generationId, eventId)).isOne();
    }

    private void assertPoisonReplayEvent(UUID eventId, String eventType, String payload) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT event_type, delivery_state, attempt_count, next_attempt_at,
                       last_error, payload = CAST(? AS JSONB) AS payload_unchanged
                FROM operational_event_outbox
                WHERE event_id = ?
                """, payload, eventId);
        assertThat(row)
                .containsEntry("event_type", eventType)
                .containsEntry("delivery_state", "DEAD")
                .containsEntry("attempt_count", 1)
                .containsEntry("next_attempt_at", Timestamp.from(NOW))
                .containsEntry("payload_unchanged", true);
        assertThat(row.get("last_error")).asString()
                .contains("UnsupportedOperationalEventSchemaException")
                .hasSizeLessThanOrEqualTo(1_000);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM projection_event_receipts WHERE event_id = ?
                """, Long.class, eventId)).isZero();
    }

    private void advisoryLock(Connection connection, boolean acquire) throws Exception {
        String function = acquire ? "pg_advisory_lock" : "pg_advisory_unlock";
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + function + "(?)")) {
            statement.setLong(1, 310032L);
            statement.execute();
        }
    }

    private void eventWriterSharedLock(Connection connection, boolean acquire) throws Exception {
        String function = acquire ? "pg_advisory_lock_shared" : "pg_advisory_unlock_shared";
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + function + "(310031)")) {
            statement.execute();
        }
    }

    private void awaitColdStartWaiters(long expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Long waiters = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM pg_locks
                    WHERE locktype = 'advisory' AND NOT granted AND objid = 310032
                    """, Long.class);
            if (waiters != null && waiters >= expected) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("cold-start advisory lock 대기자 확인 시간이 초과되었습니다.");
    }

    private void holdSharedLock(CountDownLatch lockReady, CountDownLatch releaseLock) {
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock_shared(310031)", Object.class);
            lockReady.countDown();
            await(releaseLock);
        });
    }

    private long awaitBuildingGeneration(long previousId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long id = jdbcTemplate.query("""
                    SELECT id FROM projection_generations
                    WHERE status = 'BUILDING' AND id <> ?
                    ORDER BY id DESC LIMIT 1
                    """, resultSet -> resultSet.next() ? resultSet.getLong(1) : null, previousId);
            if (id != null) {
                return id;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("BUILDING generation 생성 시간이 초과되었습니다.");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private long insertPromotionCampaign(long storeId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO promotion_campaigns (
                    version, store_id, scope, discount_type, discount_value, priority, title,
                    start_at, end_at, lifecycle_status, created_at, updated_at
                ) VALUES (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', 1500, 1, '운영 프로모션',
                    ?, ?, 'SCHEDULED', ?, ?)
                RETURNING id
                """, Long.class, storeId, Timestamp.from(NOW.minusSeconds(86_400)),
                Timestamp.from(NOW.plusSeconds(86_400)), Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private long insertCouponCampaign(long storeId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO coupon_campaigns (
                    version, owner_type, store_id, scope, discount_type, discount_value,
                    max_discount_amount, minimum_purchase_amount, stackable, title,
                    issue_starts_at, issue_ends_at, validity_type, validity_days,
                    lifecycle_status, issued_count, created_at, updated_at
                ) VALUES (
                    0, 'STORE', ?, 'ALL_PRODUCTS', 'FIXED_AMOUNT', 700,
                    NULL, 0, TRUE, '운영 쿠폰', ?, ?, 'DAYS_FROM_ISSUANCE', 7,
                    'SCHEDULED', 1, ?, ?
                ) RETURNING id
                """, Long.class, storeId, Timestamp.from(NOW.minusSeconds(86_400)),
                Timestamp.from(NOW.plusSeconds(86_400)), Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private long insertMemberCoupon(long memberId, long campaignId, Instant issuedAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_coupons (
                    member_id, coupon_campaign_id, issued_at, valid_until,
                    discount_type, discount_value, max_discount_amount,
                    minimum_purchase_amount, scope, stackable, status
                ) VALUES (?, ?, ?, ?, 'FIXED_AMOUNT', 700, NULL, 0,
                    'ALL_PRODUCTS', TRUE, 'ISSUED')
                RETURNING id
                """, Long.class, memberId, campaignId, Timestamp.from(issuedAt),
                Timestamp.from(issuedAt.plusSeconds(7 * 86_400L)));
    }

    private UUID insertOutbox(
            String eventType,
            Instant occurredAt,
            String payload,
            Long aggregateId,
            Long storeId
    ) {
        return insertOutbox(eventType, 1, occurredAt, payload, aggregateId, storeId);
    }

    private UUID insertOutbox(
            String eventType,
            int schemaVersion,
            Instant occurredAt,
            String payload,
            Long aggregateId,
            Long storeId
    ) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO operational_event_outbox (
                    event_id, event_type, schema_version, aggregate_type,
                    aggregate_id, aggregate_version, store_id, partition_key,
                    occurred_at, payload, delivery_state, next_attempt_at
                ) VALUES (?, ?, ?, 'test', ?, 1, ?, ?, ?, CAST(? AS JSONB), 'PENDING', ?)
                """, eventId, eventType, schemaVersion, aggregateId, storeId,
                "test:" + eventId, Timestamp.from(occurredAt), payload, Timestamp.from(occurredAt));
        return eventId;
    }

    private long insertGeneration(String status, Instant cutoff, Instant statusAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO projection_generations (
                    status, cutoff_at, tracking_started_at, bootstrap_high_water_id,
                    activated_at, retired_at
                ) VALUES (?, ?, ?, 0, ?, ?)
                RETURNING id
                """, Long.class, status, Timestamp.from(cutoff), Timestamp.from(cutoff),
                "ACTIVE".equals(status) ? Timestamp.from(statusAt) : null,
                "RETIRED".equals(status) ? Timestamp.from(statusAt) : null);
    }

    private long activeGenerationId() {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM projection_generations WHERE status = 'ACTIVE'
                """, Long.class);
    }

    private Map<String, Object> generation(long generationId) {
        return jdbcTemplate.queryForMap("""
                SELECT status, cutoff_at, tracking_started_at, bootstrap_high_water_id,
                       activated_at, retired_at
                FROM projection_generations WHERE id = ?
                """, generationId);
    }

    private java.util.List<Long> generationIds() {
        return jdbcTemplate.queryForList("SELECT id FROM projection_generations", Long.class);
    }

    private long metricSum(String table, long generationId, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(%s), 0) FROM %s WHERE generation_id = ?
                """.formatted(column, table), Long.class, generationId);
    }

    private long campaignMetricSum(long generationId, String kind, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(%s), 0) FROM campaign_metric_hourly
                WHERE generation_id = ? AND campaign_kind = ?
                """.formatted(column), Long.class, generationId, kind);
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
                CREATE UNIQUE INDEX IF NOT EXISTS uq_projection_generations_active_test
                ON projection_generations ((status)) WHERE status = 'ACTIVE'
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
                    updated_at TIMESTAMPTZ NOT NULL,
                    PRIMARY KEY (generation_id, product_id)
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
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS campaign_audit_projection (
                    id BIGSERIAL PRIMARY KEY,
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    event_id UUID NOT NULL, campaign_kind VARCHAR(20) NOT NULL,
                    campaign_id BIGINT NOT NULL, owner_type VARCHAR(20) NOT NULL,
                    owner_store_id BIGINT NOT NULL DEFAULT 0, actor_member_id BIGINT NOT NULL,
                    command VARCHAR(20) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
                    aggregate_version BIGINT, before_summary JSONB, after_summary JSONB NOT NULL,
                    UNIQUE (generation_id, event_id)
                )
                """);
    }

    private record CommerceFixture(
            long sellerId,
            long buyerId,
            long storeId,
            long productId,
            long promotionId,
            long couponCampaignId,
            long memberCouponId
    ) {
    }

    private record WriterEvents(
            UUID lateCommittedEventId,
            UUID afterHighWaterEventId
    ) {
    }
}
