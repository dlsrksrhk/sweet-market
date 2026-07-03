package com.sweet.market.cart.api;

public record CartResponse(
        Long productId,
        boolean carted
) {
}
