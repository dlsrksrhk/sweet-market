package com.sweet.market.operations.api;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Component
public class DashboardPeriodResolver {

    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 90;

    public DashboardPeriod resolve(String preset, LocalDate from, LocalDate to, Instant now) {
        String normalizedPreset = normalize(preset);
        if (normalizedPreset != null && (from != null || to != null)) {
            throw validationError();
        }

        LocalDate resolvedFrom;
        LocalDate resolvedTo;
        if (normalizedPreset != null) {
            int days = presetDays(normalizedPreset);
            resolvedTo = now.atZone(TIMEZONE).toLocalDate();
            resolvedFrom = resolvedTo.minusDays(days - 1L);
        } else if (from == null && to == null) {
            resolvedTo = now.atZone(TIMEZONE).toLocalDate();
            resolvedFrom = resolvedTo.minusDays(DEFAULT_DAYS - 1L);
        } else {
            if (from == null || to == null) {
                throw validationError();
            }
            resolvedFrom = from;
            resolvedTo = to;
        }

        long inclusiveDays = ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1;
        if (inclusiveDays < 1 || inclusiveDays > MAX_DAYS) {
            throw validationError();
        }
        return new DashboardPeriod(
                resolvedFrom,
                resolvedTo,
                resolvedFrom.atStartOfDay(TIMEZONE).toInstant(),
                resolvedTo.plusDays(1).atStartOfDay(TIMEZONE).toInstant(),
                TIMEZONE.getId()
        );
    }

    private int presetDays(String preset) {
        return switch (preset) {
            case "TODAY" -> 1;
            case "LAST_7_DAYS" -> 7;
            case "LAST_30_DAYS" -> 30;
            case "LAST_90_DAYS" -> 90;
            default -> throw validationError();
        };
    }

    private String normalize(String preset) {
        return preset == null || preset.isBlank() ? null : preset.trim().toUpperCase(Locale.ROOT);
    }

    private BusinessException validationError() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
}
