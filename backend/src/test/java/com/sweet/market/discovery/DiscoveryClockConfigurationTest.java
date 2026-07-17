package com.sweet.market.discovery;

import com.sweet.market.discovery.config.DiscoveryClockConfiguration;
import com.sweet.market.discovery.experiment.M30PerformanceFixtureInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryClockConfigurationTest {

    @Test
    void performance_fixture_profile은_fixture와_같은_고정_clock을_제공한다() {
        new ApplicationContextRunner()
                .withUserConfiguration(DiscoveryClockConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=performance-fixture",
                        "market.performance-fixture.instant=2026-07-17T00:00:00Z"
                )
                .run(context -> assertThat(context.getBean("discoveryClock", Clock.class).instant())
                        .isEqualTo(M30PerformanceFixtureInitializer.FIXTURE_INSTANT));
    }

    @Test
    void 일반_profile은_시스템_UTC_clock을_제공한다() {
        Instant before = Instant.now().minusSeconds(1);

        new ApplicationContextRunner()
                .withUserConfiguration(DiscoveryClockConfiguration.class)
                .run(context -> assertThat(context.getBean("discoveryClock", Clock.class).instant())
                        .isBetween(before, Instant.now().plusSeconds(1)));
    }

    @Test
    void initializer를_비활성화해도_performance_fixture_profile을_사용할수있다() {
        new ApplicationContextRunner()
                .withUserConfiguration(M30PerformanceFixtureInitializer.class)
                .withPropertyValues(
                        "spring.profiles.active=performance-fixture",
                        "market.performance-fixture.initialize=false"
                )
                .run(context -> assertThat(context)
                        .doesNotHaveBean(M30PerformanceFixtureInitializer.class));
    }
}
