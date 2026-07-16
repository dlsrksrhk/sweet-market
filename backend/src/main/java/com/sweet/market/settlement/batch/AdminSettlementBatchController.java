package com.sweet.market.settlement.batch;

import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/batches/settlements")
public class AdminSettlementBatchController {

    private final JobLauncher jobLauncher;
    private final Job settlementJob;
    private final AdminSettlementBatchHistoryService historyService;

    public AdminSettlementBatchController(
            JobLauncher jobLauncher,
            Job settlementJob,
            AdminSettlementBatchHistoryService historyService
    ) {
        this.jobLauncher = jobLauncher;
        this.settlementJob = settlementJob;
        this.historyService = historyService;
    }

    @PostMapping
    public ApiResponse<AdminSettlementBatchResponse> run(
            @Valid @RequestBody AdminSettlementBatchRequest request
    ) {
        JobParameters parameters = new JobParametersBuilder()
                .addString("confirmedBefore", request.confirmedBefore().toString())
                .addLong("limit", request.limit().longValue())
                .addLong("chunkSize", request.chunkSize().longValue())
                .addLong("requestedAt", System.nanoTime())
                .toJobParameters();

        JobExecution jobExecution = launch(parameters);
        return ApiResponse.ok(AdminSettlementBatchResponse.from(jobExecution, request));
    }

    @GetMapping("/executions")
    public ApiResponse<List<AdminSettlementBatchExecutionSummaryResponse>> executions(
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(historyService.findRecent(size));
    }

    @GetMapping("/executions/{executionId}")
    public ApiResponse<AdminSettlementBatchExecutionDetailResponse> execution(
            @PathVariable Long executionId
    ) {
        return ApiResponse.ok(historyService.findOne(executionId));
    }

    private JobExecution launch(JobParameters parameters) {
        try {
            return jobLauncher.run(settlementJob, parameters);
        } catch (
                JobExecutionAlreadyRunningException
                | JobRestartException
                | JobInstanceAlreadyCompleteException
                | JobParametersInvalidException exception
        ) {
            throw new BusinessException(ErrorCode.BATCH_LAUNCH_FAILED);
        }
    }
}
