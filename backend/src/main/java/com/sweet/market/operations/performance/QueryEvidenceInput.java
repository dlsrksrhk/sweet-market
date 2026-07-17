package com.sweet.market.operations.performance;

import java.math.BigDecimal;

public record QueryEvidenceInput(
        String cacheMode,
        String queryShape,
        String bindSummary,
        String planSummary,
        BigDecimal executionMillis,
        long actualRows,
        long sharedHitBlocks,
        long sharedReadBlocks
) {
}
