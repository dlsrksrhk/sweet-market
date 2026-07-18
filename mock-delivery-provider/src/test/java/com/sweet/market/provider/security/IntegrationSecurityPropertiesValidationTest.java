package com.sweet.market.provider.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationSecurityPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withInitializer(context -> context.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
            .withUserConfiguration(SecurityPropertiesConfiguration.class);

    @Test
    void 누락된_운영_자격증명은_보안_설정_바인딩을_실패시킨다() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            Throwable startupFailure = context.getStartupFailure();
            assertThat(startupFailure).hasRootCauseInstanceOf(BindValidationException.class);

            String failureMessages = Stream.iterate(
                            startupFailure,
                            Objects::nonNull,
                            Throwable::getCause)
                    .map(Throwable::getMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
            assertThat(failureMessages)
                    .contains("clients[0].apiKey")
                    .contains("clients[0].keys[0].keyId")
                    .contains("clients[0].keys[0].secret")
                    .contains("clients[0].keys[1].keyId")
                    .contains("clients[0].keys[1].secret");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IntegrationSecurityProperties.class)
    static class SecurityPropertiesConfiguration {}
}
