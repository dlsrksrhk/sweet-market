package com.sweet.market.coupon.query;

import com.sweet.market.coupon.domain.*;

import java.time.Instant;

public record MemberCouponWalletRow(
        Long id, Long campaignId, String title, String label, CouponCampaignOwnerType source, Long storeId,
        String storeName,
        CouponDiscountType discountType, long discountValue, Long maxDiscountAmount,
        long minimumPurchaseAmount, CouponScope scope, boolean stackable,
        Instant issuedAt, Instant validUntil, MemberCouponStatus persistedStatus,
        CouponLifecycleStatus campaignLifecycleStatus, Instant campaignIssueStartsAt, Instant campaignIssueEndsAt
) {
}
