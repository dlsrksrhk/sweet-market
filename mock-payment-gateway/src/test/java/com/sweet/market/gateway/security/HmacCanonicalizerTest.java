package com.sweet.market.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HmacCanonicalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HmacCanonicalizer canonicalizer = new HmacCanonicalizer();

    @Test
    void 계약_vector의_body_digest와_canonical과_signature가_일치한다() throws Exception {
        JsonNode vector = objectMapper.readTree(Files.readString(
                Path.of("..", "contracts", "hmac-v1-test-vectors.json"),
                StandardCharsets.UTF_8
        ));
        byte[] body = vector.required("bodyUtf8").asText().getBytes(StandardCharsets.UTF_8);

        String canonical = canonicalizer.canonicalize(
                vector.required("keyId").asText(),
                Instant.ofEpochSecond(vector.required("timestamp").asLong()),
                UUID.fromString(vector.required("requestId").asText()),
                vector.required("method").asText(),
                vector.required("rawTarget").asText(),
                body
        );

        assertThat(canonicalizer.bodySha256(body)).isEqualTo(vector.required("bodySha256").asText());
        assertThat(canonical).isEqualTo(vector.required("canonical").asText());
        assertThat(canonicalizer.sign(vector.required("secret").asText(), canonical))
                .isEqualTo(vector.required("signature").asText());
        assertThat(canonicalizer.matches(vector.required("signature").asText(), vector.required("signature").asText()))
                .isTrue();
    }
}
