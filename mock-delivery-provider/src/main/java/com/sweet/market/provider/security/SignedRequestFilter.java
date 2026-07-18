package com.sweet.market.provider.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.provider.web.IntegrationErrorResponse;
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

public final class SignedRequestFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "integration.requestId";
    public static final String CLIENT_ID_ATTRIBUTE = "integration.clientId";
    public static final String CORRELATION_ID_ATTRIBUTE = "integration.correlationId";

    private static final Pattern EPOCH_SECONDS = Pattern.compile("^[0-9]+$");
    private static final Pattern LOWERCASE_HMAC = Pattern.compile("^[0-9a-f]{64}$");

    private final IntegrationSecurityProperties properties;
    private final HmacCanonicalizer canonicalizer;
    private final ReplayGuard replayGuard;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public SignedRequestFilter(
            IntegrationSecurityProperties properties,
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
        return !path.equals("/api/v1") && !path.startsWith("/api/v1/");
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

        ParsedHeaders parsed;
        try {
            parsed = parseHeaders(request, body);
        } catch (IllegalArgumentException | DateTimeException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "INTEGRATION_REQUEST_INVALID", "Signed request headers are invalid", null);
            return;
        }

        SignedRequest signedRequest = parsed.signedRequest();
        Credential credential = findCredential(signedRequest.apiKey(), signedRequest.keyId());
        if (credential == null) {
            authenticationFailed(response);
            return;
        }

        Instant receivedAt = clock.instant();
        Duration age = Duration.between(signedRequest.timestamp(), receivedAt).abs();
        if (age.compareTo(properties.allowedClockSkew()) > 0) {
            authenticationFailed(response);
            return;
        }

        String canonical = canonicalizer.canonicalize(
                signedRequest.keyId(), signedRequest.timestamp(), signedRequest.requestId(),
                signedRequest.method(), signedRequest.rawTarget(), signedRequest.body());
        String expectedSignature = canonicalizer.sign(credential.secret(), canonical);
        if (!canonicalizer.matches(expectedSignature, signedRequest.signature())) {
            authenticationFailed(response);
            return;
        }

        Instant expiresAt = receivedAt.plus(properties.replayRetention());
        boolean firstUse = replayGuard.tryClaim(
                credential.clientId(), signedRequest.requestId(), receivedAt, expiresAt);
        if (!firstUse) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                    "INTEGRATION_REPLAY_DETECTED", "Request replay was detected",
                    signedRequest.requestId());
            return;
        }

        request.setAttribute(REQUEST_ID_ATTRIBUTE, signedRequest.requestId());
        request.setAttribute(CLIENT_ID_ATTRIBUTE, credential.clientId());
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, parsed.correlationId());
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private ParsedHeaders parseHeaders(HttpServletRequest request, byte[] body) {
        String apiKey = requiredHeader(request, "X-Api-Key");
        String keyId = requiredHeader(request, "X-Key-Id");
        UUID requestId = canonicalUuid(requiredHeader(request, "X-Request-Id"));

        String epochSeconds = requiredHeader(request, "X-Timestamp");
        if (!EPOCH_SECONDS.matcher(epochSeconds).matches()) {
            throw new IllegalArgumentException("Timestamp must contain epoch seconds");
        }
        Instant timestamp = Instant.ofEpochSecond(Long.parseLong(epochSeconds));

        String signature = requiredHeader(request, "X-Signature");
        if (!LOWERCASE_HMAC.matcher(signature).matches()) {
            throw new IllegalArgumentException("Signature must be lowercase HMAC hex");
        }

        UUID correlationId = canonicalUuid(requiredHeader(request, "X-Correlation-Id"));
        MediaType contentType = MediaType.parseMediaType(requiredHeader(request, "Content-Type"));
        if (!MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            throw new IllegalArgumentException("Content type must be compatible JSON");
        }

        String rawTarget = request.getRequestURI();
        if (request.getQueryString() != null) {
            rawTarget = rawTarget + "?" + request.getQueryString();
        }
        SignedRequest signedRequest = new SignedRequest(
                apiKey, keyId, requestId, timestamp, request.getMethod(), rawTarget, body, signature);
        return new ParsedHeaders(signedRequest, correlationId);
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required header is missing");
        }
        return value;
    }

    private UUID canonicalUuid(String value) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("UUID must use canonical form");
        }
        return parsed;
    }

    private Credential findCredential(String apiKey, String keyId) {
        for (IntegrationSecurityProperties.Client client : properties.clients()) {
            if (!client.apiKey().equals(apiKey)) {
                continue;
            }
            for (IntegrationSecurityProperties.Key key : client.keys()) {
                if (key.keyId().equals(keyId)) {
                    return new Credential(client.clientId(), key.secret());
                }
            }
        }
        return null;
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
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new IntegrationErrorResponse(code, message, requestId));
    }

    private record Credential(String clientId, String secret) {}

    private record ParsedHeaders(SignedRequest signedRequest, UUID correlationId) {}
}
