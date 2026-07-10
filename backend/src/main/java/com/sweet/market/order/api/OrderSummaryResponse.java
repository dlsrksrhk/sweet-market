package com.sweet.market.order.api;

import java.time.LocalDateTime;

import com.sweet.market.order.domain.Order;
import com.sweet.market.refund.domain.RefundRequest;

public record OrderSummaryResponse(
        Long id,
        Long productId,
        Long storeId,
        String storeName,
        String storeType,
        String productTitle,
        long productPrice,
        Long sellerId,
        String sellerNickname,
        String status,
        String productStatus,
        LocalDateTime orderedAt,
        boolean reviewed,
        String refundStatus,
        LocalDateTime refundRequestedAt,
        LocalDateTime refundHandledAt,
        String refundRejectReason
) {

    public static OrderSummaryResponse from(Order order) {
        return from(order, false);
    }

    public static OrderSummaryResponse from(Order order, boolean reviewed) {
        return from(order, reviewed, null);
    }

    public static OrderSummaryResponse from(Order order, boolean reviewed, RefundRequest refundRequest) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getProduct().getId(),
                order.getProduct().getStore().getId(),
                order.getProduct().getStore().getPublicName(),
                order.getProduct().getStore().getType().name(),
                order.getProduct().getTitle(),
                order.getProduct().getPrice(),
                order.getSeller().getId(),
                order.getSeller().getNickname(),
                order.getStatus().name(),
                order.getProduct().getStatus().name(),
                order.getOrderedAt(),
                reviewed,
                refundRequest == null ? null : refundRequest.getStatus().name(),
                refundRequest == null ? null : refundRequest.getRequestedAt(),
                refundRequest == null ? null : refundRequest.getHandledAt(),
                refundRequest == null ? null : refundRequest.getRejectReason()
        );
    }
}
