package com.sweet.market.operations.projection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@ConditionalOnProperty(
        prefix = "market.operations-projector",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ProjectionGenerationInitializer implements ApplicationRunner {

    private final ProjectionGenerationService service;
    private final Clock clock;

    @Autowired
    public ProjectionGenerationInitializer(ProjectionGenerationService service) {
        this(service, Clock.systemUTC());
    }

    ProjectionGenerationInitializer(ProjectionGenerationService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.ensureActiveGeneration(clock.instant());
    }
}
