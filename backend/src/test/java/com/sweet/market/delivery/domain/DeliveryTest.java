package com.sweet.market.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.product.domain.Product;

class DeliveryTest {

    @Test
    void 배송을_시작하면_주문이_배송중이_된다() {
        Order order = createPaidOrder();

        Delivery delivery = Delivery.start(order, "tracking-123");

        assertThat(delivery.getOrder()).isSameAs(order);
        assertThat(delivery.getTrackingNumber()).isEqualTo("tracking-123");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
        assertThat(delivery.getStartedAt()).isNotNull();
        assertThat(delivery.getCompletedAt()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
    }

    @Test
    void 배송을_완료하면_주문이_배송완료가_된다() {
        Order order = createPaidOrder();
        Delivery delivery = Delivery.start(order, "tracking-123");

        delivery.complete();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getCompletedAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void 배송중인_배송만_완료할_수_있다() {
        Order order = createPaidOrder();
        Delivery delivery = Delivery.start(order, "tracking-123");
        delivery.complete();

        assertThatThrownBy(delivery::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Delivery cannot be completed: DELIVERED");
    }

    private Order createPaidOrder() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        Order order = Order.create(buyer, product);
        Payment.approve(order, "pay_123");
        return order;
    }
}
