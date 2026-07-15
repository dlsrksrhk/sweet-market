package com.sweet.market.coupon.query;

import java.time.Instant;

import com.sweet.market.coupon.domain.CouponCampaignOwnerType;
import com.sweet.market.coupon.domain.CouponDiscountType;
import com.sweet.market.coupon.domain.CouponLifecycleStatus;
import com.sweet.market.coupon.domain.CouponScope;
import com.sweet.market.coupon.domain.CouponValidityType;

/** Paged owner-list projection: it deliberately excludes target collections. */
public record CouponCampaignSummaryRow(
        Long id, CouponCampaignOwnerType ownerType, Long storeId, String storeName,
        CouponScope scope, CouponDiscountType discountType, long discountValue, Long maxDiscountAmount,
        long minimumPurchaseAmount, boolean stackable, String title, String label,
        Instant issueStartsAt, Instant issueEndsAt, CouponValidityType validityType,
        Instant commonExpiresAt, Integer validityDays, Integer issueLimit, int issuedCount,
        CouponLifecycleStatus lifecycleStatus, long targetCount
) {
}
