package com.sweet.market.settlement.batch;

import java.time.LocalDateTime;
import java.util.List;

public record AdminSettlementBatchExecutionDetailResponse(
        Long executionId,
        String jobName,
        String status,
        String exitCode,
        LocalDateTime createTime,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Parameters parameters,
        Step step,
        List<String> failureMessages
) {

    public record Parameters(
            String confirmedBefore,
            Long limit,
            Long chunkSize
    ) {
    }

    public record Step(
            int readCount,
            int writeCount,
            int skipCount,
            int rollbackCount
    ) {
    }
}
