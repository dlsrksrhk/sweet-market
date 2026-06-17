package com.sweet.market.seller.report;

import java.time.LocalDateTime;
import java.util.List;

public record SellerDashboardReportResponse(
        LocalDateTime generatedAt,
        SellerReportPeriodResponse period,
        SellerReportSummaryResponse summary,
        List<SellerStatusCountResponse> productStatusCounts,
        List<SellerStatusCountResponse> orderStatusCounts
) {
}
