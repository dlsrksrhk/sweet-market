package com.sweet.market.coupon.scheduler;

import com.sweet.market.coupon.application.CouponRedemptionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CouponReservationExpiryScheduler {

    private final CouponRedemptionService couponRedemptionService;

    public CouponReservationExpiryScheduler(CouponRedemptionService couponRedemptionService) {
        this.couponRedemptionService = couponRedemptionService;
    }

    @Scheduled(fixedDelayString = "${market.coupon-reservation-expiry.fixed-delay-ms:60000}")
    public void expireReservations() {
        couponRedemptionService.expireReservations(Instant.now());
    }
}
