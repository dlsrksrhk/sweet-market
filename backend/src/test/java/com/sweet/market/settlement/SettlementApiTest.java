package com.sweet.market.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettlementApiTest extends IntegrationTestSupport {

    @Test
    void 판매자는_확정된_주문_정산에_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createConfirmedOrder(buyerToken, productId);

        mockMvc.perform(post("/api/settlements/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.amount").value(2_000_000))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.settledAt").exists());
    }

    @Test
    void 쿠폰_할인_주문의_정산은_쿠폰_스냅샷을_반환한다() throws Exception {
        String sellerToken = signupAndLogin("coupon-settlement-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-settlement-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "쿠폰 정산 상품");
        Long orderId = createConfirmedOrder(buyerToken, productId);
        jdbcTemplate.update("update orders set member_coupon_id = ?, coupon_discount_amount = ?, final_price = ? where id = ?",
                9_999L, 1_000L, 1_999_000L, orderId);

        mockMvc.perform(post("/api/settlements/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberCouponId").value(9_999L))
                .andExpect(jsonPath("$.data.couponDiscountAmount").value(1_000L));
    }

    @Test
    void 프로모션_종료와_상품가격_변경_후에도_정산은_주문_최종가격을_사용한다() throws Exception {
        String sellerToken = signupAndLogin("snapshot-settlement-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("snapshot-settlement-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "프로모션 정산 상품");
        Long promotionId = createStoreWidePromotion(productId, 500_000L);
        Long orderId = createOrder(buyerToken, productId);

        jdbcTemplate.update("update products set price = ? where id = ?", 300_000L, productId);
        jdbcTemplate.update("update promotion_campaigns set lifecycle_status = 'ENDED' where id = ?", promotionId);
        approvePayment(buyerToken, orderId);
        startDelivery(buyerToken, orderId);
        completeDelivery(buyerToken, orderId);
        mockMvc.perform(post("/api/orders/{orderId}/confirm", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/settlements/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(1_500_000L));

        mockMvc.perform(get("/api/settlements/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderId").value(orderId))
                .andExpect(jsonPath("$.data[0].amount").value(1_500_000L));
    }

    @Test
    void 확정되지_않은_주문은_정산할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);

        mockMvc.perform(post("/api/settlements/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_CREATE_NOT_ALLOWED"));
    }

    @Test
    void 환불_요청된_주문은_정산할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/settlements/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_CREATE_NOT_ALLOWED"));
    }

    @Test
    void 환불_완료된_주문은_정산할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);
        approveRefundRequest(sellerToken, refundRequestId);

        mockMvc.perform(post("/api/settlements/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_CREATE_NOT_ALLOWED"));
    }

    @Test
    void 같은_주문은_중복_정산할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createConfirmedOrder(buyerToken, productId);
        createSettlement(sellerToken, orderId);

        mockMvc.perform(post("/api/settlements/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SETTLEMENT"));
    }

    @Test
    void 판매자가_아니면_정산_생성에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createConfirmedOrder(buyerToken, productId);

        mockMvc.perform(post("/api/settlements/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ACCESS_DENIED"));
    }

    @Test
    void 판매자는_자신의_정산_목록만_조회한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String otherSellerToken = signupAndLogin("other-seller@example.com", "password123", "otherSeller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-buyer@example.com", "password123", "otherBuyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long otherProductId = createProduct(otherSellerToken, "iPhone");
        Long orderId = createConfirmedOrder(buyerToken, productId);
        Long otherOrderId = createConfirmedOrder(otherBuyerToken, otherProductId);
        createSettlement(sellerToken, orderId);
        createSettlement(otherSellerToken, otherOrderId);

        mockMvc.perform(get("/api/settlements/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].orderId").value(orderId))
                .andExpect(jsonPath("$.data[0].sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data[0].productTitle").value("MacBook Pro"));
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

    private Long createProduct(String accessToken, String title) throws Exception {
        Long storeId = activePersonalStoreId(accessToken);
        Long uploadId = uploadImage(accessToken, title.replace(" ", "-").toLowerCase() + ".jpg");

        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "%s",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(storeId, title, uploadId)))
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
                ) values (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', ?, 10, '정산 스냅샷 할인',
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
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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

    private Long createConfirmedOrder(String accessToken, Long productId) throws Exception {
        Long orderId = createDeliveredOrder(accessToken, productId);
        mockMvc.perform(post("/api/orders/{orderId}/confirm", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        return orderId;
    }

    private Long createDeliveredOrder(String accessToken, Long productId) throws Exception {
        Long orderId = createOrder(accessToken, productId);
        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/{orderId}/start", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/{orderId}/complete", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        return orderId;
    }

    private void approvePayment(String accessToken, Long orderId) throws Exception {
        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private void startDelivery(String accessToken, Long orderId) throws Exception {
        mockMvc.perform(post("/api/deliveries/{orderId}/start", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private void completeDelivery(String accessToken, Long orderId) throws Exception {
        mockMvc.perform(post("/api/deliveries/{orderId}/complete", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private Long createRefundRequest(String accessToken, Long orderId) throws Exception {
        String response = mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private void approveRefundRequest(String accessToken, Long refundRequestId) throws Exception {
        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/approve", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private Long createSettlement(String accessToken, Long orderId) throws Exception {
        String response = mockMvc.perform(post("/api/settlements/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }
}
