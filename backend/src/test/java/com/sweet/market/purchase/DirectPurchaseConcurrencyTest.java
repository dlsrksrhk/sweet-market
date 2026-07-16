package com.sweet.market.purchase;

import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class DirectPurchaseConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 하나의_단품에_동시구매자는_한명만_성공한다() throws Exception {
        Long productId = transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "판매자"));
            Store store = storeRepository.save(Store.createPersonal(seller, "상점", "소개"));
            return productRepository.save(Product.create(store, "단품", "설명", 10_000L)).getId();
        });
        List<String> buyerTokens = List.of(
                accessToken("buyer-1@example.com", "구매자1"),
                accessToken("buyer-2@example.com", "구매자2")
        );
        ExecutorService executor = Executors.newFixedThreadPool(buyerTokens.size());
        CountDownLatch ready = new CountDownLatch(buyerTokens.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Integer>> futures = buyerTokens.stream()
                    .map(token -> executor.submit(postDirectOrderAfterBarrier(token, productId, ready, start)))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = futures.stream().map(this::getStatus).toList();

            assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
            assertThat(statuses).filteredOn(status -> status == 409).hasSize(buyerTokens.size() - 1);
        } finally {
            executor.shutdownNow();
        }
    }

    private String accessToken(String email, String nickname) {
        Member buyer = memberRepository.save(Member.create(email, "encoded-password", nickname));
        return jwtProvider.createAccessToken(buyer.getId(), buyer.getEmail(), buyer.getRole());
    }

    private Callable<Integer> postDirectOrderAfterBarrier(
            String token,
            Long productId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 주문 시작 시간이 초과되었습니다.");
            }
            return mockMvc.perform(post("/api/orders")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":%d}".formatted(productId)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
    }

    private int getStatus(Future<Integer> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
