package com.sweet.market.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
class SettlementBatchJobTest extends IntegrationTestSupport {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job settlementJob;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(settlementJob);
    }

    @Test
    void 확정_시각이_기준보다_이전인_미정산_주문을_정산한다() throws Exception {
        Order settledOrder = createConfirmedOrder("settled");
        transactionTemplate.executeWithoutResult(status -> {
            Order order = entityManager.find(Order.class, settledOrder.getId());
            settlementRepository.save(Settlement.create(order));
        });
        createConfirmedOrder("unsettled");

        BatchRunResult result = launchSettlementJob(LocalDateTime.now().plusDays(1), 10, 5);

        assertThat(result.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(settlementRepository.count()).isEqualTo(2);
        assertThat(result.readCount()).isEqualTo(1);
        assertThat(result.writeCount()).isEqualTo(1);
        assertThat(result.skipCount()).isZero();
    }

    @Test
    void 기준_시각_이후에_확정된_주문은_정산하지_않는다() throws Exception {
        createConfirmedOrder("future");

        BatchRunResult result = launchSettlementJob(LocalDateTime.now().minusDays(1), 10, 5);

        assertThat(result.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(settlementRepository.count()).isZero();
        assertThat(result.readCount()).isZero();
        assertThat(result.writeCount()).isZero();
    }

    @Test
    void 같은_조건으로_다시_실행해도_중복_정산하지_않는다() throws Exception {
        createConfirmedOrder("first");
        createConfirmedOrder("second");
        LocalDateTime confirmedBefore = LocalDateTime.now().plusDays(1);

        BatchRunResult firstResult = launchSettlementJob(confirmedBefore, 10, 5);
        BatchRunResult secondResult = launchSettlementJob(confirmedBefore, 10, 5);

        assertThat(firstResult.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(secondResult.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(settlementRepository.count()).isEqualTo(2);
        assertThat(secondResult.readCount()).isZero();
        assertThat(secondResult.writeCount()).isZero();
    }

    private BatchRunResult launchSettlementJob(LocalDateTime confirmedBefore, int limit, int chunkSize) throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("confirmedBefore", confirmedBefore.toString())
                .addLong("limit", (long) limit)
                .addLong("chunkSize", (long) chunkSize)
                .addLong("requestedAt", System.nanoTime())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        assertThat(stepExecutions).hasSize(1);
        StepExecution stepExecution = stepExecutions.iterator().next();
        return new BatchRunResult(
                jobExecution.getStatus(),
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount()
        );
    }

    private Order createConfirmedOrder(String suffix) {
        return transactionTemplate.execute(status -> {
            Member seller = Member.create("seller-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
            Member buyer = Member.create("buyer-" + suffix + "@example.com", "encoded-password", "buyer-" + suffix);
            entityManager.persist(seller);
            entityManager.persist(buyer);

            Product product = Product.create(seller, "MacBook Pro " + suffix, "M3 laptop", 2_000_000L);
            entityManager.persist(product);

            Order order = Order.create(buyer, product);
            order.markPaid();
            order.startShipping();
            order.completeDelivery();
            order.confirm();
            entityManager.persist(order);
            return order;
        });
    }

    private record BatchRunResult(BatchStatus status, long readCount, long writeCount, long skipCount) {
    }
}
