package com.sweet.market.coupon.api;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.sweet.market.coupon.domain.CouponCampaignOwnerType;
import com.sweet.market.coupon.domain.CouponDiscountType;
import com.sweet.market.coupon.domain.CouponEffectiveStatus;
import com.sweet.market.coupon.domain.CouponScope;
import com.sweet.market.coupon.domain.CouponValidityType;
import com.sweet.market.coupon.query.AvailableCouponCampaignRow;

public record AvailableCouponCampaignResponse(
        Long id, CouponCampaignOwnerType ownerType, CouponScope scope, CouponDiscountType discountType,
        long discountValue, Long maxDiscountAmount, long minimumPurchaseAmount, boolean stackable,
        String title, String label, LocalDateTime issueStartsAt, LocalDateTime issueEndsAt,
        CouponValidityType validityType, LocalDateTime commonExpiresAt, Integer validityDays,
        CouponEffectiveStatus effectiveStatus, boolean claimed
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static AvailableCouponCampaignResponse from(AvailableCouponCampaignRow row) {
        return new AvailableCouponCampaignResponse(row.id(), row.ownerType(), row.scope(), row.discountType(),
                row.discountValue(), row.maxDiscountAmount(), row.minimumPurchaseAmount(), row.stackable(),
                row.title(), row.label(), local(row.issueStartsAt()), local(row.issueEndsAt()), row.validityType(),
                local(row.commonExpiresAt()), row.validityDays(), CouponEffectiveStatus.ACTIVE, row.claimed());
    }

    private static LocalDateTime local(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, KST); }
}
