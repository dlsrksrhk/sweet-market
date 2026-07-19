package com.sweet.market.gateway.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter extends OncePerRequestFilter {

    static final String CORRELATION_HEADER = "X-Correlation-Id";
    static final String CORRELATION_ATTRIBUTE = "integration.correlationId";
    static final String TRACEPARENT_ATTRIBUTE = "integration.traceparent";
    static final String MDC_KEY = "correlationId";

    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        UUID correlationId = parseCorrelationId(request.getHeader(CORRELATION_HEADER));
        request.setAttribute(CORRELATION_ATTRIBUTE, correlationId);

        String traceparent = request.getHeader("traceparent");
        if (traceparent != null && TRACEPARENT_PATTERN.matcher(traceparent).matches()) {
            request.setAttribute(TRACEPARENT_ATTRIBUTE, traceparent);
        }

        String correlationValue = correlationId.toString();
        response.setHeader(CORRELATION_HEADER, correlationValue);
        MDC.put(MDC_KEY, correlationValue);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private UUID parseCorrelationId(String value) {
        if (value != null) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                // Generate a correlation ID for absent or invalid general request context.
            }
        }
        return UUID.randomUUID();
    }
}
