package com.sweet.market.provider;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.flyway.enabled=false")
class MockDeliveryProviderApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void 배송_제공자_애플리케이션이_기동한다() {
        assertThat(applicationContext).isNotNull();
    }
}
