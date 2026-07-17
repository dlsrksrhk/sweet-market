package com.sweet.market.operations.performance;

import java.time.Instant;
import java.util.List;

public record CacheModeMeasurementInput(
        String cacheMode,
        String gitCommit,
        Boolean dirtyWorktree,
        String fixtureVersion,
        String scenarioVersion,
        String environmentName,
        String hardwareDescription,
        int warmupSeconds,
        int measuredSeconds,
        Instant startedAt,
        Instant completedAt,
        List<EndpointMetricInput> endpointMetrics,
        List<QueryEvidenceInput> queryEvidence
) {
}
