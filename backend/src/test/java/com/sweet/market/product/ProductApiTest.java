package com.sweet.market.product;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.product.application.ProductImageUploadService;
import com.sweet.market.support.IntegrationTestSupport;

class ProductApiTest extends IntegrationTestSupport {

    @Autowired
    private FailingImageConfirmService failingImageConfirmService;

    @Test
    void 활성_사업자_상점만_재고형_상품을_등록할_수_있다() throws Exception {
        String personalToken = signupAndLogin("stock-personal@example.com", "password123", "seller");
        Long personalStoreId = activePersonalStoreId(personalToken);

        createStockProduct(personalToken, personalStoreId, 5, 3)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STORE_INVALID_TYPE"));
    }

    @Test
    void 재고형_상품은_초기수량과_저재고_임계값을_검증한다() throws Exception {
        String businessToken = signupAndLogin("stock-business@example.com", "password123", "seller");
        Long businessStoreId = createActiveBusinessStore("stock-business@example.com");

        createStockProduct(businessToken, businessStoreId, -1, 3)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 재고형_상품을_등록하면_초기_재고와_이력이_생성된다() throws Exception {
        String businessToken = signupAndLogin("stock-initialization@example.com", "password123", "seller");
        Long businessStoreId = createActiveBusinessStore("stock-initialization@example.com");

        Long productId = createStockProductId(businessToken, businessStoreId, 5, 3);

        assertThat(jdbcTemplate.queryForObject(
                "select total_quantity from inventories where product_id = ?", Integer.class, productId
        )).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from inventory_adjustments where product_id = ? and change_type = 'INITIALIZATION'",
                Integer.class,
                productId
        )).isEqualTo(1);
    }

    @Test
    void 재고가_없는_재고형_상품_상세는_계산된_품절_상태만_노출한다() throws Exception {
        String businessToken = signupAndLogin("stock-detail@example.com", "password123", "seller");
        Long businessStoreId = createActiveBusinessStore("stock-detail@example.com");
        Long productId = createStockProductId(businessToken, businessStoreId, 0, 3);

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.availability.status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.availability.quantity").doesNotExist())
                .andExpect(jsonPath("$.data.purchasable").value(false))
                .andExpect(jsonPath("$.data.totalQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.reservedQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.availableQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.lowStockThreshold").doesNotExist());
    }

    @Test
    void 재고형_상품만_저재고_임계값을_수정할_수_있다() throws Exception {
        String businessToken = signupAndLogin("stock-threshold@example.com", "password123", "seller");
        Long businessStoreId = createActiveBusinessStore("stock-threshold@example.com");
        Long stockProductId = createStockProductId(businessToken, businessStoreId, 5, 3);
        Long imageId = getFirstImageId(stockProductId);

        mockMvc.perform(patch("/api/products/{productId}", stockProductId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + businessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "재고 상품",
                                  "description": "재고로 판매하는 상품",
                                  "price": 10000,
                                  "lowStockThreshold": 2,
                                  "images": [{"imageId": %d, "sortOrder": 0, "representative": true}]
                                }
                                """.formatted(imageId)))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "select low_stock_threshold from products where id = ?", Integer.class, stockProductId
        )).isEqualTo(2);
    }

    @Test
    void 상품_수정으로_판매정책을_변경할_수_없다() throws Exception {
        String accessToken = signupAndLogin("policy-update@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);
        Long imageId = getFirstImageId(productId);

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "salesPolicy": "STOCK_MANAGED",
                                  "images": [{"imageId": %d, "sortOrder": 0, "representative": true}]
                                }
                                """.formatted(imageId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 비활성_사업자_상점에는_상품을_등록할_수_없다() throws Exception {
        String token = signupAndLogin("inactive-create@example.com", "password123", "seller");
        Long storeId = createInactiveBusinessStore("inactive-create@example.com");
        Long uploadId = uploadImage(token, "inactive.jpg");

        mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":%d,\"title\":\"상품\",\"description\":\"설명\",\"price\":10000,\"salesPolicy\":\"SINGLE_ITEM\",\"images\":[{\"uploadId\":%d,\"sortOrder\":0,\"representative\":true}]}".formatted(storeId, uploadId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
    }

    @Test
    void 비활성_사업자_상점_상품은_공개_목록에서_제외된다() throws Exception {
        Long productId = createInactiveBusinessProduct("inactive-list@example.com");

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
        assertThat(productId).isPositive();
    }

    @Test
    void 비활성_사업자_상점_상품의_직접_조회는_구매_불가를_반환한다() throws Exception {
        Long productId = createInactiveBusinessProduct("inactive-detail@example.com");

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchasable").value(false));
    }

    @Test
    void 비활성_사업자_상점_상품은_장바구니에_담거나_주문할_수_없다() throws Exception {
        Long productId = createInactiveBusinessProduct("inactive-cart@example.com");
        String buyerToken = signupAndLogin("inactive-buyer@example.com", "password123", "buyer");

        mockMvc.perform(post("/api/products/{productId}/cart", productId).header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":%d}".formatted(productId)))
                .andExpect(status().isConflict());
    }

    private Long createInactiveBusinessStore(String email) {
        Long memberId = jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, email);
        Long storeId = jdbcTemplate.queryForObject("""
                insert into stores (version, owner_member_id, type, public_name, introduction, status, created_at, updated_at)
                values (0, ?, 'BUSINESS', '비활성 사업자 상점', '', 'PENDING', current_timestamp, current_timestamp) returning id
                """, Long.class, memberId);
        jdbcTemplate.update("insert into store_memberships (store_id, member_id, role, active, created_at) values (?, ?, 'OWNER', true, current_timestamp)", storeId, memberId);
        return storeId;
    }

    private Long createActiveBusinessStore(String email) {
        Long memberId = jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, email);
        Long storeId = jdbcTemplate.queryForObject("""
                insert into stores (version, owner_member_id, type, public_name, introduction, status, created_at, updated_at)
                values (0, ?, 'BUSINESS', '활성 사업자 상점', '', 'ACTIVE', current_timestamp, current_timestamp) returning id
                """, Long.class, memberId);
        jdbcTemplate.update("insert into store_memberships (store_id, member_id, role, active, created_at) values (?, ?, 'OWNER', true, current_timestamp)", storeId, memberId);
        return storeId;
    }

    private Long createInactiveBusinessProduct(String email) throws Exception {
        signupAndLogin(email, "password123", "seller");
        Long storeId = createInactiveBusinessStore(email);
        return jdbcTemplate.queryForObject("""
                insert into products (version, store_id, title, description, price, status, sales_policy)
                values (0, ?, '비활성 상품', '설명', 10000, 'ON_SALE', 'SINGLE_ITEM') returning id
                """, Long.class, storeId);
    }

    @Test
    void 상품_등록에_성공한다() throws Exception {
        String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long storeId = activePersonalStoreId(accessToken);
        Long uploadId = uploadImage(accessToken, "macbook.jpg");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
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
                                """.formatted(storeId, uploadId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.storeId").value(storeId))
                .andExpect(jsonPath("$.data.storeName").value("seller의 상점"))
                .andExpect(jsonPath("$.data.storeType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.title").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.description").value("M3 laptop"))
                .andExpect(jsonPath("$.data.price").value(2000000))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andExpect(jsonPath("$.data.images[0].imageUrl", startsWith("/uploads/products/public/")))
                .andExpect(jsonPath("$.data.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.images[0].representative").value(true));
    }

    @Test
    void 상품_등록은_JWT가_필요하다() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": []
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 잘못된_상품_등록_요청은_검증_오류를_반환한다() throws Exception {
        String accessToken = signupAndLogin("seller@example.com", "password123", "seller");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "description": "",
                                  "price": 0,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": [
                                    {
                                      "uploadId": null,
                                      "sortOrder": -1,
                                      "representative": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void 상품_등록은_이미지가_필요하다() throws Exception {
        String accessToken = signupAndLogin("seller-required@example.com", "password123", "seller");
        Long storeId = activePersonalStoreId(accessToken);

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": []
                                }
                                """.formatted(storeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_REQUIRED"));
    }

    @Test
    void 다른_회원의_임시_업로드로_상품을_등록할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller-denied@example.com", "password123", "seller");
        Long storeId = activePersonalStoreId(sellerToken);
        String otherToken = signupAndLogin("other-denied@example.com", "password123", "other");
        Long uploadId = uploadImage(otherToken, "other-product.jpg");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
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
                                """.formatted(storeId, uploadId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));
    }

    @Test
    void 잘못된_이미지_배치로_실패한_임시_업로드는_다시_사용할_수_있다() throws Exception {
        String accessToken = signupAndLogin("seller-retry@example.com", "password123", "seller");
        Long storeId = activePersonalStoreId(accessToken);
        Long uploadId = uploadImage(accessToken, "retry-product.jpg");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": false
                                    }
                                  ]
                                }
                                """.formatted(storeId, uploadId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
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
                                """.formatted(storeId, uploadId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.images[0].imageUrl", startsWith("/uploads/products/public/")))
                .andExpect(jsonPath("$.data.images[0].representative").value(true));
    }

    @Test
    void 일부_임시_업로드가_권한_오류여도_앞선_업로드는_다시_사용할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller-partial@example.com", "password123", "seller");
        Long storeId = activePersonalStoreId(sellerToken);
        String otherToken = signupAndLogin("other-partial@example.com", "password123", "other");
        Long sellerUploadId = uploadImage(sellerToken, "seller-product.jpg");
        Long otherUploadId = uploadImage(otherToken, "other-product.jpg");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    },
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 1,
                                      "representative": false
                                    }
                                  ]
                                }
                                """.formatted(storeId, sellerUploadId, otherUploadId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
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
                                """.formatted(storeId, sellerUploadId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.images[0].imageUrl", startsWith("/uploads/products/public/")))
                .andExpect(jsonPath("$.data.images[0].representative").value(true));
    }

    @Test
    void 중복된_임시_업로드는_사용할_수_없고_다시_사용할_수_있다() throws Exception {
        String accessToken = signupAndLogin("seller-duplicate@example.com", "password123", "seller");
        Long storeId = activePersonalStoreId(accessToken);
        Long uploadId = uploadImage(accessToken, "duplicate-product.jpg");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    },
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 1,
                                      "representative": false
                                    }
                                  ]
                                }
                                """.formatted(storeId, uploadId, uploadId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
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
                                """.formatted(storeId, uploadId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.images[0].imageUrl", startsWith("/uploads/products/public/")))
                .andExpect(jsonPath("$.data.images[0].representative").value(true));
    }

    @Test
    void 임시_업로드_확인_후_롤백되면_같은_업로드를_다시_사용할_수_있다() throws Exception {
        String accessToken = signupAndLogin("seller-rollback@example.com", "password123", "seller");
        Long storeId = activePersonalStoreId(accessToken);
        Long uploadId = uploadImage(accessToken, "rollback-product.jpg");

        assertThatThrownBy(() -> failingImageConfirmService.confirmAndThrow(1L, uploadId))
                .isInstanceOf(IllegalStateException.class);

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
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
                                """.formatted(storeId, uploadId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.images[0].imageUrl", startsWith("/uploads/products/public/")));
    }

    @Test
    void 소유자는_상품_수정에_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer-update-count@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long imageId = getFirstImageId(productId);

        addWishlist(buyerToken, productId);

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "iPhone 15 Pro",
                                  "description": "Natural titanium",
                                  "price": 1200000,
                                  "images": [
                                    {
                                      "imageId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(imageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.title").value("iPhone 15 Pro"))
                .andExpect(jsonPath("$.data.description").value("Natural titanium"))
                .andExpect(jsonPath("$.data.price").value(1200000))
                .andExpect(jsonPath("$.data.wishlistCount").value(1))
                .andExpect(jsonPath("$.data.wishlisted").value(false));
    }

    @Test
    void 소유자는_상품_이미지_구성을_수정할_수_있다() throws Exception {
        String accessToken = signupAndLogin("seller-update-images@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);
        Long existingImageId = getFirstImageId(productId);
        Long newUploadId = uploadImage(accessToken, "macbook-2.jpg");

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": [
                                    {
                                      "imageId": %d,
                                      "sortOrder": 1,
                                      "representative": false
                                    },
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(existingImageId, newUploadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images", hasSize(2)))
                .andExpect(jsonPath("$.data.images[0].imageUrl", startsWith("/uploads/products/public/")))
                .andExpect(jsonPath("$.data.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.images[0].representative").value(true))
                .andExpect(jsonPath("$.data.images[1].id").value(existingImageId))
                .andExpect(jsonPath("$.data.images[1].sortOrder").value(1))
                .andExpect(jsonPath("$.data.images[1].representative").value(false));
    }

    @Test
    void 상품_수정은_최소_한_개_이미지를_유지해야_한다() throws Exception {
        String accessToken = signupAndLogin("seller-update-required@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_REQUIRED"));
    }

    @Test
    void 상품_수정에서_다른_상품의_이미지는_사용할_수_없다() throws Exception {
        String accessToken = signupAndLogin("seller-cross-product-image@example.com", "password123", "seller");
        Long targetProductId = createProduct(accessToken);
        Long otherProductId = createProduct(accessToken);
        Long otherProductImageId = getFirstImageId(otherProductId);

        mockMvc.perform(patch("/api/products/{productId}", targetProductId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": [
                                    {
                                      "imageId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(otherProductImageId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_NOT_FOUND"));
    }

    @Test
    void 소유자가_아니면_상품_수정에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken);
        Long imageId = getFirstImageId(productId);

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "iPhone 15 Pro",
                                  "description": "Natural titanium",
                                  "price": 1200000,
                                  "images": [
                                    {
                                      "imageId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(imageId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
    }

    @Test
    void 소유자는_상품_숨김에_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer-hide-count@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        addWishlist(buyerToken, productId);

        mockMvc.perform(delete("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.wishlistCount").value(1))
                .andExpect(jsonPath("$.data.wishlisted").value(false));
    }

    @Test
    void 소유자가_아니면_상품_숨김에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(delete("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
    }

    @Test
    void JWT_없이_판매중_상품_목록을_조회한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        addWishlist(buyerToken, productId);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].title").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.content[0].storeName").value("seller의 상점"))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl", startsWith("/uploads/products/public/")))
                .andExpect(jsonPath("$.data.content[0].wishlistCount").value(1))
                .andExpect(jsonPath("$.data.content[0].wishlisted").value(false));
    }

    @Test
    void JWT_없이_판매중_상품_상세를_조회한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        addWishlist(buyerToken, productId);

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.title").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andExpect(jsonPath("$.data.wishlistCount").value(1))
                .andExpect(jsonPath("$.data.wishlisted").value(false));
    }

    @Test
    void 상품_응답은_상점과_판매자_식별자를_일관되게_반환한다() throws Exception {
        String sellerToken = signupAndLogin("seller-response@example.com", "password123", "seller");
        Long storeId = activePersonalStoreId(sellerToken);
        Long sellerId = jdbcTemplate.queryForObject(
                "select owner_member_id from stores where id = ?",
                Long.class,
                storeId
        );
        Long productId = createProduct(sellerToken);

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(storeId))
                .andExpect(jsonPath("$.data.storeName").value("seller의 상점"))
                .andExpect(jsonPath("$.data.storeType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.sellerId").value(sellerId))
                .andExpect(jsonPath("$.data.sellerNickname").value("seller"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(productId))
                .andExpect(jsonPath("$.data.content[0].storeId").value(storeId))
                .andExpect(jsonPath("$.data.content[0].storeName").value("seller의 상점"))
                .andExpect(jsonPath("$.data.content[0].storeType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(sellerId))
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"));
    }

    @Test
    void 숨김_상품은_공개_목록과_상세_조회에서_제외된다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(delete("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 상품_수정에서_기존_이미지가_잘못되어도_새_업로드는_다시_사용할_수_있다() throws Exception {
        String accessToken = signupAndLogin("seller-invalid-existing@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);
        Long existingImageId = getFirstImageId(productId);
        Long uploadId = uploadImage(accessToken, "retry-update.jpg");

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": [
                                    {
                                      "imageId": 999,
                                      "sortOrder": 1,
                                      "representative": false
                                    },
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(uploadId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_NOT_FOUND"));

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": [
                                    {
                                      "imageId": %d,
                                      "sortOrder": 1,
                                      "representative": false
                                    },
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(existingImageId, uploadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images", hasSize(2)))
                .andExpect(jsonPath("$.data.images[0].representative").value(true));
    }

    @Test
    void 상품_수정에서_다른_업로드가_잘못되어도_앞선_업로드는_다시_사용할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller-invalid-upload@example.com", "password123", "seller");
        String otherToken = signupAndLogin("other-invalid-upload@example.com", "password123", "other");
        Long productId = createProduct(sellerToken);
        Long existingImageId = getFirstImageId(productId);
        Long sellerUploadId = uploadImage(sellerToken, "seller-update.jpg");
        Long otherUploadId = uploadImage(otherToken, "other-update.jpg");

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": [
                                    {
                                      "imageId": %d,
                                      "sortOrder": 2,
                                      "representative": false
                                    },
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    },
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 1,
                                      "representative": false
                                    }
                                  ]
                                }
                                """.formatted(existingImageId, sellerUploadId, otherUploadId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": [
                                    {
                                      "imageId": %d,
                                      "sortOrder": 1,
                                      "representative": false
                                    },
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(existingImageId, sellerUploadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images", hasSize(2)))
                .andExpect(jsonPath("$.data.images[0].representative").value(true));
    }

    @Test
    void 상품_수정에서_제외한_기존_로컬_이미지는_DB와_공개_파일에서_삭제된다() throws Exception {
        String accessToken = signupAndLogin("seller-omit-local@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);
        Long firstImageId = getFirstImageId(productId);
        Long secondUploadId = uploadImage(accessToken, "omit-local.jpg");

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": [
                                    {
                                      "imageId": %d,
                                      "sortOrder": 1,
                                      "representative": false
                                    },
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(firstImageId, secondUploadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images", hasSize(2)));

        JsonNode beforeOmitImages = getProductImages(productId);
        Long omittedImageId = beforeOmitImages.get(0).path("id").asLong();
        Long retainedImageId = beforeOmitImages.get(1).path("id").asLong();
        String omittedStoredFileName = storedFileName(beforeOmitImages.get(0).path("imageUrl").asText());
        Path omittedPublicPath = Path.of("build/test-product-images/public", omittedStoredFileName);
        assertThat(Files.exists(omittedPublicPath)).isTrue();

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": [
                                    {
                                      "imageId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(retainedImageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andExpect(jsonPath("$.data.images[0].id").value(retainedImageId));

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andExpect(jsonPath("$.data.images[0].id").value(retainedImageId));
        Integer omittedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_images WHERE id = ?",
                Integer.class,
                omittedImageId
        );
        assertThat(omittedRows).isZero();
        assertThat(Files.exists(omittedPublicPath)).isFalse();
    }

    @Test
    void 상품_이미지_URL_추가_엔드포인트는_더_이상_지원하지_않는다() throws Exception {
        String accessToken = signupAndLogin("seller-url-add-gone@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);

        mockMvc.perform(post("/api/products/{productId}/images", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "imageUrl": "https://example.com/macbook-2.jpg"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void 상품_이미지_삭제_엔드포인트는_더_이상_지원하지_않는다() throws Exception {
        String accessToken = signupAndLogin("seller-url-delete-gone@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);
        Long imageId = getFirstImageId(productId);

        mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, imageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
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
                                """.formatted(storeId, uploadId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private ResultActions createStockProduct(
            String accessToken,
            Long storeId,
            int initialTotalQuantity,
            int lowStockThreshold
    ) throws Exception {
        Long uploadId = uploadImage(accessToken, "stock-product.jpg");
        return mockMvc.perform(post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "storeId": %d,
                          "title": "재고 상품",
                          "description": "재고로 판매하는 상품",
                          "price": 10000,
                          "salesPolicy": "STOCK_MANAGED",
                          "initialTotalQuantity": %d,
                          "lowStockThreshold": %d,
                          "images": [
                            {
                              "uploadId": %d,
                              "sortOrder": 0,
                              "representative": true
                            }
                          ]
                        }
                        """.formatted(storeId, initialTotalQuantity, lowStockThreshold, uploadId)));
    }

    private Long createStockProductId(
            String accessToken,
            Long storeId,
            int initialTotalQuantity,
            int lowStockThreshold
    ) throws Exception {
        String response = createStockProduct(accessToken, storeId, initialTotalQuantity, lowStockThreshold)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void addWishlist(String accessToken, Long productId) throws Exception {
        mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
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

    private Long getFirstImageId(Long productId) throws Exception {
        return getProductImages(productId).get(0).path("id").asLong();
    }

    private JsonNode getProductImages(Long productId) throws Exception {
        String response = mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("images");
    }

    private String storedFileName(String imageUrl) {
        return imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
    }

    @TestConfiguration
    static class FailingImageConfirmTestConfiguration {

        @Bean
        FailingImageConfirmService failingImageConfirmService(ProductImageUploadService productImageUploadService) {
            return new FailingImageConfirmService(productImageUploadService);
        }
    }

    static class FailingImageConfirmService {

        private final ProductImageUploadService productImageUploadService;

        FailingImageConfirmService(ProductImageUploadService productImageUploadService) {
            this.productImageUploadService = productImageUploadService;
        }

        @Transactional
        void confirmAndThrow(Long memberId, Long uploadId) {
            productImageUploadService.confirm(memberId, uploadId, 0, true);
            throw new IllegalStateException("rollback after confirm");
        }
    }
}
