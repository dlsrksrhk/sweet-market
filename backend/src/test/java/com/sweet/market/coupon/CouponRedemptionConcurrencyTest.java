package com.sweet.market.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class CouponRedemptionConcurrencyTest extends IntegrationTestSupport {

    @Test
    void 같은_쿠폰으로_동시_주문하면_한건만_예약하고_재고도_한건만_예약한다() throws Exception {
        String sellerToken = signupAndLogin("concurrent-order-seller@example.com", "seller");
        String buyerToken = signupAndLogin("concurrent-order-buyer@example.com", "buyer");
        Long productId = createStockProduct(sellerToken);
        Long couponId = issueCoupon("concurrent-order-buyer@example.com");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<OrderAttempt>> attempts = List.of(
                    executor.submit(() -> submitOrder(buyerToken, productId, couponId, ready, start)),
                    executor.submit(() -> submitOrder(buyerToken, productId, couponId, ready, start))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<OrderAttempt> results = attempts.stream().map(this::await).toList();

            assertThat(results).filteredOn(result -> result.status() == 201).hasSize(1);
            assertThat(results).filteredOn(result -> result.status() == 409
                    && "MEMBER_COUPON_ALREADY_RESERVED".equals(result.code())).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("select count(*) from coupon_reservations where member_coupon_id = ? and status = 'RESERVED'", Integer.class, couponId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders where member_coupon_id = ?", Integer.class, couponId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select reserved_quantity from inventories where product_id = ?", Integer.class, productId))
                .isEqualTo(1);
    }

    private OrderAttempt submitOrder(String token, Long productId, Long couponId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("동시 주문 시작 시간이 초과되었습니다.");
        }
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"memberCouponId\":%d}".formatted(productId, couponId)))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new OrderAttempt(result.getResponse().getStatus(), objectMapper.readTree(body).path("code").asText(null));
    }

    private OrderAttempt await(Future<OrderAttempt> attempt) {
        try {
            return attempt.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String signupAndLogin(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, "password123", nickname))))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private Long createStockProduct(String sellerToken) throws Exception {
        Long storeId = activePersonalStoreId(sellerToken);
        jdbcTemplate.update("update stores set type = 'BUSINESS' where id = ?", storeId);
        Long uploadId = uploadImage(sellerToken);
        String response = mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "storeId": %d, "title": "동시 주문 재고 상품", "description": "설명", "price": 10000,
                                  "salesPolicy": "STOCK_MANAGED", "initialTotalQuantity": 2, "lowStockThreshold": 1,
                                  "images": [{ "uploadId": %d, "sortOrder": 0, "representative": true }] }
                                """.formatted(storeId, uploadId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long uploadImage(String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "concurrent.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        String response = mockMvc.perform(multipart("/api/product-image-uploads").file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long issueCoupon(String buyerEmail) {
        Long campaignId = jdbcTemplate.queryForObject("""
                insert into coupon_campaigns (
                    version, owner_type, scope, discount_type, discount_value, max_discount_amount,
                    minimum_purchase_amount, stackable, title, issue_starts_at, issue_ends_at,
                    validity_type, validity_days, lifecycle_status, issued_count, created_at, updated_at
                ) values (0, 'PLATFORM', 'ALL_PRODUCTS', 'FIXED_AMOUNT', 1000, null,
                    0, true, '동시 주문 쿠폰', current_timestamp - interval '1 day', current_timestamp + interval '1 day',
                    'DAYS_FROM_ISSUANCE', 7, 'ENDED', 0, current_timestamp, current_timestamp) returning id
                """, Long.class);
        Long buyerId = jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, buyerEmail);
        return jdbcTemplate.queryForObject("""
                insert into member_coupons (member_id, coupon_campaign_id, issued_at, valid_until, discount_type,
                    discount_value, max_discount_amount, minimum_purchase_amount, scope, stackable, status)
                values (?, ?, current_timestamp, current_timestamp + interval '7 days', 'FIXED_AMOUNT',
                    1000, null, 0, 'ALL_PRODUCTS', true, 'ISSUED') returning id
                """, Long.class, buyerId, campaignId);
    }

    private record OrderAttempt(int status, String code) {
    }
}
