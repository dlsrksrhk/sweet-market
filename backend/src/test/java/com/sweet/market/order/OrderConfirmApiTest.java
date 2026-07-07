package com.sweet.market.order;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
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

class OrderConfirmApiTest extends IntegrationTestSupport {

    @Test
    void 주문자는_배송완료_주문_구매확정에_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createDeliveredOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/confirm", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.productStatus").value("SOLD_OUT"));
    }

    @Test
    void 배송완료가_아닌_주문은_구매확정할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);
        approvePayment(buyerToken, orderId);

        mockMvc.perform(post("/api/orders/{orderId}/confirm", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CONFIRM_NOT_ALLOWED"));
    }

    @Test
    void 환불_요청된_주문은_구매확정할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createDeliveredOrder(buyerToken, productId);
        createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/orders/{orderId}/confirm", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CONFIRM_NOT_ALLOWED"));
    }

    @Test
    void 주문자가_아니면_구매확정에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken);
        Long orderId = createDeliveredOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/confirm", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
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
        Long uploadId = uploadImage(accessToken, "macbook-1.jpg");

        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
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
                                """.formatted(uploadId)))
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

    private Long createDeliveredOrder(String accessToken, Long productId) throws Exception {
        Long orderId = createOrder(accessToken, productId);
        approvePayment(accessToken, orderId);
        startDelivery(accessToken, orderId);
        completeDelivery(accessToken, orderId);
        return orderId;
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
}
