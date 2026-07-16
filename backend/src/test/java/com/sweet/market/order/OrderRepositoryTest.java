package com.sweet.market.order;

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

class OrderRepositoryTest extends IntegrationTestSupport {

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
    void 취소된_주문이_있어도_같은_상품으로_새_주문을_저장할_수_있다() {
        Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "seller"));
        Member firstBuyer = memberRepository.save(Member.create("first-buyer@example.com", "encoded-password", "firstBuyer"));
        Member secondBuyer = memberRepository.save(Member.create("second-buyer@example.com", "encoded-password", "secondBuyer"));
        Product product = productRepository.save(Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L));

        Order firstOrder = orderRepository.save(Order.create(firstBuyer, product));
        product.reserve();
        entityManager.flush();
        entityManager.clear();

        Order foundFirstOrder = orderRepository.findWithBuyerAndProductById(firstOrder.getId()).orElseThrow();
        foundFirstOrder.cancel();
        entityManager.flush();
        entityManager.clear();

        Product foundProduct = productRepository.findById(product.getId()).orElseThrow();
        Order secondOrder = orderRepository.save(Order.create(secondBuyer, foundProduct));
        foundProduct.reserve();
        entityManager.flush();
        entityManager.clear();

        Order foundSecondOrder = orderRepository.findWithBuyerAndProductById(secondOrder.getId()).orElseThrow();

        assertThat(foundSecondOrder.getProduct().getId()).isEqualTo(product.getId());
        assertThat(foundSecondOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(foundSecondOrder.getProduct().getStatus()).isEqualTo(ProductStatus.RESERVED);
    }
}
