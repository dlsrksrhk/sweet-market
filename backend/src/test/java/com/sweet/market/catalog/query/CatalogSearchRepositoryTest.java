package com.sweet.market.catalog.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.catalog.domain.CatalogAvailabilityFilter;
import com.sweet.market.catalog.domain.CatalogSort;
import com.sweet.market.cart.domain.CartItem;
import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreType;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import com.sweet.market.wishlist.domain.WishlistItem;
import com.sweet.market.wishlist.repository.WishlistItemRepository;

import jakarta.persistence.EntityManager;

class CatalogSearchRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private CatalogSearchRepository repository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 활성_상점의_구매가능_상품만_가격_오름차순_키셋으로_조회한다() {
        Store activeStore = 활성_사업자_상점("active-catalog@example.com");
        Product first = 단품_상품을_저장한다(activeStore, "동일가_이전", "설명", 10_000L, ProductCategory.COMPUTERS);
        Product second = 단품_상품을_저장한다(activeStore, "동일가_이후", "설명", 10_000L, ProductCategory.COMPUTERS);
        Product third = 단품_상품을_저장한다(activeStore, "세번째", "설명", 20_000L, ProductCategory.COMPUTERS);
        Product fourth = 단품_상품을_저장한다(activeStore, "네번째", "설명", 30_000L, ProductCategory.COMPUTERS);
        Product hidden = 단품_상품을_저장한다(activeStore, "숨김", "설명", 1_000L, ProductCategory.COMPUTERS);
        상태를_변경한다(hidden, "HIDDEN");
        Product reserved = 단품_상품을_저장한다(activeStore, "예약", "설명", 1_000L, ProductCategory.COMPUTERS);
        상태를_변경한다(reserved, "RESERVED");
        Product soldOut = 단품_상품을_저장한다(activeStore, "판매완료", "설명", 1_000L, ProductCategory.COMPUTERS);
        상태를_변경한다(soldOut, "SOLD_OUT");
        Product zeroStock = 재고_상품을_저장한다(activeStore, "재고없음", "설명", 1_000L, 3, 0, ProductCategory.COMPUTERS);
        Store suspendedStore = 활성_사업자_상점("suspended-catalog@example.com");
        Product suspendedStoreProduct = 단품_상품을_저장한다(suspendedStore, "중단상점", "설명", 1_000L, ProductCategory.COMPUTERS);
        suspendedStore.suspend();
        storeRepository.saveAndFlush(suspendedStore);

        CatalogSearchCriteria criteria = 조건(CatalogSort.PRICE_ASC, 2);
        List<CatalogProductRow> firstPage = repository.findPage(criteria, null);
        CatalogCursor cursor = new CatalogCursor(
                CatalogSort.PRICE_ASC,
                firstPage.get(1).price(),
                firstPage.get(1).productId(),
                "fingerprint",
                Instant.now().plusSeconds(60)
        );

        List<CatalogProductRow> secondPage = repository.findPage(criteria, cursor);

        assertThat(firstPage).hasSize(3);
        assertThat(firstPage).extracting(CatalogProductRow::productId)
                .containsExactly(first.getId(), second.getId(), third.getId())
                .doesNotContain(hidden.getId(), reserved.getId(), soldOut.getId(), zeroStock.getId(), suspendedStoreProduct.getId());
        assertThat(secondPage).extracting(CatalogProductRow::productId)
                .containsExactly(third.getId(), fourth.getId())
                .doesNotContainAnyElementsOf(firstPage.subList(0, 2).stream().map(CatalogProductRow::productId).toList());
    }

    @Test
    void 가격_내림차순은_동일한_가격에서_ID_내림차순으로_키셋을_조회한다() {
        Store store = 활성_사업자_상점("price-desc-catalog@example.com");
        Product lowerId = 단품_상품을_저장한다(store, "동일가_이전", "설명", 10_000L, ProductCategory.OTHER);
        Product higherId = 단품_상품을_저장한다(store, "동일가_이후", "설명", 10_000L, ProductCategory.OTHER);
        Product expensive = 단품_상품을_저장한다(store, "고가", "설명", 20_000L, ProductCategory.OTHER);

        List<CatalogProductRow> firstPage = repository.findPage(조건(CatalogSort.PRICE_DESC, 2), null);
        CatalogCursor cursor = new CatalogCursor(
                CatalogSort.PRICE_DESC,
                firstPage.get(1).price(),
                firstPage.get(1).productId(),
                "fingerprint",
                Instant.now().plusSeconds(60)
        );

        List<CatalogProductRow> secondPage = repository.findPage(조건(CatalogSort.PRICE_DESC, 2), cursor);

        assertThat(firstPage).extracting(CatalogProductRow::productId)
                .containsExactly(expensive.getId(), higherId.getId(), lowerId.getId());
        assertThat(secondPage).extracting(CatalogProductRow::productId)
                .containsExactly(lowerId.getId());
    }

    @Test
    void 카탈로그_필터와_대표이미지와_구매자_가용성만_투영한다() {
        Store businessStore = 활성_사업자_상점("business-catalog@example.com");
        Store personalStore = 개인_상점("personal-catalog@example.com");
        Product matching = 재고_상품을_저장한다(
                businessStore, "노트북", "설명에만_발견되는_키워드", 15_000L, 3, 2, ProductCategory.COMPUTERS
        );
        대표_이미지를_추가한다(matching, "https://example.com/first.jpg", "https://example.com/representative.jpg");
        Product inStock = 재고_상품을_저장한다(businessStore, "재고충분", "설명", 15_000L, 3, 4, ProductCategory.COMPUTERS);
        Product singleItem = 단품_상품을_저장한다(businessStore, "단품", "설명", 15_000L, ProductCategory.COMPUTERS);
        Product personal = 단품_상품을_저장한다(personalStore, "개인", "설명", 15_000L, ProductCategory.COMPUTERS);

        assertThat(repository.findPage(조건(null, ProductCategory.COMPUTERS, null, null, null, null, null, null, CatalogSort.NEWEST, 10), null))
                .extracting(CatalogProductRow::productId)
                .contains(matching.getId());
        assertThat(repository.findPage(조건(null, null, null, null, CatalogAvailabilityFilter.LOW_STOCK, null, null, null, CatalogSort.NEWEST, 10), null))
                .extracting(CatalogProductRow::productId)
                .contains(matching.getId())
                .doesNotContain(inStock.getId(), singleItem.getId());
        assertThat(repository.findPage(조건(null, null, null, null, null, ProductSalesPolicy.SINGLE_ITEM, null, null, CatalogSort.NEWEST, 10), null))
                .extracting(CatalogProductRow::productId)
                .contains(singleItem.getId())
                .doesNotContain(matching.getId());
        assertThat(repository.findPage(조건(null, null, null, null, null, null, StoreType.PERSONAL, null, CatalogSort.NEWEST, 10), null))
                .extracting(CatalogProductRow::productId)
                .contains(personal.getId())
                .doesNotContain(matching.getId());
        assertThat(repository.findPage(조건(null, null, null, null, null, null, null, businessStore.getId(), CatalogSort.NEWEST, 10), null))
                .extracting(CatalogProductRow::productId)
                .contains(matching.getId())
                .doesNotContain(personal.getId());

        CatalogProductRow row = repository.findPage(조건("발견되는_키워드", null, null, null, null, null, null, null, CatalogSort.NEWEST, 10), null).getFirst();

        assertThat(row.productId()).isEqualTo(matching.getId());
        assertThat(row.representativeImageUrl()).isEqualTo("https://example.com/representative.jpg");
        assertThat(row.availability().status()).isEqualTo(BuyerAvailabilityResponse.AvailabilityStatus.LOW_STOCK);
        assertThat(row.availability().quantity()).isEqualTo(2);
    }

    @Test
    void 관심상품과_장바구니_표시는_현재_페이지의_상품_ID로만_일괄_조회한다() {
        Store store = 활성_사업자_상점("batch-catalog@example.com");
        Member buyer = 회원("catalog-buyer@example.com");
        Product first = 단품_상품을_저장한다(store, "첫번째", "설명", 10_000L, ProductCategory.OTHER);
        Product second = 단품_상품을_저장한다(store, "두번째", "설명", 20_000L, ProductCategory.OTHER);
        Product outsidePage = 단품_상품을_저장한다(store, "페이지밖", "설명", 30_000L, ProductCategory.OTHER);
        transactionTemplate.executeWithoutResult(status -> {
            Member managedBuyer = entityManager.find(Member.class, buyer.getId());
            entityManager.persist(WishlistItem.create(managedBuyer, entityManager.find(Product.class, first.getId())));
            entityManager.persist(CartItem.create(managedBuyer, entityManager.find(Product.class, second.getId())));
            entityManager.persist(WishlistItem.create(managedBuyer, entityManager.find(Product.class, outsidePage.getId())));
            entityManager.persist(CartItem.create(managedBuyer, entityManager.find(Product.class, outsidePage.getId())));
            entityManager.flush();
        });

        List<Long> pageProductIds = List.of(first.getId(), second.getId());

        assertThat(wishlistItemRepository.findProductIdsByBuyerIdAndProductIdIn(buyer.getId(), pageProductIds))
                .containsExactly(first.getId());
        assertThat(cartItemRepository.findProductIdsByBuyerIdAndProductIdIn(buyer.getId(), pageProductIds))
                .containsExactly(second.getId());
    }

    private CatalogSearchCriteria 조건(CatalogSort sort, int size) {
        return 조건(null, null, null, null, null, null, null, null, sort, size);
    }

    private CatalogSearchCriteria 조건(
            String keyword,
            ProductCategory category,
            Long minPrice,
            Long maxPrice,
            CatalogAvailabilityFilter availability,
            ProductSalesPolicy salesPolicy,
            StoreType storeType,
            Long storeId,
            CatalogSort sort,
            int size
    ) {
        return new CatalogSearchCriteria(
                keyword, category, minPrice, maxPrice, availability, salesPolicy, storeType, storeId, sort, size
        );
    }

    private Store 활성_사업자_상점(String email) {
        Store store = Store.applyBusiness(회원(email), "사업자 상점", "소개", "상호", "123-45-67890");
        store.approve();
        return storeRepository.saveAndFlush(store);
    }

    private Store 개인_상점(String email) {
        return storeRepository.saveAndFlush(Store.createPersonal(회원(email), "개인 상점", "소개"));
    }

    private Member 회원(String email) {
        return memberRepository.saveAndFlush(Member.create(email, "encoded-password", "판매자"));
    }

    private Product 단품_상품을_저장한다(Store store, String title, String description, long price, ProductCategory category) {
        return transactionTemplate.execute(status -> {
            Product product = Product.create(entityManager.find(Store.class, store.getId()), title, description, price,
                    ProductSalesPolicy.SINGLE_ITEM, null, null, category);
            entityManager.persist(product);
            entityManager.flush();
            return product;
        });
    }

    private Product 재고_상품을_저장한다(
            Store store,
            String title,
            String description,
            long price,
            int lowStockThreshold,
            int quantity,
            ProductCategory category
    ) {
        return transactionTemplate.execute(status -> {
            Product product = Product.create(entityManager.find(Store.class, store.getId()), title, description, price,
                    ProductSalesPolicy.STOCK_MANAGED, lowStockThreshold, quantity, category);
            entityManager.persist(product);
            entityManager.persist(Inventory.initialize(product, quantity));
            entityManager.flush();
            return product;
        });
    }

    private void 상태를_변경한다(Product product, String status) {
        jdbcTemplate.update("update products set status = ? where id = ?", status, product.getId());
    }

    private void 대표_이미지를_추가한다(Product product, String firstUrl, String representativeUrl) {
        transactionTemplate.executeWithoutResult(status -> {
            Product managedProduct = entityManager.find(Product.class, product.getId());
            managedProduct.addImage(firstUrl);
            managedProduct.addImage(representativeUrl);
            managedProduct.getImages().get(1).changeArrangement(1, true);
            managedProduct.getImages().get(0).changeArrangement(0, false);
            entityManager.flush();
        });
    }
}
