package com.sweet.market.provider;

import com.sweet.market.provider.security.IntegrationSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "provider.integration-security.allowed-clock-skew=PT5M",
        "provider.integration-security.max-body-bytes=1048576",
        "provider.integration-security.replay-retention=PT10M",
        "provider.integration-security.cleanup-batch-size=1000",
        "provider.integration-security.clients[0].client-id=sweet-market-test",
        "provider.integration-security.clients[0].api-key=provider-test-api-key",
        "provider.integration-security.clients[0].keys[0].key-id=provider-test-current-key",
        "provider.integration-security.clients[0].keys[0].secret=provider-test-current-secret"
})
class MockDeliveryProviderApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private IntegrationSecurityProperties securityProperties;

    @Test
    void 배송_제공자_애플리케이션이_기동한다() {
        assertThat(applicationContext).isNotNull();
        assertThat(securityProperties.clients().getFirst().apiKey())
                .isEqualTo("provider-test-api-key");
    }
}
