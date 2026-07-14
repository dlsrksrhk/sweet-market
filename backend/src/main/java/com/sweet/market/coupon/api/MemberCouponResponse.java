package com.sweet.market.coupon.api;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sweet.market.coupon.domain.CouponEffectiveStatus;
import com.sweet.market.coupon.domain.CouponLifecycleStatus;
import com.sweet.market.coupon.domain.MemberCoupon;
import com.sweet.market.coupon.domain.MemberCouponStatus;
import com.sweet.market.coupon.query.MemberCouponWalletRow;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberCouponResponse(
        Long id, Long campaignId, String title, String label,
        com.sweet.market.coupon.domain.CouponDiscountType discountType, long discountValue,
        Long maxDiscountAmount, long minimumPurchaseAmount, com.sweet.market.coupon.domain.CouponScope scope,
        boolean stackable, LocalDateTime issuedAt, LocalDateTime validUntil,
        MemberCouponStatus status, CouponEffectiveStatus unavailabilityReason
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static MemberCouponResponse from(MemberCoupon coupon, Instant now) {
        MemberCouponStatus status = coupon.walletStatus(now);
        CouponEffectiveStatus reason = status == MemberCouponStatus.UNAVAILABLE
                ? coupon.getCampaign().effectiveStatus(now) : null;
        return new MemberCouponResponse(coupon.getId(), coupon.getCampaign().getId(), coupon.getCampaign().getTitle(),
                coupon.getCampaign().getLabel(), coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(), coupon.getMinimumPurchaseAmount(), coupon.getScope(), coupon.isStackable(),
                local(coupon.getIssuedAt()), local(coupon.getValidUntil()), status, reason);
    }

    public static MemberCouponResponse from(MemberCouponWalletRow row, Instant now) {
        CouponEffectiveStatus effectiveStatus = effectiveStatus(row.campaignLifecycleStatus(), row.campaignIssueStartsAt(), row.campaignIssueEndsAt(), now);
        MemberCouponStatus status = walletStatus(row.persistedStatus(), row.validUntil(), effectiveStatus, now);
        return new MemberCouponResponse(row.id(), row.campaignId(), row.title(), row.label(), row.discountType(),
                row.discountValue(), row.maxDiscountAmount(), row.minimumPurchaseAmount(), row.scope(), row.stackable(),
                local(row.issuedAt()), local(row.validUntil()), status,
                status == MemberCouponStatus.UNAVAILABLE ? effectiveStatus : null);
    }

    private static MemberCouponStatus walletStatus(MemberCouponStatus persisted, Instant validUntil, CouponEffectiveStatus effective, Instant now) {
        if (persisted == MemberCouponStatus.USED) return MemberCouponStatus.USED;
        if (!now.isBefore(validUntil)) return MemberCouponStatus.EXPIRED;
        return effective == CouponEffectiveStatus.ACTIVE ? MemberCouponStatus.ISSUED : MemberCouponStatus.UNAVAILABLE;
    }

    private static CouponEffectiveStatus effectiveStatus(CouponLifecycleStatus lifecycle, Instant startsAt, Instant endsAt, Instant now) {
        if (lifecycle == CouponLifecycleStatus.PAUSED) return CouponEffectiveStatus.PAUSED;
        if (lifecycle == CouponLifecycleStatus.ENDED || !now.isBefore(endsAt)) return CouponEffectiveStatus.ENDED;
        return now.isBefore(startsAt) ? CouponEffectiveStatus.SCHEDULED : CouponEffectiveStatus.ACTIVE;
    }

    private static LocalDateTime local(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, KST); }
}
