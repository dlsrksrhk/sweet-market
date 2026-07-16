package com.sweet.market.purchase;

import com.sweet.market.auth.security.JwtProvider;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class PurchaseReservationConcurrencyTest extends IntegrationTestSupport {

    @Autowired private MemberRepository memberRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryService inventoryService;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void 재고보다_많은_동시구매에서_성공수는_가용수량과_같다() throws Exception {
        Long productId = createStockProduct(3);
        PurchaseAttemptResult result = runConcurrentPurchases(productId, 10);

        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.conflictCount()).isEqualTo(7);
        assertThat(availableQuantity(result.productId())).isZero();
    }

    private Long createStockProduct(int stock) {
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create("race-seller@example.com", "encoded-password", "판매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "상점", "소개"));
            Product product = productRepository.save(Product.create(
                    store, "동시 구매 재고 상품", "설명", 10_000L, ProductSalesPolicy.STOCK_MANAGED, stock, stock
            ));
            inventoryService.initialize(product, stock, seller.getId());
            return product.getId();
        });
    }

    private PurchaseAttemptResult runConcurrentPurchases(Long productId, int buyers) throws Exception {
        List<String> tokens = java.util.stream.IntStream.range(0, buyers)
                .mapToObj(index -> accessToken("race-buyer-" + index + "@example.com", "구매자" + index))
                .toList();
        ExecutorService executor = Executors.newFixedThreadPool(buyers);
        CountDownLatch ready = new CountDownLatch(buyers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> attempts = tokens.stream()
                    .map(token -> executor.submit(() -> purchaseAfterBarrier(token, productId, ready, start)))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = attempts.stream().map(this::await).toList();
            return new PurchaseAttemptResult(productId,
                    (int) statuses.stream().filter(status -> status == 201).count(),
                    (int) statuses.stream().filter(status -> status == 409).count());
        } finally {
            executor.shutdownNow();
        }
    }

    private int purchaseAfterBarrier(String token, Long productId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("동시 구매 시작 시간이 초과되었습니다.");
        return mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d}".formatted(productId)))
                .andReturn().getResponse().getStatus();
    }

    private String accessToken(String email, String nickname) {
        Member buyer = memberRepository.save(Member.create(email, "encoded-password", nickname));
        return jwtProvider.createAccessToken(buyer.getId(), buyer.getEmail(), buyer.getRole());
    }

    private int availableQuantity(Long productId) {
        return jdbcTemplate.queryForObject("select total_quantity - reserved_quantity from inventories where product_id = ?", Integer.class, productId);
    }

    private int await(Future<Integer> attempt) {
        try { return attempt.get(15, TimeUnit.SECONDS); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private record PurchaseAttemptResult(Long productId, int successCount, int conflictCount) { }
}
