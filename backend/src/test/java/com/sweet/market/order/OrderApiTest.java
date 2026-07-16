package com.sweet.market.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.payment.application.PaymentGateway;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderApiTest extends IntegrationTestSupport {

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    @Test
    void 주문_생성에_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.buyerNickname").value("buyer"))
                .andExpect(jsonPath("$.data.sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.productStatus").value("RESERVED"))
                .andExpect(jsonPath("$.data.orderedAt").exists())
                .andExpect(jsonPath("$.data.canceledAt").doesNotExist());

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
    }

    @Test
    void 주문_생성은_JWT가_필요하다() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 존재하지_않는_상품은_주문할_수_없다() throws Exception {
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 999
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 판매중이_아닌_상품은_주문할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                                {
                                                  "productId": %d
                                                }
                                """.formatted(productId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_ON_SALE"))
                .andExpect(jsonPath("$.message").value("판매 중인 상품만 주문할 수 있습니다."));
    }

    @Test
    void 직접_주문은_현재_프로모션_가격을_스냅샷으로_저장한다() throws Exception {
        String sellerToken = signupAndLogin("promotion-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("promotion-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long promotionId = createStoreWidePromotion(productId, 500_000L);

        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productPrice").value(1_500_000L))
                .andExpect(jsonPath("$.data.listPrice").value(2_000_000L))
                .andExpect(jsonPath("$.data.promotionCampaignId").value(promotionId))
                .andExpect(jsonPath("$.data.promotionDiscountAmount").value(500_000L))
                .andExpect(jsonPath("$.data.finalPrice").value(1_500_000L))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(response).path("data").path("id").asLong();

        assertThat(jdbcTemplate.queryForObject("select list_price from orders where id = ?", Long.class, orderId))
                .isEqualTo(2_000_000L);
        assertThat(jdbcTemplate.queryForObject("select promotion_campaign_id from orders where id = ?", Long.class, orderId))
                .isEqualTo(promotionId);
        assertThat(jdbcTemplate.queryForObject("select promotion_discount_amount from orders where id = ?", Long.class, orderId))
                .isEqualTo(500_000L);
        assertThat(jdbcTemplate.queryForObject("select final_price from orders where id = ?", Long.class, orderId))
                .isEqualTo(1_500_000L);
    }

    @Test
    void 쿠폰을_선택한_주문은_프로모션_후_쿠폰_할인과_예약을_스냅샷으로_저장한다() throws Exception {
        String sellerToken = signupAndLogin("coupon-order-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-order-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, 10_000L);
        createStoreWidePromotion(productId, 1_000L);
        Long couponId = issueFixedAmountCoupon("coupon-order-buyer@example.com", 1_000L);

        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": %d, "memberCouponId": %d }
                                """.formatted(productId, couponId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberCouponId").value(couponId))
                .andExpect(jsonPath("$.data.couponDiscountAmount").value(1_000L))
                .andExpect(jsonPath("$.data.finalPrice").value(8_000L))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(response).path("data").path("id").asLong();

        assertThat(jdbcTemplate.queryForObject("select member_coupon_id from orders where id = ?", Long.class, orderId))
                .isEqualTo(couponId);
        assertThat(jdbcTemplate.queryForObject("select coupon_discount_amount from orders where id = ?", Long.class, orderId))
                .isEqualTo(1_000L);
        assertThat(jdbcTemplate.queryForObject("select status from coupon_reservations where order_id = ?", String.class, orderId))
                .isEqualTo("RESERVED");
        assertThat(jdbcTemplate.queryForObject("select extract(epoch from expires_at - reserved_at) from coupon_reservations where order_id = ?", Long.class, orderId))
                .isEqualTo(1_800L);
    }

    @Test
    void 선택한_쿠폰은_발급_시점_할인_정책을_주문에_적용한다() throws Exception {
        String sellerToken = signupAndLogin("coupon-snapshot-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-snapshot-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, 10_000L);
        Long couponId = issueFixedAmountCoupon("coupon-snapshot-buyer@example.com", 1_000L);
        jdbcTemplate.update("update coupon_campaigns set discount_value = 9_000 where id = (select coupon_campaign_id from member_coupons where id = ?)", couponId);

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"memberCouponId\":%d}".formatted(productId, couponId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.couponDiscountAmount").value(1_000L))
                .andExpect(jsonPath("$.data.finalPrice").value(9_000L));
    }

    @Test
    void 사용했거나_예약된_쿠폰을_선택하면_쿠폰_오류를_반환한다() throws Exception {
        String sellerToken = signupAndLogin("coupon-error-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-error-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, 10_000L);
        Long usedCouponId = issueFixedAmountCoupon("coupon-error-buyer@example.com", 1_000L);
        Long reservedCouponId = issueFixedAmountCoupon("coupon-error-buyer@example.com", 2_000L);
        jdbcTemplate.update("update member_coupons set status = 'USED' where id = ?", usedCouponId);
        Long existingOrderId = createOrder(buyerToken, createProduct(sellerToken, 10_000L));
        jdbcTemplate.update("""
                insert into coupon_reservations (member_coupon_id, order_id, status, reserved_at, expires_at)
                values (?, ?, 'RESERVED', current_timestamp, current_timestamp + interval '30 minutes')
                """, reservedCouponId, existingOrderId);

        assertCouponOrderError(buyerToken, productId, usedCouponId, "MEMBER_COUPON_NOT_ISSUED");
        assertCouponOrderError(buyerToken, productId, reservedCouponId, "MEMBER_COUPON_ALREADY_RESERVED");
    }

    @Test
    void 주문자는_주문_취소에_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.data.canceledAt").exists());

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    @Test
    void 예약_상품은_판매자가_숨길_수_없고_주문자는_취소할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(delete("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CHANGE_NOT_ALLOWED"));

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"));
    }

    @Test
    void 주문자가_아니면_주문_취소에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
    }

    @Test
    void 주문자는_결제된_주문을_주문_API로_즉시_취소한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);
        approvePayment(buyerToken, orderId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.data.canceledAt").exists());

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));

        String paymentStatus = jdbcTemplate.queryForObject(
                "select status from payments where order_id = ?",
                String.class,
                orderId
        );
        Object paymentCanceledAt = jdbcTemplate.queryForObject(
                "select canceled_at from payments where order_id = ?",
                Object.class,
                orderId
        );
        assertThat(paymentStatus).isEqualTo("CANCELED");
        assertThat(paymentCanceledAt).isNotNull();
    }

    @Test
    void 주문_API로_취소된_결제는_결제_API로_다시_취소해도_외부_취소를_반복하지_않는다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);
        approvePayment(buyerToken, orderId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/payments/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELED"));

        verify(paymentGateway, times(1)).cancel("fake-payment-" + orderId);
    }

    @Test
    void 결제된_주문에_결제가_없으면_주문_API_취소에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);
        jdbcTemplate.update("update orders set status = 'PAID' where id = ?", orderId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void 이미_취소한_주문은_다시_취소해도_같은_결과를_반환한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"));
    }

    private String signupAndLogin(String email, String password, String nickname) throws Exception {
        SignupRequest signupRequest = new SignupRequest(email, password, nickname);
        LoginRequest loginRequest = new LoginRequest(email, password);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupRequest)))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }

    private Long createProduct(String accessToken) throws Exception {
        return createProduct(accessToken, 2_000_000L);
    }

    @Test
    void 결제_전_쿠폰_주문을_취소하면_예약을_해제하고_쿠폰과_재고를_복구한다() throws Exception {
        String sellerToken = signupAndLogin("coupon-cancel-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-cancel-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, 10_000L);
        Long couponId = issueFixedAmountCoupon("coupon-cancel-buyer@example.com", 1_000L);
        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"memberCouponId\":%d}".formatted(productId, couponId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        assertThat(jdbcTemplate.queryForObject(
                "select status from coupon_reservations where order_id = ?", String.class, orderId
        )).isEqualTo("RELEASED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from member_coupons where id = ?", String.class, couponId
        )).isEqualTo("ISSUED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from products where id = ?", String.class, productId
        )).isEqualTo("ON_SALE");
    }

    private Long createProduct(String accessToken, long price) throws Exception {
        Long storeId = activePersonalStoreId(accessToken);
        Long uploadId = uploadImage(accessToken, "macbook-1.jpg");

        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": %d,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(storeId, price, uploadId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private Long createStoreWidePromotion(Long productId, long discountAmount) {
        Long storeId = jdbcTemplate.queryForObject("select store_id from products where id = ?", Long.class, productId);
        jdbcTemplate.update("update stores set type = 'BUSINESS' where id = ?", storeId);
        return jdbcTemplate.queryForObject("""
                insert into promotion_campaigns (
                    version, store_id, scope, discount_type, discount_value, priority, title,
                    start_at, end_at, lifecycle_status, created_at, updated_at
                ) values (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', ?, 10, '주문 할인',
                    current_timestamp - interval '1 minute', current_timestamp + interval '1 minute', 'DRAFT',
                    current_timestamp, current_timestamp)
                returning id
                """, Long.class, storeId, discountAmount);
    }

    private Long uploadImage(String accessToken, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        String response = mockMvc.perform(multipart("/api/product-image-uploads")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private Long createOrder(String accessToken, Long productId) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private Long issueFixedAmountCoupon(String buyerEmail, long discountAmount) {
        Long campaignId = jdbcTemplate.queryForObject("""
                insert into coupon_campaigns (
                    version, owner_type, scope, discount_type, discount_value, max_discount_amount,
                    minimum_purchase_amount, stackable, title, issue_starts_at, issue_ends_at,
                    validity_type, validity_days, lifecycle_status, issued_count, created_at, updated_at
                ) values (
                    0, 'PLATFORM', 'ALL_PRODUCTS', 'FIXED_AMOUNT', ?, null,
                    0, true, '주문 쿠폰', current_timestamp - interval '1 day', current_timestamp + interval '1 day',
                    'DAYS_FROM_ISSUANCE', 7, 'ENDED', 0, current_timestamp, current_timestamp
                ) returning id
                """, Long.class, discountAmount);
        Long buyerId = jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, buyerEmail);
        return jdbcTemplate.queryForObject("""
                insert into member_coupons (
                    member_id, coupon_campaign_id, issued_at, valid_until, discount_type, discount_value,
                    max_discount_amount, minimum_purchase_amount, scope, stackable, status
                ) values (
                    ?, ?, current_timestamp, current_timestamp + interval '7 days', 'FIXED_AMOUNT', ?,
                    null, 0, 'ALL_PRODUCTS', true, 'ISSUED'
                ) returning id
                """, Long.class, buyerId, campaignId, discountAmount);
    }

    private void assertCouponOrderError(String accessToken, Long productId, Long couponId, String errorCode) throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"memberCouponId\":%d}".formatted(productId, couponId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(errorCode));
    }

    private Long approvePayment(String accessToken, Long orderId) throws Exception {
        String response = mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }
}
