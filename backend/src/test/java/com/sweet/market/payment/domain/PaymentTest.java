package com.sweet.market.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.common.domain.error.DomainException;
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
    void 취소된_결제는_다시_취소해도_상태가_유지된다() {
        Order order = createOrder();
        Payment payment = Payment.approve(order, "pay_123");
        payment.cancel();

        payment.cancel();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void 승인된_결제는_환불완료로_변경할_수_있다() {
        Order order = createOrder();
        Payment payment = Payment.approve(order, "pay_123");

        payment.refund();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void 결제_환불은_주문_상태를_변경하지_않는다() {
        Order order = createOrder();
        Payment payment = Payment.approve(order, "pay_123");

        payment.refund();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 취소된_결제는_환불할_수_없다() {
        Order order = createOrder();
        Payment payment = Payment.approve(order, "pay_123");
        payment.cancel();

        assertThatThrownBy(payment::refund)
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(PaymentDomainError.REFUND_NOT_ALLOWED);
    }

    @Test
    void 환불된_결제는_취소할_수_없다() {
        Order order = createOrder();
        Payment payment = Payment.approve(order, "pay_123");
        payment.refund();

        assertThatThrownBy(payment::cancel)
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(PaymentDomainError.CANCELLATION_NOT_ALLOWED);
    }

    private Order createOrder() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        return Order.create(buyer, product);
    }
}
