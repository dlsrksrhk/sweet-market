package com.sweet.market.settlement.batch;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import com.sweet.market.settlement.domain.Settlement;

@Configuration
public class SettlementBatchConfig {

    private static final int DEFAULT_CHUNK_SIZE = 100;
    private static final int DEFAULT_LIMIT = 0;
    private static final int SKIP_LIMIT = 10_000;

    @Bean(name = "settlementJob")
    public Job settlementJob(JobRepository jobRepository, Step settlementStep) {
        return new JobBuilder("settlementJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(settlementStep)
                .build();
    }

    @Bean(name = "settlementStep")
    @JobScope
    public Step settlementStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcPagingItemReader<Long> settlementOrderIdReader,
            SettlementItemProcessor settlementItemProcessor,
            ItemWriter<Settlement> settlementItemWriter,
            @Value("#{jobParameters['chunkSize']}") Long chunkSize
    ) {
        return new StepBuilder("settlementStep", jobRepository)
                .<Long, Settlement>chunk(toPositiveInt(chunkSize, DEFAULT_CHUNK_SIZE), transactionManager)
                .reader(settlementOrderIdReader)
                .processor(settlementItemProcessor)
                .writer(settlementItemWriter)
                .faultTolerant()
                .retry(DataAccessException.class)
                .retryLimit(3)
                .skip(SettlementBatchSkippableException.class)
                .skipLimit(SKIP_LIMIT)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<Long> settlementOrderIdReader(
            DataSource dataSource,
            @Value("#{jobParameters['confirmedBefore']}") String confirmedBefore,
            @Value("#{jobParameters['limit']}") Long limit,
            @Value("#{jobParameters['chunkSize']}") Long chunkSize,
            @Value("#{jobParameters['forcedOrderId']}") Long forcedOrderId
    ) throws Exception {
        JdbcPagingItemReader<Long> reader = new JdbcPagingItemReader<>();
        reader.setName("settlementOrderIdReader");
        reader.setDataSource(dataSource);
        reader.setQueryProvider(settlementOrderIdQueryProvider(forcedOrderId));
        reader.setParameterValues(settlementOrderIdParameters(confirmedBefore, forcedOrderId));
        reader.setRowMapper((resultSet, rowNum) -> resultSet.getLong("id"));
        reader.setPageSize(toPositiveInt(chunkSize, DEFAULT_CHUNK_SIZE));
        reader.setMaxItemCount(toNonNegativeInt(limit, DEFAULT_LIMIT));
        reader.setSaveState(false);
        reader.afterPropertiesSet();
        return reader;
    }

    private PagingQueryProvider settlementOrderIdQueryProvider(Long forcedOrderId) {
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("select o.id as id");
        queryProvider.setFromClause("from orders o");
        if (forcedOrderId == null) {
            queryProvider.setWhereClause("""
                o.status = 'CONFIRMED'
                and o.confirmed_at < :confirmedBefore
                and not exists (
                    select 1
                    from settlements s
                    where s.order_id = o.id
                )
                """);
        } else {
            queryProvider.setWhereClause("""
                o.status = 'CONFIRMED'
                and o.confirmed_at < :confirmedBefore
                and o.id = :forcedOrderId
                """);
        }

        Map<String, Order> sortKeys = new LinkedHashMap<>();
        sortKeys.put("id", Order.ASCENDING);
        queryProvider.setSortKeys(sortKeys);

        return queryProvider;
    }

    private Map<String, Object> settlementOrderIdParameters(String confirmedBefore, Long forcedOrderId) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("confirmedBefore", parseConfirmedBefore(confirmedBefore));
        if (forcedOrderId != null) {
            parameters.put("forcedOrderId", forcedOrderId);
        }
        return parameters;
    }

    private Timestamp parseConfirmedBefore(String confirmedBefore) {
        if (confirmedBefore == null || confirmedBefore.isBlank()) {
            return Timestamp.valueOf(LocalDateTime.now());
        }
        return Timestamp.valueOf(LocalDateTime.parse(confirmedBefore));
    }

    private static int toPositiveInt(Long value, int defaultValue) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return Math.toIntExact(value);
    }

    private static int toNonNegativeInt(Long value, int defaultValue) {
        if (value == null || value < 0) {
            return defaultValue;
        }
        return Math.toIntExact(value);
    }
}
