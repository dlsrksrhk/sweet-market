package com.sweet.market.purchase.application;

public record DirectPurchaseCommand(
        Long buyerId,
        Long productId,
        Long memberCouponId
) {
}
