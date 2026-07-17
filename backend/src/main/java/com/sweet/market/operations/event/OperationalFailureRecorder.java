package com.sweet.market.operations.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OperationalFailureRecorder {

    private static final Logger log = LoggerFactory.getLogger(OperationalFailureRecorder.class);

    private final OperationalEventRecorder recorder;
    private final TransactionTemplate requiresNew;
    private final Counter missingOutcomeCounter;

    public OperationalFailureRecorder(
            OperationalEventRecorder recorder,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry
    ) {
        this.recorder = recorder;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.missingOutcomeCounter = Counter.builder("operations.events.missing.outcomes")
                .description("Operational outcome events that could not be recorded")
                .register(meterRegistry);
    }

    public void recordSafely(OperationalEvent event) {
        try {
            requiresNew.executeWithoutResult(status -> recorder.record(event));
        } catch (RuntimeException exception) {
            missingOutcomeCounter.increment();
            log.error("Failed to record operational outcome eventId={}", event.eventId(), exception);
        }
    }
}
