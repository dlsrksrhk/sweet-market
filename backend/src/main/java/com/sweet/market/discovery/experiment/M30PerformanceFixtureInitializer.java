package com.sweet.market.discovery.experiment;

import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.coupon.domain.CouponCampaignOwnerType;
import com.sweet.market.coupon.domain.CouponDiscountType;
import com.sweet.market.coupon.domain.CouponScope;
import com.sweet.market.coupon.domain.CouponValidityType;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.promotion.domain.PromotionCampaign;
import com.sweet.market.promotion.domain.PromotionDiscountType;
import com.sweet.market.promotion.domain.PromotionScope;
import com.sweet.market.store.domain.Store;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Component
@Profile("performance-fixture")
@ConditionalOnProperty(prefix = "market.performance-fixture", name = "initialize", havingValue = "true", matchIfMissing = true)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class M30PerformanceFixtureInitializer implements ApplicationRunner {

    public static final String FIXTURE_VERSION = "m30-v1";
    public static final long RANDOM_SEED = 310031L;
    public static final Instant FIXTURE_INSTANT = Instant.parse("2026-07-17T00:00:00Z");
    public static final int BATCH_SIZE = 500;

    private static final int BUSINESS_STORE_COUNT = 10;
    private static final int BUYER_COUNT = 250;
    private static final int PRODUCT_COUNT = 10_000;
    private static final int PROMOTION_COUNT = 20;
    private static final int COUPON_COUNT = 20;
    private static final int WISHLIST_COUNT = 50_000;
    private static final int PRODUCT_VIEW_COUNT = 200_000;
    private static final String FIXTURE_PASSWORD = "password123";
    private static final List<String> CORE_TABLES = List.of(
            "members", "stores", "products", "inventories", "promotion_campaigns", "coupon_campaigns",
            "wishlist_items", "product_view_events"
    );

    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final String fixtureVersion;
    private final long randomSeed;

    public M30PerformanceFixtureInitializer(
            EntityManager entityManager,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate,
            @Value("${market.performance-fixture.version}") String fixtureVersion,
            @Value("${market.performance-fixture.random-seed}") long randomSeed
    ) {
        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.fixtureVersion = fixtureVersion;
        this.randomSeed = randomSeed;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        run();
    }

    @Transactional
    public void run() {
        verifyConfiguration();
        requireEmptyMarketplace();

        FixtureMembers members = createMembers();
        List<Long> storeIds = createBusinessStores(members.ownerIds());
        List<Long> productIds = createProductsAndInventories(storeIds);
        createCampaigns(storeIds);
        createWishlists(members.buyerIds(), productIds);
        createProductViews(productIds);
    }

    private void verifyConfiguration() {
        if (!FIXTURE_VERSION.equals(fixtureVersion) || randomSeed != RANDOM_SEED) {
            throw new IllegalStateException(
                    "m30-v1 performance fixture requires version m30-v1 and random seed 310031"
            );
        }
    }

    private void requireEmptyMarketplace() {
        List<String> nonemptyTables = CORE_TABLES.stream()
                .filter(table -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class) > 0)
                .toList();
        if (!nonemptyTables.isEmpty()) {
            throw new IllegalStateException(
                    "m30-v1 performance fixture requires an empty marketplace database; nonempty tables: "
                            + String.join(", ", nonemptyTables)
            );
        }
    }

    private FixtureMembers createMembers() {
        String encodedPassword = passwordEncoder.encode(FIXTURE_PASSWORD);
        entityManager.persist(Member.createAdmin("admin@example.com", encodedPassword, "m30-admin"));

        List<Long> ownerIds = new ArrayList<>(BUSINESS_STORE_COUNT);
        for (int index = 1; index <= BUSINESS_STORE_COUNT; index++) {
            Member owner = Member.create(
                    "m30-owner-%02d@example.test".formatted(index),
                    encodedPassword,
                    "m30-owner-%02d".formatted(index)
            );
            entityManager.persist(owner);
            ownerIds.add(owner.getId());
        }

        List<Long> buyerIds = new ArrayList<>(BUYER_COUNT);
        for (int index = 1; index <= BUYER_COUNT; index++) {
            Member buyer = Member.create(
                    "m30-buyer-%03d@example.test".formatted(index),
                    encodedPassword,
                    "m30-buyer-%03d".formatted(index)
            );
            entityManager.persist(buyer);
            buyerIds.add(buyer.getId());
        }
        flushAndClear();
        return new FixtureMembers(List.copyOf(ownerIds), List.copyOf(buyerIds));
    }

    private List<Long> createBusinessStores(List<Long> ownerIds) {
        List<Long> storeIds = new ArrayList<>(BUSINESS_STORE_COUNT);
        for (int index = 0; index < BUSINESS_STORE_COUNT; index++) {
            Member owner = entityManager.getReference(Member.class, ownerIds.get(index));
            Store store = Store.applyBusiness(
                    owner,
                    "M30 대표 상점 %02d".formatted(index + 1),
                    "m30-v1 대표 성능 fixture 상점",
                    "M30 Representative Business %02d".formatted(index + 1),
                    "M30-%010d".formatted(index + 1)
            );
            store.approve();
            entityManager.persist(store);
            storeIds.add(store.getId());
        }
        flushAndClear();
        return List.copyOf(storeIds);
    }

    private List<Long> createProductsAndInventories(List<Long> storeIds) {
        Random random = new Random(randomSeed);
        ProductCategory[] categories = ProductCategory.values();
        List<Long> productIds = new ArrayList<>(PRODUCT_COUNT);
        for (int index = 0; index < PRODUCT_COUNT; index++) {
            Store store = entityManager.getReference(Store.class, storeIds.get(index % storeIds.size()));
            int stock = 20 + random.nextInt(181);
            Product product = Product.create(
                    store,
                    "M30 Fixture Product %05d".formatted(index + 1),
                    "Deterministic m30-v1 representative catalog product %05d".formatted(index + 1),
                    10_000L + random.nextInt(490_001),
                    ProductSalesPolicy.STOCK_MANAGED,
                    5 + random.nextInt(16),
                    stock,
                    categories[index % categories.length]
            );
            product.addLegacyImage("https://fixture.invalid/m30/products/%05d.jpg".formatted(index + 1));
            entityManager.persist(product);
            entityManager.persist(Inventory.initialize(product, stock));
            productIds.add(product.getId());
            flushBatch(index + 1);
        }
        flushRemainder(PRODUCT_COUNT);
        return List.copyOf(productIds);
    }

    private void createCampaigns(List<Long> storeIds) {
        Instant startAt = FIXTURE_INSTANT.minusSeconds(86_400);
        for (int index = 0; index < PROMOTION_COUNT; index++) {
            Store store = entityManager.getReference(Store.class, storeIds.get(index % storeIds.size()));
            PromotionCampaign campaign = PromotionCampaign.create(
                    store,
                    PromotionScope.STORE_WIDE,
                    PromotionDiscountType.PERCENTAGE,
                    5 + index % 20,
                    index % 5,
                    "M30 Promotion %02d".formatted(index + 1),
                    "m30-v1 scheduled promotion",
                    startAt,
                    FIXTURE_INSTANT.plusSeconds(86_400L * (20 + index)),
                    List.of()
            );
            campaign.schedule(FIXTURE_INSTANT);
            entityManager.persist(campaign);
        }

        for (int index = 0; index < COUPON_COUNT; index++) {
            Store store = entityManager.getReference(Store.class, storeIds.get(index % storeIds.size()));
            CouponCampaign campaign = CouponCampaign.create(
                    CouponCampaignOwnerType.STORE,
                    store,
                    CouponScope.ALL_PRODUCTS,
                    CouponDiscountType.PERCENTAGE,
                    5 + index % 15,
                    50_000L,
                    10_000L,
                    false,
                    "M30 Coupon %02d".formatted(index + 1),
                    "m30-v1 scheduled coupon",
                    startAt,
                    FIXTURE_INSTANT.plusSeconds(86_400L * (20 + index)),
                    CouponValidityType.DAYS_FROM_ISSUANCE,
                    null,
                    30,
                    100_000,
                    List.of()
            );
            campaign.schedule(FIXTURE_INSTANT);
            entityManager.persist(campaign);
        }
        flushAndClear();
    }

    private void createWishlists(List<Long> buyerIds, List<Long> productIds) {
        LocalDateTime createdAt = LocalDateTime.ofInstant(FIXTURE_INSTANT.minusSeconds(3_600), ZoneOffset.UTC);
        for (int batchStart = 0; batchStart < WISHLIST_COUNT; batchStart += BATCH_SIZE) {
            int start = batchStart;
            int size = Math.min(BATCH_SIZE, WISHLIST_COUNT - start);
            jdbcTemplate.batchUpdate(
                    "INSERT INTO wishlist_items (buyer_id, product_id, created_at) VALUES (?, ?, ?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement statement, int batchIndex) throws SQLException {
                            int sequence = start + batchIndex;
                            statement.setLong(1, buyerIds.get(sequence % buyerIds.size()));
                            statement.setLong(2, productIds.get(sequence / buyerIds.size()));
                            statement.setTimestamp(3, Timestamp.valueOf(createdAt));
                        }

                        @Override
                        public int getBatchSize() {
                            return size;
                        }
                    }
            );
        }
    }

    private void createProductViews(List<Long> productIds) {
        Random random = new Random(randomSeed);
        for (int batchStart = 0; batchStart < PRODUCT_VIEW_COUNT; batchStart += BATCH_SIZE) {
            int start = batchStart;
            int size = Math.min(BATCH_SIZE, PRODUCT_VIEW_COUNT - start);
            long[] offsets = new long[size];
            for (int index = 0; index < size; index++) {
                offsets[index] = random.nextLong(6L * 86_400L);
            }
            jdbcTemplate.batchUpdate(
                    "INSERT INTO product_view_events (product_id, visitor_hash, viewed_at) VALUES (?, ?, ?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement statement, int batchIndex) throws SQLException {
                            int sequence = start + batchIndex;
                            statement.setLong(1, productIds.get(sequence % productIds.size()));
                            statement.setString(2, syntheticVisitorHash(sequence));
                            statement.setTimestamp(3, Timestamp.from(FIXTURE_INSTANT.minusSeconds(offsets[batchIndex])));
                        }

                        @Override
                        public int getBatchSize() {
                            return size;
                        }
                    }
            );
        }
    }

    private String syntheticVisitorHash(int sequence) {
        String hex = Long.toHexString(randomSeed + sequence).toLowerCase(Locale.ROOT);
        return "0".repeat(64 - hex.length()) + hex;
    }

    private void flushBatch(int completedCount) {
        if (completedCount % BATCH_SIZE == 0) {
            flushAndClear();
        }
    }

    private void flushRemainder(int completedCount) {
        if (completedCount % BATCH_SIZE != 0) {
            flushAndClear();
        }
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private record FixtureMembers(List<Long> ownerIds, List<Long> buyerIds) {
    }
}
