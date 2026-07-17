package com.sweet.market.operations.projection;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DbOutboxProjectorSchedulingTest {

    private static final Instant NOW = Instant.parse("2026-07-17T02:03:04Z");

    @Test
    void 스케줄러는_현재시각과_batch_size로_projector를_호출한다() {
        OperationalProjectionCoordinator coordinator = mock(OperationalProjectionCoordinator.class);
        OperationsProjectorProperties properties = new OperationsProjectorProperties(true, 1_000L, 23);
        DbOutboxProjector projector = new DbOutboxProjector(
                coordinator, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        projector.project();

        verify(coordinator).projectNextBatch(NOW, 23);
    }

    @Test
    void 스케줄러는_설정된_fixed_delay를_사용한다() throws Exception {
        Method method = DbOutboxProjector.class.getDeclaredMethod("project");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${market.operations-projector.fixed-delay-ms:1000}");
    }

    @Test
    void 스케줄러는_enabled_속성으로_보호한다() {
        ConditionalOnProperty conditional = DbOutboxProjector.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.prefix()).isEqualTo("market.operations-projector");
        assertThat(conditional.name()).containsExactly("enabled");
        assertThat(conditional.havingValue()).isEqualTo("true");
        assertThat(conditional.matchIfMissing()).isTrue();
    }

    @Test
    void 보존_스케줄러는_매일_100일_기준으로_정리한다() throws Exception {
        OperationalProjectionRepository repository = mock(OperationalProjectionRepository.class);
        OperationalEventRetentionScheduler scheduler = new OperationalEventRetentionScheduler(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.deleteExpiredEvents();

        verify(repository).deleteProcessedBefore(NOW.minus(100, ChronoUnit.DAYS));
        Method method = OperationalEventRetentionScheduler.class.getDeclaredMethod("deleteExpiredEvents");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
    }
}
