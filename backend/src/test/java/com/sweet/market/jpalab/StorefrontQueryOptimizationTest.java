package com.sweet.market.jpalab;

import com.sweet.market.cart.domain.CartItem;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.review.domain.Review;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.operations.StoreCatalogQueryService;
import com.sweet.market.store.operations.StoreCatalogSearchRequest;
import com.sweet.market.store.operations.StoreCatalogSort;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.store.storefront.StorefrontProductSort;
import com.sweet.market.store.storefront.StorefrontQueryService;
import com.sweet.market.wishlist.domain.WishlistItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontQueryOptimizationTest extends QueryOptimizationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreMembershipRepository storeMembershipRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StorefrontQueryService storefrontQueryService;

    @Autowired
    private StoreCatalogQueryService storeCatalogQueryService;

    @Test
    @Transactional
    void 공개_상점_헤더와_첫_상품_페이지는_세_쿼리_이내로_조회한다() {
        Fixture fixture = seedFullPageFixture();
        flushAndClear();
        resetStatistics();

        var storefront = storefrontQueryService.findStorefront(fixture.storeId());
        var products = storefrontQueryService.findProducts(
                fixture.storeId(),
                ProductStatus.ON_SALE,
                StorefrontProductSort.NEWEST,
                0,
                12,
                fixture.viewerId()
        );

        assertThat(storefront.reviewCount()).isEqualTo(2);
        assertThat(products.getContent()).hasSize(12);
        assertThat(products.getContent()).allSatisfy(product -> assertThat(product.thumbnailUrl()).isNotBlank());
        assertThat(products.getContent()).allSatisfy(product -> {
            assertThat(product.availability()).isNotNull();
            assertThat(product.availability().status().name()).isEqualTo("IN_STOCK");
        });
        assertThat(products.getContent()).anySatisfy(product -> {
            assertThat(product.wishlistCount()).isPositive();
            assertThat(product.wishlisted()).isTrue();
            assertThat(product.carted()).isTrue();
        });
        assertThat(queryCount()).isLessThanOrEqualTo(5L);
        assertThat(collectionFetchCount()).isZero();
    }

    @Test
    @Transactional
    void 운영_상점_목록과_요약과_첫_상품_페이지는_여섯_쿼리_이내로_조회한다() {
        Fixture fixture = seedFullPageFixture();
        flushAndClear();
        resetStatistics();

        var stores = storeCatalogQueryService.findOperableStores(fixture.viewerId());
        var summary = storeCatalogQueryService.findSummary(fixture.viewerId(), fixture.storeId());
        var products = storeCatalogQueryService.findProducts(
                fixture.viewerId(),
                fixture.storeId(),
                new StoreCatalogSearchRequest(null, null, StoreCatalogSort.NEWEST, 0, 12)
        );

        assertThat(stores).hasSize(1);
        assertThat(summary.onSaleCount()).isEqualTo(20);
        assertThat(products.getContent()).hasSize(12);
        assertThat(products.getContent()).allSatisfy(product -> assertThat(product.thumbnailUrl()).isNotBlank());
        assertThat(products.getContent()).allSatisfy(product -> {
            assertThat(product.salesPolicy().name()).isEqualTo("SINGLE_ITEM");
            assertThat(product.totalQuantity()).isNull();
            assertThat(product.reservedQuantity()).isNull();
            assertThat(product.availableQuantity()).isNull();
            assertThat(product.lowStockThreshold()).isNull();
        });
        assertThat(queryCount()).isLessThanOrEqualTo(6L);
        assertThat(collectionFetchCount()).isZero();
    }

    private Fixture seedFullPageFixture() {
        Member owner = memberRepository.save(Member.create(
                "query-budget-owner@example.com",
                "encoded-password",
                "쿼리 예산 소유자"
        ));
        Member viewer = memberRepository.save(Member.create(
                "query-budget-viewer@example.com",
                "encoded-password",
                "쿼리 예산 운영자"
        ));
        Store store = Store.applyBusiness(
                owner,
                "쿼리 예산 상점",
                "전체 페이지 조회 픽스처",
                "쿼리 예산 법인",
                "123-45-67890"
        );
        store.approve();
        storeRepository.save(store);
        storeMembershipRepository.save(StoreMembership.createManager(store, viewer));

        for (int index = 1; index <= 20; index++) {
            Product product = Product.create(store, "상품 " + index, "상품 설명 " + index, 10_000L + index);
            product.replaceImages(List.of(
                    image("representative-" + index + ".jpg", 1, true),
                    image("fallback-" + index + ".jpg", 0, false)
            ));
            productRepository.save(product);
            if (index >= 19) {
                entityManager.persist(WishlistItem.create(viewer, product));
                entityManager.persist(CartItem.create(viewer, product));
            }
        }

        for (int index = 1; index <= 2; index++) {
            Product reviewedProduct = Product.create(
                    store,
                    "리뷰 상품 " + index,
                    "리뷰 상품 설명 " + index,
                    20_000L + index
            );
            reviewedProduct.replaceImages(List.of(image("reviewed-" + index + ".jpg", 0, true)));
            productRepository.save(reviewedProduct);
            Order order = Order.create(viewer, reviewedProduct);
            entityManager.persist(order);
            entityManager.persist(Review.create(order, 3 + index, "쿼리 예산 리뷰"));
        }
        entityManager.flush();
        entityManager.createQuery("""
                        update ProductImage image
                        set image.representative = false
                        where image.product.id in (
                            select product.id
                            from Product product
                            where product.store.id = :storeId
                              and product.title in ('상품 19', '상품 20')
                        )
                        """)
                .setParameter("storeId", store.getId())
                .executeUpdate();
        return new Fixture(store.getId(), viewer.getId());
    }

    private ProductImage image(String fileName, int sortOrder, boolean representative) {
        return ProductImage.local(
                "https://example.com/" + fileName,
                fileName,
                fileName,
                "image/jpeg",
                100L,
                sortOrder,
                representative
        );
    }

    private record Fixture(Long storeId, Long viewerId) {
    }
}
