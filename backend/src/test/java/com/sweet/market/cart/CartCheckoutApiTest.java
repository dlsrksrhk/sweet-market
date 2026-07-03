package com.sweet.market.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

class CartCheckoutApiTest extends IntegrationTestSupport {

    @Test
    void 선택한_장바구니_항목을_상품별_주문으로_전환한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long keyboardId = createProduct(sellerToken, "Keyboard", "keyboard.jpg");
        Long mouseId = createProduct(sellerToken, "Mouse", "mouse.jpg");

        addCart(buyerToken, keyboardId);
        addCart(buyerToken, mouseId);
        Long keyboardCartItemId = findCartItemId(buyerId, keyboardId);
        Long mouseCartItemId = findCartItemId(buyerId, mouseId);

        mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d, %d]
                                }
                                """.formatted(keyboardCartItemId, mouseCartItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders.length()").value(2))
                .andExpect(jsonPath("$.data.orders[0].id", isA(Number.class)))
                .andExpect(jsonPath("$.data.orders[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.orders[0].productStatus").value("RESERVED"))
                .andExpect(jsonPath("$.data.orders[1].id", isA(Number.class)))
                .andExpect(jsonPath("$.data.orders[1].status").value("CREATED"))
                .andExpect(jsonPath("$.data.orders[1].productStatus").value("RESERVED"));

        assertThat(countOrders()).isEqualTo(2);
        assertThat(countCartItems()).isZero();
        assertThat(findProductStatus(keyboardId)).isEqualTo("RESERVED");
        assertThat(findProductStatus(mouseId)).isEqualTo("RESERVED");
    }

    @Test
    void 선택한_항목_중_하나라도_구매_불가이면_아무_주문도_생성하지_않는다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long availableProductId = createProduct(sellerToken, "Available Product", "available.jpg");
        Long hiddenProductId = createProduct(sellerToken, "Hidden Product", "hidden.jpg");

        addCart(buyerToken, availableProductId);
        addCart(buyerToken, hiddenProductId);
        Long availableCartItemId = findCartItemId(buyerId, availableProductId);
        Long hiddenCartItemId = findCartItemId(buyerId, hiddenProductId);

        mockMvc.perform(delete("/api/products/{productId}", hiddenProductId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d, %d]
                                }
                                """.formatted(availableCartItemId, hiddenCartItemId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_NOT_ALLOWED"));

        assertThat(countOrders()).isZero();
        assertThat(countCartItems()).isEqualTo(2);
        assertThat(findProductStatus(availableProductId)).isEqualTo("ON_SALE");
        assertThat(findProductStatus(hiddenProductId)).isEqualTo("HIDDEN");
    }

    @Test
    void 빈_장바구니_체크아웃은_실패한다() throws Exception {
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");

        mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_EMPTY"));
    }

    @Test
    void 중복된_장바구니_항목으로_체크아웃할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long productId = createProduct(sellerToken, "Duplicate Product", "duplicate.jpg");

        addCart(buyerToken, productId);
        Long cartItemId = findCartItemId(buyerId, productId);

        mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d, %d]
                                }
                                """.formatted(cartItemId, cartItemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_INVALID_ITEMS"));
    }

    @Test
    void 다른_구매자의_장바구니_항목은_체크아웃할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other@example.com", "password123", "other");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long productId = createProduct(sellerToken, "Private Cart Product", "private-cart.jpg");

        addCart(buyerToken, productId);
        Long cartItemId = findCartItemId(buyerId, productId);

        mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherBuyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d]
                                }
                                """.formatted(cartItemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_INVALID_ITEMS"));
    }

    @Test
    void 장바구니_체크아웃은_JWT가_필요하다() throws Exception {
        mockMvc.perform(post("/api/me/cart/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [1]
                                }
                                """))
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

    private Long createProduct(String accessToken, String title, String fileName) throws Exception {
        Long uploadId = uploadImage(accessToken, fileName);

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

    private void addCart(String accessToken, Long productId) throws Exception {
        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private Long findMemberIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email
        );
    }

    private Long findCartItemId(Long buyerId, Long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM cart_items WHERE buyer_id = ? AND product_id = ?",
                Long.class,
                buyerId,
                productId
        );
    }

    private long countOrders() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        return count == null ? 0 : count;
    }

    private long countCartItems() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cart_items", Long.class);
        return count == null ? 0 : count;
    }

    private String findProductStatus(Long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM products WHERE id = ?",
                String.class,
                productId
        );
    }
}
