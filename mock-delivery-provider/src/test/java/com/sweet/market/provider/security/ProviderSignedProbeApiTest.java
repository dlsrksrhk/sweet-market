package com.sweet.market.provider.security;

import com.sweet.market.provider.support.ProviderIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "provider.integration-security.allowed-clock-skew=PT5M",
        "provider.integration-security.max-body-bytes=1048576",
        "provider.integration-security.replay-retention=PT10M",
        "provider.integration-security.cleanup-batch-size=1000",
        "provider.integration-security.clients[0].client-id=sweet-market",
        "provider.integration-security.clients[0].api-key=provider-api-key",
        "provider.integration-security.clients[0].keys[0].key-id=current-key",
        "provider.integration-security.clients[0].keys[0].secret=current-secret-32-bytes-minimum-value",
        "provider.integration-security.clients[0].keys[1].key-id=next-key",
        "provider.integration-security.clients[0].keys[1].secret=next-secret-32-bytes-minimum-value"
})
@AutoConfigureMockMvc
@Import(ProviderSignedProbeApiTest.FixedClockConfiguration.class)
class ProviderSignedProbeApiTest extends ProviderIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");
    private static final String BODY = "{\"message\":\"delivery-contract-probe\"}";
    private static final String CURRENT_KEY_ID = "current-key";
    private static final String CURRENT_SECRET = "current-secret-32-bytes-minimum-value";
    private static final String NEXT_KEY_ID = "next-key";
    private static final String NEXT_SECRET = "next-secret-32-bytes-minimum-value";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearReplays() {
        jdbcTemplate.update("DELETE FROM integration_request_replays");
    }

    @Test
    void 올바른_서명은_배송_probe를_허용한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        mockMvc.perform(signedPost("/api/v1/probes", BODY, requestId, correlationId,
                        NOW, CURRENT_KEY_ID, CURRENT_SECRET, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.service").value("mock-delivery-provider"))
                .andExpect(jsonPath("$.message").value("delivery-contract-probe"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()));
    }

    @Test
    void rotation_중_두_keyId를_허용한다() throws Exception {
        mockMvc.perform(signedPost("/api/v1/probes", BODY, UUID.randomUUID(), UUID.randomUUID(),
                        NOW, CURRENT_KEY_ID, CURRENT_SECRET, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk());
        mockMvc.perform(signedPost("/api/v1/probes", BODY, UUID.randomUUID(), UUID.randomUUID(),
                        NOW, NEXT_KEY_ID, NEXT_SECRET, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk());
    }

    @Test
    void 잘못된_signature와_만료_timestamp를_거부한다() throws Exception {
        MockHttpServletRequestBuilder invalidSignature = signedPost(
                "/api/v1/probes", BODY, UUID.randomUUID(), UUID.randomUUID(),
                NOW, CURRENT_KEY_ID, CURRENT_SECRET, MediaType.APPLICATION_JSON_VALUE)
                .with(request -> replaceHeader(request, "X-Signature", "0".repeat(64)));

        mockMvc.perform(invalidSignature)
                .andExpect(status().isUnauthorized())
                .andExpect(integrationError(
                        "INTEGRATION_AUTHENTICATION_FAILED", "Request authentication failed", null));

        mockMvc.perform(signedPost("/api/v1/probes", BODY, UUID.randomUUID(), UUID.randomUUID(),
                        NOW.minusSeconds(301), CURRENT_KEY_ID, CURRENT_SECRET,
                        MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(integrationError(
                        "INTEGRATION_AUTHENTICATION_FAILED", "Request authentication failed", null));
    }

    @Test
    void replay는_한번만_처리한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        MockHttpServletRequestBuilder first = signedPost(
                "/api/v1/probes", BODY, requestId, UUID.randomUUID(),
                NOW, CURRENT_KEY_ID, CURRENT_SECRET, MediaType.APPLICATION_JSON_VALUE);
        MockHttpServletRequestBuilder replay = signedPost(
                "/api/v1/probes", BODY, requestId, UUID.randomUUID(),
                NOW, CURRENT_KEY_ID, CURRENT_SECRET, MediaType.APPLICATION_JSON_VALUE);

        mockMvc.perform(first).andExpect(status().isOk());
        mockMvc.perform(replay)
                .andExpect(status().isConflict())
                .andExpect(integrationError(
                        "INTEGRATION_REPLAY_DETECTED", "Request replay was detected", requestId));
    }

    @Test
    void 서명실패는_replay_table을_오염시키지_않는다() throws Exception {
        UUID requestId = UUID.randomUUID();
        MockHttpServletRequestBuilder invalid = signedPost(
                "/api/v1/probes", BODY, requestId, UUID.randomUUID(),
                NOW, CURRENT_KEY_ID, CURRENT_SECRET, MediaType.APPLICATION_JSON_VALUE)
                .with(request -> replaceHeader(request, "X-Signature", "f".repeat(64)));

        mockMvc.perform(invalid).andExpect(status().isUnauthorized());

        org.assertj.core.api.Assertions.assertThat(replayCount()).isZero();
    }

    @Test
    void raw_body_limit를_JSON_parse전에_적용한다() throws Exception {
        mockMvc.perform(post("/api/v1/probes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("x".repeat(1_048_577)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(integrationError(
                        "INTEGRATION_BODY_TOO_LARGE", "Request body is too large", null));
    }

    @Test
    void correlation_UUID를_replay_claim전에_검증한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        org.springframework.http.HttpHeaders headers = signedHeaders(
                "POST", "/api/v1/probes", BODY, requestId, UUID.randomUUID(),
                NOW, CURRENT_KEY_ID, CURRENT_SECRET);
        headers.remove("X-Correlation-Id");
        MockHttpServletRequestBuilder request = post("/api/v1/probes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .headers(headers);

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(integrationError(
                        "INTEGRATION_REQUEST_INVALID", "Signed request headers are invalid", null));

        org.assertj.core.api.Assertions.assertThat(replayCount()).isZero();
    }

    @Test
    void request와_correlation_UUID는_canonical_형식이어야_한다() throws Exception {
        MockHttpServletRequestBuilder nonCanonicalRequestId = signedPost(
                "/api/v1/probes", BODY, UUID.randomUUID(), UUID.randomUUID(),
                NOW, CURRENT_KEY_ID, CURRENT_SECRET, MediaType.APPLICATION_JSON_VALUE)
                .with(request -> replaceHeader(
                        request, "X-Request-Id", "3b2f8c6a-2f88-4f75-8c7b-4ad40b519a4"));
        MockHttpServletRequestBuilder nonCanonicalCorrelationId = signedPost(
                "/api/v1/probes", BODY, UUID.randomUUID(), UUID.randomUUID(),
                NOW, CURRENT_KEY_ID, CURRENT_SECRET, MediaType.APPLICATION_JSON_VALUE)
                .with(request -> replaceHeader(
                        request, "X-Correlation-Id", "3b2f8c6a-2f88-4f75-8c7b-4ad40b519a4"));

        mockMvc.perform(nonCanonicalRequestId).andExpect(status().isBadRequest());
        mockMvc.perform(nonCanonicalCorrelationId).andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(replayCount()).isZero();
    }

    @Test
    void charset_parameter가_있는_JSON을_허용한다() throws Exception {
        mockMvc.perform(signedPost("/api/v1/probes", BODY, UUID.randomUUID(), UUID.randomUUID(),
                        NOW, CURRENT_KEY_ID, CURRENT_SECRET, "application/json;charset=UTF-8"))
                .andExpect(status().isOk());
    }

    @Test
    void api_v1_exact와_descendant만_서명_filter를_통과한다() throws Exception {
        mockMvc.perform(get("/api/v1"))
                .andExpect(status().isBadRequest())
                .andExpect(integrationError(
                        "INTEGRATION_REQUEST_INVALID", "Signed request headers are invalid", null));

        mockMvc.perform(get("/api/v10/probes"))
                .andExpect(status().isForbidden())
                .andExpect(integrationError(
                        "INTEGRATION_REQUEST_INVALID", "Requested route is not allowed", null));
    }

    @Test
    void health는_서명없이_허용한다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void 인증된_undefined_route와_method_error는_requestId를_보존한다() throws Exception {
        UUID notFoundRequestId = UUID.randomUUID();
        UUID methodRequestId = UUID.randomUUID();

        mockMvc.perform(signedPost("/api/v1/undefined", BODY, notFoundRequestId, UUID.randomUUID(),
                        NOW, CURRENT_KEY_ID, CURRENT_SECRET, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(integrationError(
                        "INTEGRATION_REQUEST_INVALID", "Requested route was not found", notFoundRequestId));

        mockMvc.perform(signedGet("/api/v1/probes", methodRequestId, UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(integrationError(
                        "INTEGRATION_REQUEST_INVALID", "Request method is not allowed", methodRequestId));
    }

    private org.springframework.test.web.servlet.ResultMatcher integrationError(
            String code,
            String message,
            UUID requestId
    ) {
        return result -> {
            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON).match(result);
            jsonPath("$", aMapWithSize(3)).match(result);
            jsonPath("$.code").value(code).match(result);
            jsonPath("$.message").value(message).match(result);
            if (requestId == null) {
                jsonPath("$.requestId").value(nullValue()).match(result);
            } else {
                jsonPath("$.requestId").value(requestId.toString()).match(result);
            }
        };
    }

    private MockHttpServletRequestBuilder signedPost(
            String target,
            String body,
            UUID requestId,
            UUID correlationId,
            Instant timestamp,
            String keyId,
            String secret,
            String contentType
    ) throws Exception {
        return post(target)
                .contentType(contentType)
                .content(body)
                .headers(signedHeaders("POST", target, body, requestId, correlationId,
                        timestamp, keyId, secret));
    }

    private MockHttpServletRequestBuilder signedGet(
            String target,
            UUID requestId,
            UUID correlationId
    ) throws Exception {
        return get(target)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(signedHeaders("GET", target, "", requestId, correlationId,
                        NOW, CURRENT_KEY_ID, CURRENT_SECRET));
    }

    private org.springframework.http.HttpHeaders signedHeaders(
            String method,
            String target,
            String body,
            UUID requestId,
            UUID correlationId,
            Instant timestamp,
            String keyId,
            String secret
    ) throws Exception {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-Api-Key", "provider-api-key");
        headers.add("X-Key-Id", keyId);
        headers.add("X-Request-Id", requestId.toString());
        headers.add("X-Timestamp", Long.toString(timestamp.getEpochSecond()));
        headers.add("X-Signature", signature(method, target, body, requestId, timestamp, keyId, secret));
        headers.add("X-Correlation-Id", correlationId.toString());
        return headers;
    }

    private String signature(
            String method,
            String target,
            String body,
            UUID requestId,
            Instant timestamp,
            String keyId,
            String secret
    ) throws Exception {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String bodyHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bodyBytes));
        String canonical = String.join("\n",
                "v1", keyId, Long.toString(timestamp.getEpochSecond()), requestId.toString(),
                method.toUpperCase(Locale.ROOT), target, bodyHash);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private int replayCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_request_replays", Integer.class);
    }

    private org.springframework.mock.web.MockHttpServletRequest replaceHeader(
            org.springframework.mock.web.MockHttpServletRequest request,
            String name,
            String value
    ) {
        request.removeHeader(name);
        request.addHeader(name, value);
        return request;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock providerTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
