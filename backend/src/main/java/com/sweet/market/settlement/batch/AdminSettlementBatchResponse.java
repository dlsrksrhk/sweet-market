package com.sweet.market.settlement.batch;

import java.time.LocalDateTime;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;

public record AdminSettlementBatchResponse(
        Long jobExecutionId,
        String jobName,
        BatchStatus status,
        Parameters parameters
) {

    public static AdminSettlementBatchResponse from(
            JobExecution jobExecution,
            AdminSettlementBatchRequest request
    ) {
        return new AdminSettlementBatchResponse(
                jobExecution.getId(),
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus(),
                new Parameters(request.confirmedBefore(), request.limit(), request.chunkSize())
        );
    }

    public record Parameters(
            LocalDateTime confirmedBefore,
            Integer limit,
            Integer chunkSize
    ) {
    }
}
