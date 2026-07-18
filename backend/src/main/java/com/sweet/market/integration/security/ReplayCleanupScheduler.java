package com.sweet.market.integration.security;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

public final class ReplayCleanupScheduler {

    private final ReplayGuard replayGuard;
    private final Clock clock;
    private final int cleanupBatchSize;

    public ReplayCleanupScheduler(ReplayGuard replayGuard, Clock clock, int cleanupBatchSize) {
        this.replayGuard = replayGuard;
        this.clock = clock;
        this.cleanupBatchSize = cleanupBatchSize;
    }

    @Scheduled(fixedRateString = "PT1H")
    int cleanupExpired() {
        return replayGuard.deleteExpired(clock.instant(), cleanupBatchSize);
    }
}
