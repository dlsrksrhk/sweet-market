package com.sweet.market.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartCheckoutApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 기존_장바구니_상품도_다시_추가할_때_재고를_재검증한다() throws Exception {
        signupAndLogin("stock-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("stock-buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-stock-buyer@example.com", "password123", "other");
        Long productId = createStockProduct("stock-seller@example.com", 1);
        addCart(buyerToken, productId);

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherBuyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_PRODUCT_NOT_ON_SALE"));
    }

    @Test
    void 재고형_상품은_장바구니에서_예약하지_않고_체크아웃에서_예약하며_취소는_한번만_해제한다() throws Exception {
        String sellerToken = signupAndLogin("stock-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("stock-buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("stock-buyer@example.com");
        Long productId = createStockProduct("stock-seller@example.com", 5);

        addCart(buyerToken, productId);
        assertInventory(productId, 5, 0);
        Long cartItemId = findCartItemId(buyerId, productId);

        String response = mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d]
                                }
                                """.formatted(cartItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders[0].productStatus").value("ON_SALE"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long orderId = objectMapper.readTree(response).path("data").path("orders").get(0).path("id").asLong();
        assertInventory(productId, 5, 1);

        cancelOrder(buyerToken, orderId);
        cancelOrder(buyerToken, orderId);

        assertInventory(productId, 5, 0);
        assertThat(countInventoryAdjustments(orderId, "RELEASE")).isEqualTo(1);
    }

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
    void 장바구니에_담은_뒤_만료된_프로모션은_체크아웃_스냅샷에_적용하지_않는다() throws Exception {
        String sellerToken = signupAndLogin("promotion-cart-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("promotion-cart-buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("promotion-cart-buyer@example.com");
        Long productId = createProduct(sellerToken, "Promotion Product", "promotion.jpg");
        Long promotionId = createStoreWidePromotion(productId, 500_000L);
        addCart(buyerToken, productId);
        Long cartItemId = findCartItemId(buyerId, productId);
        jdbcTemplate.update("update promotion_campaigns set end_at = current_timestamp - interval '1 second' where id = ?", promotionId);

        String response = mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d]
                                }
                                """.formatted(cartItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders[0].productPrice").value(2_000_000L))
                .andExpect(jsonPath("$.data.orders[0].listPrice").value(2_000_000L))
                .andExpect(jsonPath("$.data.orders[0].promotionCampaignId").doesNotExist())
                .andExpect(jsonPath("$.data.orders[0].promotionDiscountAmount").value(0L))
                .andExpect(jsonPath("$.data.orders[0].finalPrice").value(2_000_000L))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(response).path("data").path("orders").get(0).path("id").asLong();

        assertThat(jdbcTemplate.queryForObject("select list_price from orders where id = ?", Long.class, orderId))
                .isEqualTo(2_000_000L);
        assertThat(jdbcTemplate.queryForObject("select promotion_campaign_id from orders where id = ?", Long.class, orderId))
                .isNull();
        assertThat(jdbcTemplate.queryForObject("select promotion_discount_amount from orders where id = ?", Long.class, orderId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("select final_price from orders where id = ?", Long.class, orderId))
                .isEqualTo(2_000_000L);
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

        assertThat(countOrders()).isZero();
        assertThat(countCartItems()).isEqualTo(1);
        assertThat(findProductStatus(productId)).isEqualTo("ON_SALE");
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

        assertThat(countOrders()).isZero();
        assertThat(countCartItems()).isEqualTo(1);
        assertThat(findProductStatus(productId)).isEqualTo("ON_SALE");
    }

    @Test
    void 존재하지_않는_장바구니_항목이_포함되면_아무_주문도_생성하지_않는다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long productId = createProduct(sellerToken, "Invalid Cart Product", "invalid-cart.jpg");

        addCart(buyerToken, productId);
        Long cartItemId = findCartItemId(buyerId, productId);

        mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d, 999999]
                                }
                                """.formatted(cartItemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_INVALID_ITEMS"));

        assertThat(countOrders()).isZero();
        assertThat(countCartItems()).isEqualTo(1);
        assertThat(findProductStatus(productId)).isEqualTo("ON_SALE");
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

    private Long createStockProduct(String sellerEmail, int totalQuantity) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member seller = memberRepository.findByEmail(sellerEmail).orElseThrow();
            Store store = Store.applyBusiness(seller, "재고 상점", "소개", "법인", "123-45-67890");
            store.approve();
            storeRepository.save(store);
            Product product = Product.create(
                    store,
                    "재고 상품",
                    "설명",
                    10_000L,
                    ProductSalesPolicy.STOCK_MANAGED,
                    2,
                    totalQuantity
            );
            product.addLegacyImage("stock.jpg");
            Product savedProduct = productRepository.save(product);
            inventoryService.initialize(savedProduct, totalQuantity, seller.getId());
            return savedProduct.getId();
        });
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

    private void cancelOrder(String accessToken, Long orderId) throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private void assertInventory(Long productId, int totalQuantity, int reservedQuantity) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_quantity FROM inventories WHERE product_id = ?",
                Integer.class,
                productId
        )).isEqualTo(totalQuantity);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM inventories WHERE product_id = ?",
                Integer.class,
                productId
        )).isEqualTo(reservedQuantity);
    }

    private long countInventoryAdjustments(Long orderId, String changeType) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_adjustments WHERE order_id = ? AND change_type = ?",
                Long.class,
                orderId,
                changeType
        );
        return count == null ? 0 : count;
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
