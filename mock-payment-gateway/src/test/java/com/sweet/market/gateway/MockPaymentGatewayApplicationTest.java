package com.sweet.market.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "gateway.integration-security.allowed-clock-skew=PT5M",
        "gateway.integration-security.max-body-bytes=1048576",
        "gateway.integration-security.replay-retention=PT10M",
        "gateway.integration-security.cleanup-batch-size=1000",
        "gateway.integration-security.clients[0].client-id=sweet-market",
        "gateway.integration-security.clients[0].api-key=context-test-api-key",
        "gateway.integration-security.clients[0].keys[0].key-id=context-current-key",
        "gateway.integration-security.clients[0].keys[0].secret=context-current-secret-32bytes-minimum",
        "gateway.integration-security.clients[0].keys[1].key-id=context-next-key",
        "gateway.integration-security.clients[0].keys[1].secret=context-next-secret-32bytes-minimum"
})
class MockPaymentGatewayApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void 결제_게이트웨이_애플리케이션이_기동한다() {
        assertThat(applicationContext).isNotNull();
    }
}
