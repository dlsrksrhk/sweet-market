package com.sweet.market.refund.api;

import java.time.LocalDateTime;

import com.sweet.market.refund.domain.RefundRequest;

public record RefundRequestResponse(
        Long id,
        Long orderId,
        Long productId,
        String productTitle,
        Long buyerId,
        String buyerNickname,
        String reason,
        String status,
        LocalDateTime requestedAt,
        Long handledById,
        LocalDateTime handledAt,
        String rejectReason
) {

    public static RefundRequestResponse from(RefundRequest refundRequest) {
        return new RefundRequestResponse(
                refundRequest.getId(),
                refundRequest.getOrder().getId(),
                refundRequest.getOrder().getProduct().getId(),
                refundRequest.getOrder().getProduct().getTitle(),
                refundRequest.getBuyer().getId(),
                refundRequest.getBuyer().getNickname(),
                refundRequest.getReason(),
                refundRequest.getStatus().name(),
                refundRequest.getRequestedAt(),
                refundRequest.getHandledBy() == null ? null : refundRequest.getHandledBy().getId(),
                refundRequest.getHandledAt(),
                refundRequest.getRejectReason()
        );
    }
}
