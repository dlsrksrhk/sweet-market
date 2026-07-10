package com.sweet.market.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.admin.AdminProductSummaryResponse;
import com.sweet.market.product.api.ProductSummaryResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.query.ProductQueryService;
import com.sweet.market.product.repository.ProductRepository;

import jakarta.persistence.PersistenceUnitUtil;

class ProductQueryOptimizationTest extends QueryOptimizationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductQueryService productQueryService;

    @Test
    @Transactional
    void 상품_목록_단순_조회는_seller_N_plus_1이_발생한다() {
        seedProductsWithDifferentSellers();
        flushAndClear();
        resetStatistics();

        List<String> sellerNicknames;
        // Streaming keeps lazy seller loads observable; getResultList can be masked by default_batch_fetch_size.
        try (Stream<Product> products = entityManager.createQuery(
                        "select p from Product p where p.status = :status order by p.id desc",
                        Product.class
                )
                .setParameter("status", ProductStatus.ON_SALE)
                .getResultStream()) {
            sellerNicknames = products
                    .map(product -> product.getStore().getOwnerMember().getNickname())
                    .toList();
        }

        assertThat(sellerNicknames).hasSize(3);
        assertThat(queryCount()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @Transactional
    void 상품_목록_최적화_조회는_seller를_함께_로딩한다() {
        seedProductsWithDifferentSellers();
        flushAndClear();
        resetStatistics();

        List<String> sellerNicknames = productRepository.findByStatusOrderByIdDesc(
                        ProductStatus.ON_SALE,
                        PageRequest.of(0, 10)
                )
                .getContent()
                .stream()
                .map(product -> product.getStore().getOwnerMember().getNickname())
                .toList();

        assertThat(sellerNicknames).hasSize(3);
        assertThat(queryCount()).isLessThanOrEqualTo(2);
    }

    @Test
    @Transactional
    void 상품_목록_최적화_조회는_images를_로딩하지_않는다() {
        seedProductsWithDifferentSellers();
        flushAndClear();

        List<Product> products = productRepository.findByStatusOrderByIdDesc(
                        ProductStatus.ON_SALE,
                        PageRequest.of(0, 10)
                )
                .getContent();

        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        assertThat(products).hasSize(3);
        assertThat(products)
                .allSatisfy(product -> {
                    assertThat(persistenceUnitUtil.isLoaded(product, "store")).isTrue();
                    assertThat(persistenceUnitUtil.isLoaded(product, "images")).isFalse();
                });
    }

    @Test
    @Transactional
    void 공개_상품_목록_조회는_이미지_컬렉션을_초기화하지_않는다() {
        seedProductsWithDifferentSellersAndImages();
        flushAndClear();
        resetStatistics();

        List<ProductSummaryResponse> summaries = productQueryService.findOnSaleProducts(null, PageRequest.of(0, 10))
                .getContent();

        assertThat(summaries).hasSize(3);
        assertThat(summaries)
                .allSatisfy(summary -> {
                    assertThat(summary.thumbnailUrl()).startsWith("https://example.com/product-");
                    assertThat(summary.wishlistCount()).isZero();
                    assertThat(summary.wishlisted()).isFalse();
                });
        assertThat(queryCount()).isLessThanOrEqualTo(2);
        assertThat(collectionFetchCount()).isZero();
    }

    @Test
    @Transactional
    void 상품_요약_프로젝션은_대표_이미지의_가장_낮은_순서를_썸네일로_사용한다() {
        Member seller = memberRepository.save(Member.create(
                "seller-representative-order@example.com",
                "encoded-password",
                "seller"
        ));
        Product product = Product.create(seller, "Product", "Description", 10_000L);
        ProductImage nonRepresentativeImage = ProductImage.local(
                "https://example.com/non-representative.jpg",
                "non-representative.jpg",
                "non-representative.jpg",
                "image/jpeg",
                100L,
                0,
                false
        );
        ProductImage lowerSortRepresentativeImage = ProductImage.local(
                "https://example.com/z-lower-sort-representative.jpg",
                "z-lower-sort-representative.jpg",
                "z-lower-sort-representative.jpg",
                "image/jpeg",
                100L,
                1,
                true
        );
        ProductImage higherSortRepresentativeImage = ProductImage.local(
                "https://example.com/a-higher-sort-representative.jpg",
                "a-higher-sort-representative.jpg",
                "a-higher-sort-representative.jpg",
                "image/jpeg",
                100L,
                2,
                false
        );
        product.replaceImages(List.of(
                nonRepresentativeImage,
                lowerSortRepresentativeImage,
                higherSortRepresentativeImage
        ));
        higherSortRepresentativeImage.changeArrangement(2, true);
        productRepository.save(product);
        flushAndClear();

        ProductSummaryResponse sellerSummary = productRepository.findSummariesBySellerIdOrderByIdDesc(
                        seller.getId(),
                        PageRequest.of(0, 10)
                )
                .getContent()
                .get(0);
        AdminProductSummaryResponse adminSummary = productRepository.searchAdminProducts(
                        null,
                        null,
                        null,
                        PageRequest.of(0, 10)
                )
                .getContent()
                .get(0);

        assertThat(sellerSummary.thumbnailUrl()).isEqualTo("https://example.com/z-lower-sort-representative.jpg");
        assertThat(adminSummary.thumbnailUrl()).isEqualTo("https://example.com/z-lower-sort-representative.jpg");
    }

    private void seedProductsWithDifferentSellers() {
        for (int i = 1; i <= 3; i++) {
            Member seller = memberRepository.save(Member.create(
                    "seller" + i + "@example.com",
                    "encoded-password",
                    "seller" + i
            ));
            productRepository.save(Product.create(
                    seller,
                    "Product " + i,
                    "Description " + i,
                    10_000L * i
            ));
        }
    }

    private void seedProductsWithDifferentSellersAndImages() {
        for (int i = 1; i <= 3; i++) {
            Member seller = memberRepository.save(Member.create(
                    "seller-with-images" + i + "@example.com",
                    "encoded-password",
                    "seller" + i
            ));
            Product product = Product.create(
                    seller,
                    "Product " + i,
                    "Description " + i,
                    10_000L * i
            );
            product.replaceImages(List.of(ProductImage.local(
                    "https://example.com/product-" + i + ".jpg",
                    "product-" + i + ".jpg",
                    "product-" + i + ".jpg",
                    "image/jpeg",
                    100L,
                    0,
                    true
            )));
            productRepository.save(product);
        }
    }
}
