package com.sweet.market.purchase.api;

public record CartCheckoutFailureResponse(
        String code,
        String message,
        CartCheckoutFailure data
) {
}
