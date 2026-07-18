package com.sweet.market.gateway.security;

import com.sweet.market.gateway.support.GatewayIntegrationTestSupport;
import com.sweet.market.gateway.support.GatewaySecurityTestConfiguration;
import com.sweet.market.gateway.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static com.sweet.market.gateway.support.GatewaySecurityTestConfiguration.VECTOR_INSTANT;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "gateway.integration-security.allowed-clock-skew=PT5M",
        "gateway.integration-security.max-body-bytes=1048576",
        "gateway.integration-security.replay-retention=PT10M",
        "gateway.integration-security.cleanup-batch-size=1000",
        "gateway.integration-security.clients[0].client-id=sweet-market",
        "gateway.integration-security.clients[0].api-key=m32-test-api-key",
        "gateway.integration-security.clients[0].keys[0].key-id=m32-test-key-1",
        "gateway.integration-security.clients[0].keys[0].secret=m32-test-hmac-secret-32bytes-minimum",
        "gateway.integration-security.clients[0].keys[1].key-id=m32-test-key-2",
        "gateway.integration-security.clients[0].keys[1].secret=m32-next-hmac-secret-32bytes-minimum",
        "gateway.integration-security.clients[1].client-id=second-client",
        "gateway.integration-security.clients[1].api-key=second-test-api-key",
        "gateway.integration-security.clients[1].keys[0].key-id=second-test-key-1",
        "gateway.integration-security.clients[1].keys[0].secret=second-hmac-secret-32bytes-minimum"
})
@AutoConfigureMockMvc
@Import(GatewaySecurityTestConfiguration.class)
class GatewaySignedProbeApiTest extends GatewayIntegrationTestSupport {

    private static final String CURRENT_API_KEY = "m32-test-api-key";
    private static final String CURRENT_KEY_ID = "m32-test-key-1";
    private static final String CURRENT_SECRET = "m32-test-hmac-secret-32bytes-minimum";
    private static final String NEXT_KEY_ID = "m32-test-key-2";
    private static final String NEXT_SECRET = "m32-next-hmac-secret-32bytes-minimum";
    private static final String SECOND_API_KEY = "second-test-api-key";
    private static final String SECOND_KEY_ID = "second-test-key-1";
    private static final String SECOND_SECRET = "second-hmac-secret-32bytes-minimum";
    private static final String BODY = "{\"message\":\"m32-contract-probe\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    private final HmacCanonicalizer canonicalizer = new HmacCanonicalizer();

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM integration_request_replays");
        clock.setInstant(VECTOR_INSTANT);
    }

    @Test
    void 올바른_서명은_probe를_허용한다() throws Exception {
        UUID requestId = UUID.fromString("3b2f8c6a-2f88-4f75-8c7b-4ad40b519a41");
        UUID correlationId = UUID.fromString("409b1a12-0dc7-4d67-839c-8f4750ba901a");

        mockMvc.perform(signedProbe(BODY, CURRENT_API_KEY, CURRENT_KEY_ID, CURRENT_SECRET,
                        requestId, VECTOR_INSTANT, correlationId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.service").value("mock-payment-gateway"))
                .andExpect(jsonPath("$.message").value("m32-contract-probe"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()));
    }

    @Test
    void 현재키와_다음키는_rotation_중_모두_허용한다() throws Exception {
        mockMvc.perform(signedProbe(BODY, CURRENT_API_KEY, CURRENT_KEY_ID, CURRENT_SECRET,
                        UUID.randomUUID(), VECTOR_INSTANT, UUID.randomUUID()))
                .andExpect(status().isOk());

        mockMvc.perform(signedProbe(BODY, CURRENT_API_KEY, NEXT_KEY_ID, NEXT_SECRET,
                        UUID.randomUUID(), VECTOR_INSTANT, UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void 잘못된_서명은_저장하지_않고_거부한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        MockHttpServletRequestBuilder request = signedProbe(
                BODY, CURRENT_API_KEY, CURRENT_KEY_ID, CURRENT_SECRET,
                requestId, VECTOR_INSTANT, UUID.randomUUID()
        ).with(servletRequest -> {
            servletRequest.removeHeader("X-Signature");
            servletRequest.addHeader("X-Signature", "0".repeat(64));
            return servletRequest;
        });

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value(nullValue()));

        assertReplayCount(0);
    }

    @Test
    void 만료된_timestamp를_거부한다() throws Exception {
        Instant expiredTimestamp = VECTOR_INSTANT.minusSeconds(301);

        mockMvc.perform(signedProbe(BODY, CURRENT_API_KEY, CURRENT_KEY_ID, CURRENT_SECRET,
                        UUID.randomUUID(), expiredTimestamp, UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTEGRATION_AUTHENTICATION_FAILED"));

        assertReplayCount(0);
    }

    @Test
    void 같은_requestId의_replay를_거부한다() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(signedProbe(BODY, CURRENT_API_KEY, CURRENT_KEY_ID, CURRENT_SECRET,
                        requestId, VECTOR_INSTANT, UUID.randomUUID()))
                .andExpect(status().isOk());

        mockMvc.perform(signedProbe(BODY, CURRENT_API_KEY, CURRENT_KEY_ID, CURRENT_SECRET,
                        requestId, VECTOR_INSTANT, UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REPLAY_DETECTED"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
    }

    @Test
    void 같은_requestId라도_다른_client는_독립적으로_처리한다() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(signedProbe(BODY, CURRENT_API_KEY, CURRENT_KEY_ID, CURRENT_SECRET,
                        requestId, VECTOR_INSTANT, UUID.randomUUID()))
                .andExpect(status().isOk());

        mockMvc.perform(signedProbe(BODY, SECOND_API_KEY, SECOND_KEY_ID, SECOND_SECRET,
                        requestId, VECTOR_INSTANT, UUID.randomUUID()))
                .andExpect(status().isOk());

        assertReplayCount(2);
    }

    @Test
    void 일_MiB를_넘는_chunked_body를_JSON_parse전에_거부한다() throws Exception {
        byte[] oversizedBody = new byte[1_048_577];

        mockMvc.perform(post("/api/v1/probes")
                        .header("Transfer-Encoding", "chunked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedBody))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_BODY_TOO_LARGE"))
                .andExpect(jsonPath("$.requestId").value(nullValue()));

        assertReplayCount(0);
    }

    @Test
    void actuator_health는_서명없이_허용한다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void 잘못된_header는_400_오류로_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/probes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .header("X-Api-Key", CURRENT_API_KEY)
                        .header("X-Key-Id", CURRENT_KEY_ID)
                        .header("X-Request-Id", "not-a-uuid")
                        .header("X-Timestamp", Long.toString(VECTOR_INSTANT.getEpochSecond()))
                        .header("X-Signature", "0".repeat(64)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.requestId").value(nullValue()));
    }

    @Test
    void 표현할수_없는_timestamp_header는_400_오류로_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/probes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .header("X-Api-Key", CURRENT_API_KEY)
                        .header("X-Key-Id", CURRENT_KEY_ID)
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .header("X-Timestamp", Long.toString(Long.MAX_VALUE))
                        .header("X-Signature", "0".repeat(64)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.requestId").value(nullValue()));
    }

    @Test
    void 알수없는_credential은_401_오류로_거부한다() throws Exception {
        mockMvc.perform(signedProbe(BODY, "unknown-api-key", CURRENT_KEY_ID, CURRENT_SECRET,
                        UUID.randomUUID(), VECTOR_INSTANT, UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value(nullValue()));

        assertReplayCount(0);
    }

    @Test
    void 인증된_probe_validation_오류는_requestId를_보존한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        String invalidBody = "{\"message\":\"\"}";

        mockMvc.perform(signedProbe(invalidBody, CURRENT_API_KEY, CURRENT_KEY_ID, CURRENT_SECRET,
                        requestId, VECTOR_INSTANT, UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
    }

    @Test
    void 인증된_correlationId_validation_오류는_requestId를_보존한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        MockHttpServletRequestBuilder request = signedProbe(
                BODY, CURRENT_API_KEY, CURRENT_KEY_ID, CURRENT_SECRET,
                requestId, VECTOR_INSTANT, UUID.randomUUID()
        ).with(servletRequest -> {
            servletRequest.removeHeader("X-Correlation-Id");
            servletRequest.addHeader("X-Correlation-Id", "not-a-uuid");
            return servletRequest;
        });

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
    }

    @Test
    void 정의되지_않은_route는_거부한다() throws Exception {
        mockMvc.perform(get("/not-defined"))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder signedProbe(
            String body,
            String apiKey,
            String keyId,
            String secret,
            UUID requestId,
            Instant timestamp,
            UUID correlationId
    ) {
        String canonical = canonicalizer.canonicalize(
                keyId,
                timestamp,
                requestId,
                "POST",
                "/api/v1/probes",
                body.getBytes(StandardCharsets.UTF_8)
        );
        return post("/api/v1/probes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Api-Key", apiKey)
                .header("X-Key-Id", keyId)
                .header("X-Request-Id", requestId.toString())
                .header("X-Timestamp", Long.toString(timestamp.getEpochSecond()))
                .header("X-Signature", canonicalizer.sign(secret, canonical))
                .header("X-Correlation-Id", correlationId.toString());
    }

    private void assertReplayCount(int expected) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_request_replays",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expected);
    }
}
