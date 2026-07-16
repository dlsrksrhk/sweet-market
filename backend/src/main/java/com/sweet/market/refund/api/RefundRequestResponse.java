package com.sweet.market.refund.api;

import com.sweet.market.refund.domain.RefundRequest;

import java.time.LocalDateTime;

public record RefundRequestResponse(
        Long id,
        Long orderId,
        Long productId,
        String productTitle,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        String reason,
        String status,
        LocalDateTime requestedAt,
        Long handledById,
        LocalDateTime handledAt,
        String rejectReason,
        Long memberCouponId,
        long couponDiscountAmount
) {

    public static RefundRequestResponse from(RefundRequest refundRequest) {
        return new RefundRequestResponse(
                refundRequest.getId(),
                refundRequest.getOrder().getId(),
                refundRequest.getOrder().getProduct().getId(),
                refundRequest.getOrder().getProduct().getTitle(),
                refundRequest.getBuyer().getId(),
                refundRequest.getBuyer().getNickname(),
                refundRequest.getOrder().getSeller().getId(),
                refundRequest.getOrder().getSeller().getNickname(),
                refundRequest.getReason(),
                refundRequest.getStatus().name(),
                refundRequest.getRequestedAt(),
                refundRequest.getHandledBy() == null ? null : refundRequest.getHandledBy().getId(),
                refundRequest.getHandledAt(),
                refundRequest.getRejectReason(),
                refundRequest.getOrder().getMemberCouponId(),
                refundRequest.getOrder().getCouponDiscountAmount()
        );
    }
}
