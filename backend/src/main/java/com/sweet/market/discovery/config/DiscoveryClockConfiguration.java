package com.sweet.market.discovery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@Configuration
public class DiscoveryClockConfiguration {

    @Bean("discoveryClock")
    @Profile("!performance-fixture")
    Clock systemDiscoveryClock() {
        return Clock.systemUTC();
    }

    @Bean("discoveryClock")
    @Profile("performance-fixture")
    Clock performanceFixtureDiscoveryClock(
            @Value("${market.performance-fixture.instant}") String fixtureInstant
    ) {
        return Clock.fixed(Instant.parse(fixtureInstant), ZoneOffset.UTC);
    }
}
