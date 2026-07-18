package com.sweet.market.provider.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class ReplayCleanupScheduler {

    private final ReplayGuard replayGuard;
    private final Clock clock;
    private final int cleanupBatchSize;

    public ReplayCleanupScheduler(
            ReplayGuard replayGuard,
            Clock clock,
            IntegrationSecurityProperties properties
    ) {
        this.replayGuard = replayGuard;
        this.clock = clock;
        this.cleanupBatchSize = properties.cleanupBatchSize();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public int cleanupExpiredReplays() {
        return replayGuard.deleteExpired(clock.instant(), cleanupBatchSize);
    }
}
