package com.sweet.market.jpalab;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class DirtyCheckingTest extends IntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @Transactional
    void dirty_checking은_주문_취소와_상품_상태_복구를_update로_반영한다() {
        Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "seller"));
        Member buyer = memberRepository.save(Member.create("buyer@example.com", "encoded-password", "buyer"));
        Product product = productRepository.save(Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L));
        Order order = orderRepository.save(Order.create(buyer, product));
        entityManager.flush();
        entityManager.clear();

        Order foundOrder = orderRepository.findWithBuyerAndProductById(order.getId()).orElseThrow();

        foundOrder.cancel();
        entityManager.flush();
        entityManager.clear();

        Order canceledOrder = orderRepository.findWithBuyerAndProductById(order.getId()).orElseThrow();
        Product restoredProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(restoredProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }
}
