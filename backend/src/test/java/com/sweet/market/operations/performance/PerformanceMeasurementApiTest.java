package com.sweet.market.operations.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sweet.market.auth.security.JwtProvider;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
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
    void commit_fixture_scenario_hardware가_다른_측정쌍은_거부한다() throws Exception {
        String token = bearer(saveAdmin("performance-compare@example.com"));

        for (String field : List.of("gitCommit", "fixtureVersion", "scenarioVersion", "hardwareDescription")) {
            ObjectNode request = validRequest();
            String differentValue = field.equals("gitCommit")
                    ? "abcdef0123456789abcdef0123456789abcdef01"
                    : "different";
            ((ObjectNode) request.path("on")).put(field, differentValue);

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
        ObjectNode invalidPath = validRequest();
        invalidPath.put("artifactDirectory", "performance/results/../secret");
        assertBadRequest(token, invalidPath, "artifactDirectory");

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
                .containsOnly(
                        "findRunByMeasurementId", "insertRun", "insertEndpointMetrics",
                        "insertQueryEvidence", "findRunById", "findEndpointMetrics", "findQueryEvidence"
                );
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
                "off", mode("OFF", "2026-07-17T00:00:00Z", "2026-07-17T00:05:00Z"),
                "on", mode("ON", "2026-07-17T00:10:00Z", "2026-07-17T00:15:00Z")
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
