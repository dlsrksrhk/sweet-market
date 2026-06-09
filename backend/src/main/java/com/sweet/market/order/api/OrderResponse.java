package com.sweet.market.order.api;

import java.time.LocalDateTime;

import com.sweet.market.order.domain.Order;

public record OrderResponse(
        Long id,
        Long buyerId,
        String buyerNickname,
        Long productId,
        Long sellerId,
        String sellerNickname,
        String productTitle,
        long productPrice,
        String status,
        String productStatus,
        LocalDateTime orderedAt,
        LocalDateTime canceledAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getBuyer().getId(),
                order.getBuyer().getNickname(),
                order.getProduct().getId(),
                order.getProduct().getSeller().getId(),
                order.getProduct().getSeller().getNickname(),
                order.getProduct().getTitle(),
                order.getProduct().getPrice(),
                order.getStatus().name(),
                order.getProduct().getStatus().name(),
                order.getOrderedAt(),
                order.getCanceledAt()
        );
    }
}
