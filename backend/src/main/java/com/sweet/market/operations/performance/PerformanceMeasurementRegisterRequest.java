package com.sweet.market.operations.performance;

import java.util.UUID;

public record PerformanceMeasurementRegisterRequest(
        UUID measurementId,
        String artifactDirectory,
        CacheModeMeasurementInput off,
        CacheModeMeasurementInput on
) {
}
