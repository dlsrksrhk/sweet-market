package com.sweet.market.productview;

import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.productview.application.ProductViewRecordingService;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductViewApiTest extends IntegrationTestSupport {

    @Autowired
    private ProductViewRecordingService productViewRecordingService;

    @Test
    void 익명_상세_조회는_방문자_쿠키를_발급한다() throws Exception {
        Long productId = createBuyerVisibleProduct("view-cookie@example.com", "view-cookie");

        mockMvc.perform(post("/api/products/{productId}/views", productId))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("sm_visitor=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=604800")));

        assertThat(viewCount(productId)).isEqualTo(1);
    }

    @Test
    void 방문자_쿠키는_HttpOnly_속성을_포함한다() throws Exception {
        Long productId = createBuyerVisibleProduct("view-cookie-http-only@example.com", "view-cookie-http-only");

        mockMvc.perform(post("/api/products/{productId}/views", productId))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    void 숨김_상품_조회는_기존_공개_오류를_반환하고_이벤트를_기록하지_않는다() throws Exception {
        Long productId = createBuyerVisibleProduct("view-hidden@example.com", "view-hidden");
        jdbcTemplate.update("update products set status = 'HIDDEN' where id = ?", productId);

        mockMvc.perform(post("/api/products/{productId}/views", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        assertThat(viewCount(productId)).isZero();
    }

    @Test
    void 같은_방문자의_삼초_이내_조회는_한번만_기록한다() throws Exception {
        Long productId = createBuyerVisibleProduct("view-deduplication@example.com", "view-deduplication");
        Instant viewedAt = Instant.parse("2026-07-16T00:00:00Z");

        productViewRecordingService.record(productId, "same-visitor", viewedAt);
        productViewRecordingService.record(productId, "same-visitor", viewedAt.plusSeconds(2));

        assertThat(viewCount(productId)).isEqualTo(1);
    }

    @Test
    void 동시에_기록해도_한개의_조회_이벤트만_생성한다() throws Exception {
        Long productId = createBuyerVisibleProduct("view-concurrent@example.com", "view-concurrent");
        Instant viewedAt = Instant.parse("2026-07-16T00:00:00Z");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> recordAfterStart(productId, viewedAt, ready, start));
            Future<?> second = executor.submit(() -> recordAfterStart(productId, viewedAt, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(viewCount(productId)).isEqualTo(1);
    }

    private Long createBuyerVisibleProduct(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, "password123", nickname))))
                .andExpect(status().isCreated());

        Long storeId = jdbcTemplate.queryForObject(
                "select id from stores where owner_member_id = (select id from members where email = ?)",
                Long.class,
                email
        );
        return jdbcTemplate.queryForObject("""
                insert into products (version, store_id, title, description, price, category, status, sales_policy)
                values (0, ?, '조회 상품', '조회 기록 테스트 상품', 1000, 'OTHER', 'ON_SALE', 'SINGLE_ITEM')
                returning id
                """, Long.class, storeId);
    }

    private long viewCount(Long productId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from product_view_events where product_id = ?",
                Long.class,
                productId
        );
    }

    private void recordAfterStart(Long productId, Instant viewedAt, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            productViewRecordingService.record(productId, "concurrent-visitor", viewedAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
