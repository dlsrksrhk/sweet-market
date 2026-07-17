package com.sweet.market.operations.performance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PerformanceMeasurementResponse(
        long runId,
        UUID measurementId,
        String payloadHash,
        String gitCommit,
        boolean dirtyWorktree,
        String fixtureVersion,
        String scenarioVersion,
        String environmentName,
        String hardwareDescription,
        String artifactDirectory,
        int warmupSeconds,
        int measuredSeconds,
        Instant offStartedAt,
        Instant offCompletedAt,
        Instant onStartedAt,
        Instant onCompletedAt,
        long registeredBy,
        Instant registeredAt,
        boolean valid,
        boolean comparable,
        List<EndpointMetricInput> endpointMetrics,
        List<QueryEvidenceInput> queryEvidence
) {
}
