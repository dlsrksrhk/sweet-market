package com.sweet.market.coupon.query;

import com.sweet.market.coupon.domain.*;

import java.time.Instant;

public record AvailableCouponCampaignRow(
        Long id, CouponCampaignOwnerType ownerType, CouponScope scope, CouponDiscountType discountType,
        long discountValue, Long maxDiscountAmount, long minimumPurchaseAmount, boolean stackable,
        String title, String label, Instant issueStartsAt, Instant issueEndsAt,
        CouponValidityType validityType, Instant commonExpiresAt, Integer validityDays,
        Integer issueLimit, int issuedCount, CouponLifecycleStatus lifecycleStatus, Long storeId, String storeName,
        boolean claimed
) {
}
