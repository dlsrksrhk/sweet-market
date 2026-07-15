package com.sweet.market.coupon.query;

import java.time.Instant;

import com.sweet.market.coupon.domain.CouponDiscountType;
import com.sweet.market.coupon.domain.CouponCampaignOwnerType;
import com.sweet.market.coupon.domain.CouponLifecycleStatus;
import com.sweet.market.coupon.domain.CouponScope;
import com.sweet.market.coupon.domain.MemberCouponStatus;

public record MemberCouponWalletRow(
        Long id, Long campaignId, String title, String label, CouponCampaignOwnerType source, Long storeId, String storeName,
        CouponDiscountType discountType, long discountValue, Long maxDiscountAmount,
        long minimumPurchaseAmount, CouponScope scope, boolean stackable,
        Instant issuedAt, Instant validUntil, MemberCouponStatus persistedStatus,
        CouponLifecycleStatus campaignLifecycleStatus, Instant campaignIssueStartsAt, Instant campaignIssueEndsAt
) {
}
