package com.sweet.market.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.api.ProductSummaryResponse;
import com.sweet.market.product.domain.Product;
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
    void 상품_목록_naive_조회는_seller_N_plus_1이_발생한다() {
        seedProductsWithDifferentSellers();
        flushAndClear();
        resetStatistics();

        List<ProductSummaryResponse> responses = entityManager.createQuery(
                        "select p from Product p where p.status = :status order by p.id desc",
                        Product.class
                )
                .setParameter("status", ProductStatus.ON_SALE)
                .getResultStream()
                .map(ProductSummaryResponse::from)
                .toList();

        assertThat(responses).hasSize(3);
        assertThat(queryCount()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @Transactional
    void 상품_목록_최적화_조회는_seller를_함께_로딩한다() {
        seedProductsWithDifferentSellers();
        flushAndClear();
        resetStatistics();

        List<ProductSummaryResponse> responses = productQueryService.findOnSaleProducts(PageRequest.of(0, 10))
                .getContent();

        assertThat(responses).hasSize(3);
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
                    assertThat(persistenceUnitUtil.isLoaded(product, "seller")).isTrue();
                    assertThat(persistenceUnitUtil.isLoaded(product, "images")).isFalse();
                });
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
}
