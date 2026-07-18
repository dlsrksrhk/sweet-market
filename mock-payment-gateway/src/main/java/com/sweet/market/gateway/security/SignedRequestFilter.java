package com.sweet.market.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.gateway.web.IntegrationErrorResponse;
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

    private static final Pattern EPOCH_SECONDS = Pattern.compile("^[0-9]+$");
    private static final Pattern LOWERCASE_SHA256 = Pattern.compile("^[0-9a-f]{64}$");

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
        return !request.getRequestURI().startsWith("/api/v1/");
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

        SignedRequest signedRequest;
        try {
            signedRequest = parseSignedRequest(request, body);
        } catch (IllegalArgumentException | DateTimeException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "INTEGRATION_REQUEST_INVALID", "Signed request headers are invalid", null);
            return;
        }

        ResolvedCredential credential = resolveCredential(signedRequest.apiKey(), signedRequest.keyId());
        if (credential == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INTEGRATION_AUTHENTICATION_FAILED", "Request authentication failed", null);
            return;
        }

        Instant receivedAt = clock.instant();
        if (outsideAllowedClockSkew(signedRequest.timestamp(), receivedAt)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INTEGRATION_AUTHENTICATION_FAILED", "Request authentication failed", null);
            return;
        }

        String canonical = canonicalizer.canonicalize(
                signedRequest.keyId(),
                signedRequest.timestamp(),
                signedRequest.requestId(),
                signedRequest.method(),
                signedRequest.rawTarget(),
                signedRequest.body()
        );
        String expectedSignature = canonicalizer.sign(credential.secret(), canonical);
        if (!canonicalizer.matches(expectedSignature, signedRequest.signature())) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INTEGRATION_AUTHENTICATION_FAILED", "Request authentication failed", null);
            return;
        }

        Instant expiresAt = receivedAt.plus(properties.replayRetention());
        if (!replayGuard.tryClaim(
                credential.clientId(), signedRequest.requestId(), receivedAt, expiresAt)) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                    "INTEGRATION_REPLAY_DETECTED", "Request replay was detected", signedRequest.requestId());
            return;
        }

        request.setAttribute(REQUEST_ID_ATTRIBUTE, signedRequest.requestId());
        request.setAttribute(CLIENT_ID_ATTRIBUTE, credential.clientId());
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private SignedRequest parseSignedRequest(HttpServletRequest request, byte[] body) {
        String apiKey = requiredHeader(request, "X-Api-Key");
        String keyId = requiredHeader(request, "X-Key-Id");
        UUID requestId = UUID.fromString(requiredHeader(request, "X-Request-Id"));
        String timestampHeader = requiredHeader(request, "X-Timestamp");
        if (!EPOCH_SECONDS.matcher(timestampHeader).matches()) {
            throw new IllegalArgumentException("Invalid timestamp");
        }
        Instant timestamp = Instant.ofEpochSecond(Long.parseLong(timestampHeader));
        String signature = requiredHeader(request, "X-Signature");
        if (!LOWERCASE_SHA256.matcher(signature).matches()) {
            throw new IllegalArgumentException("Invalid signature");
        }
        if (!MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(request.getContentType())) {
            throw new IllegalArgumentException("Invalid content type");
        }
        String rawTarget = request.getRequestURI();
        if (request.getQueryString() != null) {
            rawTarget += "?" + request.getQueryString();
        }
        return new SignedRequest(
                apiKey, keyId, requestId, timestamp,
                request.getMethod(), rawTarget, body, signature
        );
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing header");
        }
        return value;
    }

    private ResolvedCredential resolveCredential(String apiKey, String keyId) {
        for (IntegrationSecurityProperties.Client client : properties.clients()) {
            if (!client.apiKey().equals(apiKey)) {
                continue;
            }
            for (IntegrationSecurityProperties.Key key : client.keys()) {
                if (key.keyId().equals(keyId)) {
                    return new ResolvedCredential(client.clientId(), key.secret());
                }
            }
        }
        return null;
    }

    private boolean outsideAllowedClockSkew(Instant timestamp, Instant receivedAt) {
        Duration difference = Duration.between(timestamp, receivedAt).abs();
        return difference.compareTo(properties.allowedClockSkew()) > 0;
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

    private record ResolvedCredential(String clientId, String secret) {}
}
