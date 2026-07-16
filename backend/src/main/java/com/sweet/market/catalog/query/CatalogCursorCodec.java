package com.sweet.market.catalog.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

@Component
public class CatalogCursorCodec {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final Duration maxAge;
    private final Clock clock;

    @Autowired
    public CatalogCursorCodec(ObjectMapper objectMapper, CatalogCursorProperties properties) {
        this(objectMapper, properties.secret(), properties.maxAge(), Clock.systemUTC());
    }

    CatalogCursorCodec(ObjectMapper objectMapper, String secret, Duration maxAge, Clock clock) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.secret = Objects.requireNonNull(secret).getBytes(StandardCharsets.UTF_8);
        this.maxAge = Objects.requireNonNull(maxAge);
        this.clock = Objects.requireNonNull(clock);
    }

    public String encode(CatalogCursor cursor) {
        try {
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(cursor));
            return payload + '.' + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sign(payload));
        } catch (JsonProcessingException | GeneralSecurityException exception) {
            throw new IllegalStateException("Catalog cursor serialization failed", exception);
        }
    }

    public CatalogCursor decode(String token, String expectedFingerprint) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw invalidCursor();
            }

            byte[] actualSignature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(sign(parts[0]), actualSignature)) {
                throw invalidCursor();
            }

            CatalogCursor cursor = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]),
                    CatalogCursor.class
            );
            if (!Objects.equals(cursor.filterFingerprint(), expectedFingerprint)) {
                throw invalidCursor();
            }
            if (!cursor.expiresAt().isAfter(Instant.now(clock))) {
                throw new BusinessException(ErrorCode.CATALOG_CURSOR_STALE);
            }
            return cursor;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException | IOException | GeneralSecurityException exception) {
            throw invalidCursor();
        }
    }

    public Instant expiresAt() {
        return Instant.now(clock).plus(maxAge);
    }

    private byte[] sign(String payload) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_SHA_256);
        mac.init(new SecretKeySpec(secret, HMAC_SHA_256));
        return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
    }

    private BusinessException invalidCursor() {
        return new BusinessException(ErrorCode.CATALOG_CURSOR_INVALID);
    }
}
