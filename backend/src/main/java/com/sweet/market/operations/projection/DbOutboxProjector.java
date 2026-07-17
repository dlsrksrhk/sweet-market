package com.sweet.market.operations.projection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@ConditionalOnProperty(
        prefix = "market.operations-projector",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DbOutboxProjector {

    private final OperationalProjectionCoordinator coordinator;
    private final OperationsProjectorProperties properties;
    private final Clock clock;

    @Autowired
    public DbOutboxProjector(
            OperationalProjectionCoordinator coordinator,
            OperationsProjectorProperties properties
    ) {
        this(coordinator, properties, Clock.systemUTC());
    }

    DbOutboxProjector(
            OperationalProjectionCoordinator coordinator,
            OperationsProjectorProperties properties,
            Clock clock
    ) {
        this.coordinator = coordinator;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${market.operations-projector.fixed-delay-ms:1000}")
    public void project() {
        coordinator.projectNextBatch(clock.instant(), properties.batchSize());
    }
}
