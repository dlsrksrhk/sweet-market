package com.sweet.market.settlement.batch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;

@Service
public class AdminSettlementBatchHistoryService {

    private static final String SETTLEMENT_JOB_NAME = "settlementJob";

    private final JdbcTemplate jdbcTemplate;

    public AdminSettlementBatchHistoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminSettlementBatchExecutionSummaryResponse> findRecent(int size) {
        int boundedSize = Math.clamp(size, 1, 100);

        return jdbcTemplate.query("""
                        select
                            je.job_execution_id,
                            ji.job_name,
                            je.status,
                            je.exit_code,
                            je.create_time,
                            je.start_time,
                            je.end_time
                        from batch_job_execution je
                        join batch_job_instance ji on ji.job_instance_id = je.job_instance_id
                        where ji.job_name = ?
                        order by je.job_execution_id desc
                        limit ?
                        """,
                (rs, rowNum) -> summaryResponse(rs),
                SETTLEMENT_JOB_NAME,
                boundedSize
        );
    }

    public AdminSettlementBatchExecutionDetailResponse findOne(Long executionId) {
        AdminSettlementBatchExecutionSummaryResponse summary = findSummary(executionId);
        Map<String, String> parameters = findParameters(executionId);
        AdminSettlementBatchExecutionDetailResponse.Step step = findStep(executionId);
        List<String> failureMessages = findFailureMessages(executionId);

        return new AdminSettlementBatchExecutionDetailResponse(
                summary.executionId(),
                summary.jobName(),
                summary.status(),
                summary.exitCode(),
                summary.createTime(),
                summary.startTime(),
                summary.endTime(),
                new AdminSettlementBatchExecutionDetailResponse.Parameters(
                        parameters.get("confirmedBefore"),
                        parseLong(parameters.get("limit")),
                        parseLong(parameters.get("chunkSize"))
                ),
                step,
                failureMessages
        );
    }

    private AdminSettlementBatchExecutionSummaryResponse findSummary(Long executionId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select
                                je.job_execution_id,
                                ji.job_name,
                                je.status,
                                je.exit_code,
                                je.create_time,
                                je.start_time,
                                je.end_time
                            from batch_job_execution je
                            join batch_job_instance ji on ji.job_instance_id = je.job_instance_id
                            where ji.job_name = ?
                              and je.job_execution_id = ?
                            """,
                    (rs, rowNum) -> summaryResponse(rs),
                    SETTLEMENT_JOB_NAME,
                    executionId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
    }

    private Map<String, String> findParameters(Long executionId) {
        return jdbcTemplate.query("""
                        select parameter_name, parameter_value
                        from batch_job_execution_params
                        where job_execution_id = ?
                        """,
                (rs, rowNum) -> Map.entry(rs.getString("parameter_name"), rs.getString("parameter_value")),
                executionId
        ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private AdminSettlementBatchExecutionDetailResponse.Step findStep(Long executionId) {
        return jdbcTemplate.queryForObject("""
                        select
                            coalesce(sum(read_count), 0) as read_count,
                            coalesce(sum(write_count), 0) as write_count,
                            coalesce(sum(
                                coalesce(read_skip_count, 0)
                                + coalesce(process_skip_count, 0)
                                + coalesce(write_skip_count, 0)
                            ), 0) as skip_count,
                            coalesce(sum(rollback_count), 0) as rollback_count
                        from batch_step_execution
                        where job_execution_id = ?
                        """,
                (rs, rowNum) -> new AdminSettlementBatchExecutionDetailResponse.Step(
                        rs.getInt("read_count"),
                        rs.getInt("write_count"),
                        rs.getInt("skip_count"),
                        rs.getInt("rollback_count")
                ),
                executionId
        );
    }

    private List<String> findFailureMessages(Long executionId) {
        return jdbcTemplate.queryForList("""
                        select exit_message
                        from (
                            select 0 as source_order, job_execution_id as id, exit_message
                            from batch_job_execution
                            where job_execution_id = ?
                              and nullif(trim(exit_message), '') is not null
                            union all
                            select 1 as source_order, step_execution_id as id, exit_message
                            from batch_step_execution
                            where job_execution_id = ?
                              and nullif(trim(exit_message), '') is not null
                        ) failure_messages
                        order by source_order, id
                        """,
                String.class,
                executionId,
                executionId
        );
    }

    private AdminSettlementBatchExecutionSummaryResponse summaryResponse(ResultSet rs) throws SQLException {
        return new AdminSettlementBatchExecutionSummaryResponse(
                rs.getLong("job_execution_id"),
                rs.getString("job_name"),
                rs.getString("status"),
                rs.getString("exit_code"),
                rs.getObject("create_time", LocalDateTime.class),
                rs.getObject("start_time", LocalDateTime.class),
                rs.getObject("end_time", LocalDateTime.class)
        );
    }

    private Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        return Long.valueOf(value);
    }
}
