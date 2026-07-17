package com.sweet.market.operations.projection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
@ConditionalOnProperty(
        prefix = "market.operations-projector",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ProjectionGenerationCleanupScheduler {

    private static final Duration RETIRED_RETENTION = Duration.ofDays(7);

    private final OperationalProjectionRepository repository;
    private final Clock clock;

    @Autowired
    public ProjectionGenerationCleanupScheduler(OperationalProjectionRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ProjectionGenerationCleanupScheduler(
            OperationalProjectionRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${market.operations-projector.generation-cleanup-cron:0 0 4 * * *}",
            zone = "Asia/Seoul"
    )
    public void cleanup() {
        repository.deleteExpiredRetiredGenerations(clock.instant().minus(RETIRED_RETENTION));
    }
}
