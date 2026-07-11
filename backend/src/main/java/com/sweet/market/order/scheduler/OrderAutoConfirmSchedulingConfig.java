package com.sweet.market.order.scheduler;

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
public class OrderAutoConfirmSchedulingConfig {
}
