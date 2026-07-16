package com.sweet.market.purchase;

import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryLockingComparisonTest extends IntegrationTestSupport {

    private static final int BUYERS = 10;
    private static final int STOCK = 3;

    @Autowired private MemberRepository memberRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryService inventoryService;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void 조건부_수정은_재고만큼만_예약한다() throws Exception {
        ExperimentResult result = runConditionalUpdateScenario(BUYERS, STOCK);
        printResult(result);
        assertExpected(result, STOCK, BUYERS);
        assertThat(result.retries()).isZero();
    }

    @Test
    void 낙관적_재시도는_제한된_횟수로_재고만큼만_예약한다() throws Exception {
        ExperimentResult result = runOptimisticRetryScenario(BUYERS, STOCK, 5);
        printResult(result);
        assertExpected(result, STOCK, BUYERS);
        assertThat(result.retries()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void 비관적_잠금은_재고만큼만_예약한다() throws Exception {
        ExperimentResult result = runPessimisticLockScenario(BUYERS, STOCK);
        printResult(result);
        assertExpected(result, STOCK, BUYERS);
        assertThat(result.retries()).isZero();
    }

    ExperimentResult runConditionalUpdateScenario(int buyers, int stock) throws Exception {
        Long productId = createStockProduct(stock, "conditional");
        return runScenario("conditional-update", buyers, productId, ignored -> transactionTemplate.execute(status ->
                jdbcTemplate.update("""
                        update inventories set reserved_quantity = reserved_quantity + 1, version = version + 1
                        where product_id = ? and total_quantity - reserved_quantity > 0
                        """, productId) == 1));
    }

    ExperimentResult runOptimisticRetryScenario(int buyers, int stock, int maxRetries) throws Exception {
        Long productId = createStockProduct(stock, "optimistic");
        AtomicInteger retries = new AtomicInteger();
        return runScenario("optimistic-retry", buyers, productId, ignored -> transactionTemplate.execute(status -> {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                Map<String, Object> inventory = jdbcTemplate.queryForMap(
                        "select version, total_quantity, reserved_quantity from inventories where product_id = ?", productId);
                int available = ((Number) inventory.get("total_quantity")).intValue()
                        - ((Number) inventory.get("reserved_quantity")).intValue();
                if (available == 0) return false;
                long version = ((Number) inventory.get("version")).longValue();
                if (jdbcTemplate.update("""
                        update inventories set reserved_quantity = reserved_quantity + 1, version = version + 1
                        where product_id = ? and version = ? and total_quantity - reserved_quantity > 0
                        """, productId, version) == 1) return true;
                retries.incrementAndGet();
            }
            return false;
        }), retries);
    }

    ExperimentResult runPessimisticLockScenario(int buyers, int stock) throws Exception {
        Long productId = createStockProduct(stock, "pessimistic");
        return runScenario("pessimistic-lock", buyers, productId, ignored -> transactionTemplate.execute(status -> {
            Map<String, Object> inventory = jdbcTemplate.queryForMap(
                    "select total_quantity, reserved_quantity from inventories where product_id = ? for update", productId);
            int available = ((Number) inventory.get("total_quantity")).intValue()
                    - ((Number) inventory.get("reserved_quantity")).intValue();
            if (available == 0) return false;
            return jdbcTemplate.update("update inventories set reserved_quantity = reserved_quantity + 1, version = version + 1 where product_id = ?", productId) == 1;
        }));
    }

    private ExperimentResult runScenario(String strategy, int buyers, Long productId, IntFunction<Boolean> reservation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(buyers);
        CountDownLatch ready = new CountDownLatch(buyers);
        CountDownLatch start = new CountDownLatch(1);
        long startedAt = System.nanoTime();
        try {
            List<Future<Boolean>> attempts = java.util.stream.IntStream.range(0, buyers)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("동시 실험 시작 시간이 초과되었습니다.");
                        return reservation.apply(index);
                    })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successes = (int) attempts.stream().filter(this::await).count();
            int retries = reservation instanceof RetryAwareReservation retryAware ? retryAware.retries().get() : 0;
            return new ExperimentResult(strategy, successes, buyers - successes, retries,
                    Duration.ofNanos(System.nanoTime() - startedAt));
        } finally {
            executor.shutdownNow();
        }
    }

    private ExperimentResult runScenario(String strategy, int buyers, Long productId, IntFunction<Boolean> reservation, AtomicInteger retries) throws Exception {
        return runScenario(strategy, buyers, productId, new RetryAwareReservation(reservation, retries));
    }

    private Long createStockProduct(int stock, String suffix) {
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create(suffix + "-seller@example.com", "encoded-password", "판매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "상점", "소개"));
            Product product = productRepository.save(Product.create(store, "잠금 비교 상품", "설명", 10_000L,
                    ProductSalesPolicy.STOCK_MANAGED, stock, stock));
            inventoryService.initialize(product, stock, seller.getId());
            return product.getId();
        });
    }

    private void assertExpected(ExperimentResult result, int stock, int buyers) {
        assertThat(result.successes()).isEqualTo(stock);
        assertThat(result.conflicts()).isEqualTo(buyers - stock);
        Integer available = jdbcTemplate.queryForObject("select total_quantity - reserved_quantity from inventories where product_id = (select id from products order by id desc limit 1)", Integer.class);
        assertThat(available).isZero();
    }

    private void printResult(ExperimentResult result) {
        System.out.printf("strategy=%s successes=%d conflicts=%d retries=%d elapsed=%dms%n",
                result.strategy(), result.successes(), result.conflicts(), result.retries(), result.elapsed().toMillis());
    }

    private boolean await(Future<Boolean> future) {
        try { return future.get(15, TimeUnit.SECONDS); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private record ExperimentResult(String strategy, int successes, int conflicts, int retries, Duration elapsed) { }

    private record RetryAwareReservation(IntFunction<Boolean> delegate, AtomicInteger retries) implements IntFunction<Boolean> {
        @Override public Boolean apply(int value) { return delegate.apply(value); }
    }
}
