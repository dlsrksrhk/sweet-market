package com.sweet.market.cart.api;

import com.sweet.market.order.api.OrderSummaryResponse;

import java.util.List;

public record CartCheckoutResponse(
        List<OrderSummaryResponse> orders
) {
}
