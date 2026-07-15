package com.sweet.market.coupon.application.issuance;

public class CouponIssuanceGateUnavailableException extends RuntimeException {

    public CouponIssuanceGateUnavailableException(Throwable cause) {
        super("쿠폰 발급 예약 게이트를 사용할 수 없습니다.", cause);
    }
}
