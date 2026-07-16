package com.sweet.market.cart.api;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CartCheckoutRequest(
        @NotNull(message = "장바구니 항목 ID는 필수입니다.")
        List<Long> cartItemIds
) {
}
