package com.sweet.market.wishlist;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
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

class WishlistApiTest extends IntegrationTestSupport {

    @Test
    void 판매중_상품을_찜할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.wishlisted").value(true))
                .andExpect(jsonPath("$.data.wishlistCount").value(1));
    }

    @Test
    void 같은_상품을_두_번_찜해도_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wishlisted").value(true))
                .andExpect(jsonPath("$.data.wishlistCount").value(1));

        mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.wishlisted").value(true))
                .andExpect(jsonPath("$.data.wishlistCount").value(1));

        assertThat(countWishlistItems(productId)).isEqualTo(1);
    }

    @Test
    void 찜을_해제할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.wishlisted").value(false))
                .andExpect(jsonPath("$.data.wishlistCount").value(0));

        assertThat(countWishlistItems(productId)).isZero();
    }

    @Test
    void 없는_찜을_해제해도_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(delete("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.wishlisted").value(false))
                .andExpect(jsonPath("$.data.wishlistCount").value(0));
    }

    @Test
    void 자기_상품은_찜할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WISHLIST_OWN_PRODUCT_NOT_ALLOWED"));
    }

    @Test
    void 판매중이_아닌_상품은_새로_찜할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(delete("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WISHLIST_PRODUCT_NOT_ON_SALE"));
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

    private long countWishlistItems(Long productId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wishlist_items WHERE product_id = ?",
                Long.class,
                productId
        );
        return count == null ? 0 : count;
    }
}
