package com.sweet.market.integration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalRequestSignerTest {

    private static final UUID REQUEST_ID = UUID.fromString("3b2f8c6a-2f88-4f75-8c7b-4ad40b519a41");
    private static final Instant TIMESTAMP = Instant.ofEpochSecond(1_784_386_800L);

    @Test
    void 공개_vector와_동일한_외부요청_signature를_소문자_method로도_생성한다() throws Exception {
        HmacVector vector = new ObjectMapper().readValue(
                Path.of("..", "contracts", "hmac-v1-test-vectors.json").toFile(), HmacVector.class);
        ExternalRequestSigner signer = new HmacExternalRequestSigner(properties(), new HmacCanonicalizer());

        SignedHeaders headers = signer.sign(
                ExternalSystem.PAYMENT_GATEWAY,
                vector.method().toLowerCase(java.util.Locale.ROOT),
                vector.rawTarget(),
                UUID.fromString(vector.requestId()),
                Instant.ofEpochSecond(vector.timestamp()),
                vector.bodyUtf8().getBytes(StandardCharsets.UTF_8),
                "409b1a12-0dc7-4d67-839c-8f4750ba901a"
        );

        assertThat(headers).isEqualTo(new SignedHeaders(
                vector.apiKey(),
                vector.keyId(),
                UUID.fromString(vector.requestId()),
                vector.timestamp(),
                vector.signature(),
                "409b1a12-0dc7-4d67-839c-8f4750ba901a"
        ));
    }

    @Test
    void source별_API_key와_keyId를_분리한다() {
        ExternalRequestSigner signer = new HmacExternalRequestSigner(properties(), new HmacCanonicalizer());

        SignedHeaders payment = signer.sign(
                ExternalSystem.PAYMENT_GATEWAY, "POST", "/payment", UUID.randomUUID(),
                TIMESTAMP, new byte[0], UUID.randomUUID().toString());
        SignedHeaders delivery = signer.sign(
                ExternalSystem.DELIVERY_PROVIDER, "POST", "/delivery", UUID.randomUUID(),
                TIMESTAMP, new byte[0], UUID.randomUUID().toString());

        assertThat(payment.apiKey()).isEqualTo("m32-test-api-key");
        assertThat(payment.keyId()).isEqualTo("m32-test-key-1");
        assertThat(delivery.apiKey()).isEqualTo("delivery-outbound-api-key");
        assertThat(delivery.keyId()).isEqualTo("delivery-outbound-key-1");
        assertThat(payment.signature()).isNotEqualTo(delivery.signature());
    }

    private ExternalIntegrationProperties properties() {
        return new ExternalIntegrationProperties(
                true,
                Duration.ofMinutes(5),
                1_048_576,
                Duration.ofMinutes(10),
                1_000,
                new ExternalIntegrationProperties.Inbound(
                        new ExternalIntegrationProperties.InboundCredential(
                                "payment-webhook-api-key", "payment-webhook-key-1", "payment-webhook-secret-32bytes-minimum",
                                "payment-webhook-key-2", "payment-webhook-next-secret-32bytes-minimum"),
                        new ExternalIntegrationProperties.InboundCredential(
                                "delivery-webhook-api-key", "delivery-webhook-key-1", "delivery-webhook-secret-32bytes-minimum",
                                "delivery-webhook-key-2", "delivery-webhook-next-secret-32bytes-minimum")
                ),
                new ExternalIntegrationProperties.Outbound(
                        new ExternalIntegrationProperties.OutboundCredential(
                                "m32-test-api-key", "m32-test-key-1", "m32-test-hmac-secret-32bytes-minimum"),
                        new ExternalIntegrationProperties.OutboundCredential(
                                "delivery-outbound-api-key", "delivery-outbound-key-1", "delivery-outbound-secret-32bytes-minimum")
                )
        );
    }

    private record HmacVector(
            String apiKey,
            String keyId,
            String secret,
            long timestamp,
            String requestId,
            String method,
            String rawTarget,
            String bodyUtf8,
            String bodySha256,
            String canonical,
            String signature
    ) {
    }
}
