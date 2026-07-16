package com.sweet.market.purchase.api;

import java.util.List;

public record CartCheckoutFailure(
        List<CartCheckoutFailureItem> items
) {
}
