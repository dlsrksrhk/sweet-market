package com.sweet.market.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.cart.domain.CartItem;
import com.sweet.market.catalog.domain.CatalogSort;
import com.sweet.market.catalog.query.CatalogCursor;
import com.sweet.market.catalog.query.CatalogCursorCodec;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import com.sweet.market.wishlist.domain.WishlistItem;

import jakarta.persistence.EntityManager;

class CatalogApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private CatalogCursorCodec catalogCursorCodec;

    @Test
    void 글로벌과_상점_카탈로그는_동일한_필터계약으로_구매가능_상품만_반환한다() throws Exception {
        Store store = saveActiveBusinessStore("catalog-filter@example.com");
        Product matching = saveStockProduct(store, "게이밍 노트북", 20_000L, 3, 3, ProductCategory.COMPUTERS);
        saveStockProduct(store, "재고 충분 노트북", 20_000L, 10, 3, ProductCategory.COMPUTERS);
        saveStockProduct(store, "품절 노트북", 20_000L, 0, 3, ProductCategory.COMPUTERS);

        mockMvc.perform(get("/api/catalog/products")
                        .queryParam("keyword", "노트북")
                        .queryParam("category", "COMPUTERS")
                        .queryParam("minPrice", "20000")
                        .queryParam("maxPrice", "20000")
                        .queryParam("availability", "LOW_STOCK")
                        .queryParam("salesPolicy", "STOCK_MANAGED")
                        .queryParam("storeType", "BUSINESS")
                        .queryParam("sort", "PRICE_ASC")
                        .queryParam("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(matching.getId()))
                .andExpect(jsonPath("$.data.content[0].category").value("COMPUTERS"))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(store.getOwnerMember().getId()))
                .andExpect(jsonPath("$.data.content[0].availability.status").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data.content[0].totalQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].reservedQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].inventoryAdjustments").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].wishlisted").value(false))
                .andExpect(jsonPath("$.data.content[0].carted").value(false));

        mockMvc.perform(get("/api/stores/{storeId}/catalog/products", store.getId())
                        .queryParam("keyword", "노트북")
                        .queryParam("category", "COMPUTERS")
                        .queryParam("availability", "LOW_STOCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(matching.getId()));
    }

    @Test
    void 인증된_구매자는_찜과_장바구니_개인화_플래그를_받는다() throws Exception {
        Store store = saveActiveBusinessStore("catalog-personalization@example.com");
        Product product = saveProduct(store, "개인화 상품", 10_000L, ProductCategory.OTHER);
        Member viewer = saveMember("catalog-viewer@example.com", "구매자");
        saveWishlist(viewer, product);
        saveCart(viewer, product);

        mockMvc.perform(get("/api/catalog/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(product.getId()))
                .andExpect(jsonPath("$.data.content[0].wishlisted").value(true))
                .andExpect(jsonPath("$.data.content[0].carted").value(true));
    }

    @Test
    void 카탈로그_구매자_카드는_프로모션_가격_필드를_반환한다() throws Exception {
        Store store = saveActiveBusinessStore("catalog-promotion-card@example.com");
        Product product = saveProduct(store, "카탈로그 할인 상품", 10_000L, ProductCategory.OTHER);
        Long promotionId = jdbcTemplate.queryForObject("""
                insert into promotion_campaigns (
                    version, store_id, scope, discount_type, discount_value, priority, title,
                    start_at, end_at, lifecycle_status, created_at, updated_at
                ) values (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', 3_000, 1, '카탈로그 카드 할인',
                    current_timestamp - interval '1 hour', current_timestamp + interval '1 hour',
                    'SCHEDULED', current_timestamp, current_timestamp)
                returning id
                """, Long.class, store.getId());

        mockMvc.perform(get("/api/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(product.getId()))
                .andExpect(jsonPath("$.data.content[0].price").value(10_000L))
                .andExpect(jsonPath("$.data.content[0].listPrice").value(10_000L))
                .andExpect(jsonPath("$.data.content[0].promotionId").value(promotionId))
                .andExpect(jsonPath("$.data.content[0].promotionTitle").value("카탈로그 카드 할인"))
                .andExpect(jsonPath("$.data.content[0].promotionDiscountAmount").value(3_000L))
                .andExpect(jsonPath("$.data.content[0].effectivePrice").value(7_000L));
    }

    @Test
    void 카탈로그_커서는_다음_페이지와_정렬_기준을_유지한다() throws Exception {
        Store store = saveActiveBusinessStore("catalog-cursor@example.com");
        Product first = saveProduct(store, "저가 상품", 10_000L, ProductCategory.OTHER);
        Product second = saveProduct(store, "고가 상품", 20_000L, ProductCategory.OTHER);

        String firstPage = mockMvc.perform(get("/api/catalog/products")
                        .queryParam("sort", "PRICE_ASC")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(first.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = objectMapper.readTree(firstPage).path("data").path("nextCursor").asText();

        mockMvc.perform(get("/api/catalog/products")
                        .queryParam("sort", "PRICE_ASC")
                        .queryParam("size", "1")
                        .queryParam("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 만료되거나_변조되거나_필터가_다른_카탈로그_커서는_오류를_반환한다() throws Exception {
        String fingerprint = new com.sweet.market.catalog.api.CatalogSearchRequest(
                null, null, null, null, null, null, null, null, CatalogSort.NEWEST, null, null
        ).filterFingerprint();
        String expiredCursor = catalogCursorCodec.encode(new CatalogCursor(
                CatalogSort.NEWEST, null, 1L, fingerprint, Instant.now().minusSeconds(1)
        ));
        String mismatchedCursor = catalogCursorCodec.encode(new CatalogCursor(
                CatalogSort.NEWEST, null, 1L, "different-fingerprint", Instant.now().plusSeconds(60)
        ));

        mockMvc.perform(get("/api/catalog/products").queryParam("cursor", expiredCursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_CURSOR_STALE"));
        mockMvc.perform(get("/api/catalog/products").queryParam("cursor", mismatchedCursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_CURSOR_INVALID"));
        mockMvc.perform(get("/api/catalog/products").queryParam("cursor", expiredCursor + "tampered"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_CURSOR_INVALID"));
    }

    @Test
    void 카탈로그는_잘못된_필터_범위_열거값과_페이지_크기를_거부한다() throws Exception {
        mockMvc.perform(get("/api/catalog/products")
                        .queryParam("minPrice", "20000")
                        .queryParam("maxPrice", "10000"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/catalog/products").queryParam("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/catalog/products").queryParam("category", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 상점_카탈로그는_쿼리_상점_ID와_비활성_또는_없는_상점을_거부한다() throws Exception {
        Store active = saveActiveBusinessStore("catalog-route-active@example.com");
        Store pending = storeRepository.save(Store.applyBusiness(
                saveMember("catalog-route-pending@example.com", "대기 소유자"),
                "대기 상점", "소개", "법인", "111-11-11111"
        ));
        Store suspended = saveActiveBusinessStore("catalog-route-suspended@example.com");
        suspended.suspend();
        storeRepository.save(suspended);

        mockMvc.perform(get("/api/stores/{storeId}/catalog/products", active.getId())
                        .queryParam("storeId", active.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/stores/{storeId}/catalog/products", active.getId())
                        .queryParam("storeId", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/stores/{storeId}/catalog/products", active.getId())
                        .queryParam("storeId", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertCatalogStoreNotFound(pending.getId());
        assertCatalogStoreNotFound(suspended.getId());
        assertCatalogStoreNotFound(999_999L);
    }

    private Store saveActiveBusinessStore(String email) {
        Store store = Store.applyBusiness(saveMember(email, "소유자"), "공개 상점", "소개", "법인", "999-99-99999");
        store.approve();
        return storeRepository.save(store);
    }

    private Product saveProduct(Store store, String title, long price, ProductCategory category) {
        return transactionTemplate.execute(status -> {
            Product product = Product.create(entityManager.find(Store.class, store.getId()), title, "검색 설명", price,
                    ProductSalesPolicy.SINGLE_ITEM, null, null, category);
            entityManager.persist(product);
            entityManager.flush();
            return product;
        });
    }

    private Product saveStockProduct(
            Store store, String title, long price, int totalQuantity, int lowStockThreshold, ProductCategory category
    ) {
        return transactionTemplate.execute(status -> {
            Product product = Product.create(entityManager.find(Store.class, store.getId()), title, "검색 설명", price,
                    ProductSalesPolicy.STOCK_MANAGED, lowStockThreshold, totalQuantity, category);
            entityManager.persist(product);
            entityManager.persist(Inventory.initialize(product, totalQuantity));
            entityManager.flush();
            return product;
        });
    }

    private Member saveMember(String email, String nickname) {
        return memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), nickname));
    }

    private void saveWishlist(Member buyer, Product product) {
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(WishlistItem.create(
                entityManager.find(Member.class, buyer.getId()), entityManager.find(Product.class, product.getId())
        )));
    }

    private void saveCart(Member buyer, Product product) {
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(CartItem.create(
                entityManager.find(Member.class, buyer.getId()), entityManager.find(Product.class, product.getId())
        )));
    }

    private String accessToken(Member member) {
        return jwtProvider.createAccessToken(member.getId(), member.getEmail(), member.getRole());
    }

    private void assertCatalogStoreNotFound(Long storeId) throws Exception {
        mockMvc.perform(get("/api/stores/{storeId}/catalog/products", storeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }
}
