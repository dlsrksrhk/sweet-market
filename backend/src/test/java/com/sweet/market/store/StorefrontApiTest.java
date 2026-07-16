package com.sweet.market.store;

import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.cart.domain.CartItem;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.review.domain.Review;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import com.sweet.market.wishlist.domain.WishlistItem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StorefrontApiTest extends IntegrationTestSupport {

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

    @Test
    void 활성_상점_헤더는_현재_상품_기준의_평점과_공개_상품_수만_노출한다() throws Exception {
        Store store = saveActiveBusinessStore("active-storefront@example.com");
        Member historicalSeller = saveMember("historical-seller@example.com", "과거 판매자");
        Member firstBuyer = saveMember("first-buyer@example.com", "구매자1");
        Member secondBuyer = saveMember("second-buyer@example.com", "구매자2");
        Product firstReviewed = saveProduct(store, "리뷰 상품 1");
        Product secondReviewed = saveProduct(store, "리뷰 상품 2");
        saveProduct(store, "판매 상품");
        Product hiddenProduct = saveProduct(store, "숨김 상품");
        jdbcTemplate.update("update products set status = 'HIDDEN' where id = ?", hiddenProduct.getId());
        saveReview(firstBuyer, firstReviewed, 4);
        saveReview(secondBuyer, secondReviewed, 5);
        jdbcTemplate.update("update reviews set seller_id = ?", historicalSeller.getId());

        mockMvc.perform(get("/api/stores/{storeId}", store.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operatingStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.reviewCount").value(2))
                .andExpect(jsonPath("$.data.publicProductCount").value(3))
                .andExpect(jsonPath("$.data.legalBusinessName").doesNotExist())
                .andExpect(jsonPath("$.data.businessRegistrationId").doesNotExist())
                .andExpect(jsonPath("$.data.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.data.ownerMemberId").doesNotExist())
                .andExpect(jsonPath("$.data.memberships").doesNotExist());
    }

    @Test
    void 리뷰가_없는_활성_상점은_평점이_없고_리뷰_수가_0이다() throws Exception {
        Store store = saveActiveBusinessStore("no-reviews@example.com");

        mockMvc.perform(get("/api/stores/{storeId}", store.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value((Object) null))
                .andExpect(jsonPath("$.data.reviewCount").value(0));
    }

    @Test
    void 활성_프로모션_상점_상품은_공통_가격_필드를_노출한다() throws Exception {
        Store store = saveActiveBusinessStore("promotion-storefront@example.com");
        Product product = saveProduct(store, "프로모션 상품", 10_000L);
        Long promotionId = createStoreWidePromotion(product.getId(), 1_000L);

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(product.getId()))
                .andExpect(jsonPath("$.data.content[0].listPrice").value(10_000))
                .andExpect(jsonPath("$.data.content[0].promotionId").value(promotionId))
                .andExpect(jsonPath("$.data.content[0].promotionTitle").value("상점 할인"))
                .andExpect(jsonPath("$.data.content[0].promotionDiscountAmount").value(1_000))
                .andExpect(jsonPath("$.data.content[0].effectivePrice").value(9_000));
    }

    @Test
    void 중단된_상점은_공개_정체성과_0으로_초기화된_집계만_노출한다() throws Exception {
        Store store = saveActiveBusinessStore("suspended-storefront@example.com");
        Product product = saveProduct(store, "중단 전 상품");
        saveReview(saveMember("suspended-buyer@example.com", "구매자"), product, 5);
        store.suspend();
        storeRepository.save(store);

        mockMvc.perform(get("/api/stores/{storeId}", store.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(store.getId()))
                .andExpect(jsonPath("$.data.type").value("BUSINESS"))
                .andExpect(jsonPath("$.data.publicName").value("공개 상점"))
                .andExpect(jsonPath("$.data.introduction").value("공개 소개"))
                .andExpect(jsonPath("$.data.operatingStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.averageRating").value((Object) null))
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.publicProductCount").value(0));
    }

    @Test
    void 승인_대기_상점은_공개_헤더로_조회할_수_없다() throws Exception {
        Store store = storeRepository.save(Store.applyBusiness(
                saveMember("pending-storefront@example.com", "소유자"),
                "대기 상점", "대기 소개", "대기 법인", "111-11-11111"
        ));

        assertStoreNotFound(store.getId());
    }

    @Test
    void 거절된_상점은_공개_헤더로_조회할_수_없다() throws Exception {
        Store store = Store.applyBusiness(
                saveMember("rejected-storefront@example.com", "소유자"),
                "거절 상점", "거절 소개", "거절 법인", "222-22-22222"
        );
        store.reject("서류 미비");
        storeRepository.save(store);

        assertStoreNotFound(store.getId());
    }

    @Test
    void 존재하지_않는_상점은_공개_헤더로_조회할_수_없다() throws Exception {
        assertStoreNotFound(999_999L);
    }

    @Test
    void 상점_상품은_기본값으로_판매중_상품만_최신순_조회하며_호환_필드를_유지한다() throws Exception {
        Store store = saveActiveBusinessStore("catalog-default@example.com");
        Product older = saveProduct(store, "오래된 상품", 10_000L);
        Product newer = saveProduct(store, "최신 상품", 20_000L);
        Product reserved = saveProduct(store, "예약 상품", 30_000L);
        reserved.reserve();
        persistProductState(reserved);
        addImages(newer, "https://example.com/representative.jpg", "https://example.com/ordered-first.jpg");
        jdbcTemplate.update("update product_images set sort_order = 9 where product_id = ? and representative = true", newer.getId());
        Member viewer = saveMember("catalog-viewer@example.com", "조회자");
        Member anotherViewer = saveMember("catalog-other-viewer@example.com", "다른 조회자");
        saveWishlist(viewer, newer);
        saveWishlist(anotherViewer, newer);
        saveCart(viewer, newer);

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.data.content[0].storeId").value(store.getId()))
                .andExpect(jsonPath("$.data.content[0].storeName").value("공개 상점"))
                .andExpect(jsonPath("$.data.content[0].storeType").value("BUSINESS"))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(store.getOwnerMember().getId()))
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("소유자"))
                .andExpect(jsonPath("$.data.content[0].title").value("최신 상품"))
                .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value("https://example.com/representative.jpg"))
                .andExpect(jsonPath("$.data.content[0].wishlistCount").value(2))
                .andExpect(jsonPath("$.data.content[0].wishlisted").value(true))
                .andExpect(jsonPath("$.data.content[0].carted").value(true))
                .andExpect(jsonPath("$.data.content[1].id").value(older.getId()));
    }

    @Test
    void 상점_상품은_예약과_판매완료_상태를_각각_조회하고_숨김_상태는_거부한다() throws Exception {
        Store store = saveActiveBusinessStore("catalog-status@example.com");
        Product reserved = saveProduct(store, "예약 상품", 10_000L);
        reserved.reserve();
        persistProductState(reserved);
        Product soldOut = saveProduct(store, "판매완료 상품", 20_000L);
        soldOut.reserve();
        soldOut.markSoldOutFromReservation();
        persistProductState(soldOut);

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId())
                        .queryParam("status", "RESERVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(reserved.getId()));

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId())
                        .queryParam("status", "SOLD_OUT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(soldOut.getId()));

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId())
                        .queryParam("status", "HIDDEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 재고형_상품은_저재고만_구매자에게_수량을_보여주고_품절_상태를_계산한다() throws Exception {
        Store store = saveActiveBusinessStore("availability-storefront@example.com");
        Product lowStock = saveStockProduct(store, "저재고 상품", 5, 3);
        Product soldOut = saveStockProduct(store, "품절 상품", 5, 0);

        mockMvc.perform(get("/api/products/{productId}", lowStock.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability.policy").value("STOCK_MANAGED"))
                .andExpect(jsonPath("$.data.availability.status").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data.availability.quantity").value(3))
                .andExpect(jsonPath("$.data.totalQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.reservedQuantity").doesNotExist());

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(lowStock.getId()))
                .andExpect(jsonPath("$.data.content[0].availability.status").value("LOW_STOCK"));

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId())
                        .queryParam("status", "SOLD_OUT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(soldOut.getId()))
                .andExpect(jsonPath("$.data.content[0].availability.status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.content[0].availability.quantity").doesNotExist());
    }

    @Test
    void 상점_상품은_가격순과_동일가격_ID_내림차순으로_정렬한다() throws Exception {
        Store store = saveActiveBusinessStore("catalog-sort@example.com");
        Product expensive = saveProduct(store, "고가 상품", 30_000L);
        Product lowerEqualPriceId = saveProduct(store, "동일가 이전 상품", 10_000L);
        Product higherEqualPriceId = saveProduct(store, "동일가 최신 상품", 10_000L);

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId())
                        .queryParam("sort", "PRICE_ASC")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].price").value(10_000))
                .andExpect(jsonPath("$.data.content[0].id").value(higherEqualPriceId.getId()))
                .andExpect(jsonPath("$.data.content[1].id").value(lowerEqualPriceId.getId()));

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId())
                        .queryParam("sort", "PRICE_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(expensive.getId()))
                .andExpect(jsonPath("$.data.content[1].id").value(higherEqualPriceId.getId()))
                .andExpect(jsonPath("$.data.content[2].id").value(lowerEqualPriceId.getId()));
    }

    @Test
    void 상점_상품_페이지_크기는_최대_40개까지_허용한다() throws Exception {
        Store store = saveActiveBusinessStore("catalog-page-size@example.com");

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId())
                        .queryParam("size", "40"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId())
                        .queryParam("size", "41"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 중단된_상점의_상품은_빈_페이지로_조회한다() throws Exception {
        Store store = saveActiveBusinessStore("catalog-suspended@example.com");
        saveProduct(store, "중단 전 상품", 10_000L);
        store.suspend();
        storeRepository.save(store);

        mockMvc.perform(get("/api/stores/{storeId}/products", store.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 비공개_상태이거나_존재하지_않는_상점의_상품은_조회할_수_없다() throws Exception {
        Store pending = storeRepository.save(Store.applyBusiness(
                saveMember("catalog-pending@example.com", "대기 소유자"),
                "대기 상점", "대기 소개", "대기 법인", "333-33-33333"
        ));
        Store rejected = Store.applyBusiness(
                saveMember("catalog-rejected@example.com", "거절 소유자"),
                "거절 상점", "거절 소개", "거절 법인", "444-44-44444"
        );
        rejected.reject("서류 미비");
        storeRepository.save(rejected);

        assertCatalogStoreNotFound(pending.getId());
        assertCatalogStoreNotFound(rejected.getId());
        assertCatalogStoreNotFound(999_999L);
    }

    private Store saveActiveBusinessStore(String email) {
        Store store = Store.applyBusiness(
                saveMember(email, "소유자"),
                "공개 상점", "공개 소개", "비공개 법인", "999-99-99999"
        );
        store.approve();
        return storeRepository.save(store);
    }

    private Product saveProduct(Store store, String title) {
        return saveProduct(store, title, 10_000L);
    }

    private Product saveProduct(Store store, String title, long price) {
        return transactionTemplate.execute(status -> {
            Store managedStore = entityManager.find(Store.class, store.getId());
            Product product = Product.create(managedStore, title, "설명", price);
            entityManager.persist(product);
            entityManager.flush();
            return product;
        });
    }

    private Product saveStockProduct(Store store, String title, int lowStockThreshold, int totalQuantity) {
        return transactionTemplate.execute(status -> {
            Store managedStore = entityManager.find(Store.class, store.getId());
            Product product = Product.create(
                    managedStore,
                    title,
                    "설명",
                    10_000L,
                    ProductSalesPolicy.STOCK_MANAGED,
                    lowStockThreshold,
                    totalQuantity
            );
            entityManager.persist(product);
            entityManager.persist(Inventory.initialize(product, totalQuantity));
            entityManager.flush();
            return product;
        });
    }

    private void persistProductState(Product product) {
        transactionTemplate.executeWithoutResult(status -> {
            Product managedProduct = entityManager.find(Product.class, product.getId());
            jdbcTemplate.update("update products set status = ? where id = ?", product.getStatus().name(), managedProduct.getId());
        });
    }

    private void addImages(Product product, String representativeUrl, String otherUrl) {
        transactionTemplate.executeWithoutResult(status -> {
            Product managedProduct = entityManager.find(Product.class, product.getId());
            managedProduct.addImage(representativeUrl);
            managedProduct.addImage(otherUrl);
            entityManager.flush();
        });
    }

    private void saveWishlist(Member buyer, Product product) {
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(WishlistItem.create(
                entityManager.find(Member.class, buyer.getId()),
                entityManager.find(Product.class, product.getId())
        )));
    }

    private Long createStoreWidePromotion(Long productId, long discountAmount) {
        Long storeId = jdbcTemplate.queryForObject("select store_id from products where id = ?", Long.class, productId);
        return jdbcTemplate.queryForObject("""
                insert into promotion_campaigns (
                    version, store_id, scope, discount_type, discount_value, priority, title,
                    start_at, end_at, lifecycle_status, created_at, updated_at
                ) values (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', ?, 10, '상점 할인',
                    current_timestamp - interval '1 minute', current_timestamp + interval '1 minute', 'DRAFT',
                    current_timestamp, current_timestamp)
                returning id
                """, Long.class, storeId, discountAmount);
    }

    private void saveCart(Member buyer, Product product) {
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(CartItem.create(
                entityManager.find(Member.class, buyer.getId()),
                entityManager.find(Product.class, product.getId())
        )));
    }

    private String accessToken(Member member) {
        return jwtProvider.createAccessToken(member.getId(), member.getEmail(), member.getRole());
    }

    private void saveReview(Member buyer, Product product, int rating) {
        transactionTemplate.executeWithoutResult(status -> {
            Member managedBuyer = entityManager.find(Member.class, buyer.getId());
            Product managedProduct = entityManager.find(Product.class, product.getId());
            Order order = Order.create(managedBuyer, managedProduct);
            entityManager.persist(order);
            entityManager.persist(Review.create(order, rating, "리뷰"));
            entityManager.flush();
        });
    }

    private Member saveMember(String email, String nickname) {
        return memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), nickname));
    }

    private void assertStoreNotFound(Long storeId) throws Exception {
        mockMvc.perform(get("/api/stores/{storeId}", storeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    private void assertCatalogStoreNotFound(Long storeId) throws Exception {
        mockMvc.perform(get("/api/stores/{storeId}/products", storeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }
}
