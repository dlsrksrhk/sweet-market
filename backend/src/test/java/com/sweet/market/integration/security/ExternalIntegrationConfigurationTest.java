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

    @Test
    void 허용_clock_skew는_정확히_300초여야_한다() {
        assertBindingFails("market.external-integrations.allowed-clock-skew", "PT4M59S");
        assertBindingFails("market.external-integrations.allowed-clock-skew", "PT5M1S");
    }

    @Test
    void replay_보존기간은_정확히_10분이어야_한다() {
        assertBindingFails("market.external-integrations.replay-retention", "PT9M59S");
        assertBindingFails("market.external-integrations.replay-retention", "PT10M1S");
    }

    @Test
    void body_제한은_정확히_1048576이고_overflow할수_없다() {
        assertBindingFails("market.external-integrations.max-body-bytes", "1048575");
        assertBindingFails("market.external-integrations.max-body-bytes", "1048577");
        assertBindingFails("market.external-integrations.max-body-bytes", Integer.toString(Integer.MAX_VALUE));
    }

    @Test
    void cleanup_batch는_1이상_1000이하여야_한다() {
        assertBindingFails("market.external-integrations.cleanup-batch-size", "0");
        assertBindingFails("market.external-integrations.cleanup-batch-size", "1001");
    }

    private void assertBindingFails(String property, String value) {
        validContextRunner()
                .withPropertyValues(property + "=" + value)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("external-integrations");
                });
    }

    private ApplicationContextRunner validContextRunner() {
        return contextRunner.withPropertyValues(
                "market.external-integrations.enabled=true",
                "market.external-integrations.allowed-clock-skew=PT5M",
                "market.external-integrations.max-body-bytes=1048576",
                "market.external-integrations.replay-retention=PT10M",
                "market.external-integrations.cleanup-batch-size=1000",
                "market.external-integrations.inbound.payment-gateway.api-key=payment-in-api",
                "market.external-integrations.inbound.payment-gateway.current-key-id=payment-in-key-1",
                "market.external-integrations.inbound.payment-gateway.current-secret=payment-in-secret",
                "market.external-integrations.inbound.payment-gateway.next-key-id=payment-in-key-2",
                "market.external-integrations.inbound.payment-gateway.next-secret=payment-in-next-secret",
                "market.external-integrations.inbound.delivery-provider.api-key=delivery-in-api",
                "market.external-integrations.inbound.delivery-provider.current-key-id=delivery-in-key-1",
                "market.external-integrations.inbound.delivery-provider.current-secret=delivery-in-secret",
                "market.external-integrations.inbound.delivery-provider.next-key-id=delivery-in-key-2",
                "market.external-integrations.inbound.delivery-provider.next-secret=delivery-in-next-secret",
                "market.external-integrations.outbound.payment-gateway.api-key=payment-out-api",
                "market.external-integrations.outbound.payment-gateway.current-key-id=payment-out-key",
                "market.external-integrations.outbound.payment-gateway.current-secret=payment-out-secret",
                "market.external-integrations.outbound.delivery-provider.api-key=delivery-out-api",
                "market.external-integrations.outbound.delivery-provider.current-key-id=delivery-out-key",
                "market.external-integrations.outbound.delivery-provider.current-secret=delivery-out-secret"
        );
    }
}
