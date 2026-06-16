package com.sweet.market.settlement.admin;

import java.time.LocalDateTime;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Service;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;

@Service
public class AdminSettlementRetryService {

    private final OrderRepository orderRepository;
    private final SettlementRepository settlementRepository;
    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    public AdminSettlementRetryService(
            OrderRepository orderRepository,
            SettlementRepository settlementRepository,
            JobLauncher jobLauncher,
            Job settlementJob
    ) {
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
        this.jobLauncher = jobLauncher;
        this.settlementJob = settlementJob;
    }

    public AdminSettlementRetryResponse retry(AdminSettlementRetryRequest request) {
        Long orderId = request.orderId();
        Order order = orderRepository.findAdminSettlementRetryTargetById(orderId)
                .orElse(null);

        if (order == null) {
            return AdminSettlementRetryResponse.of(
                    AdminSettlementRetryResultCode.ORDER_NOT_FOUND,
                    orderId,
                    null,
                    null
            );
        }

        Settlement existingSettlement = settlementRepository.findWithOrderByOrderId(orderId)
                .orElse(null);
        if (existingSettlement != null) {
            return AdminSettlementRetryResponse.of(
                    AdminSettlementRetryResultCode.ALREADY_SETTLED,
                    orderId,
                    existingSettlement.getId(),
                    null
            );
        }

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            return AdminSettlementRetryResponse.of(
                    AdminSettlementRetryResultCode.ORDER_NOT_CONFIRMED,
                    orderId,
                    null,
                    null
            );
        }

        JobExecution jobExecution = launch(orderId);
        Long jobExecutionId = jobExecution.getId();
        Settlement createdSettlement = settlementRepository.findWithOrderByOrderId(orderId)
                .orElse(null);

        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            return AdminSettlementRetryResponse.of(
                    AdminSettlementRetryResultCode.BATCH_FAILED,
                    orderId,
                    null,
                    jobExecutionId
            );
        }

        if (writeCount(jobExecution) == 1 && createdSettlement != null) {
            return AdminSettlementRetryResponse.of(
                    AdminSettlementRetryResultCode.CREATED,
                    orderId,
                    createdSettlement.getId(),
                    jobExecutionId
            );
        }

        if (createdSettlement != null) {
            return AdminSettlementRetryResponse.of(
                    AdminSettlementRetryResultCode.ALREADY_SETTLED,
                    orderId,
                    createdSettlement.getId(),
                    null
            );
        }

        return AdminSettlementRetryResponse.of(
                AdminSettlementRetryResultCode.BATCH_FAILED,
                orderId,
                null,
                jobExecutionId
        );
    }

    private long writeCount(JobExecution jobExecution) {
        return jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();
    }

    private JobExecution launch(Long orderId) {
        JobParameters parameters = new JobParametersBuilder()
                .addString("confirmedBefore", LocalDateTime.now().plusSeconds(1).toString())
                .addLong("limit", 1L)
                .addLong("chunkSize", 1L)
                .addLong("forcedOrderId", orderId)
                .addLong("requestedAt", System.nanoTime())
                .toJobParameters();

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
