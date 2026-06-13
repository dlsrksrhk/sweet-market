package com.sweet.market.settlement.batch;

import java.time.LocalDateTime;

public record AdminSettlementBatchExecutionSummaryResponse(
        Long executionId,
        String jobName,
        String status,
        String exitCode,
        LocalDateTime createTime,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
