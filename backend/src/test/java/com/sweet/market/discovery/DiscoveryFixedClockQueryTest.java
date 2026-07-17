package com.sweet.market.discovery;

import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.catalog.domain.CatalogSort;
import com.sweet.market.catalog.query.CatalogProductRow;
import com.sweet.market.catalog.query.CatalogSearchCriteria;
import com.sweet.market.catalog.query.CatalogSearchRepository;
import com.sweet.market.discovery.api.ActiveEventResponse;
import com.sweet.market.discovery.cache.ActiveEventCache;
import com.sweet.market.discovery.experiment.M30PerformanceFixtureInitializer;
import com.sweet.market.discovery.query.DiscoveryQueryService;
import com.sweet.market.discovery.query.DiscoveryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import com.sweet.market.wishlist.repository.WishlistItemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class DiscoveryFixedClockQueryTest extends IntegrationTestSupport {

    private static final Instant FIXED_NOW = M30PerformanceFixtureInitializer.FIXTURE_INSTANT;
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void 고정_clock은_wall_clock과_무관하게_캠페인과_이벤트를_조회한다() {
        Store store = activeStore("fixed-event@example.com");
        visibleProduct(store, "고정 이벤트 상품");
        long promotionId = promotion(store, FIXED_NOW.minusSeconds(3_600), FIXED_NOW.plusSeconds(3_600));
        coupon(store, FIXED_NOW.minusSeconds(3_600), FIXED_NOW.plusSeconds(3_600));

        List<ActiveEventResponse> events = discoveryService().activeEvents();

        assertThat(events).hasSize(2);
        assertThat(events).extracting(ActiveEventResponse::eventId).contains(promotionId);
    }

    @Test
    void 고정_clock은_인기상품의_칠일_경계를_고정한다() {
        Store store = activeStore("fixed-popularity@example.com");
        Product included = visibleProduct(store, "고정 인기 상품");
        jdbcTemplate.update(
                "INSERT INTO product_view_events (product_id, visitor_hash, viewed_at) VALUES (?, ?, ?)",
                included.getId(), "0".repeat(64),
                OffsetDateTime.ofInstant(FIXED_NOW.minusSeconds(7 * 86_400L).plusSeconds(60), ZoneOffset.UTC)
        );

        assertThat(discoveryService().popularProducts(null))
                .extracting(response -> response.id())
                .containsExactly(included.getId());
    }

    @Test
    void 고정_clock은_카탈로그의_프로모션_가격을_고정한다() {
        Store store = activeStore("fixed-catalog@example.com");
        Product product = visibleProduct(store, "고정 카탈로그 상품");
        long promotionId = promotion(store, FIXED_NOW.minusSeconds(3_600), FIXED_NOW.plusSeconds(3_600));
        CatalogSearchRepository repository = new CatalogSearchRepository(
                new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource()), FIXED_CLOCK
        );

        CatalogProductRow row = repository.findPage(criteria(store.getId()), null).getFirst();

        assertThat(row.productId()).isEqualTo(product.getId());
        assertThat(row.promotionId()).isEqualTo(promotionId);
        assertThat(row.effectivePrice()).isEqualTo(9_000L);
    }

    private DiscoveryQueryService discoveryService() {
        DiscoveryRepository repository = new DiscoveryRepository(
                new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource()), FIXED_CLOCK
        );
        return new DiscoveryQueryService(
                repository, wishlistItemRepository, cartItemRepository, new ActiveEventCache(false), FIXED_CLOCK
        );
    }

    private Store activeStore(String email) {
        Member owner = memberRepository.save(Member.create(email, "encoded", "고정 시각 판매자"));
        Store store = Store.applyBusiness(owner, "고정 시각 상점", "소개", "고정 법인", "123-45-67890");
        store.approve();
        return storeRepository.save(store);
    }

    private Product visibleProduct(Store store, String title) {
        Product product = Product.create(store, title, "설명", 10_000L);
        product.replaceImages(List.of(ProductImage.local(
                "https://example.com/fixed-clock.jpg", "fixed-clock.jpg", "fixed-clock.jpg", "image/jpeg", 100L, 0, true
        )));
        entityManager.persist(product);
        entityManager.flush();
        return product;
    }

    private long promotion(Store store, Instant startsAt, Instant endsAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO promotion_campaigns (
                    version, store_id, scope, discount_type, discount_value, priority, title,
                    start_at, end_at, lifecycle_status, created_at, updated_at
                ) VALUES (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', 1000, 1, '고정 프로모션',
                    ?, ?, 'SCHEDULED', ?, ?) RETURNING id
                """, Long.class, store.getId(), Timestamp.from(startsAt), Timestamp.from(endsAt),
                Timestamp.from(FIXED_NOW), Timestamp.from(FIXED_NOW));
    }

    private long coupon(Store store, Instant startsAt, Instant endsAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO coupon_campaigns (
                    version, owner_type, store_id, scope, discount_type, discount_value,
                    max_discount_amount, minimum_purchase_amount, stackable, title,
                    issue_starts_at, issue_ends_at, validity_type, common_expires_at, validity_days,
                    lifecycle_status, issued_count, issue_limit, created_at, updated_at
                ) VALUES (0, 'STORE', ?, 'ALL_PRODUCTS', 'FIXED_AMOUNT', 1000,
                    NULL, 0, FALSE, '고정 쿠폰', ?, ?, 'COMMON_EXPIRY', ?, NULL,
                    'SCHEDULED', 0, NULL, ?, ?) RETURNING id
                """, Long.class, store.getId(), Timestamp.from(startsAt), Timestamp.from(endsAt),
                Timestamp.from(endsAt.plusSeconds(3_600)), Timestamp.from(FIXED_NOW), Timestamp.from(FIXED_NOW));
    }

    private CatalogSearchCriteria criteria(long storeId) {
        return new CatalogSearchCriteria(
                null, null, null, null, null, null, null, storeId, CatalogSort.NEWEST, 20
        );
    }
}
