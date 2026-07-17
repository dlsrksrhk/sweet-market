package com.sweet.market.operations.inventory;

import com.sweet.market.inventory.application.InventoryAdjustmentRequest;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.inventory.domain.InventoryAdjustmentReason;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.operations.event.OperationalEventRecorder;
import com.sweet.market.operations.projection.OperationalProjectionCoordinator;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "market.operations-projector.enabled=false")
class InventoryPressureProjectionTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-07-17T03:00:00Z");

    @Autowired private InventoryOutcomeEventFactory eventFactory;
    @Autowired private OperationalEventRecorder eventRecorder;
    @Autowired private OperationalProjectionCoordinator coordinator;
    @Autowired private InventoryPressureMaintenanceService maintenanceService;
    @Autowired private InventoryService inventoryService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreMembershipRepository storeMembershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void 재고_압력_projection_테이블을_준비한다() {
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
                CREATE TABLE IF NOT EXISTS inventory_pressure_projection (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    product_id BIGINT NOT NULL, store_id BIGINT NOT NULL, sales_policy VARCHAR(20) NOT NULL,
                    available_quantity INTEGER, low_stock BOOLEAN NOT NULL, last_sold_out_at TIMESTAMPTZ,
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
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS store_metric_hourly (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    bucket_start TIMESTAMPTZ NOT NULL, store_id BIGINT NOT NULL,
                    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
                    order_success_count BIGINT NOT NULL DEFAULT 0, purchase_failure_count BIGINT NOT NULL DEFAULT 0,
                    reservation_failure_count BIGINT NOT NULL DEFAULT 0,
                    promotion_applied_amount BIGINT NOT NULL DEFAULT 0, promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0, promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_applied_amount BIGINT NOT NULL DEFAULT 0, coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0, coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    sold_out_transition_count BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (generation_id, bucket_start, store_id, outcome_reason)
                )
                """);
        jdbcTemplate.execute("""
                TRUNCATE TABLE inventory_failure_hourly, inventory_pressure_projection,
                    store_metric_hourly, projection_event_receipts, operational_event_outbox,
                    projection_generations RESTART IDENTITY CASCADE
                """);
        jdbcTemplate.update("""
                INSERT INTO projection_generations (status, cutoff_at, tracking_started_at, activated_at)
                VALUES ('ACTIVE', ?, ?, ?)
                """, Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
    }

    @Test
    void STOCK_MANAGED_수량_5이하는_저재고로_표시한다() {
        record("RESERVE", 1L, "STOCK_MANAGED", 6, false, 1L, NOW);
        project();
        assertThat(lowStock(1L)).isFalse();

        record("RESERVE", 1L, "STOCK_MANAGED", 5, false, 2L, NOW.plusSeconds(60));
        project();
        assertThat(lowStock(1L)).isTrue();
    }

    @Test
    void 운영자_재고조정은_0에서_10과_6에서_5의_상태를_projection에_반영한다() {
        StockFixture restored = stockFixture("pressure-restored", 0);
        StockFixture lowered = stockFixture("pressure-lowered", 6);
        jdbcTemplate.execute("TRUNCATE TABLE operational_event_outbox RESTART IDENTITY CASCADE");

        adjust(restored, 10);
        adjust(lowered, 5);
        project();

        assertThat(jdbcTemplate.queryForMap("""
                SELECT available_quantity, low_stock FROM inventory_pressure_projection
                WHERE product_id = ?
                """, restored.productId())).containsEntry("available_quantity", 10)
                .containsEntry("low_stock", false);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT available_quantity, low_stock FROM inventory_pressure_projection
                WHERE product_id = ?
                """, lowered.productId())).containsEntry("available_quantity", 5)
                .containsEntry("low_stock", true);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM operational_event_outbox
                WHERE event_type = 'INVENTORY_OUTCOME' AND payload ->> 'action' = 'ADJUST'
                """, Long.class)).isEqualTo(2L);
    }

    @Test
    void SINGLE_ITEM은_저재고에서_제외하고_품절전환은_기록한다() {
        record("RESERVE", 2L, "SINGLE_ITEM", null, false, 1L, NOW);
        record("SOLD_OUT", 2L, "SINGLE_ITEM", null, true, 2L, NOW.plusSeconds(60));

        project();

        assertThat(lowStock(2L)).isFalse();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(sold_out_transition_count), 0) FROM store_metric_hourly
                WHERE store_id = 21
                """, Long.class)).isOne();
    }

    @Test
    void 낮은_version의_재고_event는_최신_projection을_덮어쓰지_않는다() {
        record("RESERVE", 3L, "STOCK_MANAGED", 4, false, 3L, NOW);
        record("RESERVATION_FAILED", 3L, "STOCK_MANAGED", 6, false, 2L, NOW.plusSeconds(60));

        project();

        assertThat(jdbcTemplate.queryForMap("""
                SELECT available_quantity, aggregate_version, low_stock,
                       recent_reservation_failure_count
                FROM inventory_pressure_projection WHERE product_id = 3
                """)).containsEntry("available_quantity", 4)
                .containsEntry("aggregate_version", 3L)
                .containsEntry("low_stock", true)
                .containsEntry("recent_reservation_failure_count", 1L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(failure_count), 0) FROM inventory_failure_hourly
                WHERE product_id = 3
                """, Long.class)).isOne();
    }

    @Test
    void 수량이_0인_출하_event는_품절전환_시각을_덮어쓰지_않는다() {
        Instant soldOutAt = NOW.plusSeconds(60);
        record("SOLD_OUT", 5L, "STOCK_MANAGED", 0, true, 1L, soldOutAt);
        record("SHIPMENT", 5L, "STOCK_MANAGED", 0, true, 2L, soldOutAt.plusSeconds(60));

        project();

        assertThat(jdbcTemplate.queryForObject("""
                SELECT last_sold_out_at FROM inventory_pressure_projection WHERE product_id = 5
                """, Timestamp.class).toInstant()).isEqualTo(soldOutAt);
    }

    @Test
    void 최신_복구보다_version이_낮은_품절전환도_품절시각만_기록한다() {
        Instant restoredAt = NOW.plusSeconds(120);
        Instant soldOutAt = NOW.plusSeconds(60);
        record("RESTORE", 6L, "STOCK_MANAGED", 10, false, 3L, restoredAt);
        record("SOLD_OUT", 6L, "STOCK_MANAGED", 0, true, 2L, soldOutAt);

        project();

        assertThat(jdbcTemplate.queryForMap("""
                SELECT available_quantity, aggregate_version, last_sold_out_at
                FROM inventory_pressure_projection WHERE product_id = 6
                """)).containsEntry("available_quantity", 10)
                .containsEntry("aggregate_version", 3L)
                .containsEntry("last_sold_out_at", Timestamp.from(soldOutAt));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(sold_out_transition_count), 0) FROM store_metric_hourly
                WHERE store_id = 21
                """, Long.class)).isOne();
    }

    @Test
    void _90일밖_재고실패는_recent_count에서_제외한다() {
        jdbcTemplate.update("""
                INSERT INTO inventory_pressure_projection (
                    generation_id, product_id, store_id, sales_policy, available_quantity,
                    low_stock, recent_reservation_failure_count, aggregate_version, updated_at
                ) VALUES (1, 4, 21, 'STOCK_MANAGED', 4, TRUE, 99, 1, ?)
                """, Timestamp.from(NOW));
        insertFailureBucket(NOW.minusSeconds(90L * 86_400 + 3_600), 7);
        insertFailureBucket(NOW.minusSeconds(90L * 86_400), 2);
        insertFailureBucket(NOW.minusSeconds(86_400), 3);

        maintenanceService.refresh(NOW);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT recent_reservation_failure_count FROM inventory_pressure_projection
                WHERE product_id = 4
                """, Long.class)).isEqualTo(5L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM inventory_failure_hourly WHERE product_id = 4
                """, Long.class)).isEqualTo(2L);
    }

    private void record(String action, long productId, String policy, Integer quantity,
                        boolean soldOut, long version, Instant occurredAt) {
        eventRecorder.record(eventFactory.outcome(
                action, productId, 21L, policy, quantity, soldOut, version, occurredAt));
    }

    private void project() {
        coordinator.projectNextBatch(Instant.now().plusSeconds(2), 100);
    }

    private boolean lowStock(long productId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT low_stock FROM inventory_pressure_projection WHERE product_id = ?
                """, Boolean.class, productId));
    }

    private void insertFailureBucket(Instant bucket, long count) {
        jdbcTemplate.update("""
                INSERT INTO inventory_failure_hourly (
                    generation_id, bucket_start, product_id, store_id, failure_count
                ) VALUES (1, ?, 4, 21, ?)
                """, Timestamp.from(bucket), count);
    }

    private StockFixture stockFixture(String prefix, int initialQuantity) {
        return transactionTemplate.execute(status -> {
            Member owner = memberRepository.save(Member.create(
                    prefix + "@example.com", "encoded", "소유자"));
            Store store = storeRepository.save(Store.createPersonal(owner, prefix + " 상점", "소개"));
            storeMembershipRepository.save(StoreMembership.createOwner(store, owner));
            Product product = productRepository.save(Product.create(
                    store, prefix + " 상품", "설명", 10_000L,
                    ProductSalesPolicy.STOCK_MANAGED, 5, initialQuantity));
            inventoryService.initialize(product, initialQuantity, owner.getId());
            return new StockFixture(owner.getId(), store.getId(), product.getId());
        });
    }

    private void adjust(StockFixture fixture, int totalQuantity) {
        inventoryService.adjust(
                fixture.ownerId(), fixture.storeId(), fixture.productId(),
                new InventoryAdjustmentRequest(totalQuantity, InventoryAdjustmentReason.RESTOCK, null));
    }

    private record StockFixture(long ownerId, long storeId, long productId) { }
}
