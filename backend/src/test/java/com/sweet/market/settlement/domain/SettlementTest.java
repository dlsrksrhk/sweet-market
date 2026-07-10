package com.sweet.market.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.product.domain.Product;

class SettlementTest {

    @Test
    void 확정된_주문으로_정산을_생성한다() {
        Order order = createConfirmedOrder();

        Settlement settlement = Settlement.create(order);

        assertThat(settlement.getOrder()).isSameAs(order);
        assertThat(settlement.getSeller()).isSameAs(order.getSeller());
        assertThat(settlement.getAmount()).isEqualTo(order.getProduct().getPrice());
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(settlement.getSettledAt()).isNotNull();
    }

    @Test
    void 확정되지_않은_주문은_정산할_수_없다() {
        Order order = createDeliveredOrder();

        assertThatThrownBy(() -> Settlement.create(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order cannot be settled: DELIVERED");
    }

    private Order createConfirmedOrder() {
        Order order = createDeliveredOrder();
        order.confirm();
        return order;
    }

    private Order createDeliveredOrder() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        Order order = Order.create(buyer, product);
        Payment.approve(order, "pay_123");
        Delivery.start(order, "tracking-123").complete();
        return order;
    }
}
