package com.sweet.market.operations.projection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(
        prefix = "market.operations-projector",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OperationalEventRetentionScheduler {

    private final OperationalProjectionRepository repository;
    private final Clock clock;

    @Autowired
    public OperationalEventRetentionScheduler(OperationalProjectionRepository repository) {
        this(repository, Clock.systemUTC());
    }

    OperationalEventRetentionScheduler(
            OperationalProjectionRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void deleteExpiredEvents() {
        repository.deleteProcessedBefore(clock.instant().minus(100, ChronoUnit.DAYS));
    }
}
