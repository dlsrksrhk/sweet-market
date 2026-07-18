package com.sweet.market.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class GatewaySecurityConfig {

    @Bean
    Clock gatewayClock() {
        return Clock.systemUTC();
    }

    @Bean
    HmacCanonicalizer hmacCanonicalizer() {
        return new HmacCanonicalizer();
    }

    @Bean
    SignedRequestFilter signedRequestFilter(
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
    SecurityFilterChain gatewaySecurityFilterChain(
            HttpSecurity http,
            SignedRequestFilter signedRequestFilter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/**").permitAll()
                        .anyRequest().denyAll())
                .addFilterBefore(signedRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
