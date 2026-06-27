package com.sweet.market.seller.report;

import java.time.LocalDate;

public record SellerDailySalesResponse(
        LocalDate date,
        long confirmedOrderCount,
        long confirmedSalesAmount
) {
}
