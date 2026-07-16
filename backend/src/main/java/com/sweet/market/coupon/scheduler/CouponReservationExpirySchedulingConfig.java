package com.sweet.market.coupon.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "market.scheduling",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CouponReservationExpirySchedulingConfig {
}
