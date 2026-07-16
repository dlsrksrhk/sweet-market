package com.sweet.market.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartApiTest extends IntegrationTestSupport {

    @Test
    void 장바구니는_프로모션_일시정지와_만료_후_현재_가격으로_갱신된다() throws Exception {
        String sellerToken = signupAndLogin("promotion-cart-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("promotion-cart-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long promotionId = createStoreWidePromotion(productId, 1_000L);
        addCart(buyerToken, productId);

        mockMvc.perform(get("/api/me/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productId").value(productId))
                .andExpect(jsonPath("$.data.content[0].listPrice").value(2_000_000))
                .andExpect(jsonPath("$.data.content[0].promotionId").value(promotionId))
                .andExpect(jsonPath("$.data.content[0].promotionTitle").value("장바구니 할인"))
                .andExpect(jsonPath("$.data.content[0].promotionDiscountAmount").value(1_000))
                .andExpect(jsonPath("$.data.content[0].effectivePrice").value(1_999_000));

        jdbcTemplate.update("update promotion_campaigns set lifecycle_status = 'PAUSED' where id = ?", promotionId);

        mockMvc.perform(get("/api/me/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].promotionId").value((Object) null))
                .andExpect(jsonPath("$.data.content[0].promotionTitle").value((Object) null))
                .andExpect(jsonPath("$.data.content[0].promotionDiscountAmount").value(0))
                .andExpect(jsonPath("$.data.content[0].effectivePrice").value(2_000_000));

        jdbcTemplate.update("""
                update promotion_campaigns
                set lifecycle_status = 'DRAFT', end_at = ?
                where id = ?
                """, Timestamp.from(Instant.now().minusSeconds(5)), promotionId);

        mockMvc.perform(get("/api/me/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].promotionId").value((Object) null))
                .andExpect(jsonPath("$.data.content[0].promotionDiscountAmount").value(0))
                .andExpect(jsonPath("$.data.content[0].effectivePrice").value(2_000_000));
    }

    @Test
    void 내_장바구니는_최근에_담은_순서로_조회된다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long oldProductId = createProduct(sellerToken, "Old MacBook Pro", "old-macbook.jpg");
        Long recentProductId = createProduct(sellerToken, "Recent MacBook Pro", "recent-macbook.jpg");

        addCart(buyerToken, oldProductId);
        addCart(buyerToken, recentProductId);
        updateCartedAt(buyerId, oldProductId, "2026-01-01 10:00:00");
        updateCartedAt(buyerId, recentProductId, "2026-01-02 10:00:00");

        mockMvc.perform(get("/api/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].cartItemId").value(findCartItemId(buyerId, recentProductId)))
                .andExpect(jsonPath("$.data.content[0].productId").value(recentProductId))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(findMemberIdByEmail("seller@example.com")))
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.content[0].title").value("Recent MacBook Pro"))
                .andExpect(jsonPath("$.data.content[0].price").value(2000000))
                .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.content[0].checkoutAvailable").value(true))
                .andExpect(jsonPath("$.data.content[0].unavailableReason").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].cartedAt").value("2026-01-02T10:00:00"))
                .andExpect(jsonPath("$.data.content[1].productId").value(oldProductId))
                .andExpect(jsonPath("$.data.content[1].cartedAt").value("2026-01-01T10:00:00"));
    }

    @Test
    void 내_장바구니는_sort_파라미터가_있어도_최근에_담은_순서로_조회된다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long oldProductId = createProduct(sellerToken, "Old MacBook Pro", "old-sort-macbook.jpg");
        Long recentProductId = createProduct(sellerToken, "Recent MacBook Pro", "recent-sort-macbook.jpg");

        addCart(buyerToken, oldProductId);
        addCart(buyerToken, recentProductId);
        updateCartedAt(buyerId, oldProductId, "2026-01-01 10:00:00");
        updateCartedAt(buyerId, recentProductId, "2026-01-02 10:00:00");

        mockMvc.perform(get("/api/me/cart")
                        .param("sort", "createdAt,asc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].productId").value(recentProductId))
                .andExpect(jsonPath("$.data.content[1].productId").value(oldProductId));
    }

    @Test
    void 내_장바구니는_구매할_수_없는_상품도_보여주되_선택_불가로_표시한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long onSaleProductId = createProduct(sellerToken, "On Sale Product", "on-sale.jpg");
        Long reservedProductId = createProduct(sellerToken, "Reserved Product", "reserved.jpg");
        Long soldOutProductId = createProduct(sellerToken, "Sold Out Product", "sold-out.jpg");
        Long hiddenProductId = createProduct(sellerToken, "Hidden Product", "hidden.jpg");

        addCart(buyerToken, onSaleProductId);
        addCart(buyerToken, reservedProductId);
        addCart(buyerToken, soldOutProductId);
        addCart(buyerToken, hiddenProductId);
        updateProductStatus(reservedProductId, "RESERVED");
        updateProductStatus(soldOutProductId, "SOLD_OUT");
        updateProductStatus(hiddenProductId, "HIDDEN");
        updateCartedAt(buyerId, onSaleProductId, "2026-01-01 10:00:00");
        updateCartedAt(buyerId, reservedProductId, "2026-01-02 10:00:00");
        updateCartedAt(buyerId, soldOutProductId, "2026-01-03 10:00:00");
        updateCartedAt(buyerId, hiddenProductId, "2026-01-04 10:00:00");

        mockMvc.perform(get("/api/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(4))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[0].productId").value(hiddenProductId))
                .andExpect(jsonPath("$.data.content[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.content[0].checkoutAvailable").value(false))
                .andExpect(jsonPath("$.data.content[0].unavailableReason").value("HIDDEN"))
                .andExpect(jsonPath("$.data.content[1].productId").value(soldOutProductId))
                .andExpect(jsonPath("$.data.content[1].status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.content[1].checkoutAvailable").value(false))
                .andExpect(jsonPath("$.data.content[1].unavailableReason").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.content[2].productId").value(reservedProductId))
                .andExpect(jsonPath("$.data.content[2].status").value("RESERVED"))
                .andExpect(jsonPath("$.data.content[2].checkoutAvailable").value(false))
                .andExpect(jsonPath("$.data.content[2].unavailableReason").value("RESERVED"))
                .andExpect(jsonPath("$.data.content[3].productId").value(onSaleProductId))
                .andExpect(jsonPath("$.data.content[3].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.content[3].checkoutAvailable").value(true))
                .andExpect(jsonPath("$.data.content[3].unavailableReason").doesNotExist());
    }

    @Test
    void 재고형_상품의_장바구니는_가용_재고와_구매_가능성을_함께_계산한다() throws Exception {
        String sellerToken = signupAndLogin("stock-cart-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("stock-cart-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "Stock Product", "stock-product.jpg");
        addCart(buyerToken, productId);
        makeStockManaged(productId, 5, 2, 3);

        mockMvc.perform(get("/api/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].availability.policy").value("STOCK_MANAGED"))
                .andExpect(jsonPath("$.data.content[0].availability.status").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data.content[0].availability.quantity").value(3))
                .andExpect(jsonPath("$.data.content[0].checkoutAvailable").value(true))
                .andExpect(jsonPath("$.data.content[0].totalQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].reservedQuantity").doesNotExist());

        jdbcTemplate.update("UPDATE inventories SET reserved_quantity = total_quantity WHERE product_id = ?", productId);

        mockMvc.perform(get("/api/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.content[0].availability.status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.content[0].availability.quantity").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].checkoutAvailable").value(false))
                .andExpect(jsonPath("$.data.content[0].unavailableReason").value("SOLD_OUT"));
    }

    @Test
    void 로그인한_사용자는_상품_목록에서_장바구니_상태를_본다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long cartedProductId = createProduct(sellerToken, "Carted Product", "carted-product.jpg");
        Long otherProductId = createProduct(sellerToken, "Other Product", "other-product.jpg");

        addCart(buyerToken, cartedProductId);

        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(otherProductId))
                .andExpect(jsonPath("$.data.content[0].carted").value(false))
                .andExpect(jsonPath("$.data.content[1].id").value(cartedProductId))
                .andExpect(jsonPath("$.data.content[1].carted").value(true));
    }

    @Test
    void 로그인한_사용자는_상품_상세에서_장바구니_상태를_본다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        addCart(buyerToken, productId);

        mockMvc.perform(get("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.carted").value(true));
    }

    @Test
    void 익명_사용자는_상품_목록과_상세에서_장바구니_상태를_false로_본다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(productId))
                .andExpect(jsonPath("$.data.content[0].carted").value(false));

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.carted").value(false));
    }

    @Test
    void 내_장바구니_조회는_JWT가_필요하다() throws Exception {
        mockMvc.perform(get("/api/me/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 판매중_상품을_장바구니에_담을_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.carted").value(true));

        assertThat(countCartItems(productId)).isEqualTo(1);
    }

    @Test
    void 같은_상품을_두_번_담아도_장바구니_항목은_하나만_생긴다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carted").value(true));

        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.carted").value(true));

        assertThat(countCartItems(productId)).isEqualTo(1);
    }

    @Test
    void 장바구니에서_상품을_제거할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.carted").value(false));

        assertThat(countCartItems(productId)).isZero();
    }

    @Test
    void 없는_장바구니_항목을_제거해도_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(delete("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.carted").value(false));
    }

    @Test
    void 장바구니_담기는_JWT가_필요하다() throws Exception {
        mockMvc.perform(post("/api/products/{productId}/cart", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 장바구니_제거는_JWT가_필요하다() throws Exception {
        mockMvc.perform(delete("/api/products/{productId}/cart", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 자기_상품은_장바구니에_담을_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CART_OWN_PRODUCT_NOT_ALLOWED"));
    }

    @Test
    void 판매중이_아닌_상품은_새로_장바구니에_담을_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(delete("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_PRODUCT_NOT_ON_SALE"));
    }

    @Test
    void 존재하지_않는_상품은_장바구니에서_제거할_수_없다() throws Exception {
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");

        mockMvc.perform(delete("/api/products/{productId}/cart", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
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
        return createProduct(accessToken, "MacBook Pro", "cart-product.jpg");
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
                ) values (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', ?, 10, '장바구니 할인',
                    current_timestamp - interval '1 minute', current_timestamp + interval '1 minute', 'DRAFT',
                    current_timestamp, current_timestamp)
                returning id
                """, Long.class, storeId, discountAmount);
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

    private void updateCartedAt(Long buyerId, Long productId, String cartedAt) {
        jdbcTemplate.update(
                "UPDATE cart_items SET created_at = ? WHERE buyer_id = ? AND product_id = ?",
                java.sql.Timestamp.valueOf(cartedAt),
                buyerId,
                productId
        );
    }

    private void updateProductStatus(Long productId, String status) {
        jdbcTemplate.update(
                "UPDATE products SET status = ? WHERE id = ?",
                status,
                productId
        );
    }

    private void makeStockManaged(Long productId, int totalQuantity, int reservedQuantity, int lowStockThreshold) {
        jdbcTemplate.update(
                "UPDATE products SET sales_policy = 'STOCK_MANAGED', low_stock_threshold = ? WHERE id = ?",
                lowStockThreshold,
                productId
        );
        jdbcTemplate.update(
                "INSERT INTO inventories (version, product_id, total_quantity, reserved_quantity) VALUES (0, ?, ?, ?)",
                productId,
                totalQuantity,
                reservedQuantity
        );
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

    private long countCartItems(Long productId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_items WHERE product_id = ?",
                Long.class,
                productId
        );
        return count == null ? 0 : count;
    }
}
