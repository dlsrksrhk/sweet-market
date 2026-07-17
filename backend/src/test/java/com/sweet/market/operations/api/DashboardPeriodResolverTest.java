package com.sweet.market.operations.api;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardPeriodResolverTest {

    private static final Instant KST_MIDNIGHT = Instant.parse("2026-07-17T15:00:00Z");
    private final DashboardPeriodResolver resolver = new DashboardPeriodResolver();

    @Test
    void 기본조회기간은_오늘을_포함한_최근30일이다() {
        DashboardPeriod period = resolver.resolve(null, null, null, KST_MIDNIGHT);

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 7, 18));
        assertThat(period.fromInclusive()).isEqualTo(Instant.parse("2026-06-18T15:00:00Z"));
        assertThat(period.toExclusive()).isEqualTo(Instant.parse("2026-07-18T15:00:00Z"));
        assertThat(period.timezone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 네가지_프리셋은_KST_포함기간으로_해석한다() {
        assertPreset("TODAY", LocalDate.of(2026, 7, 18));
        assertPreset("LAST_7_DAYS", LocalDate.of(2026, 7, 12));
        assertPreset("LAST_30_DAYS", LocalDate.of(2026, 6, 19));
        assertPreset("LAST_90_DAYS", LocalDate.of(2026, 4, 20));
    }

    @Test
    void 잘못된_조회기간조합은_검증오류로_거부한다() {
        assertValidation(() -> resolver.resolve(
                "TODAY", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), KST_MIDNIGHT));
        assertValidation(() -> resolver.resolve(
                null, LocalDate.of(2026, 7, 1), null, KST_MIDNIGHT));
        assertValidation(() -> resolver.resolve(
                null, null, LocalDate.of(2026, 7, 2), KST_MIDNIGHT));
        assertValidation(() -> resolver.resolve(
                null, LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1), KST_MIDNIGHT));
        assertValidation(() -> resolver.resolve("LAST_WEEK", null, null, KST_MIDNIGHT));
        assertValidation(() -> resolver.resolve(
                null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1), KST_MIDNIGHT));
    }

    private void assertPreset(String preset, LocalDate expectedFrom) {
        DashboardPeriod period = resolver.resolve(preset, null, null, KST_MIDNIGHT);
        assertThat(period.from()).isEqualTo(expectedFrom);
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 7, 18));
    }

    private void assertValidation(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
