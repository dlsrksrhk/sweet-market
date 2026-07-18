package com.sweet.market.integration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.integration.web.IntegrationErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public final class SignedWebhookFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "integration.requestId";
    public static final String CORRELATION_ID_ATTRIBUTE = "integration.correlationId";
    public static final String SOURCE_ATTRIBUTE = "integration.source";

    private static final String PAYMENT_PATH = "/api/integrations/payment-gateway/v1/probes";
    private static final String DELIVERY_PATH = "/api/integrations/delivery-provider/v1/probes";
    private static final Pattern EPOCH_SECONDS = Pattern.compile("^[0-9]+$");
    private static final Pattern LOWERCASE_SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private final ExternalIntegrationProperties properties;
    private final HmacCanonicalizer canonicalizer;
    private final ReplayGuard replayGuard;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public SignedWebhookFilter(
            ExternalIntegrationProperties properties,
            HmacCanonicalizer canonicalizer,
            ReplayGuard replayGuard,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.canonicalizer = canonicalizer;
        this.replayGuard = replayGuard;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !PAYMENT_PATH.equals(path) && !DELIVERY_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] body = request.getInputStream().readNBytes(properties.maxBodyBytes() + 1);
        if (body.length > properties.maxBodyBytes()) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "INTEGRATION_BODY_TOO_LARGE", "Request body is too large", null);
            return;
        }

        ExternalSystem source = sourceFor(request.getRequestURI());
        ParsedRequest parsed;
        try {
            parsed = parse(request);
        } catch (IllegalArgumentException | DateTimeException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "INTEGRATION_REQUEST_INVALID", "Signed request headers are invalid", null);
            return;
        }

        ExternalIntegrationProperties.InboundCredential credential =
                properties.inboundCredential(source);
        String secret = resolveSecret(credential, parsed.apiKey(), parsed.keyId());
        if (secret == null) {
            authenticationFailed(response);
            return;
        }

        Instant receivedAt = clock.instant();
        if (outsideAllowedClockSkew(parsed.timestamp(), receivedAt)) {
            authenticationFailed(response);
            return;
        }

        String canonical = canonicalizer.canonicalize(
                parsed.keyId(),
                parsed.timestamp().getEpochSecond(),
                parsed.requestId(),
                request.getMethod(),
                rawTarget(request),
                body
        );
        if (!canonicalizer.matches(canonicalizer.sign(secret, canonical), parsed.signature())) {
            authenticationFailed(response);
            return;
        }

        if (!replayGuard.tryClaim(
                source,
                parsed.requestId(),
                receivedAt,
                receivedAt.plus(properties.replayRetention()))) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                    "INTEGRATION_REPLAY_DETECTED", "Request replay was detected", parsed.requestId());
            return;
        }

        request.setAttribute(REQUEST_ID_ATTRIBUTE, parsed.requestId());
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, parsed.correlationId());
        request.setAttribute(SOURCE_ATTRIBUTE, source);
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private ParsedRequest parse(HttpServletRequest request) {
        String apiKey = requiredHeader(request, "X-Api-Key");
        String keyId = requiredHeader(request, "X-Key-Id");
        UUID requestId = canonicalUuid(requiredHeader(request, "X-Request-Id"));
        String timestampValue = requiredHeader(request, "X-Timestamp");
        if (!EPOCH_SECONDS.matcher(timestampValue).matches()) {
            throw new IllegalArgumentException("Timestamp must contain epoch seconds");
        }
        Instant timestamp = Instant.ofEpochSecond(Long.parseLong(timestampValue));
        String signature = requiredHeader(request, "X-Signature");
        if (!LOWERCASE_SHA256.matcher(signature).matches()) {
            throw new IllegalArgumentException("Signature must be lowercase SHA-256 hex");
        }
        UUID correlationId = canonicalUuid(requiredHeader(request, "X-Correlation-Id"));
        MediaType contentType = MediaType.parseMediaType(requiredHeader(request, "Content-Type"));
        if (!MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            throw new IllegalArgumentException("Content-Type must be compatible with application/json");
        }
        return new ParsedRequest(apiKey, keyId, requestId, timestamp, signature, correlationId);
    }

    private String resolveSecret(
            ExternalIntegrationProperties.InboundCredential credential,
            String apiKey,
            String keyId
    ) {
        if (!credential.apiKey().equals(apiKey)) {
            return null;
        }
        if (credential.currentKeyId().equals(keyId)) {
            return credential.currentSecret();
        }
        if (credential.nextKeyId().equals(keyId)) {
            return credential.nextSecret();
        }
        return null;
    }

    private boolean outsideAllowedClockSkew(Instant timestamp, Instant receivedAt) {
        return Duration.between(timestamp, receivedAt).abs()
                .compareTo(properties.allowedClockSkew()) > 0;
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required signed header is missing");
        }
        return value;
    }

    private UUID canonicalUuid(String value) {
        UUID uuid = UUID.fromString(value);
        if (!uuid.toString().equals(value)) {
            throw new IllegalArgumentException("UUID must use canonical representation");
        }
        return uuid;
    }

    private ExternalSystem sourceFor(String path) {
        if (PAYMENT_PATH.equals(path)) {
            return ExternalSystem.PAYMENT_GATEWAY;
        }
        if (DELIVERY_PATH.equals(path)) {
            return ExternalSystem.DELIVERY_PROVIDER;
        }
        throw new IllegalArgumentException("Unsupported signed webhook path");
    }

    private String rawTarget(HttpServletRequest request) {
        if (request.getQueryString() == null) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + request.getQueryString();
    }

    private void authenticationFailed(HttpServletResponse response) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                "INTEGRATION_AUTHENTICATION_FAILED", "Request authentication failed", null);
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String code,
            String message,
            UUID requestId
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new IntegrationErrorResponse(code, message, requestId));
    }

    private record ParsedRequest(
            String apiKey,
            String keyId,
            UUID requestId,
            Instant timestamp,
            String signature,
            UUID correlationId
    ) {
    }
}
