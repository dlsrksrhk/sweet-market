package com.sweet.market.cart.api;

import java.util.List;

import com.sweet.market.order.api.OrderSummaryResponse;

public record CartCheckoutResponse(
        List<OrderSummaryResponse> orders
) {
}
