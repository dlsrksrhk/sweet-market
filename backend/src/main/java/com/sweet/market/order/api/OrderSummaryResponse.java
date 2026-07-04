package com.sweet.market.order.api;

import java.time.LocalDateTime;

import com.sweet.market.order.domain.Order;

public record OrderSummaryResponse(
        Long id,
        Long productId,
        String productTitle,
        long productPrice,
        Long sellerId,
        String sellerNickname,
        String status,
        String productStatus,
        LocalDateTime orderedAt,
        boolean reviewed
) {

    public static OrderSummaryResponse from(Order order) {
        return from(order, false);
    }

    public static OrderSummaryResponse from(Order order, boolean reviewed) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getProduct().getId(),
                order.getProduct().getTitle(),
                order.getProduct().getPrice(),
                order.getProduct().getSeller().getId(),
                order.getProduct().getSeller().getNickname(),
                order.getStatus().name(),
                order.getProduct().getStatus().name(),
                order.getOrderedAt(),
                reviewed
        );
    }
}
