package com.sweet.market.wishlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WishlistApiTest extends IntegrationTestSupport {

    @Test
    void 내_찜_목록은_최근_찜한_순서로_조회된다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long storeId = activePersonalStoreId(sellerToken);
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-buyer@example.com", "password123", "otherBuyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long oldProductId = createProduct(sellerToken, "Old MacBook Pro", "old-macbook.jpg");
        Long recentProductId = createProduct(sellerToken, "Recent MacBook Pro", "recent-macbook.jpg");

        addWishlist(buyerToken, oldProductId);
        addWishlist(buyerToken, recentProductId);
        addWishlist(otherBuyerToken, recentProductId);
        updateWishedAt(buyerId, oldProductId, "2026-01-01 10:00:00");
        updateWishedAt(buyerId, recentProductId, "2026-01-02 10:00:00");

        mockMvc.perform(get("/api/me/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].wishlistItemId").value(findWishlistItemId(buyerId, recentProductId)))
                .andExpect(jsonPath("$.data.content[0].productId").value(recentProductId))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(findMemberIdByEmail("seller@example.com")))
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.content[0].title").value("Recent MacBook Pro"))
                .andExpect(jsonPath("$.data.content[0].price").value(2000000))
                .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.content[0].wishlisted").value(true))
                .andExpect(jsonPath("$.data.content[0].wishlistCount").value(2))
                .andExpect(jsonPath("$.data.content[0].wishedAt").value("2026-01-02T10:00:00"))
                .andExpect(jsonPath("$.data.content[1].productId").value(oldProductId))
                .andExpect(jsonPath("$.data.content[1].wishedAt").value("2026-01-01T10:00:00"));
    }

    @Test
    void 내_찜_목록은_예약과_판매완료를_보여주고_숨김은_제외한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long onSaleProductId = createProduct(sellerToken, "On Sale Product", "on-sale.jpg");
        Long reservedProductId = createProduct(sellerToken, "Reserved Product", "reserved.jpg");
        Long soldOutProductId = createProduct(sellerToken, "Sold Out Product", "sold-out.jpg");
        Long hiddenProductId = createProduct(sellerToken, "Hidden Product", "hidden.jpg");

        addWishlist(buyerToken, onSaleProductId);
        addWishlist(buyerToken, reservedProductId);
        addWishlist(buyerToken, soldOutProductId);
        addWishlist(buyerToken, hiddenProductId);
        updateProductStatus(reservedProductId, "RESERVED");
        updateProductStatus(soldOutProductId, "SOLD_OUT");
        updateProductStatus(hiddenProductId, "HIDDEN");
        updateWishedAt(buyerId, onSaleProductId, "2026-01-01 10:00:00");
        updateWishedAt(buyerId, reservedProductId, "2026-01-02 10:00:00");
        updateWishedAt(buyerId, soldOutProductId, "2026-01-03 10:00:00");
        updateWishedAt(buyerId, hiddenProductId, "2026-01-04 10:00:00");

        mockMvc.perform(get("/api/me/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].productId").value(soldOutProductId))
                .andExpect(jsonPath("$.data.content[0].status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.content[1].productId").value(reservedProductId))
                .andExpect(jsonPath("$.data.content[1].status").value("RESERVED"))
                .andExpect(jsonPath("$.data.content[2].productId").value(onSaleProductId))
                .andExpect(jsonPath("$.data.content[2].status").value("ON_SALE"));
    }

    @Test
    void 내_찜_목록은_대표_이미지를_썸네일로_응답한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long storeId = activePersonalStoreId(sellerToken);
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        ProductImageUploadFixture firstImage = uploadProductImage(sellerToken, "wishlist-first.jpg");
        ProductImageUploadFixture representativeImage = uploadProductImage(sellerToken, "wishlist-representative.jpg");

        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "Wishlist Thumbnail Product",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": false
                                    },
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 1,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(storeId, firstImage.id(), representativeImage.id())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long productId = objectMapper.readTree(response).path("data").path("id").asLong();
        addWishlist(buyerToken, productId);

        mockMvc.perform(get("/api/me/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value(representativeImage.publicUrl()));
    }

    @Test
    void 내_찜_목록_조회는_JWT가_필요하다() throws Exception {
        mockMvc.perform(get("/api/me/wishlist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 로그인한_사용자는_상품_목록에서_찜_상태와_수를_본다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-buyer@example.com", "password123", "otherBuyer");
        Long wishedProductId = createProduct(sellerToken, "Wishlisted Product", "wishlisted.jpg");
        Long otherOnlyProductId = createProduct(sellerToken, "Other Only Product", "other-only.jpg");

        addWishlist(buyerToken, wishedProductId);
        addWishlist(otherBuyerToken, wishedProductId);
        addWishlist(otherBuyerToken, otherOnlyProductId);

        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(otherOnlyProductId))
                .andExpect(jsonPath("$.data.content[0].wishlistCount").value(1))
                .andExpect(jsonPath("$.data.content[0].wishlisted").value(false))
                .andExpect(jsonPath("$.data.content[1].id").value(wishedProductId))
                .andExpect(jsonPath("$.data.content[1].wishlistCount").value(2))
                .andExpect(jsonPath("$.data.content[1].wishlisted").value(true));
    }

    @Test
    void 로그인한_사용자는_상품_상세에서_찜_상태와_수를_본다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-buyer@example.com", "password123", "otherBuyer");
        Long wishedProductId = createProduct(sellerToken, "Wishlisted Product", "wishlisted.jpg");
        Long otherOnlyProductId = createProduct(sellerToken, "Other Only Product", "other-only.jpg");

        addWishlist(buyerToken, wishedProductId);
        addWishlist(otherBuyerToken, wishedProductId);
        addWishlist(otherBuyerToken, otherOnlyProductId);

        mockMvc.perform(get("/api/products/{productId}", wishedProductId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(wishedProductId))
                .andExpect(jsonPath("$.data.wishlistCount").value(2))
                .andExpect(jsonPath("$.data.wishlisted").value(true));

        mockMvc.perform(get("/api/products/{productId}", otherOnlyProductId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(otherOnlyProductId))
                .andExpect(jsonPath("$.data.wishlistCount").value(1))
                .andExpect(jsonPath("$.data.wishlisted").value(false));
    }

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
    void 찜한_상품이_판매중이_아니게_되어도_다시_찜하면_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wishlisted").value(true))
                .andExpect(jsonPath("$.data.wishlistCount").value(1));

        mockMvc.perform(delete("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

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
    void 존재하지_않는_상품의_찜을_해제할_수_없다() throws Exception {
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");

        mockMvc.perform(delete("/api/products/{productId}/wishlist", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 찜_추가는_JWT가_필요하다() throws Exception {
        mockMvc.perform(post("/api/products/{productId}/wishlist", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 찜_해제는_JWT가_필요하다() throws Exception {
        mockMvc.perform(delete("/api/products/{productId}/wishlist", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
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

    @Test
    void 저재고_재고형_상품의_찜_목록은_남은_수량만_보여준다() throws Exception {
        String sellerToken = signupAndLogin("low-stock-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("low-stock-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        makeStockManaged(productId, 2, 0, 3);
        addWishlist(buyerToken, productId);

        mockMvc.perform(get("/api/me/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.content[0].availability.policy").value("STOCK_MANAGED"))
                .andExpect(jsonPath("$.data.content[0].availability.status").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data.content[0].availability.quantity").value(2))
                .andExpect(jsonPath("$.data.content[0].totalQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].reservedQuantity").doesNotExist());
    }

    @Test
    void 품절된_재고형_상품은_새로_찜할_수_없고_기존_찜에는_품절로_보인다() throws Exception {
        String sellerToken = signupAndLogin("sold-out-stock-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("sold-out-stock-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        addWishlist(buyerToken, productId);
        makeStockManaged(productId, 0, 0, 3);

        mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signupAndLogin(
                                "second-stock-buyer@example.com", "password123", "secondBuyer")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WISHLIST_PRODUCT_NOT_ON_SALE"));

        mockMvc.perform(get("/api/me/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.content[0].availability.status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.content[0].availability.quantity").doesNotExist());
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
        return createProduct(accessToken, "MacBook Pro", "macbook-1.jpg");
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

    private void addWishlist(String accessToken, Long productId) throws Exception {
        mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
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

    private Long findWishlistItemId(Long buyerId, Long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM wishlist_items WHERE buyer_id = ? AND product_id = ?",
                Long.class,
                buyerId,
                productId
        );
    }

    private void updateWishedAt(Long buyerId, Long productId, String wishedAt) {
        jdbcTemplate.update(
                "UPDATE wishlist_items SET created_at = ? WHERE buyer_id = ? AND product_id = ?",
                java.sql.Timestamp.valueOf(wishedAt),
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
                "INSERT INTO inventories (product_id, total_quantity, reserved_quantity, version) VALUES (?, ?, ?, 0)",
                productId,
                totalQuantity,
                reservedQuantity
        );
    }

    private Long uploadImage(String accessToken, String fileName) throws Exception {
        return uploadProductImage(accessToken, fileName).id();
    }

    private ProductImageUploadFixture uploadProductImage(String accessToken, String fileName) throws Exception {
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
        JsonNode data = root.path("data");
        return new ProductImageUploadFixture(
                data.path("id").asLong(),
                data.path("previewUrl").asText().replace("/uploads/products/temp/", "/uploads/products/public/")
        );
    }

    private long countWishlistItems(Long productId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wishlist_items WHERE product_id = ?",
                Long.class,
                productId
        );
        return count == null ? 0 : count;
    }

    private record ProductImageUploadFixture(Long id, String publicUrl) {
    }
}
