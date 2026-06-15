package com.sweet.market.order.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile({"local", "dev"})
public class OrderAutoConfirmSchedulingConfig {
}
