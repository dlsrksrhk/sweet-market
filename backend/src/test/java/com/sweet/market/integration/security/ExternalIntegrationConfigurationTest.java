package com.sweet.market.integration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExternalIntegrationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withUserConfiguration(ExternalIntegrationSecurityConfiguration.class);

    @Test
    void feature가_꺼져있으면_credential과_bean을_요구하지_않는다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ExternalRequestSigner.class);
            assertThat(context).doesNotHaveBean(SignedWebhookFilter.class);
        });
    }

    @Test
    void feature가_켜지고_credential이_비어있으면_binding에_실패한다() {
        contextRunner
                .withPropertyValues("market.external-integrations.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("external-integrations");
                });
    }
}
