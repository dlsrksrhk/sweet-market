package com.sweet.market.coupon.application;

import com.sweet.market.coupon.domain.MemberCoupon;

public record CouponReservationQuote(MemberCoupon memberCoupon, CouponDiscountQuote discountQuote) {
}
