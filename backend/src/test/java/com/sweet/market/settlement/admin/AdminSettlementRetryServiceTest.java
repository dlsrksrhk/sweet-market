package com.sweet.market.settlement.admin;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminSettlementRetryServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final SettlementRepository settlementRepository = mock(SettlementRepository.class);
    private final JobLauncher jobLauncher = mock(JobLauncher.class);
    private final Job settlementJob = mock(Job.class);
    private final AdminSettlementRetryService service = new AdminSettlementRetryService(
            orderRepository,
            settlementRepository,
            jobLauncher,
            settlementJob
    );

    @Test
    void 배치가_완료됐지만_쓰기_건수가_0이고_정산이_생겼으면_이미_정산됨으로_응답한다() throws Exception {
        Long orderId = 1L;
        Long settlementId = 10L;
        Order order = confirmedOrder();
        Settlement settlement = mock(Settlement.class);
        JobExecution jobExecution = jobExecution(100L, BatchStatus.COMPLETED, 0);
        when(orderRepository.findAdminSettlementRetryTargetById(orderId)).thenReturn(Optional.of(order));
        when(settlementRepository.findWithOrderByOrderId(orderId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(settlement));
        when(settlement.getId()).thenReturn(settlementId);
        when(jobLauncher.run(eq(settlementJob), any(JobParameters.class))).thenReturn(jobExecution);

        AdminSettlementRetryResponse response = service.retry(new AdminSettlementRetryRequest(orderId));

        assertThat(response.resultCode()).isEqualTo(AdminSettlementRetryResultCode.ALREADY_SETTLED);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.settlementId()).isEqualTo(settlementId);
        assertThat(response.jobExecutionId()).isNull();
        assertThat(response.message()).isEqualTo("이미 정산된 주문입니다.");
    }

    @Test
    void 배치가_완료되지_않으면_배치_실패로_응답한다() throws Exception {
        Long orderId = 2L;
        Long jobExecutionId = 101L;
        Order order = confirmedOrder();
        JobExecution jobExecution = jobExecution(jobExecutionId, BatchStatus.FAILED, 0);
        when(orderRepository.findAdminSettlementRetryTargetById(orderId)).thenReturn(Optional.of(order));
        when(settlementRepository.findWithOrderByOrderId(orderId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(jobLauncher.run(eq(settlementJob), any(JobParameters.class))).thenReturn(jobExecution);

        AdminSettlementRetryResponse response = service.retry(new AdminSettlementRetryRequest(orderId));

        assertThat(response.resultCode()).isEqualTo(AdminSettlementRetryResultCode.BATCH_FAILED);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.settlementId()).isNull();
        assertThat(response.jobExecutionId()).isEqualTo(jobExecutionId);
        assertThat(response.message()).isEqualTo("정산 배치 실행에 실패했습니다.");
    }

    @Test
    void 배치_시작_예외는_배치_실행_실패_예외로_변환한다() throws Exception {
        Long orderId = 3L;
        Order order = confirmedOrder();
        when(orderRepository.findAdminSettlementRetryTargetById(orderId)).thenReturn(Optional.of(order));
        when(settlementRepository.findWithOrderByOrderId(orderId)).thenReturn(Optional.empty());
        when(jobLauncher.run(eq(settlementJob), any(JobParameters.class)))
                .thenThrow(new JobParametersInvalidException("invalid"));

        assertThatThrownBy(() -> service.retry(new AdminSettlementRetryRequest(orderId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).errorCode()).isEqualTo(ErrorCode.BATCH_LAUNCH_FAILED));
    }

    private Order confirmedOrder() {
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        return order;
    }

    private JobExecution jobExecution(Long jobExecutionId, BatchStatus status, int writeCount) {
        JobExecution jobExecution = new JobExecution(jobExecutionId);
        jobExecution.setStatus(status);
        StepExecution stepExecution = new StepExecution("settlementStep", jobExecution);
        stepExecution.setWriteCount(writeCount);
        jobExecution.addStepExecutions(java.util.List.of(stepExecution));
        return jobExecution;
    }
}
