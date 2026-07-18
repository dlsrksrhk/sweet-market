package com.sweet.market.provider.security;

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
    void 공개_vector와_동일한_HMAC을_계산한다() throws Exception {
        JsonNode vector = objectMapper.readTree(Files.readString(
                Path.of("..", "contracts", "hmac-v1-test-vectors.json")));
        byte[] body = vector.required("bodyUtf8").textValue().getBytes(StandardCharsets.UTF_8);

        String canonical = canonicalizer.canonicalize(
                vector.required("keyId").textValue(),
                Instant.ofEpochSecond(vector.required("timestamp").longValue()),
                UUID.fromString(vector.required("requestId").textValue()),
                vector.required("method").textValue(),
                vector.required("rawTarget").textValue(),
                body
        );

        assertThat(canonicalizer.bodySha256(body)).isEqualTo(vector.required("bodySha256").textValue());
        assertThat(canonical).isEqualTo(vector.required("canonical").textValue());
        assertThat(canonicalizer.sign(vector.required("secret").textValue(), canonical))
                .isEqualTo(vector.required("signature").textValue());
    }
}
