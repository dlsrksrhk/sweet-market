package com.sweet.market.seller.report;

import java.time.LocalDate;

public record SellerPeriodResponse(
        LocalDate from,
        LocalDate to,
        long days
) {
}
