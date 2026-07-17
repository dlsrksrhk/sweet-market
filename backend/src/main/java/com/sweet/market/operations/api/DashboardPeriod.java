package com.sweet.market.operations.api;

import java.time.Instant;
import java.time.LocalDate;

public record DashboardPeriod(
        LocalDate from,
        LocalDate to,
        Instant fromInclusive,
        Instant toExclusive,
        String timezone
) {
}
