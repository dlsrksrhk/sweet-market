package com.sweet.market.operations.performance;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

@Repository
public class PerformanceMeasurementRepository {

    private static final String RUN_COLUMNS = """
            id, measurement_id, payload_hash, git_commit, dirty_worktree,
            fixture_version, scenario_version, environment_name, hardware_description,
            artifact_directory, warmup_seconds, measured_seconds,
            off_started_at, off_completed_at, on_started_at, on_completed_at,
            registered_by, registered_at
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PerformanceMeasurementRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RunRow> findRunByMeasurementId(UUID measurementId) {
        return jdbcTemplate.query("""
                        SELECT %s
                        FROM performance_measurement_runs
                        WHERE measurement_id = :measurementId
                        """.formatted(RUN_COLUMNS),
                new MapSqlParameterSource("measurementId", measurementId), runRowMapper()).stream().findFirst();
    }

    public Optional<RunRow> findRunById(long runId) {
        return jdbcTemplate.query("""
                        SELECT %s
                        FROM performance_measurement_runs
                        WHERE id = :runId
                        """.formatted(RUN_COLUMNS),
                new MapSqlParameterSource("runId", runId), runRowMapper()).stream().findFirst();
    }

    public List<RunRow> findRuns(Pageable pageable) {
        return jdbcTemplate.query("""
                        SELECT %s
                        FROM performance_measurement_runs
                        ORDER BY registered_at DESC, id DESC
                        LIMIT :limit OFFSET :offset
                        """.formatted(RUN_COLUMNS),
                new MapSqlParameterSource()
                        .addValue("limit", pageable.getPageSize())
                        .addValue("offset", pageable.getOffset()),
                runRowMapper());
    }

    public long countRuns() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM performance_measurement_runs",
                new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }

    public OptionalLong insertRun(
            PerformanceMeasurementRegisterRequest request,
            String payloadHash,
            long registeredBy
    ) {
        CacheModeMeasurementInput off = request.off();
        CacheModeMeasurementInput on = request.on();
        List<Long> runIds = jdbcTemplate.query("""
                        INSERT INTO performance_measurement_runs (
                            measurement_id, payload_hash, git_commit, dirty_worktree,
                            fixture_version, scenario_version, environment_name, hardware_description,
                            artifact_directory, warmup_seconds, measured_seconds,
                            off_started_at, off_completed_at, on_started_at, on_completed_at, registered_by
                        ) VALUES (
                            :measurementId, :payloadHash, :gitCommit, :dirtyWorktree,
                            :fixtureVersion, :scenarioVersion, :environmentName, :hardwareDescription,
                            :artifactDirectory, :warmupSeconds, :measuredSeconds,
                            :offStartedAt, :offCompletedAt, :onStartedAt, :onCompletedAt, :registeredBy
                        )
                        ON CONFLICT (measurement_id) DO NOTHING
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("measurementId", request.measurementId())
                        .addValue("payloadHash", payloadHash)
                        .addValue("gitCommit", off.gitCommit())
                        .addValue("dirtyWorktree", off.dirtyWorktree())
                        .addValue("fixtureVersion", off.fixtureVersion())
                        .addValue("scenarioVersion", off.scenarioVersion())
                        .addValue("environmentName", off.environmentName())
                        .addValue("hardwareDescription", off.hardwareDescription())
                        .addValue("artifactDirectory", request.artifactDirectory())
                        .addValue("warmupSeconds", off.warmupSeconds())
                        .addValue("measuredSeconds", off.measuredSeconds())
                        .addValue("offStartedAt", Timestamp.from(off.startedAt()))
                        .addValue("offCompletedAt", Timestamp.from(off.completedAt()))
                        .addValue("onStartedAt", Timestamp.from(on.startedAt()))
                        .addValue("onCompletedAt", Timestamp.from(on.completedAt()))
                        .addValue("registeredBy", registeredBy),
                (resultSet, rowNumber) -> resultSet.getLong("id"));
        return runIds.isEmpty() ? OptionalLong.empty() : OptionalLong.of(runIds.getFirst());
    }

    public void insertEndpointMetrics(long runId, List<EndpointMetricInput> metrics) {
        for (EndpointMetricInput metric : metrics) {
            jdbcTemplate.update("""
                            INSERT INTO performance_endpoint_metrics (
                                run_id, cache_mode, endpoint, p50_millis, p95_millis,
                                throughput_per_second, error_rate, jdbc_statement_count,
                                cache_hit_count, cache_miss_count, cache_eviction_count
                            ) VALUES (
                                :runId, :cacheMode, :endpoint, :p50Millis, :p95Millis,
                                :throughputPerSecond, :errorRate, :jdbcStatementCount,
                                :cacheHitCount, :cacheMissCount, :cacheEvictionCount
                            )
                            """,
                    new MapSqlParameterSource()
                            .addValue("runId", runId)
                            .addValue("cacheMode", metric.cacheMode())
                            .addValue("endpoint", metric.endpoint())
                            .addValue("p50Millis", metric.p50Millis())
                            .addValue("p95Millis", metric.p95Millis())
                            .addValue("throughputPerSecond", metric.throughputPerSecond())
                            .addValue("errorRate", metric.errorRate())
                            .addValue("jdbcStatementCount", metric.jdbcStatementCount())
                            .addValue("cacheHitCount", metric.cacheHitCount())
                            .addValue("cacheMissCount", metric.cacheMissCount())
                            .addValue("cacheEvictionCount", metric.cacheEvictionCount()));
        }
    }

    public void insertQueryEvidence(long runId, List<QueryEvidenceInput> evidence) {
        for (QueryEvidenceInput query : evidence) {
            jdbcTemplate.update("""
                            INSERT INTO performance_query_evidence (
                                run_id, cache_mode, query_shape, bind_summary, plan_summary,
                                execution_millis, actual_rows, shared_hit_blocks, shared_read_blocks
                            ) VALUES (
                                :runId, :cacheMode, :queryShape, :bindSummary, :planSummary,
                                :executionMillis, :actualRows, :sharedHitBlocks, :sharedReadBlocks
                            )
                            """,
                    new MapSqlParameterSource()
                            .addValue("runId", runId)
                            .addValue("cacheMode", query.cacheMode())
                            .addValue("queryShape", query.queryShape())
                            .addValue("bindSummary", query.bindSummary())
                            .addValue("planSummary", query.planSummary())
                            .addValue("executionMillis", query.executionMillis())
                            .addValue("actualRows", query.actualRows())
                            .addValue("sharedHitBlocks", query.sharedHitBlocks())
                            .addValue("sharedReadBlocks", query.sharedReadBlocks()));
        }
    }

    public List<EndpointMetricInput> findEndpointMetrics(long runId) {
        return jdbcTemplate.query("""
                        SELECT cache_mode, endpoint, p50_millis, p95_millis,
                               throughput_per_second, error_rate, jdbc_statement_count,
                               cache_hit_count, cache_miss_count, cache_eviction_count
                        FROM performance_endpoint_metrics
                        WHERE run_id = :runId
                        ORDER BY cache_mode, endpoint
                        """,
                new MapSqlParameterSource("runId", runId),
                (resultSet, rowNumber) -> new EndpointMetricInput(
                        resultSet.getString("cache_mode"),
                        resultSet.getString("endpoint"),
                        resultSet.getBigDecimal("p50_millis"),
                        resultSet.getBigDecimal("p95_millis"),
                        resultSet.getBigDecimal("throughput_per_second"),
                        resultSet.getBigDecimal("error_rate"),
                        resultSet.getLong("jdbc_statement_count"),
                        nullableLong(resultSet, "cache_hit_count"),
                        nullableLong(resultSet, "cache_miss_count"),
                        nullableLong(resultSet, "cache_eviction_count")
                ));
    }

    public List<QueryEvidenceInput> findQueryEvidence(long runId) {
        return jdbcTemplate.query("""
                        SELECT cache_mode, query_shape, bind_summary, plan_summary,
                               execution_millis, actual_rows, shared_hit_blocks, shared_read_blocks
                        FROM performance_query_evidence
                        WHERE run_id = :runId
                        ORDER BY cache_mode, query_shape
                        """,
                new MapSqlParameterSource("runId", runId),
                (resultSet, rowNumber) -> new QueryEvidenceInput(
                        resultSet.getString("cache_mode"),
                        resultSet.getString("query_shape"),
                        resultSet.getString("bind_summary"),
                        resultSet.getString("plan_summary"),
                        resultSet.getBigDecimal("execution_millis"),
                        resultSet.getLong("actual_rows"),
                        resultSet.getLong("shared_hit_blocks"),
                        resultSet.getLong("shared_read_blocks")
                ));
    }

    private RowMapper<RunRow> runRowMapper() {
        return (resultSet, rowNumber) -> new RunRow(
                resultSet.getLong("id"),
                resultSet.getObject("measurement_id", UUID.class),
                resultSet.getString("payload_hash"),
                resultSet.getString("git_commit"),
                resultSet.getBoolean("dirty_worktree"),
                resultSet.getString("fixture_version"),
                resultSet.getString("scenario_version"),
                resultSet.getString("environment_name"),
                resultSet.getString("hardware_description"),
                resultSet.getString("artifact_directory"),
                resultSet.getInt("warmup_seconds"),
                resultSet.getInt("measured_seconds"),
                resultSet.getObject("off_started_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("off_completed_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("on_started_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("on_completed_at", OffsetDateTime.class).toInstant(),
                resultSet.getLong("registered_by"),
                resultSet.getObject("registered_at", OffsetDateTime.class).toInstant()
        );
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    public record RunRow(
            long runId,
            UUID measurementId,
            String payloadHash,
            String gitCommit,
            boolean dirtyWorktree,
            String fixtureVersion,
            String scenarioVersion,
            String environmentName,
            String hardwareDescription,
            String artifactDirectory,
            int warmupSeconds,
            int measuredSeconds,
            java.time.Instant offStartedAt,
            java.time.Instant offCompletedAt,
            java.time.Instant onStartedAt,
            java.time.Instant onCompletedAt,
            long registeredBy,
            java.time.Instant registeredAt
    ) {
    }
}
