package com.sweet.market.provider.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.provider.web.IntegrationErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class ProviderSecurityConfig {

    @Bean
    Clock providerClock() {
        return Clock.systemUTC();
    }

    @Bean
    HmacCanonicalizer providerHmacCanonicalizer() {
        return new HmacCanonicalizer();
    }

    @Bean
    SignedRequestFilter providerSignedRequestFilter(
            IntegrationSecurityProperties properties,
            HmacCanonicalizer canonicalizer,
            ReplayGuard replayGuard,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        return new SignedRequestFilter(properties, canonicalizer, replayGuard, clock, objectMapper);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain providerSecurityFilterChain(
            HttpSecurity http,
            SignedRequestFilter signedRequestFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeDeniedRoute(request, response, objectMapper))
                        .accessDeniedHandler((request, response, exception) ->
                                writeDeniedRoute(request, response, objectMapper)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/**").permitAll()
                        .anyRequest().denyAll())
                .addFilterBefore(signedRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeDeniedRoute(
            HttpServletRequest request,
            HttpServletResponse response,
            ObjectMapper objectMapper
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new IntegrationErrorResponse(
                "INTEGRATION_REQUEST_INVALID",
                "Requested route is not allowed",
                authenticatedRequestId(request)));
    }

    private UUID authenticatedRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(SignedRequestFilter.REQUEST_ID_ATTRIBUTE);
        return requestId instanceof UUID uuid ? uuid : null;
    }
}
