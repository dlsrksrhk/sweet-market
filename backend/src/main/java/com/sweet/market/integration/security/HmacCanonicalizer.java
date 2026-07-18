package com.sweet.market.integration.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public final class HmacCanonicalizer {

    private static final HexFormat HEX = HexFormat.of();

    public String canonicalize(
            String keyId,
            long timestamp,
            UUID requestId,
            String method,
            String rawTarget,
            byte[] body
    ) {
        return String.join("\n",
                "v1",
                keyId,
                Long.toString(timestamp),
                requestId.toString(),
                method,
                rawTarget,
                sha256(body));
    }

    public String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HEX.formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    public boolean matches(String expected, String supplied) {
        try {
            return MessageDigest.isEqual(HEX.parseHex(expected), HEX.parseHex(supplied));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String sha256(byte[] body) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
