package com.sweet.market.coupon.query;

import java.time.Instant;

import com.sweet.market.coupon.domain.CouponCampaignOwnerType;
import com.sweet.market.coupon.domain.CouponDiscountType;
import com.sweet.market.coupon.domain.CouponLifecycleStatus;
import com.sweet.market.coupon.domain.CouponScope;
import com.sweet.market.coupon.domain.CouponValidityType;

public record AvailableCouponCampaignRow(
        Long id, CouponCampaignOwnerType ownerType, CouponScope scope, CouponDiscountType discountType,
        long discountValue, Long maxDiscountAmount, long minimumPurchaseAmount, boolean stackable,
        String title, String label, Instant issueStartsAt, Instant issueEndsAt,
        CouponValidityType validityType, Instant commonExpiresAt, Integer validityDays,
        Integer issueLimit, int issuedCount, CouponLifecycleStatus lifecycleStatus, Long storeId, String storeName, boolean claimed
) {
}
