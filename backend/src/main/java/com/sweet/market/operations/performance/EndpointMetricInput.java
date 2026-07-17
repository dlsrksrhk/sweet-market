package com.sweet.market.operations.performance;

import java.math.BigDecimal;

public record EndpointMetricInput(
        String cacheMode,
        String endpoint,
        BigDecimal p50Millis,
        BigDecimal p95Millis,
        BigDecimal throughputPerSecond,
        BigDecimal errorRate,
        long jdbcStatementCount,
        Long cacheHitCount,
        Long cacheMissCount,
        Long cacheEvictionCount
) {
}
