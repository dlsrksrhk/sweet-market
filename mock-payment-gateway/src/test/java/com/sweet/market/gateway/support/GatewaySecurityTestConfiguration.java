package com.sweet.market.gateway.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

@TestConfiguration(proxyBeanMethods = false)
public class GatewaySecurityTestConfiguration {

    public static final Instant VECTOR_INSTANT = Instant.ofEpochSecond(1784386800L);

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(VECTOR_INSTANT);
    }

}
