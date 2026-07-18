package com.sweet.market.integration.security;

import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "market.external-integrations.enabled=true",
        "market.external-integrations.allowed-clock-skew=PT5M",
        "market.external-integrations.max-body-bytes=1048576",
        "market.external-integrations.replay-retention=PT10M",
        "market.external-integrations.cleanup-batch-size=1000",
        "market.external-integrations.inbound.payment-gateway.api-key=payment-webhook-api-key",
        "market.external-integrations.inbound.payment-gateway.current-key-id=payment-webhook-key-1",
        "market.external-integrations.inbound.payment-gateway.current-secret=payment-webhook-current-secret-32bytes-minimum",
        "market.external-integrations.inbound.payment-gateway.next-key-id=payment-webhook-key-2",
        "market.external-integrations.inbound.payment-gateway.next-secret=payment-webhook-next-secret-32bytes-minimum",
        "market.external-integrations.inbound.delivery-provider.api-key=delivery-webhook-api-key",
        "market.external-integrations.inbound.delivery-provider.current-key-id=delivery-webhook-key-1",
        "market.external-integrations.inbound.delivery-provider.current-secret=delivery-webhook-current-secret-32bytes-minimum",
        "market.external-integrations.inbound.delivery-provider.next-key-id=delivery-webhook-key-2",
        "market.external-integrations.inbound.delivery-provider.next-secret=delivery-webhook-next-secret-32bytes-minimum",
        "market.external-integrations.outbound.payment-gateway.api-key=payment-outbound-api-key",
        "market.external-integrations.outbound.payment-gateway.current-key-id=payment-outbound-key-1",
        "market.external-integrations.outbound.payment-gateway.current-secret=payment-outbound-secret-32bytes-minimum",
        "market.external-integrations.outbound.delivery-provider.api-key=delivery-outbound-api-key",
        "market.external-integrations.outbound.delivery-provider.current-key-id=delivery-outbound-key-1",
        "market.external-integrations.outbound.delivery-provider.current-secret=delivery-outbound-secret-32bytes-minimum"
})
class SignedWebhookProbeApiTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.ofEpochSecond(1_784_386_800L);
    private static final String PAYMENT_PATH = "/api/integrations/payment-gateway/v1/probes";
    private static final String DELIVERY_PATH = "/api/integrations/delivery-provider/v1/probes";
    private static final String PAYMENT_PREFIX = "/api/integrations/payment-gateway/";
    private static final String PAYMENT_API_KEY = "payment-webhook-api-key";
    private static final String PAYMENT_KEY_ID = "payment-webhook-key-1";
    private static final String PAYMENT_SECRET = "payment-webhook-current-secret-32bytes-minimum";
    private static final String PAYMENT_NEXT_KEY_ID = "payment-webhook-key-2";
    private static final String PAYMENT_NEXT_SECRET = "payment-webhook-next-secret-32bytes-minimum";
    private static final String DELIVERY_API_KEY = "delivery-webhook-api-key";
    private static final String DELIVERY_KEY_ID = "delivery-webhook-key-1";
    private static final String DELIVERY_SECRET = "delivery-webhook-current-secret-32bytes-minimum";

    @TestBean(name = "externalIntegrationClock", methodName = "fixedExternalIntegrationClock")
    private Clock externalIntegrationClock;

    @BeforeEach
    void replay_테이블을_비운다() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS external_integration_request_replays (
                    id BIGSERIAL PRIMARY KEY,
                    source VARCHAR(40) NOT NULL,
                    request_id UUID NOT NULL,
                    received_at TIMESTAMPTZ NOT NULL,
                    expires_at TIMESTAMPTZ NOT NULL,
                    CONSTRAINT uq_external_replay_source_request UNIQUE (source, request_id)
                )
                """);
        jdbcTemplate.update("DELETE FROM external_integration_request_replays");
    }

    @Test
    void 결제_gateway의_서명된_probe_webhook을_수신한다() throws Exception {
        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        UUID.randomUUID(), NOW))
                .andExpect(status().isNoContent());
    }

    @Test
    void 배송_provider의_서명된_probe_webhook을_수신한다() throws Exception {
        mockMvc.perform(signedRequest(DELIVERY_PATH, deliveryBody(), DELIVERY_API_KEY, DELIVERY_KEY_ID, DELIVERY_SECRET,
                        UUID.randomUUID(), NOW))
                .andExpect(status().isNoContent());
    }

    @Test
    void path의_source와_body의_source가_다르면_거부한다() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(signedRequest(PAYMENT_PATH, deliveryBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        requestId, NOW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").value("Webhook source does not match request path"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
    }

    @Test
    void source별_API_key와_keyId를_분리한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), DELIVERY_API_KEY, DELIVERY_KEY_ID, DELIVERY_SECRET,
                        requestId, NOW))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));

        assertReplayCount(0);
    }

    @Test
    void 현재키와_다음키를_rotation중_허용한다() throws Exception {
        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        UUID.randomUUID(), NOW))
                .andExpect(status().isNoContent());
        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_NEXT_KEY_ID, PAYMENT_NEXT_SECRET,
                        UUID.randomUUID(), NOW))
                .andExpect(status().isNoContent());
    }

    @Test
    void webhook_replay와_변조와_만료를_거부한다() throws Exception {
        UUID replayRequestId = UUID.randomUUID();
        Instant now = NOW;
        MockHttpServletRequestBuilder valid = signedRequest(
                PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET, replayRequestId, now);

        mockMvc.perform(valid).andExpect(status().isNoContent());
        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        replayRequestId, now))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REPLAY_DETECTED"))
                .andExpect(jsonPath("$.requestId").value(replayRequestId.toString()));

        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                                UUID.randomUUID(), now)
                        .content("{\"source\":\"PAYMENT_GATEWAY\",\"message\":\"tampered\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTEGRATION_AUTHENTICATION_FAILED"));

        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        UUID.randomUUID(), now.minusSeconds(301)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTEGRATION_AUTHENTICATION_FAILED"));
    }

    @Test
    void 서명실패는_replay를_claim하지_않는다() throws Exception {
        UUID requestId = UUID.randomUUID();
        Instant now = NOW;

        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID,
                                "wrong-secret-32bytes-minimum", requestId, now))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID,
                        PAYMENT_SECRET, requestId, now))
                .andExpect(status().isNoContent());

        assertReplayCount(1);
    }

    @Test
    void 일_MiB초과_webhook을_parse전에_거부한다() throws Exception {
        mockMvc.perform(post(PAYMENT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new byte[1_048_577]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_BODY_TOO_LARGE"))
                .andExpect(jsonPath("$.requestId").value(nullValue()));

        assertReplayCount(0);
    }

    @Test
    void 잘못된_correlation_UUID와_contentType을_replay전에_거부한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        MockHttpServletRequestBuilder malformedCorrelation = signedRequest(
                PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET, requestId, NOW);
        malformedCorrelation.with(request -> {
            request.removeHeader("X-Correlation-Id");
            request.addHeader("X-Correlation-Id", "not-a-uuid");
            return request;
        });

        mockMvc.perform(malformedCorrelation)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                                UUID.randomUUID(), NOW)
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"));

        assertReplayCount(0);
    }

    @Test
    void 미래의_integration_route를_wildcard로_공개하지_않는다() throws Exception {
        mockMvc.perform(post("/api/integrations/future/v1/probes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_잘못된_body는_통합_오류_envelope를_반환한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        String malformedBody = "{\"source\":\"PAYMENT_GATEWAY\",\"message\":";

        mockMvc.perform(signedRequest(PAYMENT_PATH, malformedBody, PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        requestId, NOW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").value("Webhook request body is invalid"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
    }

    @Test
    void 인증된_빈_message는_통합_오류_envelope를_반환한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        String invalidBody = "{\"source\":\"PAYMENT_GATEWAY\",\"message\":\"\"}";

        mockMvc.perform(signedRequest(PAYMENT_PATH, invalidBody, PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        requestId, NOW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").value("Webhook request body is invalid"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
    }

    @Test
    void 알수없는_body_field를_거부한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        String body = "{\"source\":\"PAYMENT_GATEWAY\",\"message\":\"probe\",\"extra\":true}";

        mockMvc.perform(signedRequest(PAYMENT_PATH, body, PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        requestId, NOW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").value("Webhook request body is invalid"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
    }

    @Test
    void 정확히_과거와_미래_300초_timestamp를_허용한다() throws Exception {
        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        UUID.randomUUID(), NOW.minusSeconds(300)))
                .andExpect(status().isNoContent());
        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        UUID.randomUUID(), NOW.plusSeconds(300)))
                .andExpect(status().isNoContent());
    }

    @Test
    void 과거와_미래_301초_timestamp를_requestId와_함께_거부한다() throws Exception {
        UUID pastRequestId = UUID.randomUUID();
        UUID futureRequestId = UUID.randomUUID();

        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        pastRequestId, NOW.minusSeconds(301)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value(pastRequestId.toString()));
        mockMvc.perform(signedRequest(PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        futureRequestId, NOW.plusSeconds(301)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.requestId").value(futureRequestId.toString()));

        assertReplayCount(0);
    }

    @Test
    void timestamp_overflow는_requestId가_있는_400으로_거부한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        MockHttpServletRequestBuilder overflow = signedRequest(
                PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET, requestId, NOW);
        overflow.with(request -> {
            request.removeHeader("X-Timestamp");
            request.addHeader("X-Timestamp", Long.toString(Long.MAX_VALUE));
            return request;
        });

        mockMvc.perform(overflow)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));

        assertReplayCount(0);
    }

    @Test
    void noncanonical_request_UUID_alias는_replay전에_거부한다() throws Exception {
        String alias = "1-1-1-1-1";
        UUID normalized = UUID.fromString(alias);
        MockHttpServletRequestBuilder request = signedRequest(
                PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET, normalized, NOW);
        request.with(servletRequest -> {
            servletRequest.removeHeader("X-Request-Id");
            servletRequest.addHeader("X-Request-Id", alias);
            return servletRequest;
        });

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.requestId").value(nullValue()));

        assertReplayCount(0);
    }

    @Test
    void malformed_signature_header는_parsed_requestId를_보존한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        MockHttpServletRequestBuilder request = signedRequest(
                PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET, requestId, NOW);
        request.with(servletRequest -> {
            servletRequest.removeHeader("X-Signature");
            servletRequest.addHeader("X-Signature", "not-a-signature");
            return servletRequest;
        });

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));

        assertReplayCount(0);
    }

    @Test
    void raw_query_target을_서명에_포함한다() throws Exception {
        String rawTarget = PAYMENT_PATH + "?probe=raw&mode=query";

        mockMvc.perform(signedRequest(rawTarget, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID, PAYMENT_SECRET,
                        UUID.randomUUID(), NOW))
                .andExpect(status().isNoContent());
    }

    @Test
    void contentLength없이_일_MiB초과_chunked_body를_raw_read로_거부한다() throws Exception {
        mockMvc.perform(post(PAYMENT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Transfer-Encoding", "chunked")
                        .content(new byte[1_048_577]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("INTEGRATION_BODY_TOO_LARGE"))
                .andExpect(jsonPath("$.requestId").value(nullValue()));
    }

    @Test
    void 서명된_없는_route와_지원하지_않는_method는_정확한_envelope를_반환한다() throws Exception {
        UUID notFoundRequestId = UUID.randomUUID();
        String unknownPath = PAYMENT_PREFIX + "v1/not-defined";
        mockMvc.perform(signedRequest(
                        HttpMethod.POST, unknownPath, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID,
                        PAYMENT_SECRET, notFoundRequestId, NOW))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").value("Requested route was not found"))
                .andExpect(jsonPath("$.requestId").value(notFoundRequestId.toString()));

        UUID methodRequestId = UUID.randomUUID();
        mockMvc.perform(signedRequest(
                        HttpMethod.PUT, PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID,
                        PAYMENT_SECRET, methodRequestId, NOW))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").value("INTEGRATION_REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").value("Request method is not allowed"))
                .andExpect(jsonPath("$.requestId").value(methodRequestId.toString()));
    }

    @Test
    void 동시에_같은_replay를_claim하면_하나만_성공한다() throws Exception {
        UUID requestId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < 2; attempt++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(signedRequest(
                                    PAYMENT_PATH, paymentBody(), PAYMENT_API_KEY, PAYMENT_KEY_ID,
                                    PAYMENT_SECRET, requestId, NOW))
                            .andReturn();
                }));
            }
            ready.await();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                statuses.add(future.get().getResponse().getStatus());
            }
            Collections.sort(statuses);
            org.assertj.core.api.Assertions.assertThat(statuses).containsExactly(204, 409);
            assertReplayCount(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private MockHttpServletRequestBuilder signedRequest(
            String path,
            String body,
            String apiKey,
            String keyId,
            String secret,
            UUID requestId,
            Instant timestamp
    ) throws Exception {
        return signedRequest(HttpMethod.POST, path, body, apiKey, keyId, secret, requestId, timestamp);
    }

    private MockHttpServletRequestBuilder signedRequest(
            HttpMethod method,
            String rawTarget,
            String body,
            String apiKey,
            String keyId,
            String secret,
            UUID requestId,
            Instant timestamp
    ) throws Exception {
        String correlationId = UUID.randomUUID().toString();
        String bodyHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        String canonical = String.join("\n",
                "v1", keyId, Long.toString(timestamp.getEpochSecond()), requestId.toString(),
                method.name(), rawTarget, bodyHash);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        return request(method, rawTarget)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Api-Key", apiKey)
                .header("X-Key-Id", keyId)
                .header("X-Request-Id", requestId.toString())
                .header("X-Timestamp", timestamp.getEpochSecond())
                .header("X-Signature", signature)
                .header("X-Correlation-Id", correlationId);
    }

    private String paymentBody() {
        return "{\"source\":\"PAYMENT_GATEWAY\",\"message\":\"payment-probe\"}";
    }

    private String deliveryBody() {
        return "{\"source\":\"DELIVERY_PROVIDER\",\"message\":\"delivery-probe\"}";
    }

    private void assertReplayCount(int expected) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM external_integration_request_replays", Integer.class);
        assertThat(count).isEqualTo(expected);
    }

    private static org.assertj.core.api.AbstractIntegerAssert<?> assertThat(Integer value) {
        return org.assertj.core.api.Assertions.assertThat(value);
    }

    static Clock fixedExternalIntegrationClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
