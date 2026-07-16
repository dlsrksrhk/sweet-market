package com.sweet.market.coupon;

import com.sweet.market.coupon.scheduler.CouponReservationExpirySchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class CouponReservationExpirySchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CouponReservationExpirySchedulingConfig.class);

    @Test
    void 스케줄링을_비활성화하면_쿠폰_만료_스케줄러_인프라를_생성하지_않는다() {
        contextRunner
                .withPropertyValues("market.scheduling.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
