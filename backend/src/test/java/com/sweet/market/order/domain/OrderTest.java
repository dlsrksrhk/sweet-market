package com.sweet.market.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;

class OrderTest {

    @Test
    void 주문을_생성하면_상품이_예약된다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        Order order = Order.create(buyer, product);

        assertThat(order.getBuyer()).isSameAs(buyer);
        assertThat(order.getProduct()).isSameAs(product);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getOrderedAt()).isNotNull();
        assertThat(order.getCanceledAt()).isNull();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.RESERVED);
    }

    @Test
    void 주문을_취소하면_상품이_판매중으로_복구된다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        Order order = Order.create(buyer, product);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCanceledAt()).isNotNull();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 취소된_주문은_다시_취소해도_상태가_유지된다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        Order order = Order.create(buyer, product);
        order.cancel();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getProduct().getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 생성된_주문을_결제완료로_바꾼다() {
        Order order = createOrder();

        order.markPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 결제완료_주문을_취소하면_상품이_판매중으로_복구된다() {
        Order order = createOrder();
        order.markPaid();

        order.cancelPaidOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCanceledAt()).isNotNull();
        assertThat(order.getProduct().getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 결제완료_주문을_배송중으로_바꾼다() {
        Order order = createOrder();
        order.markPaid();

        order.startShipping();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
    }

    @Test
    void 배송중_주문을_배송완료로_바꾼다() {
        Order order = createOrder();
        order.markPaid();
        order.startShipping();

        order.completeDelivery();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void 배송완료_주문을_구매확정한다() {
        Order order = createOrder();
        order.markPaid();
        order.startShipping();
        order.completeDelivery();

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getProduct().getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    void 구매_확정하면_확정_시각을_기록한다() {
        Member seller = Member.create("seller-confirmed-at@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer-confirmed-at@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        Order order = Order.create(buyer, product);
        order.markPaid();
        order.startShipping();
        order.completeDelivery();

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isNotNull();
    }

    @Test
    void 구매_확정하지_않은_주문은_확정_시각이_없다() {
        Member seller = Member.create("seller-no-confirmed-at@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer-no-confirmed-at@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        Order order = Order.create(buyer, product);

        assertThat(order.getConfirmedAt()).isNull();
    }

    @Test
    void 배송완료가_아닌_주문은_구매확정할_수_없다() {
        Order order = createOrder();
        order.markPaid();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order cannot be confirmed: PAID");
    }

    @Test
    void 환불_요청중인_주문은_구매확정할_수_없다() {
        Order order = deliveredOrder();
        order.requestRefund();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(IllegalStateException.class);
    }

    private Order createOrder() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        return Order.create(buyer, product);
    }

    private Order deliveredOrder() {
        Order order = createOrder();
        order.markPaid();
        order.startShipping();
        order.completeDelivery();
        return order;
    }
}
