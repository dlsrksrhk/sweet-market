package com.sweet.market.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;

class PaymentTest {

    @Test
    void 결제를_승인하면_주문이_결제완료가_된다() {
        Order order = createOrder();

        Payment payment = Payment.approve(order, "pay_123");

        assertThat(payment.getOrder()).isSameAs(order);
        assertThat(payment.getExternalPaymentId()).isEqualTo("pay_123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isNotNull();
        assertThat(payment.getCanceledAt()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 결제를_취소하면_주문과_상품이_취소_상태로_복구된다() {
        Order order = createOrder();
        Payment payment = Payment.approve(order, "pay_123");

        payment.cancel();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getProduct().getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 승인된_결제만_취소할_수_있다() {
        Order order = createOrder();
        Payment payment = Payment.approve(order, "pay_123");
        payment.cancel();

        assertThatThrownBy(payment::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment cannot be canceled: CANCELED");
    }

    private Order createOrder() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        return Order.create(buyer, product);
    }
}
