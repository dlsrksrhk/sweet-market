package com.sweet.market.refund.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.product.domain.Product;

class RefundRequestTest {

    @Test
    void 배송완료_주문은_환불_요청할_수_있다() {
        Order order = deliveredOrder();

        RefundRequest refundRequest = RefundRequest.request(order, order.getBuyer(), "상품 상태가 설명과 달라 환불을 요청합니다.");

        assertThat(refundRequest.getOrder()).isSameAs(order);
        assertThat(refundRequest.getBuyer()).isSameAs(order.getBuyer());
        assertThat(refundRequest.getReason()).isEqualTo("상품 상태가 설명과 달라 환불을 요청합니다.");
        assertThat(refundRequest.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
        assertThat(refundRequest.getRequestedAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
    }

    @Test
    void 요청된_환불을_승인하면_주문이_환불완료가_된다() {
        Order order = deliveredOrder();
        Member handler = member("handler@example.com", "handler");
        RefundRequest refundRequest = RefundRequest.request(order, order.getBuyer(), "상품 상태가 설명과 달라 환불을 요청합니다.");

        refundRequest.approve(handler);

        assertThat(refundRequest.getStatus()).isEqualTo(RefundRequestStatus.APPROVED);
        assertThat(refundRequest.getHandledBy()).isSameAs(handler);
        assertThat(refundRequest.getHandledAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void 요청된_환불을_거절하면_주문이_배송완료로_돌아간다() {
        Order order = deliveredOrder();
        Member handler = member("handler@example.com", "handler");
        RefundRequest refundRequest = RefundRequest.request(order, order.getBuyer(), "상품 상태가 설명과 달라 환불을 요청합니다.");

        refundRequest.reject(handler, "상품 설명과 다른 부분을 확인할 수 없습니다.");

        assertThat(refundRequest.getStatus()).isEqualTo(RefundRequestStatus.REJECTED);
        assertThat(refundRequest.getHandledBy()).isSameAs(handler);
        assertThat(refundRequest.getHandledAt()).isNotNull();
        assertThat(refundRequest.getRejectReason()).isEqualTo("상품 설명과 다른 부분을 확인할 수 없습니다.");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void 요청_상태가_아닌_환불은_다시_처리할_수_없다() {
        Order order = deliveredOrder();
        Member handler = member("handler@example.com", "handler");
        RefundRequest refundRequest = RefundRequest.request(order, order.getBuyer(), "상품 상태가 설명과 달라 환불을 요청합니다.");
        refundRequest.approve(handler);

        assertThatThrownBy(() -> refundRequest.reject(handler, "거절 사유입니다."))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 환불_요청은_상품_판매자_소유인지_확인할_수_있다() {
        Order order = deliveredOrder();
        ReflectionTestUtils.setField(order.getProduct().getSeller(), "id", 10L);
        RefundRequest refundRequest = RefundRequest.request(order, order.getBuyer(), "상품 상태가 설명과 달라 환불을 요청합니다.");

        assertThat(refundRequest.isSellerOwnedBy(10L)).isTrue();
        assertThat(refundRequest.isSellerOwnedBy(20L)).isFalse();
    }

    @Test
    void 주문_구매자가_아니면_환불_요청할_수_없다() {
        Order order = deliveredOrder();
        Member otherBuyer = member("other@example.com", "other");

        assertThatThrownBy(() -> RefundRequest.request(order, otherBuyer, "상품 상태가 설명과 달라 환불을 요청합니다."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void 환불_사유가_유효하지_않으면_요청할_수_없고_주문_상태가_유지된다() {
        assertInvalidReason(null);
        assertInvalidReason("");
        assertInvalidReason("   ");
        assertInvalidReason("짧은사유");
    }

    @Test
    void 환불_거절_사유가_유효하지_않으면_거절할_수_없고_주문_상태가_유지된다() {
        assertInvalidRejectReason(null);
        assertInvalidRejectReason("");
        assertInvalidRejectReason("   ");
        assertInvalidRejectReason("짧음");
    }

    @Test
    void 환불_사유와_거절_사유는_최대_길이를_넘을_수_없다() {
        Order order = deliveredOrder();
        String longReason = "가".repeat(501);

        assertThatThrownBy(() -> RefundRequest.request(order, order.getBuyer(), longReason))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        RefundRequest refundRequest = RefundRequest.request(order, order.getBuyer(), "상품 상태가 설명과 달라 환불을 요청합니다.");

        assertThatThrownBy(() -> refundRequest.reject(member("handler@example.com", "handler"), longReason))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(refundRequest.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
    }

    private Order deliveredOrder() {
        Member seller = member("seller@example.com", "seller");
        Member buyer = member("buyer@example.com", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000);
        Order order = Order.create(buyer, product);
        order.markPaid();
        order.startShipping();
        order.completeDelivery();
        return order;
    }

    private Member member(String email, String nickname) {
        return Member.create(email, "encoded-password", nickname);
    }

    private void assertInvalidReason(String reason) {
        Order order = deliveredOrder();

        assertThatThrownBy(() -> RefundRequest.request(order, order.getBuyer(), reason))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    private void assertInvalidRejectReason(String rejectReason) {
        Order order = deliveredOrder();
        RefundRequest refundRequest = RefundRequest.request(order, order.getBuyer(), "상품 상태가 설명과 달라 환불을 요청합니다.");

        assertThatThrownBy(() -> refundRequest.reject(member("handler@example.com", "handler"), rejectReason))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(refundRequest.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
    }
}
