package com.sweet.market.purchase;

import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.cart.domain.CartItem;
import com.sweet.market.cart.repository.CartItemRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class CartPurchaseConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 하나의_재고_상품에_동시_장바구니_체크아웃은_한명만_성공한다() throws Exception {
        Long productId = transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "판매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "상점", "소개"));
            Product product = productRepository.save(Product.create(
                    store, "재고 상품", "설명", 10_000L, ProductSalesPolicy.STOCK_MANAGED, 1, 1
            ));
            inventoryService.initialize(product, 1, seller.getId());
            return product.getId();
        });
        List<Buyer> buyers = List.of(
                createBuyerWithCart("buyer-1@example.com", "구매자1", productId),
                createBuyerWithCart("buyer-2@example.com", "구매자2", productId)
        );
        ExecutorService executor = Executors.newFixedThreadPool(buyers.size());
        CountDownLatch ready = new CountDownLatch(buyers.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<CheckoutResult>> futures = buyers.stream()
                    .map(buyer -> executor.submit(checkoutAfterBarrier(buyer, ready, start)))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<CheckoutResult> results = futures.stream().map(this::getResult).toList();

            assertThat(results).extracting(CheckoutResult::status).containsExactlyInAnyOrder(200, 409);
            assertThat(results).filteredOn(result -> result.status() == 409)
                    .allSatisfy(result -> assertThat(result.content()).contains("CART_CHECKOUT_NOT_ALLOWED", "SOLD_OUT"));
        } finally {
            executor.shutdownNow();
        }
    }

    private Buyer createBuyerWithCart(String email, String nickname, Long productId) {
        return transactionTemplate.execute(status -> {
            Member buyer = memberRepository.save(Member.create(email, "encoded-password", nickname));
            Product product = productRepository.findById(productId).orElseThrow();
            CartItem cartItem = cartItemRepository.save(CartItem.create(buyer, product));
            String accessToken = jwtProvider.createAccessToken(buyer.getId(), buyer.getEmail(), buyer.getRole());
            return new Buyer(accessToken, cartItem.getId());
        });
    }

    private Callable<CheckoutResult> checkoutAfterBarrier(Buyer buyer, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 장바구니 체크아웃 시작 시간이 초과되었습니다.");
            }
            MvcResult result = mockMvc.perform(post("/api/me/cart/checkout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyer.accessToken())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cartItemIds\":[%d]}".formatted(buyer.cartItemId())))
                    .andReturn();
            return new CheckoutResult(result.getResponse().getStatus(), result.getResponse().getContentAsString());
        };
    }

    private CheckoutResult getResult(Future<CheckoutResult> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Buyer(String accessToken, Long cartItemId) {
    }

    private record CheckoutResult(int status, String content) {
    }
}
