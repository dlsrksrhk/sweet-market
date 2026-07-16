package com.sweet.market.purchase.api;

public class CartCheckoutFailureException extends RuntimeException {

    private final CartCheckoutFailure failure;

    public CartCheckoutFailureException(CartCheckoutFailure failure) {
        this.failure = failure;
    }

    public CartCheckoutFailure failure() {
        return failure;
    }
}
