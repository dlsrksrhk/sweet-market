package com.sweet.market.operations.projection;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ProjectionGenerationService {

    private static final long EVENT_WRITER_LOCK_KEY = 310031L;
    private static final long COLD_START_LOCK_KEY = 310032L;
    private static final long REBUILD_LOCK_KEY = COLD_START_LOCK_KEY;

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
        return repository.withCoordinationAdvisoryLock(COLD_START_LOCK_KEY, () -> {
            var initializedGeneration = repository.findActiveGenerationId();
            if (initializedGeneration.isPresent()) {
                return initializedGeneration.getAsLong();
            }
            return rebuildExclusively(null, now).generationId();
        });
    }

    public ProjectionRebuildResult rebuild(Long actorMemberId, Instant now) {
        return repository.tryWithCoordinationAdvisoryLock(
                        REBUILD_LOCK_KEY, () -> rebuildExclusively(actorMemberId, now))
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECTION_REBUILD_IN_PROGRESS));
    }

    private ProjectionRebuildResult rebuildExclusively(Long actorMemberId, Instant now) {
        if (repository.hasBuildingGeneration()) {
            throw new BusinessException(ErrorCode.PROJECTION_REBUILD_IN_PROGRESS);
        }
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
