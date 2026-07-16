package com.sweet.market.coupon.api;

import com.sweet.market.coupon.application.CouponDiscountQuote;
import com.sweet.market.coupon.domain.MemberCoupon;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record EligibleMemberCouponResponse(
        Long id, String title, long discountAmount, long finalPrice, LocalDateTime validUntil
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static EligibleMemberCouponResponse from(MemberCoupon coupon, CouponDiscountQuote quote) {
        return new EligibleMemberCouponResponse(coupon.getId(), coupon.getCampaign().getTitle(), quote.discountAmount(),
                quote.finalPrice(), LocalDateTime.ofInstant(coupon.getValidUntil(), KST));
    }
}
