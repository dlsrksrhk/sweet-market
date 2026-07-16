package com.sweet.market.settlement.domain;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.coupon.application.CouponDiscountQuote;
import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.product.domain.Product;
import com.sweet.market.promotion.application.PromotionPrice;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void 정산은_프로모션과_쿠폰_할인이_반영된_주문_최종가를_사용한다() {
        Member seller = Member.create("discount-seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("discount-buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 10_000L);
        PromotionPrice promotion = new PromotionPrice(10_000L, 31L, "프로모션", 2_000L, 8_000L);
        CouponDiscountQuote coupon = new CouponDiscountQuote(71L, 1_000L, 7_000L);
        Order order = Order.create(buyer, product, promotion, coupon);
        product.reserve();
        Payment.approve(order, "pay_discount");
        Delivery.start(order, "tracking-discount").complete();
        order.confirm();

        Settlement settlement = Settlement.create(order);

        assertThat(order.getListPrice()).isEqualTo(10_000L);
        assertThat(order.getPromotionDiscountAmount()).isEqualTo(2_000L);
        assertThat(order.getCouponDiscountAmount()).isEqualTo(1_000L);
        assertThat(order.getFinalPrice()).isEqualTo(7_000L);
        assertThat(settlement.getAmount()).isEqualTo(7_000L);
    }

    @Test
    void 확정되지_않은_주문은_정산할_수_없다() {
        Order order = createDeliveredOrder();

        assertThatThrownBy(() -> Settlement.create(order))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(SettlementDomainError.ORDER_NOT_CONFIRMED);
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
        product.reserve();
        Payment.approve(order, "pay_123");
        Delivery.start(order, "tracking-123").complete();
        return order;
    }
}
