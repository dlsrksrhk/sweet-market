package com.sweet.market.review;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class ProductReviewApiTest extends IntegrationTestSupport {

    @Test
    void 상품_상세는_상품과_판매자_리뷰_요약을_포함한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other@example.com", "password123", "otherBuyer");
        Long firstProductId = createProduct(sellerToken, "MacBook Pro", "summary-first-product.jpg");
        Long secondProductId = createProduct(sellerToken, "iPad Pro", "summary-second-product.jpg");
        Long firstOrderId = createConfirmedOrder(buyerToken, firstProductId);
        Long secondOrderId = createConfirmedOrder(otherBuyerToken, secondProductId);

        createReview(buyerToken, firstOrderId, 5, "거래가 빠르고 상품 설명도 정확했어요.");
        createReview(otherBuyerToken, secondOrderId, 3, "상품 상태는 보통이지만 응대는 괜찮았어요.");

        mockMvc.perform(get("/api/products/{productId}", firstProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstProductId))
                .andExpect(jsonPath("$.data.reviewCount").value(1))
                .andExpect(jsonPath("$.data.averageRating").value(5.0))
                .andExpect(jsonPath("$.data.sellerReviewCount").value(2))
                .andExpect(jsonPath("$.data.sellerAverageRating").value(4.0));
    }

    @Test
    void 상품_리뷰는_최신순으로_조회된다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other@example.com", "password123", "otherBuyer");
        Long productId = createProduct(sellerToken, "MacBook Pro", "review-order-product.jpg");
        Long oldOrderId = createConfirmedOrder(buyerToken, productId);
        createReview(buyerToken, oldOrderId, 5, "오래된 리뷰로 정렬 확인에 사용합니다.");
        restoreProductOnSale(productId);
        Long recentOrderId = createConfirmedOrder(otherBuyerToken, productId);
        createReview(otherBuyerToken, recentOrderId, 4, "최신 리뷰로 정렬 확인에 사용합니다.");
        updateReviewCreatedAt(oldOrderId, "2026-01-01 10:00:00");
        updateReviewCreatedAt(recentOrderId, "2026-01-02 10:00:00");

        mockMvc.perform(get("/api/products/{productId}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].orderId").value(recentOrderId))
                .andExpect(jsonPath("$.data.content[0].content").value("최신 리뷰로 정렬 확인에 사용합니다."))
                .andExpect(jsonPath("$.data.content[1].orderId").value(oldOrderId))
                .andExpect(jsonPath("$.data.content[1].content").value("오래된 리뷰로 정렬 확인에 사용합니다."));
    }

    @Test
    void 상품_리뷰는_페이지로_조회된다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other@example.com", "password123", "otherBuyer");
        Long productId = createProduct(sellerToken, "MacBook Pro", "review-page-product.jpg");
        Long firstOrderId = createConfirmedOrder(buyerToken, productId);
        createReview(buyerToken, firstOrderId, 5, "첫 번째 페이지 테스트 리뷰입니다.");
        restoreProductOnSale(productId);
        Long secondOrderId = createConfirmedOrder(otherBuyerToken, productId);
        createReview(otherBuyerToken, secondOrderId, 4, "두 번째 페이지 테스트 리뷰입니다.");

        mockMvc.perform(get("/api/products/{productId}/reviews", productId)
                        .param("size", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void 판매완료_상품도_상세와_리뷰를_조회할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro", "sold-out-visible-product.jpg");
        Long orderId = createConfirmedOrder(buyerToken, productId);
        createReview(buyerToken, orderId, 5, "판매완료 상품에도 리뷰가 공개됩니다.");

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/products/{productId}/reviews", productId))
                .andExpect(status().isOk());
    }

    @Test
    void 숨김_상품은_상세와_리뷰를_공개_조회할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(sellerToken, "MacBook Pro", "hidden-product.jpg");

        mockMvc.perform(delete("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(get("/api/products/{productId}/reviews", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 상품_목록은_판매완료_상품을_포함하지_않는다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long onSaleProductId = createProduct(sellerToken, "On Sale MacBook Pro", "on-sale-product.jpg");
        Long soldOutProductId = createProduct(sellerToken, "Sold Out MacBook Pro", "sold-out-product.jpg");
        createConfirmedOrder(buyerToken, soldOutProductId);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(onSaleProductId));
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

    private Long createProduct(String accessToken, String title, String fileName) throws Exception {
        Long storeId = activePersonalStoreId(accessToken);
        Long uploadId = uploadImage(accessToken, fileName);

        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "%s",
                                  "description": "M3 laptop",
                                  "price": 2000000,
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

    private Long createConfirmedOrder(String accessToken, Long productId) throws Exception {
        Long orderId = createOrder(accessToken, productId);
        approvePayment(accessToken, orderId);
        startDelivery(accessToken, orderId);
        completeDelivery(accessToken, orderId);

        mockMvc.perform(post("/api/orders/{orderId}/confirm", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        return orderId;
    }

    private void createReview(String accessToken, Long orderId, int rating, String content) throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/review", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": %d,
                                  "content": "%s"
                                }
                                """.formatted(rating, content)))
                .andExpect(status().isCreated());
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

    private void restoreProductOnSale(Long productId) {
        jdbcTemplate.update(
                "UPDATE products SET status = 'ON_SALE', version = version + 1 WHERE id = ?",
                productId
        );
    }

    private void updateReviewCreatedAt(Long orderId, String createdAt) {
        jdbcTemplate.update(
                "UPDATE reviews SET created_at = ?::timestamp WHERE order_id = ?",
                createdAt,
                orderId
        );
    }
}
