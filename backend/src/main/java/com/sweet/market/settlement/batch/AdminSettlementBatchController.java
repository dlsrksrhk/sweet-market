package com.sweet.market.settlement.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/batches/settlements")
public class AdminSettlementBatchController {

    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    public AdminSettlementBatchController(JobLauncher jobLauncher, Job settlementJob) {
        this.jobLauncher = jobLauncher;
        this.settlementJob = settlementJob;
    }

    @PostMapping
    public ApiResponse<AdminSettlementBatchResponse> run(
            @Valid @RequestBody AdminSettlementBatchRequest request
    ) throws Exception {
        JobParameters parameters = new JobParametersBuilder()
                .addString("confirmedBefore", request.confirmedBefore().toString())
                .addLong("limit", request.limit().longValue())
                .addLong("chunkSize", request.chunkSize().longValue())
                .addLong("requestedAt", System.nanoTime())
                .toJobParameters();

        JobExecution jobExecution = jobLauncher.run(settlementJob, parameters);
        return ApiResponse.ok(AdminSettlementBatchResponse.from(jobExecution, request));
    }
}
