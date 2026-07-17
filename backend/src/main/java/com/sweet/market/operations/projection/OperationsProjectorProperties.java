package com.sweet.market.operations.projection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "market.operations-projector")
public record OperationsProjectorProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1000") long fixedDelayMs,
        @DefaultValue("100") int batchSize
) {

    public OperationsProjectorProperties {
        if (fixedDelayMs <= 0) {
            throw new IllegalArgumentException("fixedDelayMs must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }
}
