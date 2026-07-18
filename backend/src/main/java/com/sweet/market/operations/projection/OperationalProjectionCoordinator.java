package com.sweet.market.operations.projection;

import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

@Service
public class OperationalProjectionCoordinator {

    private static final int MAX_ATTEMPTS = 10;
    private static final int MAX_ERROR_SUMMARY_LENGTH = 1000;

    private final OperationalProjectionRepository repository;
    private final List<OperationalEventHandler> handlers;
    private final TransactionTemplate batchTransaction;
    private final TransactionTemplate eventTransaction;

    public OperationalProjectionCoordinator(
            OperationalProjectionRepository repository,
            List<OperationalEventHandler> handlers,
            DataSource dataSource
    ) {
        this.repository = repository;
        this.handlers = List.copyOf(handlers);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        this.batchTransaction = new TransactionTemplate(transactionManager);
        this.eventTransaction = new TransactionTemplate(transactionManager);
        this.eventTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }

    public int projectNextBatch(Instant now, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (batchSize > 100) {
            throw new IllegalArgumentException("batchSize must not exceed 100");
        }
        Integer projected = batchTransaction.execute(status -> projectLockedBatch(now, batchSize));
        return projected == null ? 0 : projected;
    }

    public void replayNonDerivableEvents(
            long generationId,
            Instant trackingStartedAt,
            long throughOutboxId,
            Instant replayedAt
    ) {
        replay(generationId,
                repository.findNonDerivableEvents(trackingStartedAt, throughOutboxId),
                replayedAt);
    }

    public void replayAfterOutboxId(long generationId, long outboxId, Instant replayedAt) {
        replay(generationId, repository.findEventsAfterOutboxId(outboxId), replayedAt);
    }

    public void replayAfterCurrentCheckpoint(long generationId, Instant replayedAt) {
        long highWaterId = repository.findBootstrapHighWaterId(generationId);
        replay(generationId, repository.findLateCommittedEvents(generationId, highWaterId), replayedAt);
        replayAfterOutboxId(generationId, highWaterId, replayedAt);
    }

    private int projectLockedBatch(Instant now, int batchSize) {
        var activeGeneration = repository.findActiveGenerationId();
        if (activeGeneration.isEmpty()) {
            return 0;
        }
        List<OperationalEventEnvelopeRow> events = repository.lockNextBatch(now, batchSize);
        for (OperationalEventEnvelopeRow envelope : events) {
            projectOne(activeGeneration.getAsLong(), envelope, now);
        }
        return events.size();
    }

    private void projectOne(long generationId, OperationalEventEnvelopeRow envelope, Instant now) {
        try {
            eventTransaction.executeWithoutResult(status -> {
                projectAllSupportingHandlers(generationId, envelope.event(), now);
                repository.markProcessed(envelope.event().eventId(), now);
            });
        } catch (UnsupportedOperationalEventSchemaException exception) {
            markDead(envelope, now, exception);
        } catch (RuntimeException exception) {
            markRetryOrDead(envelope, now, exception);
        }
    }

    private void projectAllSupportingHandlers(long generationId, OperationalEvent event, Instant now) {
        List<OperationalEventHandler> supportingHandlers = handlers.stream()
                .filter(handler -> handler.supports(event.eventType(), event.schemaVersion()))
                .toList();
        if (supportingHandlers.isEmpty()) {
            throw new UnsupportedOperationalEventSchemaException(
                    event.eventType(), event.schemaVersion());
        }
        for (OperationalEventHandler handler : supportingHandlers) {
            if (!repository.hasReceipt(generationId, handler.projectionName(), event.eventId())) {
                handler.handle(generationId, event);
                repository.insertReceipt(
                        generationId, handler.projectionName(), event.eventId(), now);
            }
        }
    }

    private void replay(
            long generationId,
            List<OperationalEventEnvelopeRow> events,
            Instant replayedAt
    ) {
        for (OperationalEventEnvelopeRow envelope : events) {
            try {
                eventTransaction.executeWithoutResult(status ->
                        projectAllSupportingHandlers(
                                generationId, envelope.event(), replayedAt));
            } catch (UnsupportedOperationalEventSchemaException exception) {
                markDead(envelope, replayedAt, exception);
            }
        }
    }

    private void markRetryOrDead(
            OperationalEventEnvelopeRow envelope,
            Instant now,
            RuntimeException exception
    ) {
        int attemptCount = envelope.attemptCount() + 1;
        if (attemptCount >= MAX_ATTEMPTS) {
            repository.markFailed(
                    envelope.event().eventId(), "DEAD", attemptCount, now, summary(exception));
            return;
        }
        long delaySeconds = Math.min(1L << attemptCount, 300L);
        repository.markFailed(
                envelope.event().eventId(), "RETRY", attemptCount,
                now.plusSeconds(delaySeconds), summary(exception));
    }

    private void markDead(
            OperationalEventEnvelopeRow envelope,
            Instant now,
            RuntimeException exception
    ) {
        repository.markFailed(
                envelope.event().eventId(), "DEAD", envelope.attemptCount() + 1,
                now, summary(exception));
    }

    private static String summary(RuntimeException exception) {
        String message = exception.getMessage();
        String summary = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        int stackTraceStart = summary.indexOf("\n\tat ");
        String withoutStackTrace = stackTraceStart < 0
                ? summary
                : summary.substring(0, stackTraceStart);
        return withoutStackTrace.length() <= MAX_ERROR_SUMMARY_LENGTH
                ? withoutStackTrace
                : withoutStackTrace.substring(0, MAX_ERROR_SUMMARY_LENGTH);
    }

    private static final class UnsupportedOperationalEventSchemaException extends RuntimeException {

        private UnsupportedOperationalEventSchemaException(
                OperationalEventType eventType,
                int schemaVersion
        ) {
            super("Unsupported operational event schema: " + eventType + " v" + schemaVersion);
        }
    }
}
