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
        for (int index = 0; index < count; index++) {
            jdbcTemplate.update("insert into product_view_events (product_id, visitor_hash, viewed_at) values (?, ?, ?)",
                    product.getId(), "%064d".formatted(index), OffsetDateTime.now());
        }
    }

    private Long savePromotion(Store store) {
        return jdbcTemplate.queryForObject("""
                insert into promotion_campaigns (version, store_id, scope, discount_type, discount_value, priority, title,
                    start_at, end_at, lifecycle_status, created_at, updated_at)
                values (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', 1000, 1, '진행 할인',
                    current_timestamp - interval '1 hour', current_timestamp + interval '1 hour', 'SCHEDULED',
                    current_timestamp, current_timestamp) returning id
                """, Long.class, store.getId());
    }

    private Long saveCoupon(Store store) {
        return jdbcTemplate.queryForObject("""
                insert into coupon_campaigns (version, owner_type, store_id, scope, discount_type, discount_value,
                    max_discount_amount, minimum_purchase_amount, stackable, title, issue_starts_at, issue_ends_at,
                    validity_type, common_expires_at, validity_days, lifecycle_status, issued_count, issue_limit, created_at, updated_at)
                values (0, 'STORE', ?, 'ALL_PRODUCTS', 'FIXED_AMOUNT', 1000, null, 0, false, '진행 쿠폰',
                    current_timestamp - interval '1 hour', current_timestamp + interval '1 hour',
                    'COMMON_EXPIRY', current_timestamp + interval '2 hours', null, 'SCHEDULED', 0, null,
                    current_timestamp, current_timestamp) returning id
                """, Long.class, store.getId());
    }
}
