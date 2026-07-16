package com.sweet.market.order.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAutoConfirmSchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OrderAutoConfirmSchedulingConfig.class);

    @Test
    void 스케줄링을_비활성화하면_local에서도_스케줄러_인프라를_생성하지_않는다() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "market.scheduling.enabled=false"
                )
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void 스케줄링은_기본값으로_활성화된다() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
