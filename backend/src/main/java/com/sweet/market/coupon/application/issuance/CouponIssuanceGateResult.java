package com.sweet.market.coupon.application.issuance;

public record CouponIssuanceGateResult(ReservationType type, CouponIssuanceReservation reservation) {

    public static CouponIssuanceGateResult reserved(CouponIssuanceReservation reservation) {
        return new CouponIssuanceGateResult(ReservationType.RESERVED, reservation);
    }

    public static CouponIssuanceGateResult of(ReservationType type) {
        return new CouponIssuanceGateResult(type, null);
    }
}
