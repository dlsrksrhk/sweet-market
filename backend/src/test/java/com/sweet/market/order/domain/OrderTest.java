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
    void 이미_취소된_주문은_다시_취소할_수_없다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        Order order = Order.create(buyer, product);
        order.cancel();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order cannot be canceled: CANCELED");
    }
}
