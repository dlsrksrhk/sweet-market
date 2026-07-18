package com.sweet.market.gateway.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class ReplayCleanupScheduler {

    private final ReplayGuard replayGuard;
    private final Clock clock;
    private final IntegrationSecurityProperties properties;

    public ReplayCleanupScheduler(
            ReplayGuard replayGuard,
            Clock clock,
            IntegrationSecurityProperties properties
    ) {
        this.replayGuard = replayGuard;
        this.clock = clock;
        this.properties = properties;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public int cleanupExpiredReplays() {
        return replayGuard.deleteExpired(clock.instant(), properties.cleanupBatchSize());
    }
}
