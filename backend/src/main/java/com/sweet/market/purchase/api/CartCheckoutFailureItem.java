package com.sweet.market.purchase.api;

public record CartCheckoutFailureItem(
        Long cartItemId,
        Long productId,
        String productTitle,
        Reason reason
) {

    public enum Reason {
        SOLD_OUT,
        UNAVAILABLE
    }
}
