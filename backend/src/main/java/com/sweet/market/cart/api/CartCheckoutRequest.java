package com.sweet.market.cart.api;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record CartCheckoutRequest(
        @NotNull(message = "장바구니 항목 ID는 필수입니다.")
        List<Long> cartItemIds
) {
}
