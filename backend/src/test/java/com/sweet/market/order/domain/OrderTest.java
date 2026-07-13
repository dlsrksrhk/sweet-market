package com.sweet.market.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.store.domain.Store;

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
    void 재고형_주문을_생성해도_상품_상태는_판매중이다() {
        Member seller = Member.create("stock-seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("stock-buyer@example.com", "encoded-password", "buyer");
        Store store = Store.applyBusiness(seller, "상점", "소개", "법인", "123-45-67890");
        store.approve();
        Product product = Product.create(
                store,
                "재고 상품",
                "설명",
                10_000L,
                ProductSalesPolicy.STOCK_MANAGED,
                2,
                5
        );

        Order order = Order.create(buyer, product);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 재고형_주문의_취소와_구매확정은_상품_상태를_바꾸지_않는다() {
        Member seller = Member.create("stock-seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("stock-buyer@example.com", "encoded-password", "buyer");
        Store store = Store.applyBusiness(seller, "상점", "소개", "법인", "123-45-67890");
        store.approve();
        Product product = Product.create(
                store,
                "재고 상품",
                "설명",
                10_000L,
                ProductSalesPolicy.STOCK_MANAGED,
                2,
                5
        );
        Order canceledOrder = Order.create(buyer, product);
        Order confirmedOrder = Order.create(buyer, product);

        canceledOrder.cancel();
        confirmedOrder.markPaid();
        confirmedOrder.startShipping();
        confirmedOrder.completeDelivery();
        confirmedOrder.confirm();

        assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
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
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(OrderDomainError.CONFIRMATION_NOT_ALLOWED);
    }

    @Test
    void 환불_요청중인_주문은_구매확정할_수_없다() {
        Order order = deliveredOrder();
        order.requestRefund();

        assertDomainError(order::confirm, OrderDomainError.CONFIRMATION_NOT_ALLOWED);
    }

    @Test
    void 판매_불가능한_상품은_주문할_수_없다() {
        Member seller = Member.create("seller-hidden@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer-hidden@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.hide();

        assertDomainError(() -> Order.create(buyer, product), OrderDomainError.PRODUCT_NOT_PURCHASABLE);
    }

    @Test
    void 결제완료_주문은_일반_취소할_수_없다() {
        Order order = createOrder();
        order.markPaid();

        assertDomainError(order::cancel, OrderDomainError.CANCELLATION_NOT_ALLOWED);
    }

    @Test
    void 취소된_주문은_결제완료로_바꿀_수_없다() {
        Order order = createOrder();
        order.cancel();

        assertDomainError(order::markPaid, OrderDomainError.PAYMENT_NOT_ALLOWED);
    }

    @Test
    void 결제완료가_아닌_주문은_결제완료_취소할_수_없다() {
        Order order = createOrder();

        assertDomainError(order::cancelPaidOrder, OrderDomainError.PAID_ORDER_CANCELLATION_NOT_ALLOWED);
    }

    @Test
    void 결제완료가_아닌_주문은_배송을_시작할_수_없다() {
        Order order = createOrder();

        assertDomainError(order::startShipping, OrderDomainError.SHIPPING_NOT_ALLOWED);
    }

    @Test
    void 배송중이_아닌_주문은_배송을_완료할_수_없다() {
        Order order = createOrder();
        order.markPaid();

        assertDomainError(order::completeDelivery, OrderDomainError.DELIVERY_COMPLETION_NOT_ALLOWED);
    }

    @Test
    void 배송완료가_아닌_주문은_환불을_요청할_수_없다() {
        Order order = createOrder();
        order.markPaid();

        assertDomainError(order::requestRefund, OrderDomainError.REFUND_REQUEST_NOT_ALLOWED);
    }

    @Test
    void 환불_요청중이_아닌_주문은_환불할_수_없다() {
        Order order = deliveredOrder();

        assertDomainError(order::markRefunded, OrderDomainError.REFUND_NOT_ALLOWED);
    }

    @Test
    void 환불_요청중이_아닌_주문은_환불을_거절할_수_없다() {
        Order order = deliveredOrder();

        assertDomainError(order::rejectRefund, OrderDomainError.REFUND_REJECTION_NOT_ALLOWED);
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

    private void assertDomainError(ThrowingCallable callable, OrderDomainError expectedError) {
        assertThatThrownBy(callable)
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(expectedError);
    }
}
