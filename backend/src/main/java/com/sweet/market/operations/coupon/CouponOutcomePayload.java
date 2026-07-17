package com.sweet.market.operations.coupon;

public record CouponOutcomePayload(
        String outcomeType,
        String result,
        CouponOutcomeReason reason,
        long campaignId,
        String ownerType,
        Long ownerStoreId,
        Long commerceStoreId,
        Long orderId,
        long couponDiscountAmount
) {
}
