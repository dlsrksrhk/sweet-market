package com.sweet.market.integration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "market.external-integrations.enabled", havingValue = "true")
@EnableConfigurationProperties(ExternalIntegrationProperties.class)
public class ExternalIntegrationSecurityConfiguration {

    @Bean("externalIntegrationClock")
    Clock externalIntegrationClock() {
        return Clock.systemUTC();
    }

    @Bean
    HmacCanonicalizer externalIntegrationHmacCanonicalizer() {
        return new HmacCanonicalizer();
    }

    @Bean
    ExternalRequestSigner externalRequestSigner(
            ExternalIntegrationProperties properties,
            HmacCanonicalizer canonicalizer
    ) {
        return new HmacExternalRequestSigner(properties, canonicalizer);
    }

    @Bean
    ReplayGuard externalIntegrationReplayGuard(JdbcTemplate jdbcTemplate) {
        return new JdbcReplayGuard(jdbcTemplate);
    }

    @Bean
    SignedWebhookFilter signedWebhookFilter(
            ExternalIntegrationProperties properties,
            HmacCanonicalizer canonicalizer,
            ReplayGuard replayGuard,
            @Qualifier("externalIntegrationClock") Clock externalIntegrationClock,
            ObjectMapper objectMapper
    ) {
        return new SignedWebhookFilter(
                properties, canonicalizer, replayGuard, externalIntegrationClock, objectMapper);
    }

    @Bean
    FilterRegistrationBean<SignedWebhookFilter> signedWebhookFilterRegistration(
            SignedWebhookFilter filter
    ) {
        FilterRegistrationBean<SignedWebhookFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    ReplayCleanupScheduler externalIntegrationReplayCleanupScheduler(
            ReplayGuard replayGuard,
            @Qualifier("externalIntegrationClock") Clock externalIntegrationClock,
            ExternalIntegrationProperties properties
    ) {
        return new ReplayCleanupScheduler(
                replayGuard, externalIntegrationClock, properties.cleanupBatchSize());
    }
}
