package com.sweet.market.discovery;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import com.sweet.market.wishlist.domain.WishlistItem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiscoveryApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void 조회_이벤트_픽스처를_초기화한다() {
        jdbcTemplate.execute("TRUNCATE TABLE product_view_events, product_view_deduplications RESTART IDENTITY CASCADE");
    }

    @Test
    void 인기_상품은_최근_칠일_찜_다섯배와_조회수를_합산해_정렬한다() throws Exception {
        Store store = saveActiveStore("discovery-ranking-owner@example.com");
        Product wishlistLeader = saveVisibleProduct(store, "찜 선두");
        Product viewLeader = saveVisibleProduct(store, "조회 선두");
        Member buyer = saveMember("discovery-ranking-buyer@example.com");

        saveWishlist(buyer, wishlistLeader);
        saveWishlist(saveMember("discovery-ranking-buyer-2@example.com"), wishlistLeader);
        saveViews(wishlistLeader, 1);
        saveViews(viewLeader, 9);

        mockMvc.perform(get("/api/discovery/popular-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(wishlistLeader.getId()))
                .andExpect(jsonPath("$.data[1].id").value(viewLeader.getId()))
                .andExpect(jsonPath("$.data[0].wishlisted").value(false))
                .andExpect(jsonPath("$.data[0].carted").value(false));
    }

    @Test
    void 칠일보다_오래된_찜과_조회는_인기점수에서_제외된다() throws Exception {
        Store store = saveActiveStore("discovery-cutoff-owner@example.com");
        Product expired = saveVisibleProduct(store, "지난 인기 상품");
        Product recent = saveVisibleProduct(store, "최근 인기 상품");
        Member buyer = saveMember("discovery-cutoff-buyer@example.com");

        saveWishlist(buyer, expired);
        jdbcTemplate.update("update wishlist_items set created_at = current_timestamp - interval '8 days' where buyer_id = ? and product_id = ?",
                buyer.getId(), expired.getId());
        saveViews(expired, 20, OffsetDateTime.now().minusDays(8));
        saveViews(recent, 1, OffsetDateTime.now());

        mockMvc.perform(get("/api/discovery/popular-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(recent.getId()));
    }

    @Test
    void 같은_인기점수는_상품_ID_내림차순으로_정렬된다() throws Exception {
        Store store = saveActiveStore("discovery-tie-owner@example.com");
        Product first = saveVisibleProduct(store, "동점 첫 상품");
        Product second = saveVisibleProduct(store, "동점 둘 상품");

        saveViews(first, 2);
        saveViews(second, 2);

        mockMvc.perform(get("/api/discovery/popular-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data[1].id").value(first.getId()));
    }

    @Test
    void 인기_상품은_여덟개까지만_반환한다() throws Exception {
        Store store = saveActiveStore("discovery-limit-owner@example.com");
        for (int index = 0; index < 9; index++) {
            Product product = saveVisibleProduct(store, "인기 제한 상품 " + index);
            saveViews(product, 1);
        }

        mockMvc.perform(get("/api/discovery/popular-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8));
    }

    @Test
    void 활성_이벤트는_종료시각과_유형과_ID_순으로_정렬된다() throws Exception {
        Store store = saveActiveStore("discovery-event-order-owner@example.com");
        saveVisibleProduct(store, "이벤트 정렬 상품");
        Long earlyPromotion = savePromotion(store, 1);
        Long earlyCoupon = saveCoupon(store, 1);
        Long latePromotion = savePromotion(store, 2);
        Long lateCoupon = saveCoupon(store, 2);
        setEventEndTime(earlyPromotion, earlyCoupon, 1);
        setEventEndTime(latePromotion, lateCoupon, 2);

        mockMvc.perform(get("/api/discovery/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("PROMOTION"))
                .andExpect(jsonPath("$.data[0].eventId").value(earlyPromotion))
                .andExpect(jsonPath("$.data[1].eventType").value("COUPON"))
                .andExpect(jsonPath("$.data[1].eventId").value(earlyCoupon))
                .andExpect(jsonPath("$.data[2].eventType").value("PROMOTION"))
                .andExpect(jsonPath("$.data[2].eventId").value(latePromotion))
                .andExpect(jsonPath("$.data[3].eventType").value("COUPON"))
                .andExpect(jsonPath("$.data[3].eventId").value(lateCoupon));
    }

    @Test
    void 비공개_상품과_비활성_상점은_이벤트와_인기목록에_노출되지않는다() throws Exception {
        Store activeStore = saveActiveStore("discovery-visible-owner@example.com");
        Product visible = saveVisibleProduct(activeStore, "공개 상품");
        Product hidden = saveVisibleProduct(activeStore, "비공개 상품");
        hidden.hide();
        productRepository.save(hidden);
        Store suspendedStore = saveActiveStore("discovery-suspended-owner@example.com");
        Product suspendedProduct = saveVisibleProduct(suspendedStore, "중지 상점 상품");
        suspendedStore.suspend();
        storeRepository.save(suspendedStore);
        saveViews(visible, 1);
        saveViews(hidden, 100);
        saveViews(suspendedProduct, 100);
        Long promotionId = savePromotion(activeStore);
        savePromotion(suspendedStore);
        saveCoupon(activeStore);
        saveCoupon(suspendedStore);

        mockMvc.perform(get("/api/discovery/popular-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(visible.getId()));
        mockMvc.perform(get("/api/discovery/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/discovery/events/PROMOTION/{eventId}", promotionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(promotionId));
    }

    private Store saveActiveStore(String email) {
        Store store = Store.applyBusiness(saveMember(email), "공개 상점", "소개", "법인", "999-99-99999");
        store.approve();
        return storeRepository.save(store);
    }

    private Member saveMember(String email) {
        return memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), "구매자"));
    }

    private Product saveVisibleProduct(Store store, String title) {
        return transactionTemplate.execute(status -> {
            Product product = Product.create(entityManager.find(Store.class, store.getId()), title, "설명", 10_000L);
            product.replaceImages(List.of(ProductImage.local(
                    "https://example.com/" + title + ".jpg", title + ".jpg", title + ".jpg", "image/jpeg", 100L, 0, true
            )));
            entityManager.persist(product);
            entityManager.flush();
            return product;
        });
    }

    private void saveWishlist(Member buyer, Product product) {
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(WishlistItem.create(
                entityManager.find(Member.class, buyer.getId()), entityManager.find(Product.class, product.getId())
        )));
    }

    private void saveViews(Product product, int count) {
        saveViews(product, count, OffsetDateTime.now());
    }

    private void saveViews(Product product, int count, OffsetDateTime viewedAt) {
        for (int index = 0; index < count; index++) {
            jdbcTemplate.update("insert into product_view_events (product_id, visitor_hash, viewed_at) values (?, ?, ?)",
                    product.getId(), "%064d".formatted(index), viewedAt);
        }
    }

    private Long savePromotion(Store store) {
        return savePromotion(store, 1);
    }

    private Long savePromotion(Store store, int endsInHours) {
        return jdbcTemplate.queryForObject("""
                insert into promotion_campaigns (version, store_id, scope, discount_type, discount_value, priority, title,
                    start_at, end_at, lifecycle_status, created_at, updated_at)
                values (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', 1000, 1, '진행 할인',
                    current_timestamp - interval '1 hour', current_timestamp + (? * interval '1 hour'), 'SCHEDULED',
                    current_timestamp, current_timestamp) returning id
                """, Long.class, store.getId(), endsInHours);
    }

    private Long saveCoupon(Store store) {
        return saveCoupon(store, 1);
    }

    private Long saveCoupon(Store store, int endsInHours) {
        return jdbcTemplate.queryForObject("""
                insert into coupon_campaigns (version, owner_type, store_id, scope, discount_type, discount_value,
                    max_discount_amount, minimum_purchase_amount, stackable, title, issue_starts_at, issue_ends_at,
                    validity_type, common_expires_at, validity_days, lifecycle_status, issued_count, issue_limit, created_at, updated_at)
                values (0, 'STORE', ?, 'ALL_PRODUCTS', 'FIXED_AMOUNT', 1000, null, 0, false, '진행 쿠폰',
                    current_timestamp - interval '1 hour', current_timestamp + (? * interval '1 hour'),
                    'COMMON_EXPIRY', current_timestamp + ((? + 1) * interval '1 hour'), null, 'SCHEDULED', 0, null,
                    current_timestamp, current_timestamp) returning id
                """, Long.class, store.getId(), endsInHours, endsInHours);
    }

    private void setEventEndTime(Long promotionId, Long couponId, int endsInHours) {
        jdbcTemplate.update("""
                update promotion_campaigns
                set end_at = date_trunc('second', current_timestamp) + (? * interval '1 hour')
                where id = ?
                """, endsInHours, promotionId);
        jdbcTemplate.update("""
                update coupon_campaigns
                set issue_ends_at = date_trunc('second', current_timestamp) + (? * interval '1 hour')
                where id = ?
                """, endsInHours, couponId);
    }
}
