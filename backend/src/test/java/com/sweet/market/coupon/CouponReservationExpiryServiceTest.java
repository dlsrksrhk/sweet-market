package com.sweet.market.coupon;

import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.coupon.application.CouponRedemptionService;
import com.sweet.market.coupon.scheduler.CouponReservationExpiryScheduler;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CouponReservationExpiryServiceTest extends IntegrationTestSupport {

    @Autowired
    private CouponRedemptionService couponRedemptionService;

    @Autowired
    private CouponReservationExpiryScheduler couponReservationExpiryScheduler;

    @Test
    void 쿠폰_예약_만료_스케줄러는_기본_60초_간격으로_활성화된다() throws Exception {
        assertThat(couponReservationExpiryScheduler).isNotNull();
        Scheduled scheduled = CouponReservationExpiryScheduler.class.getMethod("expireReservations")
                .getAnnotation(Scheduled.class);
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${market.coupon-reservation-expiry.fixed-delay-ms:60000}");
    }

    @Test
    void 만료_시각에_도달한_예약은_주문을_취소하고_재고를_복구한다() throws Exception {
        Checkout checkout = reserveCouponOrder();
        Instant expiresAt = jdbcTemplate.queryForObject(
                "select expires_at from coupon_reservations where order_id = ?", Instant.class, checkout.orderId()
        );

        couponRedemptionService.expireReservations(expiresAt);

        assertThat(statusOf("coupon_reservations", checkout.orderId(), "order_id")).isEqualTo("EXPIRED");
        assertThat(statusOf("orders", checkout.orderId(), "id")).isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_quantity from inventories where product_id = ?", Integer.class, checkout.productId()
        )).isZero();
        assertThat(statusOf("member_coupons", checkout.couponId(), "id")).isEqualTo("ISSUED");
    }

    @Test
    void 만료_시각_전에는_예약을_만료시키지_않는다() throws Exception {
        Checkout checkout = reserveCouponOrder();
        Instant expiresAt = jdbcTemplate.queryForObject(
                "select expires_at from coupon_reservations where order_id = ?", Instant.class, checkout.orderId()
        );

        couponRedemptionService.expireReservations(expiresAt.minusMillis(1));

        assertThat(statusOf("coupon_reservations", checkout.orderId(), "order_id")).isEqualTo("RESERVED");
        assertThat(statusOf("orders", checkout.orderId(), "id")).isEqualTo("CREATED");
        assertThat(jdbcTemplate.queryForObject(
                "select reserved_quantity from inventories where product_id = ?", Integer.class, checkout.productId()
        )).isEqualTo(1);
    }

    @Test
    void 이미_만료한_예약은_다시_실행해도_상태와_재고가_변하지_않는다() throws Exception {
        Checkout checkout = reserveCouponOrder();
        Instant expiresAt = jdbcTemplate.queryForObject(
                "select expires_at from coupon_reservations where order_id = ?", Instant.class, checkout.orderId()
        );

        couponRedemptionService.expireReservations(expiresAt);
        couponRedemptionService.expireReservations(expiresAt.plusSeconds(1));

        assertThat(statusOf("coupon_reservations", checkout.orderId(), "order_id")).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from inventory_adjustments where order_id = ? and change_type = 'RELEASE'", Integer.class, checkout.orderId()
        )).isEqualTo(1);
    }

    @Test
    void 소비된_예약은_만료_대상이_아니다() throws Exception {
        Checkout checkout = reserveCouponOrder();
        mockMvc.perform(post("/api/payments/{orderId}/approve", checkout.orderId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + checkout.buyerToken()))
                .andExpect(status().isOk());
        Instant expiresAt = jdbcTemplate.queryForObject(
                "select expires_at from coupon_reservations where order_id = ?", Instant.class, checkout.orderId()
        );

        couponRedemptionService.expireReservations(expiresAt.plusSeconds(1));

        assertThat(statusOf("coupon_reservations", checkout.orderId(), "order_id")).isEqualTo("CONSUMED");
        assertThat(statusOf("orders", checkout.orderId(), "id")).isEqualTo("PAID");
        assertThat(statusOf("member_coupons", checkout.couponId(), "id")).isEqualTo("USED");
    }

    private Checkout reserveCouponOrder() throws Exception {
        String sellerToken = signupAndLogin("expiry-seller-" + System.nanoTime() + "@example.com", "seller");
        String buyerEmail = "expiry-buyer-" + System.nanoTime() + "@example.com";
        String buyerToken = signupAndLogin(buyerEmail, "buyer");
        Long productId = createStockProduct(sellerToken);
        Long couponId = issueCoupon(buyerEmail);
        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"memberCouponId\":%d}".formatted(productId, couponId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Checkout(objectMapper.readTree(response).path("data").path("id").asLong(), productId, couponId, buyerToken);
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
        MockMultipartFile image = new MockMultipartFile("file", "expiry.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        String upload = mockMvc.perform(multipart("/api/product-image-uploads").file(image)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long uploadId = objectMapper.readTree(upload).path("data").path("id").asLong();
        String product = mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "storeId": %d, "title": "만료 재고 상품", "description": "설명", "price": 10000,
                                  "salesPolicy": "STOCK_MANAGED", "initialTotalQuantity": 1, "lowStockThreshold": 1,
                                  "images": [{ "uploadId": %d, "sortOrder": 0, "representative": true }] }
                                """.formatted(storeId, uploadId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(product).path("data").path("id").asLong();
    }

    private Long issueCoupon(String buyerEmail) {
        Long campaignId = jdbcTemplate.queryForObject("""
                insert into coupon_campaigns (version, owner_type, scope, discount_type, discount_value, max_discount_amount,
                    minimum_purchase_amount, stackable, title, issue_starts_at, issue_ends_at, validity_type, validity_days,
                    lifecycle_status, issued_count, created_at, updated_at)
                values (0, 'PLATFORM', 'ALL_PRODUCTS', 'FIXED_AMOUNT', 1000, null, 0, true, '만료 쿠폰',
                    current_timestamp - interval '1 day', current_timestamp + interval '1 day', 'DAYS_FROM_ISSUANCE', 7,
                    'ENDED', 0, current_timestamp, current_timestamp) returning id
                """, Long.class);
        Long memberId = jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, buyerEmail);
        return jdbcTemplate.queryForObject("""
                insert into member_coupons (member_id, coupon_campaign_id, issued_at, valid_until, discount_type, discount_value,
                    max_discount_amount, minimum_purchase_amount, scope, stackable, status)
                values (?, ?, current_timestamp, current_timestamp + interval '7 days', 'FIXED_AMOUNT', 1000,
                    null, 0, 'ALL_PRODUCTS', true, 'ISSUED') returning id
                """, Long.class, memberId, campaignId);
    }

    private String statusOf(String table, Long id, String idColumn) {
        return jdbcTemplate.queryForObject("select status from " + table + " where " + idColumn + " = ?", String.class, id);
    }

    private record Checkout(Long orderId, Long productId, Long couponId, String buyerToken) {
    }
}
