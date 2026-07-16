package com.sweet.market.purchase;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.purchase.application.ProductReservationService;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductReservationServiceTest extends IntegrationTestSupport {

    @Autowired
    private ProductReservationService reservationService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 재고형_상품은_가용수량이_있을때만_한개를_예약한다() {
        Order order = createPersistedStockOrder(1);

        reservationService.reserve(order);

        assertThat(availableQuantity(order.getProduct().getId())).isZero();
        assertThat(countInventoryAdjustments(order.getId(), "RESERVATION")).isOne();
        assertThat(reservationBeforeQuantity(order.getId())).isZero();
        assertThat(reservationAfterQuantity(order.getId())).isEqualTo(1);
    }

    @Test
    void 단품은_판매중일때_한번만_예약한다() {
        Order winner = createPersistedSingleItemOrder();
        Order loser = createPersistedSingleItemOrderForAnotherBuyer(winner.getProduct());

        reservationService.reserve(winner);

        assertThatThrownBy(() -> reservationService.reserve(loser))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_UNAVAILABLE);
    }

    @Test
    void 재고가_없으면_예약_이력을_남기지_않는다() {
        Order order = createPersistedStockOrder(0);

        assertThatThrownBy(() -> reservationService.reserve(order))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_SOLD_OUT);

        assertThat(countInventoryAdjustments(order.getId(), "RESERVATION")).isZero();
    }

    @Test
    void 한개_재고에_동시_예약하면_하나만_성공하고_한개의_이력만_남긴다() throws Exception {
        Order winner = createPersistedStockOrder(1);
        Order loser = createPersistedStockOrderForAnotherBuyer(winner.getProduct());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        createInventoryReservationDelayTrigger();
        try {
            Future<ReservationAttempt> first = executor.submit(() -> reserveAfterBarrier(winner, ready, start));
            Future<ReservationAttempt> second = executor.submit(() -> reserveAfterBarrier(loser, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ReservationAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            assertThat(attempts).filteredOn(ReservationAttempt::succeeded).hasSize(1);
            assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
                    .extracting(ReservationAttempt::errorCode)
                    .containsExactly(ErrorCode.PRODUCT_SOLD_OUT);
            assertThat(availableQuantity(winner.getProduct().getId())).isZero();
            assertThat(countInventoryAdjustmentsForProduct(winner.getProduct().getId(), "RESERVATION")).isOne();
        } finally {
            executor.shutdownNow();
            dropInventoryReservationDelayTrigger();
        }
    }

    @Test
    void 주문_스냅샷_생성후_상품을_숨기면_재고를_예약하지_않는다() {
        Order order = createPersistedStockOrder(1);
        hideProduct(order.getProduct().getId());

        assertThatThrownBy(() -> reservationService.reserve(order))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_UNAVAILABLE);

        assertInventory(order.getProduct().getId(), 1, 0);
        assertThat(countInventoryAdjustments(order.getId(), "RESERVATION")).isZero();
    }

    @Test
    void 주문_스냅샷_생성후_상점을_중지하면_재고를_예약하지_않는다() {
        Order order = createPersistedStockOrder(1);
        suspendStore(storeId(order.getProduct().getId()));

        assertThatThrownBy(() -> reservationService.reserve(order))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_UNAVAILABLE);

        assertInventory(order.getProduct().getId(), 1, 0);
        assertThat(countInventoryAdjustments(order.getId(), "RESERVATION")).isZero();
    }

    private Order createPersistedStockOrder(int totalQuantity) {
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create("stock-seller@example.com", "encoded-password", "판매자"));
            Member buyer = memberRepository.save(Member.create("stock-buyer@example.com", "encoded-password", "구매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "재고 상점", "소개"));
            Product product = productRepository.save(Product.create(
                    store,
                    "재고 상품",
                    "설명",
                    10_000L,
                    ProductSalesPolicy.STOCK_MANAGED,
                    1,
                    totalQuantity
            ));
            inventoryRepository.save(Inventory.initialize(product, totalQuantity));
            return orderRepository.saveAndFlush(Order.create(buyer, product));
        });
    }

    private Order createPersistedSingleItemOrder() {
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create("single-seller@example.com", "encoded-password", "판매자"));
            Member buyer = memberRepository.save(Member.create("single-buyer@example.com", "encoded-password", "구매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "단품 상점", "소개"));
            Product product = productRepository.save(Product.create(store, "단품 상품", "설명", 10_000L));
            Order order = orderRepository.save(Order.create(buyer, product));
            product.restoreOnSaleFromReservation();
            return orderRepository.saveAndFlush(order);
        });
    }

    private Order createPersistedSingleItemOrderForAnotherBuyer(Product product) {
        return transactionTemplate.execute(status -> {
            Member buyer = memberRepository.save(Member.create("single-other-buyer@example.com", "encoded-password", "다른 구매자"));
            Product persistedProduct = productRepository.findById(product.getId()).orElseThrow();
            Order order = orderRepository.save(Order.create(buyer, persistedProduct));
            persistedProduct.restoreOnSaleFromReservation();
            return orderRepository.saveAndFlush(order);
        });
    }

    private Order createPersistedStockOrderForAnotherBuyer(Product product) {
        return transactionTemplate.execute(status -> {
            Member buyer = memberRepository.save(Member.create("stock-other-buyer@example.com", "encoded-password", "다른 구매자"));
            Product persistedProduct = productRepository.findById(product.getId()).orElseThrow();
            return orderRepository.saveAndFlush(Order.create(buyer, persistedProduct));
        });
    }

    private void hideProduct(Long productId) {
        transactionTemplate.executeWithoutResult(status -> productRepository.findById(productId).orElseThrow().hide());
    }

    private void suspendStore(Long storeId) {
        transactionTemplate.executeWithoutResult(status -> storeRepository.findById(storeId).orElseThrow().suspend());
    }

    private ReservationAttempt reserveAfterBarrier(Order order, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            reservationService.reserve(order);
            return new ReservationAttempt(null);
        } catch (BusinessException exception) {
            return new ReservationAttempt(exception.errorCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void createInventoryReservationDelayTrigger() {
        jdbcTemplate.execute("""
                create or replace function delay_inventory_reservation()
                returns trigger as $$
                begin
                    perform pg_sleep(0.2);
                    return new;
                end;
                $$ language plpgsql
                """);
        jdbcTemplate.execute("""
                create trigger inventory_reservation_delay
                before update on inventories
                for each row execute function delay_inventory_reservation()
                """);
    }

    private void dropInventoryReservationDelayTrigger() {
        jdbcTemplate.execute("drop trigger if exists inventory_reservation_delay on inventories");
        jdbcTemplate.execute("drop function if exists delay_inventory_reservation()");
    }

    private int availableQuantity(Long productId) {
        return jdbcTemplate.queryForObject(
                "select total_quantity - reserved_quantity from inventories where product_id = ?",
                Integer.class,
                productId
        );
    }

    private void assertInventory(Long productId, int totalQuantity, int reservedQuantity) {
        assertThat(jdbcTemplate.queryForObject(
                "select total_quantity from inventories where product_id = ?",
                Integer.class,
                productId
        )).isEqualTo(totalQuantity);
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_quantity from inventories where product_id = ?",
                Integer.class,
                productId
        )).isEqualTo(reservedQuantity);
    }

    private long countInventoryAdjustmentsForProduct(Long productId, String changeType) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from inventory_adjustments where product_id = ? and change_type = ?",
                Long.class,
                productId,
                changeType
        );
        return count == null ? 0 : count;
    }

    private Long storeId(Long productId) {
        return jdbcTemplate.queryForObject("select store_id from products where id = ?", Long.class, productId);
    }

    private long countInventoryAdjustments(Long orderId, String changeType) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from inventory_adjustments where order_id = ? and change_type = ?",
                Long.class,
                orderId,
                changeType
        );
        return count == null ? 0 : count;
    }

    private int reservationBeforeQuantity(Long orderId) {
        return jdbcTemplate.queryForObject(
                "select before_reserved_quantity from inventory_adjustments where order_id = ? and change_type = 'RESERVATION'",
                Integer.class,
                orderId
        );
    }

    private int reservationAfterQuantity(Long orderId) {
        return jdbcTemplate.queryForObject(
                "select after_reserved_quantity from inventory_adjustments where order_id = ? and change_type = 'RESERVATION'",
                Integer.class,
                orderId
        );
    }

    private record ReservationAttempt(ErrorCode errorCode) {

        private boolean succeeded() {
            return errorCode == null;
        }
    }
}
