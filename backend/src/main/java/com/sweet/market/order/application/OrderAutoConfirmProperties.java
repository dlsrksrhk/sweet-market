package com.sweet.market.order.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "market.order.auto-confirm")
public record OrderAutoConfirmProperties(
        @DefaultValue("7") int thresholdDays,
        @DefaultValue("100") int limit
) {

    public OrderAutoConfirmProperties {
        if (thresholdDays <= 0) {
            throw new IllegalArgumentException("thresholdDays must be positive");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
