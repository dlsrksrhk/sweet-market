package com.sweet.market.order.admin;

import com.sweet.market.order.domain.OrderStatus;

public record AdminOrderSearchRequest(
        Long buyerId,
        Long sellerId,
        OrderStatus status,
        Long productId
) {
}
