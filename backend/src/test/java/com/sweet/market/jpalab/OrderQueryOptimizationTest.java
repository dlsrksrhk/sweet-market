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
import com.sweet.market.order.api.OrderSummaryResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.query.OrderQueryService;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;

import jakarta.persistence.PersistenceUnitUtil;

class OrderQueryOptimizationTest extends QueryOptimizationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderQueryService orderQueryService;

    @Test
    @Transactional
    void 주문_목록_단순_조회는_product_seller_N_plus_1이_발생한다() {
        Member buyer = saveOrdersWithDifferentSellers();
        flushAndClear();
        resetStatistics();

        List<String> summaries;
        // Streaming keeps lazy product/seller loads observable; getResultList can be masked by default_batch_fetch_size.
        try (Stream<Order> orders = entityManager.createQuery(
                        "select o from Order o where o.buyer.id = :buyerId order by o.id desc",
                        Order.class
                )
                .setParameter("buyerId", buyer.getId())
                .getResultStream()) {
            summaries = orders
                    .map(order -> {
                        Product product = order.getProduct();
                        return product.getTitle() + ":" + product.getStore().getOwnerMember().getNickname();
                    })
                    .toList();
        }

        assertThat(summaries).hasSize(3);
        assertThat(queryCount()).isGreaterThanOrEqualTo(7);
    }

    @Test
    @Transactional
    void 주문_목록_최적화_조회는_product_seller를_함께_로딩한다() {
        Member buyer = saveOrdersWithDifferentSellers();
        flushAndClear();
        resetStatistics();

        List<OrderSummaryResponse> summaries = orderQueryService.findMine(buyer.getId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(summaries).hasSize(3);
        assertThat(queryCount()).isLessThanOrEqualTo(2);
    }

    @Test
    @Transactional
    void 주문_목록_최적화_조회는_product_seller가_로딩되어_있다() {
        Member buyer = saveOrdersWithDifferentSellers();
        flushAndClear();
        resetStatistics();

        List<Order> orders = orderRepository.findByBuyerIdOrderByIdDesc(buyer.getId(), PageRequest.of(0, 10))
                .getContent();

        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        assertThat(orders).hasSize(3);
        assertThat(orders)
                .allSatisfy(order -> {
                    assertThat(persistenceUnitUtil.isLoaded(order, "product")).isTrue();
                    assertThat(persistenceUnitUtil.isLoaded(order.getProduct(), "store")).isTrue();
                });
        assertThat(queryCount()).isLessThanOrEqualTo(2);
    }

    private Member saveOrdersWithDifferentSellers() {
        Member buyer = memberRepository.save(Member.create(
                "buyer@example.com",
                "encoded-password",
                "buyer"
        ));

        for (int i = 1; i <= 3; i++) {
            Member seller = memberRepository.save(Member.create(
                    "order-seller" + i + "@example.com",
                    "encoded-password",
                    "orderSeller" + i
            ));
            Product product = productRepository.save(Product.create(
                    seller,
                    "Order Product " + i,
                    "Order Description " + i,
                    10_000L * i
            ));
            orderRepository.save(Order.create(buyer, product));
        }

        return buyer;
    }
}
