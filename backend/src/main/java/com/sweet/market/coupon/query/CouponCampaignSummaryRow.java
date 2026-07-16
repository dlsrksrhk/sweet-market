package com.sweet.market.coupon.query;

import com.sweet.market.coupon.domain.*;

import java.time.Instant;

/**
 * Paged owner-list projection: it deliberately excludes target collections.
 */
public record CouponCampaignSummaryRow(
        Long id, CouponCampaignOwnerType ownerType, Long storeId, String storeName,
        CouponScope scope, CouponDiscountType discountType, long discountValue, Long maxDiscountAmount,
        long minimumPurchaseAmount, boolean stackable, String title, String label,
        Instant issueStartsAt, Instant issueEndsAt, CouponValidityType validityType,
        Instant commonExpiresAt, Integer validityDays, Integer issueLimit, int issuedCount,
        CouponLifecycleStatus lifecycleStatus, long targetCount
) {
}
