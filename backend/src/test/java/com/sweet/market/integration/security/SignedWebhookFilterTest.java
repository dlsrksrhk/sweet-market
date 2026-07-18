package com.sweet.market.integration.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SignedWebhookFilterTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_784_386_800L);

    @Test
    void contentLength없는_초과_body는_경계까지만_읽고_replay를_claim하지_않는다() throws Exception {
        AtomicInteger bytesRead = new AtomicInteger();
        AtomicBoolean replayClaimed = new AtomicBoolean();
        AtomicBoolean chainInvoked = new AtomicBoolean();
        ObjectMapper objectMapper = new ObjectMapper();
        ReplayGuard replayGuard = new ReplayGuard() {
            @Override
            public boolean tryClaim(ExternalSystem source, UUID requestId, Instant receivedAt, Instant expiresAt) {
                replayClaimed.set(true);
                return true;
            }

            @Override
            public int deleteExpired(Instant cutoff, int limit) {
                return 0;
            }
        };
        SignedWebhookFilter filter = new SignedWebhookFilter(
                validProperties(), new HmacCanonicalizer(), replayGuard,
                Clock.fixed(NOW, ZoneOffset.UTC), objectMapper);
        MockHttpServletRequest request = unknownLengthRequest(new byte[1_048_578], bytesRead);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        JsonNode error = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(error.size()).isEqualTo(3);
        assertThat(error.get("code").asText()).isEqualTo("INTEGRATION_BODY_TOO_LARGE");
        assertThat(error.get("message").asText()).isEqualTo("Request body is too large");
        assertThat(error.get("requestId").isNull()).isTrue();
        assertThat(bytesRead).hasValue(1_048_577);
        assertThat(replayClaimed).isFalse();
        assertThat(chainInvoked).isFalse();
    }

    private MockHttpServletRequest unknownLengthRequest(byte[] body, AtomicInteger bytesRead) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/api/integrations/payment-gateway/v1/probes") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override
                    public int read() {
                        int value = input.read();
                        if (value >= 0) {
                            bytesRead.incrementAndGet();
                        }
                        return value;
                    }

                    @Override
                    public int read(byte[] bytes, int offset, int length) {
                        int count = input.read(bytes, offset, length);
                        if (count > 0) {
                            bytesRead.addAndGet(count);
                        }
                        return count;
                    }

                    @Override
                    public boolean isFinished() {
                        return input.available() == 0;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                    }
                };
            }
        };
        request.addHeader("Transfer-Encoding", "chunked");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("X-Api-Key", "payment-api-key");
        request.addHeader("X-Key-Id", "payment-key-1");
        request.addHeader("X-Request-Id", "3b2f4cc6-c204-4b8d-9e98-2b3a487c85ed");
        request.addHeader("X-Timestamp", Long.toString(NOW.getEpochSecond()));
        request.addHeader("X-Signature", "0".repeat(64));
        request.addHeader("X-Correlation-Id", "42429beb-5d60-47b7-a3da-540cf1619d3b");
        return request;
    }

    private ExternalIntegrationProperties validProperties() {
        ExternalIntegrationProperties.InboundCredential inbound =
                new ExternalIntegrationProperties.InboundCredential(
                        "api-key", "key-1", "current-secret-32bytes-minimum",
                        "key-2", "next-secret-32bytes-minimum");
        ExternalIntegrationProperties.OutboundCredential outbound =
                new ExternalIntegrationProperties.OutboundCredential(
                        "api-key", "key-1", "current-secret-32bytes-minimum");
        return new ExternalIntegrationProperties(
                true,
                Duration.ofMinutes(5),
                1_048_576,
                Duration.ofMinutes(10),
                1_000,
                new ExternalIntegrationProperties.Inbound(inbound, inbound),
                new ExternalIntegrationProperties.Outbound(outbound, outbound)
        );
    }
}
