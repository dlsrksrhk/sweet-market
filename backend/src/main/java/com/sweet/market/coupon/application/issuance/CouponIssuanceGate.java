package com.sweet.market.coupon.application.issuance;

import java.time.Instant;

public interface CouponIssuanceGate {

    CouponIssuanceGateResult reserve(Long campaignId, Long memberId, int issueLimit,
            int issuedCount, Instant issueEndsAt, Instant now);

    void complete(CouponIssuanceReservation reservation, Instant now);

    void release(CouponIssuanceReservation reservation, Instant now);
}
