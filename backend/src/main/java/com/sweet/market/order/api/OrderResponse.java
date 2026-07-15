package com.sweet.market.order.api;

import java.time.LocalDateTime;

import com.sweet.market.order.domain.Order;
import com.sweet.market.refund.domain.RefundRequest;

public record OrderResponse(
        Long id,
        Long buyerId,
        String buyerNickname,
        Long productId,
        Long storeId,
        String storeName,
        String storeType,
        Long sellerId,
        String sellerNickname,
        String productTitle,
        long productPrice,
        long listPrice,
        Long promotionCampaignId,
        long promotionDiscountAmount,
        Long memberCouponId,
        long couponDiscountAmount,
        long finalPrice,
        String status,
        String productStatus,
        LocalDateTime orderedAt,
        LocalDateTime canceledAt,
        String refundStatus,
        LocalDateTime refundRequestedAt,
        LocalDateTime refundHandledAt,
        String refundRejectReason
) {

    public static OrderResponse from(Order order) {
        return from(order, null);
    }

    public static OrderResponse from(Order order, RefundRequest refundRequest) {
        return new OrderResponse(
                order.getId(),
                order.getBuyer().getId(),
                order.getBuyer().getNickname(),
                order.getProduct().getId(),
                order.getProduct().getStore().getId(),
                order.getProduct().getStore().getPublicName(),
                order.getProduct().getStore().getType().name(),
                order.getSeller().getId(),
                order.getSeller().getNickname(),
                order.getProduct().getTitle(),
                order.getFinalPrice(),
                order.getListPrice(),
                order.getPromotionCampaignId(),
                order.getPromotionDiscountAmount(),
                order.getMemberCouponId(),
                order.getCouponDiscountAmount(),
                order.getFinalPrice(),
                order.getStatus().name(),
                order.getProduct().getStatus().name(),
                order.getOrderedAt(),
                order.getCanceledAt(),
                refundRequest == null ? null : refundRequest.getStatus().name(),
                refundRequest == null ? null : refundRequest.getRequestedAt(),
                refundRequest == null ? null : refundRequest.getHandledAt(),
                refundRequest == null ? null : refundRequest.getRejectReason()
        );
    }
}
