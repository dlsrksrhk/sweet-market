package com.sweet.market.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.product.domain.Product;
import com.sweet.market.settlement.api.SettlementResponse;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.query.SettlementQueryService;
import com.sweet.market.settlement.repository.SettlementRepository;

import jakarta.persistence.PersistenceUnitUtil;

class SettlementQueryOptimizationTest extends QueryOptimizationTestSupport {

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementQueryService settlementQueryService;

    @Test
    @Transactional
    void 정산_목록_단순_조회는_order_product_N_plus_1이_발생한다() {
        Member seller = saveSettlements();
        flushAndClear();
        resetStatistics();

        List<String> productTitles;
        // Streaming keeps lazy order/product loads observable; getResultList can be masked by default_batch_fetch_size.
        try (Stream<Settlement> settlements = entityManager.createQuery(
                        "select s from Settlement s where s.seller.id = :sellerId order by s.id desc",
                        Settlement.class
                )
                .setParameter("sellerId", seller.getId())
                .getResultStream()) {
            productTitles = settlements
                    .map(settlement -> settlement.getOrder().getId() + ":" + settlement.getOrder().getProduct().getTitle())
                    .toList();
        }

        assertThat(productTitles).hasSize(3);
        assertThat(queryCount()).isGreaterThanOrEqualTo(7);
    }

    @Test
    @Transactional
    void 정산_목록_최적화_조회는_한_번의_쿼리로_응답한다() {
        Member seller = saveSettlements();
        flushAndClear();
        resetStatistics();

        List<SettlementResponse> responses = settlementQueryService.findMine(seller.getId());

        assertThat(responses).hasSize(3);
        assertThat(queryCount()).isLessThanOrEqualTo(1);
    }

    @Test
    @Transactional
    void 정산_목록_최적화_조회는_order_product를_함께_로딩한다() {
        Member seller = saveSettlements();
        flushAndClear();
        resetStatistics();

        List<Settlement> settlements = settlementRepository.findBySellerIdOrderByIdDesc(seller.getId());

        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        assertThat(settlements).hasSize(3);
        assertThat(settlements)
                .allSatisfy(settlement -> {
                    Order order = settlement.getOrder();

                    assertThat(persistenceUnitUtil.isLoaded(settlement, "order")).isTrue();
                    assertThat(persistenceUnitUtil.isLoaded(order, "product")).isTrue();
                    assertThat(persistenceUnitUtil.isLoaded(settlement, "seller")).isTrue();
                });
        assertThat(queryCount()).isLessThanOrEqualTo(1);
    }

    @Test
    void 정산_목록_최적화_조회는_필요한_그래프만_선언한다() throws NoSuchMethodException {
        Method method = SettlementRepository.class.getMethod("findBySellerIdOrderByIdDesc", Long.class);

        EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);

        assertThat(entityGraph.attributePaths())
                .containsExactlyInAnyOrder("order", "order.product", "seller");
    }

    private Member saveSettlements() {
        Member seller = Member.create(
                "settlement-seller@example.com",
                "encoded-password",
                "settlementSeller"
        );
        entityManager.persist(seller);

        for (int i = 1; i <= 3; i++) {
            Member buyer = Member.create(
                    "settlement-buyer" + i + "@example.com",
                    "encoded-password",
                    "settlementBuyer" + i
            );
            entityManager.persist(buyer);

            Product product = Product.create(
                    seller,
                    "Settlement Product " + i,
                    "Settlement Description " + i,
                    10_000L * i
            );
            entityManager.persist(product);

            Order order = Order.create(buyer, product);
            entityManager.persist(order);

            entityManager.persist(Payment.approve(order, "payment-" + i));
            Delivery delivery = Delivery.start(order, "tracking-" + i);
            entityManager.persist(delivery);
            delivery.complete();
            order.confirm();

            entityManager.persist(Settlement.create(order));
        }

        return seller;
    }
}
