package com.sweet.market.order.api;

import jakarta.validation.constraints.NotNull;

public record OrderCreateRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,
        Long memberCouponId
) {
}
