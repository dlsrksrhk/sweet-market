package com.sweet.market.operations.projection;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ProjectionGenerationService {

    private static final long EVENT_WRITER_LOCK_KEY = 310031L;

    private final ProjectionBootstrapRepository bootstrapRepository;
    private final OperationalProjectionRepository repository;
    private final OperationalProjectionCoordinator coordinator;

    public ProjectionGenerationService(
            ProjectionBootstrapRepository bootstrapRepository,
            OperationalProjectionRepository repository,
            OperationalProjectionCoordinator coordinator
    ) {
        this.bootstrapRepository = bootstrapRepository;
        this.repository = repository;
        this.coordinator = coordinator;
    }

    public long ensureActiveGeneration(Instant now) {
        var activeGeneration = repository.findActiveGenerationId();
        if (activeGeneration.isPresent()) {
            return activeGeneration.getAsLong();
        }
        return rebuild(null, now).generationId();
    }

    public ProjectionRebuildResult rebuild(Long actorMemberId, Instant now) {
        long generationId = 0L;
        try {
            Instant trackingStartedAt = repository.findActiveTrackingStartedAt().orElse(now);
            ProjectionBootstrapSnapshot snapshot =
                    bootstrapRepository.createBuildingAndPopulate(now, trackingStartedAt);
            generationId = snapshot.generationId();
            coordinator.replayNonDerivableEvents(
                    generationId, trackingStartedAt, snapshot.outboxHighWaterId());
            coordinator.replayAfterOutboxId(generationId, snapshot.outboxHighWaterId());
            repository.verifyNoDuplicateReceipts(generationId);
            long finalGenerationId = generationId;
            repository.withExclusiveAdvisoryLock(EVENT_WRITER_LOCK_KEY, () -> {
                coordinator.replayAfterCurrentCheckpoint(finalGenerationId);
                repository.verifyNoDuplicateReceipts(finalGenerationId);
                repository.activateAtomically(finalGenerationId, now);
            });
            return new ProjectionRebuildResult(generationId, "ACTIVE", snapshot.cutoff(), now);
        } catch (RuntimeException exception) {
            if (generationId != 0L) {
                repository.markBuildingFailed(generationId);
            }
            throw exception;
        }
    }
}
