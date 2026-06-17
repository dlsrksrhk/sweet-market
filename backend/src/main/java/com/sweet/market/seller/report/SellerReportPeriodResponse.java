package com.sweet.market.seller.report;

import java.time.LocalDate;

public record SellerReportPeriodResponse(
        int recentDays,
        LocalDate recentFrom,
        LocalDate recentTo
) {
}
