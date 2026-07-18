package com.sweet.market.gateway.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public final class HmacCanonicalizer {

    public String bodySha256(byte[] body) {
        return HexFormat.of().formatHex(digest("SHA-256", body));
    }

    public String canonicalize(
            String keyId, Instant timestamp, UUID requestId,
            String method, String rawTarget, byte[] body
    ) {
        return String.join("\n",
                "v1", keyId, Long.toString(timestamp.getEpochSecond()),
                requestId.toString(), method.toUpperCase(Locale.ROOT),
                rawTarget, bodySha256(body));
    }

    public String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    public boolean matches(String expectedHex, String suppliedHex) {
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.US_ASCII),
                suppliedHex.getBytes(StandardCharsets.US_ASCII));
    }

    private byte[] digest(String algorithm, byte[] value) {
        try {
            return MessageDigest.getInstance(algorithm).digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " is unavailable", exception);
        }
    }
}
