package com.sweet.market.order;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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

class OrderQueryApiTest extends IntegrationTestSupport {

    @Test
    void 구매자는_자신의_주문_목록만_조회한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other@example.com", "password123", "otherBuyer");
        Long buyerProductId = createProduct(sellerToken, "MacBook Pro");
        Long otherBuyerProductId = createProduct(sellerToken, "iPhone 15 Pro");
        Long buyerOrderId = createOrder(buyerToken, buyerProductId);
        createOrder(otherBuyerToken, otherBuyerProductId);

        mockMvc.perform(get("/api/orders/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(buyerOrderId))
                .andExpect(jsonPath("$.data.content[0].productId").value(buyerProductId))
                .andExpect(jsonPath("$.data.content[0].productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.content[0].productPrice").value(2000000))
                .andExpect(jsonPath("$.data.content[0].sellerId").isNumber())
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.content[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.content[0].productStatus").value("RESERVED"))
                .andExpect(jsonPath("$.data.content[0].orderedAt").exists());
    }

    @Test
    void 구매자_주문_목록은_환불_요청_상태를_포함한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        createRefundRequest(buyerToken, orderId);

        mockMvc.perform(get("/api/orders/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(orderId))
                .andExpect(jsonPath("$.data.content[0].refundStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.content[0].refundRequestedAt").exists())
                .andExpect(jsonPath("$.data.content[0].refundHandledAt").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].refundRejectReason").doesNotExist());
    }

    @Test
    void 구매자는_자신의_주문_상세를_조회한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.buyerId").isNumber())
                .andExpect(jsonPath("$.data.buyerNickname").value("buyer"))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.sellerId").isNumber())
                .andExpect(jsonPath("$.data.sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.productPrice").value(2000000))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.productStatus").value("RESERVED"))
                .andExpect(jsonPath("$.data.orderedAt").exists())
                .andExpect(jsonPath("$.data.canceledAt").doesNotExist());
    }

    @Test
    void 구매자_주문_상세는_환불_요청_상태를_포함한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        createRefundRequest(buyerToken, orderId);

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.refundRequestedAt").exists())
                .andExpect(jsonPath("$.data.refundHandledAt").doesNotExist())
                .andExpect(jsonPath("$.data.refundRejectReason").doesNotExist());
    }

    @Test
    void 다른_사용자의_주문_상세는_조회할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other@example.com", "password123", "otherBuyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherBuyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
    }

    @Test
    void 주문_목록_조회는_JWT가_필요하다() throws Exception {
        mockMvc.perform(get("/api/orders/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
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
        Long uploadId = uploadImage(accessToken, title.replace(" ", "-").toLowerCase() + ".jpg");

        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
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
                                """.formatted(title, uploadId)))
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

    private void createRefundRequest(String accessToken, Long orderId) throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isCreated());
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
}
