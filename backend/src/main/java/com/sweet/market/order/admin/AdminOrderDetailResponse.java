package com.sweet.market.order.admin;

import java.time.LocalDateTime;

import com.sweet.market.order.domain.Order;

public record AdminOrderDetailResponse(
        Long orderId,
        Long productId,
        String productTitle,
        long productPrice,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        String status,
        String productStatus,
        LocalDateTime orderedAt,
        LocalDateTime canceledAt,
        LocalDateTime confirmedAt,
        boolean settlementExists
) {

    public static AdminOrderDetailResponse from(Order order, boolean settlementExists) {
        return new AdminOrderDetailResponse(
                order.getId(),
                order.getProduct().getId(),
                order.getProduct().getTitle(),
                order.getFinalPrice(),
                order.getBuyer().getId(),
                order.getBuyer().getNickname(),
                order.getSeller().getId(),
                order.getSeller().getNickname(),
                order.getStatus().name(),
                order.getProduct().getStatus().name(),
                order.getOrderedAt(),
                order.getCanceledAt(),
                order.getConfirmedAt(),
                settlementExists
        );
    }
}
