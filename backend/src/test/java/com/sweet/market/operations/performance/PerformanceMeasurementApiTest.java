package com.sweet.market.operations.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PerformanceMeasurementApiTest extends IntegrationTestSupport {

    private static final UUID MEASUREMENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoSpyBean
    private PerformanceMeasurementRepository performanceMeasurementRepository;

    @Autowired
    private PerformanceMeasurementService performanceMeasurementService;

    @BeforeEach
    void createPerformanceTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS performance_measurement_runs (
                    id BIGSERIAL PRIMARY KEY, measurement_id UUID NOT NULL UNIQUE,
                    payload_hash VARCHAR(64) NOT NULL, git_commit VARCHAR(64) NOT NULL,
                    dirty_worktree BOOLEAN NOT NULL, fixture_version VARCHAR(80) NOT NULL,
                    scenario_version VARCHAR(80) NOT NULL, environment_name VARCHAR(80) NOT NULL,
                    hardware_description VARCHAR(500) NOT NULL, artifact_directory VARCHAR(500) NOT NULL,
                    warmup_seconds INTEGER NOT NULL, measured_seconds INTEGER NOT NULL,
                    off_started_at TIMESTAMPTZ NOT NULL, off_completed_at TIMESTAMPTZ NOT NULL,
                    on_started_at TIMESTAMPTZ NOT NULL, on_completed_at TIMESTAMPTZ NOT NULL,
                    registered_by BIGINT NOT NULL, registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS performance_endpoint_metrics (
                    id BIGSERIAL PRIMARY KEY,
                    run_id BIGINT NOT NULL REFERENCES performance_measurement_runs(id) ON DELETE CASCADE,
                    cache_mode VARCHAR(10) NOT NULL, endpoint VARCHAR(40) NOT NULL,
                    p50_millis NUMERIC(12,3) NOT NULL, p95_millis NUMERIC(12,3) NOT NULL,
                    throughput_per_second NUMERIC(14,3) NOT NULL, error_rate NUMERIC(8,7) NOT NULL,
                    jdbc_statement_count BIGINT NOT NULL, cache_hit_count BIGINT,
                    cache_miss_count BIGINT, cache_eviction_count BIGINT,
                    UNIQUE (run_id, cache_mode, endpoint)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS performance_query_evidence (
                    id BIGSERIAL PRIMARY KEY,
                    run_id BIGINT NOT NULL REFERENCES performance_measurement_runs(id) ON DELETE CASCADE,
                    cache_mode VARCHAR(10) NOT NULL, query_shape VARCHAR(40) NOT NULL,
                    bind_summary VARCHAR(1000) NOT NULL, plan_summary VARCHAR(4000) NOT NULL,
                    execution_millis NUMERIC(12,3) NOT NULL, actual_rows BIGINT NOT NULL,
                    shared_hit_blocks BIGINT NOT NULL, shared_read_blocks BIGINT NOT NULL,
                    UNIQUE (run_id, cache_mode, query_shape)
                )
                """);
        jdbcTemplate.execute("TRUNCATE TABLE performance_measurement_runs RESTART IDENTITY CASCADE");
    }

    @AfterEach
    void cleanUpPerformanceTables() {
        jdbcTemplate.execute("TRUNCATE TABLE performance_measurement_runs RESTART IDENTITY CASCADE");
    }

    @Test
    void ADMIN은_검증된_cache_off_on_측정쌍을_등록한다() throws Exception {
        String token = bearer(saveAdmin("performance-admin@example.com"));

        String response = mockMvc.perform(post("/api/admin/performance-measurements")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.runId").isNumber())
                .andExpect(jsonPath("$.data.measurementId").value(MEASUREMENT_ID.toString()))
                .andExpect(jsonPath("$.data.payloadHash").isString())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.comparable").value(true))
                .andExpect(jsonPath("$.data.endpointMetrics.length()").value(8))
                .andExpect(jsonPath("$.data.queryEvidence.length()").value(8))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).path("data").path("payloadHash").asText())
                .matches("[0-9a-f]{64}");
        assertThat(countRows("performance_measurement_runs")).isEqualTo(1);
        assertThat(countRows("performance_endpoint_metrics")).isEqualTo(8);
        assertThat(countRows("performance_query_evidence")).isEqualTo(8);
    }

    @Test
    void 같은_measurementId와_hash는_멱등_성공한다() throws Exception {
        String token = bearer(saveAdmin("performance-idempotent@example.com"));
        JsonNode request = validRequest();

        long firstRunId = register(token, request).path("runId").asLong();
        long secondRunId = register(token, request).path("runId").asLong();

        assertThat(secondRunId).isEqualTo(firstRunId);
        assertThat(countRows("performance_measurement_runs")).isEqualTo(1);
        assertThat(countRows("performance_endpoint_metrics")).isEqualTo(8);
        assertThat(countRows("performance_query_evidence")).isEqualTo(8);
    }

    @Test
    void 같은_measurementId의_다른_payload는_거부한다() throws Exception {
        String token = bearer(saveAdmin("performance-conflict@example.com"));
        register(token, validRequest());
        ObjectNode changed = validRequest();
        ((ObjectNode) changed.path("off")).put("environmentName", "ci-2");
        ((ObjectNode) changed.path("on")).put("environmentName", "ci-2");

        mockMvc.perform(post("/api/admin/performance-measurements")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changed.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_MEASUREMENT_CONFLICT"));

        assertThat(countRows("performance_measurement_runs")).isEqualTo(1);
    }

    @Test
    void p50이_p95보다_크면_거부한다() throws Exception {
        String token = bearer(saveAdmin("performance-percentile@example.com"));
        ObjectNode request = validRequest();
        ((ObjectNode) request.path("off").path("endpointMetrics").get(0)).put("p50Millis", 51.0);

        mockMvc.perform(post("/api/admin/performance-measurements")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("off.endpointMetrics[0].p50Millis"));
    }

    @Test
    void 음수와_1을_초과한_errorRate를_거부한다() throws Exception {
        String token = bearer(saveAdmin("performance-rate@example.com"));

        for (double invalidRate : List.of(-0.01, 1.01)) {
            ObjectNode request = validRequest();
            ((ObjectNode) request.path("on").path("endpointMetrics").get(0)).put("errorRate", invalidRate);

            mockMvc.perform(post("/api/admin/performance-measurements")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("on.endpointMetrics[0].errorRate"));
        }
    }

    @Test
    void 서비스는_모드별_실제시간이_선언시간_허용범위를_벗어나면_저장하지_않는다() throws Exception {
        Member admin = saveAdmin("performance-duration-service@example.com");
        ObjectNode request = validRequest();
        ((ObjectNode) request.path("off")).put("completedAt", "2026-07-17T00:05:36Z");

        assertThatThrownBy(() -> performanceMeasurementService.register(
                typedRequest(request), admin.getId()))
                .isInstanceOf(PerformanceMeasurementService.PerformanceMeasurementValidationException.class)
                .satisfies(exception -> assertThat(((PerformanceMeasurementService
                        .PerformanceMeasurementValidationException) exception).fieldViolations())
                        .extracting(PerformanceMeasurementService.FieldViolation::field)
                        .containsExactly("off.completedAt"));
        assertThat(countRows("performance_measurement_runs")).isZero();
    }

    @Test
    void API는_OFF_ON_실제시간차가_허용범위를_벗어나면_검증오류로_거부한다() throws Exception {
        String token = bearer(saveAdmin("performance-duration-api@example.com"));
        ObjectNode request = validRequest();
        ((ObjectNode) request.path("off")).put("completedAt", "2026-07-17T00:05:25Z");
        ((ObjectNode) request.path("on")).put("completedAt", "2026-07-17T00:15:35Z");

        mockMvc.perform(post("/api/admin/performance-measurements")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("off.on.duration"));
        assertThat(countRows("performance_measurement_runs")).isZero();
    }

    @Test
    void 실제시간은_선언시간과_5초경계이고_OFF_ON차도_5초이면_등록한다() throws Exception {
        String token = bearer(saveAdmin("performance-duration-boundary@example.com"));
        ObjectNode request = validRequest();
        ((ObjectNode) request.path("off")).put("completedAt", "2026-07-17T00:05:25Z");

        register(token, request);

        assertThat(countRows("performance_measurement_runs")).isOne();
    }

    @Test
    void 권위_run4의_OFF_361초_ON_362초는_유효하고_비교가능하다() throws Exception {
        String token = bearer(saveAdmin("performance-run4@example.com"));
        JsonNode request = objectMapper.readTree(Path.of(
                "..", "performance", "results", "m30-v1", "measurement.json").toFile());

        register(token, request);

        assertThat(countRows("performance_measurement_runs")).isOne();
    }

    @Test
    void commit_fixture_scenario_hardware가_다른_측정쌍은_거부한다() throws Exception {
        String token = bearer(saveAdmin("performance-compare@example.com"));

        for (String field : List.of(
                "gitCommit", "dirtyWorktree", "fixtureVersion", "scenarioVersion",
                "environmentName", "hardwareDescription", "warmupSeconds", "measuredSeconds"
        )) {
            ObjectNode request = validRequest();
            ObjectNode on = (ObjectNode) request.path("on");
            switch (field) {
                case "gitCommit" -> on.put(field, "abcdef0123456789abcdef0123456789abcdef01");
                case "dirtyWorktree" -> on.put(field, true);
                case "warmupSeconds" -> on.put(field, 31);
                case "measuredSeconds" -> on.put(field, 301);
                default -> on.put(field, "different");
            }

            mockMvc.perform(post("/api/admin/performance-measurements")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("off.on.comparability"));
        }
    }

    @Test
    void 일반회원은_성능측정을_등록하거나_조회할_수_없다() throws Exception {
        String token = bearer(saveMember("performance-member@example.com"));

        mockMvc.perform(post("/api/admin/performance-measurements")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/admin/performance-measurements")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/performance-measurements/{runId}", 1)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void 목록은_plan본문을_제외하고_상세는_저장된_증거를_반환한다() throws Exception {
        String token = bearer(saveAdmin("performance-query@example.com"));
        long runId = register(token, validRequest()).path("runId").asLong();

        mockMvc.perform(get("/api/admin/performance-measurements")
                        .queryParam("page", "0").queryParam("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].valid").value(true))
                .andExpect(jsonPath("$.data.content[0].comparable").value(true))
                .andExpect(jsonPath("$.data.content[0].endpointMetrics.length()").value(8))
                .andExpect(jsonPath("$.data.content[0].queryEvidence.length()").value(0))
                .andExpect(jsonPath("$.data.content[0].queryEvidence[0].planSummary").doesNotExist());

        mockMvc.perform(get("/api/admin/performance-measurements/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(runId))
                .andExpect(jsonPath("$.data.queryEvidence.length()").value(8))
                .andExpect(jsonPath("$.data.queryEvidence[0].planSummary").value("Index Scan using catalog_idx"));
    }

    @Test
    void 경로와_필수_shape와_알수없는_필드는_엄격히_검증한다() throws Exception {
        String token = bearer(saveAdmin("performance-shape@example.com"));
        for (String path : List.of(
                "/performance/results/m30-v1",
                "C:/performance/results/m30-v1",
                "performance\\results\\m30-v1",
                "performance/results/./m30-v1",
                "performance/results/a/../m30-v1",
                "performance/results/../secret"
        )) {
            ObjectNode invalidPath = validRequest();
            invalidPath.put("artifactDirectory", path);
            assertBadRequest(token, invalidPath, "artifactDirectory");
        }

        ObjectNode missingShape = validRequest();
        missingShape.path("off").path("queryEvidence").elements().next();
        ((com.fasterxml.jackson.databind.node.ArrayNode) missingShape.path("off").path("queryEvidence")).remove(0);
        assertBadRequest(token, missingShape, "off.queryEvidence");

        ObjectNode unknown = validRequest();
        unknown.put("command", "run-sql");
        mockMvc.perform(post("/api/admin/performance-measurements")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknown.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 등록API는_프로세스와_SQL을_실행하지_않는다() throws Exception {
        String token = bearer(saveAdmin("performance-passive@example.com"));
        ObjectNode request = validRequest();
        ((ObjectNode) request.path("off").path("queryEvidence").get(2))
                .put("planSummary", "SELECT pg_sleep(30); DROP TABLE members;");
        clearInvocations(performanceMeasurementRepository);

        long runId = register(token, request).path("runId").asLong();

        assertThat(countRows("members")).isEqualTo(1);
        assertThat(mockingDetails(performanceMeasurementRepository).getInvocations())
                .extracting(invocation -> invocation.getMethod().getName())
                .allMatch(Set.of(
                        "findRunByMeasurementId", "insertRun", "insertEndpointMetrics",
                        "insertQueryEvidence", "findRunById", "findEndpointMetrics", "findQueryEvidence"
                )::contains);
        mockMvc.perform(get("/api/admin/performance-measurements/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queryEvidence[0].planSummary")
                        .value("SELECT pg_sleep(30); DROP TABLE members;"));
        assertThat(countRows("members")).isEqualTo(1);
        assertThat(java.util.Arrays.stream(PerformanceMeasurementRepository.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getReturnType))
                .doesNotContain(Process.class, ProcessBuilder.class);
    }

    @Test
    void null_child와_DB_정밀도를_넘는_숫자를_거부한다() throws Exception {
        String token = bearer(saveAdmin("performance-bounds@example.com"));
        mockMvc.perform(post("/api/admin/performance-measurements")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("request"));

        ObjectNode nullChild = validRequest();
        ((com.fasterxml.jackson.databind.node.ArrayNode) nullChild.path("off").path("endpointMetrics"))
                .set(0, com.fasterxml.jackson.databind.node.NullNode.instance);
        assertBadRequest(token, nullChild, "off.endpointMetrics[0]");

        ObjectNode tooLarge = validRequest();
        ((ObjectNode) tooLarge.path("on").path("endpointMetrics").get(0))
                .put("p95Millis", new java.math.BigDecimal("1E+20"));
        assertBadRequest(token, tooLarge, "on.endpointMetrics[0].p95Millis");
    }

    @Test
    void 누락되거나_null인_필수_primitive_evidence를_거부한다() throws Exception {
        String token = bearer(saveAdmin("performance-required@example.com"));

        for (String field : List.of(
                "dirtyWorktree", "jdbcStatementCount", "actualRows", "sharedHitBlocks", "sharedReadBlocks"
        )) {
            ObjectNode missing = validRequest();
            requiredEvidenceParent(missing, field).remove(field);
            assertValidationError(token, missing);

            ObjectNode explicitNull = validRequest();
            requiredEvidenceParent(explicitNull, field).putNull(field);
            assertValidationError(token, explicitNull);
        }
    }

    @Test
    void scalar_타입_강제변환을_허용하지_않는다() throws Exception {
        String token = bearer(saveAdmin("performance-coercion@example.com"));

        ObjectNode booleanString = validRequest();
        ((ObjectNode) booleanString.path("off")).put("dirtyWorktree", "false");
        assertValidationError(token, booleanString);

        ObjectNode booleanNumber = validRequest();
        ((ObjectNode) booleanNumber.path("off")).put("dirtyWorktree", 0);
        assertValidationError(token, booleanNumber);

        ObjectNode integerString = validRequest();
        ((ObjectNode) integerString.path("off").path("endpointMetrics").get(0))
                .put("jdbcStatementCount", "1000");
        assertValidationError(token, integerString);

        ObjectNode decimalString = validRequest();
        ((ObjectNode) decimalString.path("off").path("endpointMetrics").get(0))
                .put("p50Millis", "10.0");
        assertValidationError(token, decimalString);

        ObjectNode durationString = validRequest();
        ((ObjectNode) durationString.path("off")).put("warmupSeconds", "30");
        assertValidationError(token, durationString);

        ObjectNode textNumber = validRequest();
        ((ObjectNode) textNumber.path("off")).put("environmentName", 123);
        ((ObjectNode) textNumber.path("on")).put("environmentName", 123);
        assertValidationError(token, textNumber);

        ObjectNode integerBoolean = validRequest();
        ((ObjectNode) integerBoolean.path("off").path("endpointMetrics").get(0))
                .put("jdbcStatementCount", true);
        assertValidationError(token, integerBoolean);

        ObjectNode integerFloat = validRequest();
        ((ObjectNode) integerFloat.path("off")).put("warmupSeconds", 30.0);
        assertValidationError(token, integerFloat);
    }

    @Test
    void 동시_동일_payload_등록은_하나의_snapshot만_생성한다() throws Exception {
        Member admin = saveAdmin("performance-concurrent-same@example.com");
        PerformanceMeasurementRegisterRequest request = typedRequest(validRequest());

        List<RegistrationOutcome> outcomes = registerConcurrently(
                () -> performanceMeasurementService.register(request, admin.getId()),
                () -> performanceMeasurementService.register(request, admin.getId())
        );

        assertThat(outcomes).allMatch(RegistrationOutcome::succeeded);
        assertThat(outcomes).extracting(outcome -> outcome.response().runId()).containsOnly(1L);
        assertThat(countRows("performance_measurement_runs")).isEqualTo(1);
        assertThat(countRows("performance_endpoint_metrics")).isEqualTo(8);
        assertThat(countRows("performance_query_evidence")).isEqualTo(8);
    }

    @Test
    void 동시_다른_payload의_같은_measurementId는_하나만_등록하고_다른_하나는_충돌한다() throws Exception {
        Member admin = saveAdmin("performance-concurrent-conflict@example.com");
        PerformanceMeasurementRegisterRequest first = typedRequest(validRequest());
        ObjectNode changedJson = validRequest();
        ((ObjectNode) changedJson.path("off")).put("environmentName", "ci-2");
        ((ObjectNode) changedJson.path("on")).put("environmentName", "ci-2");
        PerformanceMeasurementRegisterRequest second = typedRequest(changedJson);

        List<RegistrationOutcome> outcomes = registerConcurrently(
                () -> performanceMeasurementService.register(first, admin.getId()),
                () -> performanceMeasurementService.register(second, admin.getId())
        );

        assertThat(outcomes).filteredOn(RegistrationOutcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(RegistrationOutcome::error)
                .allSatisfy(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).errorCode())
                            .isEqualTo(ErrorCode.PERFORMANCE_MEASUREMENT_CONFLICT);
                });
        assertThat(countRows("performance_measurement_runs")).isEqualTo(1);
        assertThat(countRows("performance_endpoint_metrics")).isEqualTo(8);
        assertThat(countRows("performance_query_evidence")).isEqualTo(8);
    }

    @Test
    void child_순서와_decimal_표현이_달라도_같은_canonical_hash를_사용한다() throws Exception {
        String token = bearer(saveAdmin("performance-canonical@example.com"));
        JsonNode first = register(token, validRequest());
        ObjectNode equivalent = validRequest();

        for (String mode : List.of("off", "on")) {
            reverse((ArrayNode) equivalent.path(mode).path("endpointMetrics"));
            reverse((ArrayNode) equivalent.path(mode).path("queryEvidence"));
            for (JsonNode metric : equivalent.path(mode).path("endpointMetrics")) {
                ObjectNode value = (ObjectNode) metric;
                value.put("p50Millis", new java.math.BigDecimal("10.0000"));
                value.put("p95Millis", new java.math.BigDecimal("50.00000"));
                value.put("throughputPerSecond", new java.math.BigDecimal("100.0000"));
                value.put("errorRate", new java.math.BigDecimal("0.00000000"));
            }
            for (JsonNode evidence : equivalent.path(mode).path("queryEvidence")) {
                ((ObjectNode) evidence).put("executionMillis", new java.math.BigDecimal("2.5000"));
            }
        }

        JsonNode second = register(token, equivalent);

        assertThat(second.path("runId").asLong()).isEqualTo(first.path("runId").asLong());
        assertThat(second.path("payloadHash").asText()).isEqualTo(first.path("payloadHash").asText());
        assertThat(countRows("performance_measurement_runs")).isEqualTo(1);
        assertThat(countRows("performance_endpoint_metrics")).isEqualTo(8);
        assertThat(countRows("performance_query_evidence")).isEqualTo(8);
    }

    @Test
    void child_저장_실패는_전체_snapshot을_rollback한다() throws Exception {
        Member admin = saveAdmin("performance-rollback@example.com");
        PerformanceMeasurementRegisterRequest request = typedRequest(validRequest());
        doThrow(new IllegalStateException("forced child insert failure"))
                .when(performanceMeasurementRepository).insertQueryEvidence(anyLong(), anyList());

        try {
            assertThatThrownBy(() -> performanceMeasurementService.register(request, admin.getId()))
                    .hasMessageContaining("forced child insert failure");
        } finally {
            doCallRealMethod().when(performanceMeasurementRepository).insertQueryEvidence(anyLong(), anyList());
        }

        assertThat(countRows("performance_measurement_runs")).isZero();
        assertThat(countRows("performance_endpoint_metrics")).isZero();
        assertThat(countRows("performance_query_evidence")).isZero();
    }

    @Test
    void 목록은_최신순_다중_page를_제공하고_size를_제한하며_없는_상세는_404다() throws Exception {
        String token = bearer(saveAdmin("performance-pages@example.com"));
        for (int index = 1; index <= 3; index++) {
            ObjectNode request = validRequest();
            request.put("measurementId", new UUID(0, index).toString());
            register(token, request);
        }

        String firstPage = mockMvc.perform(get("/api/admin/performance-measurements")
                        .queryParam("page", "0").queryParam("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andReturn().getResponse().getContentAsString();
        String secondPage = mockMvc.perform(get("/api/admin/performance-measurements")
                        .queryParam("page", "1").queryParam("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(pageRunIds(firstPage)).containsExactly(3L, 2L);
        assertThat(pageRunIds(secondPage)).containsExactly(1L);
        mockMvc.perform(get("/api/admin/performance-measurements")
                        .queryParam("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
        mockMvc.perform(get("/api/admin/performance-measurements/{runId}", 9999)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_MEASUREMENT_NOT_FOUND"));
    }

    private List<Long> pageRunIds(String response) throws Exception {
        List<Long> runIds = new java.util.ArrayList<>();
        objectMapper.readTree(response).path("data").path("content")
                .forEach(row -> runIds.add(row.path("runId").asLong()));
        return runIds;
    }

    private void reverse(ArrayNode array) {
        List<JsonNode> values = new java.util.ArrayList<>();
        array.forEach(value -> values.add(value.deepCopy()));
        java.util.Collections.reverse(values);
        array.removeAll();
        values.forEach(array::add);
    }

    private PerformanceMeasurementRegisterRequest typedRequest(JsonNode request) throws Exception {
        return objectMapper.treeToValue(request, PerformanceMeasurementRegisterRequest.class);
    }

    private List<RegistrationOutcome> registerConcurrently(
            Registration registrationOne,
            Registration registrationTwo
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<RegistrationOutcome> first = executor.submit(() -> executeAfter(start, registrationOne));
            Future<RegistrationOutcome> second = executor.submit(() -> executeAfter(start, registrationTwo));
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private RegistrationOutcome executeAfter(CyclicBarrier start, Registration registration) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            return new RegistrationOutcome(registration.register(), null);
        } catch (RuntimeException exception) {
            return new RegistrationOutcome(null, exception);
        }
    }

    @FunctionalInterface
    private interface Registration {
        PerformanceMeasurementResponse register();
    }

    private record RegistrationOutcome(PerformanceMeasurementResponse response, RuntimeException error) {
        boolean succeeded() {
            return response != null;
        }
    }

    private ObjectNode requiredEvidenceParent(ObjectNode request, String field) {
        return switch (field) {
            case "dirtyWorktree" -> (ObjectNode) request.path("off");
            case "jdbcStatementCount" -> (ObjectNode) request.path("off").path("endpointMetrics").get(0);
            default -> (ObjectNode) request.path("off").path("queryEvidence").get(0);
        };
    }

    private void assertValidationError(String token, JsonNode request) throws Exception {
        mockMvc.perform(post("/api/admin/performance-measurements")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void assertBadRequest(String token, JsonNode request, String field) throws Exception {
        mockMvc.perform(post("/api/admin/performance-measurements")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
    }

    private JsonNode register(String token, JsonNode request) throws Exception {
        String response = mockMvc.perform(post("/api/admin/performance-measurements")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private ObjectNode validRequest() {
        return objectMapper.valueToTree(Map.of(
                "measurementId", MEASUREMENT_ID,
                "artifactDirectory", "performance/results/m30-v1",
                "off", mode("OFF", "2026-07-17T00:00:00Z", "2026-07-17T00:05:30Z"),
                "on", mode("ON", "2026-07-17T00:10:00Z", "2026-07-17T00:15:30Z")
        ));
    }

    private Map<String, Object> mode(String cacheMode, String startedAt, String completedAt) {
        return Map.ofEntries(
                Map.entry("cacheMode", cacheMode),
                Map.entry("gitCommit", "0123456789abcdef0123456789abcdef01234567"),
                Map.entry("dirtyWorktree", false),
                Map.entry("fixtureVersion", "m30-v1"),
                Map.entry("scenarioVersion", "m30-catalog-reads-v1"),
                Map.entry("environmentName", "ci"),
                Map.entry("hardwareDescription", "4 CPU / 8 GiB"),
                Map.entry("warmupSeconds", 30),
                Map.entry("measuredSeconds", 300),
                Map.entry("startedAt", startedAt),
                Map.entry("completedAt", completedAt),
                Map.entry("endpointMetrics", List.of(
                        endpoint(cacheMode, "catalog"), endpoint(cacheMode, "events"),
                        endpoint(cacheMode, "popularity"), endpoint(cacheMode, "detail")
                )),
                Map.entry("queryEvidence", List.of(
                        evidence(cacheMode, "GLOBAL_CATALOG"), evidence(cacheMode, "FIXED_STORE_CATALOG"),
                        evidence(cacheMode, "ACTIVE_EVENTS"), evidence(cacheMode, "POPULARITY")
                ))
        );
    }

    private Map<String, Object> endpoint(String cacheMode, String endpoint) {
        return Map.ofEntries(
                Map.entry("cacheMode", cacheMode), Map.entry("endpoint", endpoint),
                Map.entry("p50Millis", 10.0), Map.entry("p95Millis", 50.0),
                Map.entry("throughputPerSecond", 100.0), Map.entry("errorRate", 0.0),
                Map.entry("jdbcStatementCount", 1000), Map.entry("cacheHitCount", 100),
                Map.entry("cacheMissCount", 10), Map.entry("cacheEvictionCount", 0)
        );
    }

    private Map<String, Object> evidence(String cacheMode, String queryShape) {
        return Map.of(
                "cacheMode", cacheMode, "queryShape", queryShape,
                "bindSummary", "category=FOOD", "planSummary", "Index Scan using catalog_idx",
                "executionMillis", 2.5, "actualRows", 20,
                "sharedHitBlocks", 12, "sharedReadBlocks", 1
        );
    }

    private Member saveAdmin(String email) {
        return memberRepository.save(Member.createAdmin(email, passwordEncoder.encode("password123"), "admin"));
    }

    private Member saveMember(String email) {
        return memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), "member"));
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.createAccessToken(member.getId(), member.getEmail(), member.getRole());
    }

    private int countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
