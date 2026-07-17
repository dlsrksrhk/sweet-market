package com.sweet.market.operations.admin;

import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.operations.projection.OperationalProjectionCoordinator;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOperationalEventApiTest extends IntegrationTestSupport {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-16T01:02:03Z");

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private OperationalProjectionCoordinator coordinator;

    private long activeGenerationId;

    @BeforeEach
    void projection_테이블을_초기화한다() {
        createProjectionTables();
        jdbcTemplate.execute("""
                TRUNCATE TABLE campaign_audit_projection, inventory_failure_hourly,
                               inventory_pressure_projection, campaign_metric_hourly,
                               store_metric_hourly, projection_event_receipts,
                               operational_event_outbox, projection_generations
                RESTART IDENTITY CASCADE
                """);
        activeGenerationId = insertGeneration("ACTIVE");
    }

    @Test
    void ADMIN은_DEAD_event를_조회하고_payload수정없이_재시도한다() throws Exception {
        Member admin = saveAdmin("dead-event-admin@example.com");
        UUID eventId = insertDeadEvent("CAMPAIGN_COMMAND_COMPLETED", """
                {"campaignKind":"COUPON","campaignId":51,"ownerType":"STORE",
                 "ownerStoreId":11,"actorMemberId":7,"command":"PAUSE",
                 "beforeSummary":{"status":"ACTIVE"},"afterSummary":{"status":"PAUSED"}}
                """);
        Map<String, Object> before = eventIdentity(eventId);

        mockMvc.perform(get("/api/admin/operational-events/dead")
                        .queryParam("size", "1000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.data.content[0].eventType").value("CAMPAIGN_COMMAND_COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].deliveryState").value("DEAD"))
                .andExpect(jsonPath("$.data.content[0].payload.afterSummary.status").value("PAUSED"));

        Instant retryStartedAt = Instant.now();
        mockMvc.perform(post("/api/admin/operational-events/{eventId}/retry", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        Instant retryCompletedAt = Instant.now();

        Map<String, Object> after = eventIdentity(eventId);
        assertThat(after).isEqualTo(before);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT delivery_state, attempt_count, last_error
                FROM operational_event_outbox WHERE event_id = ?
                """, eventId))
                .containsEntry("delivery_state", "RETRY")
                .containsEntry("attempt_count", 0)
                .containsEntry("last_error", null);
        Instant nextAttemptAt = jdbcTemplate.queryForObject("""
                SELECT next_attempt_at FROM operational_event_outbox WHERE event_id = ?
                """, (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(), eventId);
        assertThat(nextAttemptAt).isBetween(retryStartedAt, retryCompletedAt);
    }

    @Test
    void 재시도된_event는_중복집계하지_않는다() throws Exception {
        Member admin = saveAdmin("dedup-event-admin@example.com");
        UUID eventId = insertDeadEvent("CAMPAIGN_COMMAND_COMPLETED", """
                {"campaignKind":"COUPON","campaignId":71,"ownerType":"STORE",
                 "ownerStoreId":21,"actorMemberId":7,"command":"PAUSE",
                 "beforeSummary":{"status":"ACTIVE"},"afterSummary":{"status":"PAUSED"}}
                """);
        jdbcTemplate.update("""
                INSERT INTO campaign_audit_projection (
                    generation_id, event_id, campaign_kind, campaign_id,
                    owner_type, owner_store_id, actor_member_id, command,
                    occurred_at, aggregate_version, before_summary, after_summary
                ) VALUES (?, ?, 'COUPON', 71, 'STORE', 21, 7, 'PAUSE', ?, 3,
                          '{"status":"ACTIVE"}'::jsonb, '{"status":"PAUSED"}'::jsonb)
                """, activeGenerationId, eventId, timestamp(OCCURRED_AT));
        jdbcTemplate.update("""
                INSERT INTO projection_event_receipts (
                    generation_id, projection_name, event_id, processed_at
                ) VALUES (?, 'campaign-audit', ?, ?)
                """, activeGenerationId, eventId, timestamp(OCCURRED_AT));

        mockMvc.perform(post("/api/admin/operational-events/{eventId}/retry", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        coordinator.projectNextBatch(Instant.now().plusSeconds(1), 100);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM campaign_audit_projection
                WHERE generation_id = ? AND event_id = ?
                """, Long.class, activeGenerationId, eventId)).isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT delivery_state FROM operational_event_outbox WHERE event_id = ?
                """, String.class, eventId)).isEqualTo("PROCESSED");
    }

    @Test
    void DEAD가_아닌_event는_재시도할_수_없다() throws Exception {
        Member admin = saveAdmin("invalid-retry-admin@example.com");
        UUID eventId = insertDeadEvent("CAMPAIGN_COMMAND_COMPLETED", "{}");
        jdbcTemplate.update("""
                UPDATE operational_event_outbox
                SET delivery_state = 'RETRY', attempt_count = 2
                WHERE event_id = ?
                """, eventId);

        mockMvc.perform(post("/api/admin/operational-events/{eventId}/retry", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATIONAL_EVENT_RETRY_NOT_ALLOWED"));

        assertThat(jdbcTemplate.queryForMap("""
                SELECT delivery_state, attempt_count FROM operational_event_outbox WHERE event_id = ?
                """, eventId))
                .containsEntry("delivery_state", "RETRY")
                .containsEntry("attempt_count", 2);
    }

    @Test
    void ADMIN은_projection_재구축을_시작하고_결과를_받는다() throws Exception {
        Member admin = saveAdmin("rebuild-admin@example.com");
        long previousGenerationId = activeGenerationId;

        String response = mockMvc.perform(post("/api/admin/operational-projections/rebuild")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.cutoff").isNotEmpty())
                .andExpect(jsonPath("$.data.activatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        long rebuiltGenerationId = jdbcTemplate.queryForObject("""
                SELECT id FROM projection_generations WHERE status = 'ACTIVE'
                """, Long.class);
        assertThat(objectMapper.readTree(response).path("data").path("generationId").asLong())
                .isEqualTo(rebuiltGenerationId);
        assertThat(rebuiltGenerationId).isNotEqualTo(previousGenerationId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM projection_generations WHERE id = ?
                """, String.class, previousGenerationId)).isEqualTo("RETIRED");
    }

    @Test
    void BUILDING_generation이_있으면_두번째_재구축을_거부한다() throws Exception {
        Member admin = saveAdmin("building-rebuild-admin@example.com");
        long buildingGenerationId = insertGeneration("BUILDING");

        mockMvc.perform(post("/api/admin/operational-projections/rebuild")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECTION_REBUILD_IN_PROGRESS"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM projection_generations WHERE id = ?
                """, String.class, buildingGenerationId)).isEqualTo("BUILDING");
    }

    private Member saveAdmin(String email) {
        return memberRepository.save(Member.createAdmin(email, passwordEncoder.encode("password123"), "관리자"));
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.createAccessToken(member.getId(), member.getEmail(), member.getRole());
    }

    private long insertGeneration(String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO projection_generations (
                    status, cutoff_at, tracking_started_at, bootstrap_high_water_id, activated_at
                ) VALUES (?, CURRENT_TIMESTAMP, '2026-05-01T00:00:00Z', 0,
                          CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP ELSE NULL END)
                RETURNING id
                """, Long.class, status, status);
    }

    private UUID insertDeadEvent(String eventType, String payload) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO operational_event_outbox (
                    event_id, event_type, schema_version, aggregate_type,
                    aggregate_id, aggregate_version, store_id, campaign_id,
                    partition_key, occurred_at, payload, delivery_state,
                    attempt_count, next_attempt_at, last_error, created_at
                ) VALUES (?, ?, 1, 'CAMPAIGN', 51, 3, 11, 51,
                          'campaign:51', ?, ?::jsonb, 'DEAD', 10,
                          '2026-07-16T01:05:00Z', 'projector failure', '2026-07-16T01:02:04Z')
                """, eventId, eventType, timestamp(OCCURRED_AT), payload);
        return eventId;
    }

    private Map<String, Object> eventIdentity(UUID eventId) {
        return jdbcTemplate.queryForMap("""
                SELECT id, event_id, event_type, schema_version, aggregate_type,
                       aggregate_id, aggregate_version, store_id, campaign_id,
                       partition_key, occurred_at, payload::text AS payload,
                       processed_at, created_at
                FROM operational_event_outbox WHERE event_id = ?
                """, eventId);
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private void createProjectionTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_generations (
                    id BIGSERIAL PRIMARY KEY, status VARCHAR(20) NOT NULL,
                    cutoff_at TIMESTAMPTZ NOT NULL, tracking_started_at TIMESTAMPTZ NOT NULL,
                    bootstrap_high_water_id BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    activated_at TIMESTAMPTZ, retired_at TIMESTAMPTZ
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_event_receipts (
                    id BIGSERIAL PRIMARY KEY,
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    projection_name VARCHAR(80) NOT NULL, event_id UUID NOT NULL,
                    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (generation_id, projection_name, event_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS store_metric_hourly (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    bucket_start TIMESTAMPTZ NOT NULL, store_id BIGINT NOT NULL,
                    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
                    order_success_count BIGINT NOT NULL DEFAULT 0,
                    purchase_failure_count BIGINT NOT NULL DEFAULT 0,
                    reservation_failure_count BIGINT NOT NULL DEFAULT 0,
                    promotion_applied_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_applied_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    sold_out_transition_count BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (generation_id, bucket_start, store_id, outcome_reason)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS campaign_metric_hourly (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    bucket_start TIMESTAMPTZ NOT NULL, commerce_store_id BIGINT NOT NULL DEFAULT 0,
                    campaign_kind VARCHAR(20) NOT NULL, campaign_id BIGINT NOT NULL,
                    campaign_owner_type VARCHAR(20) NOT NULL,
                    campaign_owner_store_id BIGINT NOT NULL DEFAULT 0,
                    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
                    claim_success_count BIGINT NOT NULL DEFAULT 0,
                    claim_failure_count BIGINT NOT NULL DEFAULT 0,
                    redemption_success_count BIGINT NOT NULL DEFAULT 0,
                    redemption_failure_count BIGINT NOT NULL DEFAULT 0,
                    order_success_count BIGINT NOT NULL DEFAULT 0,
                    purchase_failure_count BIGINT NOT NULL DEFAULT 0,
                    promotion_applied_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_applied_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (generation_id, bucket_start, commerce_store_id, campaign_kind,
                        campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS inventory_pressure_projection (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    product_id BIGINT NOT NULL, store_id BIGINT NOT NULL,
                    sales_policy VARCHAR(20) NOT NULL, available_quantity INTEGER,
                    low_stock BOOLEAN NOT NULL, last_sold_out_at TIMESTAMPTZ,
                    recent_reservation_failure_count BIGINT NOT NULL DEFAULT 0,
                    last_reservation_failure_at TIMESTAMPTZ, aggregate_version BIGINT NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (generation_id, product_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS inventory_failure_hourly (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    bucket_start TIMESTAMPTZ NOT NULL, product_id BIGINT NOT NULL,
                    store_id BIGINT NOT NULL, failure_count BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (generation_id, bucket_start, product_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS campaign_audit_projection (
                    id BIGSERIAL PRIMARY KEY,
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    event_id UUID NOT NULL, campaign_kind VARCHAR(20) NOT NULL,
                    campaign_id BIGINT NOT NULL, owner_type VARCHAR(20) NOT NULL,
                    owner_store_id BIGINT NOT NULL DEFAULT 0, actor_member_id BIGINT NOT NULL,
                    command VARCHAR(20) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
                    aggregate_version BIGINT, before_summary JSONB, after_summary JSONB NOT NULL,
                    UNIQUE (generation_id, event_id)
                )
                """);
    }
}
