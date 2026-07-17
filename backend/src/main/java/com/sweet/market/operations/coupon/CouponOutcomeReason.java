package com.sweet.market.operations.coupon;

public enum CouponOutcomeReason {
    NONE,
    ALREADY_CLAIMED,
    EXHAUSTED,
    INACTIVE,
    INELIGIBLE,
    UNAVAILABLE,
    EXPIRED,
    SCOPE_MISMATCH,
    COMBINATION_NOT_ALLOWED,
    RESERVATION_CONFLICT
}
