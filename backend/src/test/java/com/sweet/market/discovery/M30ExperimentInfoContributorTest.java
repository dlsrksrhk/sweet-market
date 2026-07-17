package com.sweet.market.discovery;

import com.sweet.market.discovery.experiment.M30ExperimentInfoContributor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class M30ExperimentInfoContributorTest {

    @Test
    void 실행중인_서버의_profile_clock_cache_mode_pid를_제공한다() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "performance-fixture", "local-experiment", "cache-off");
        M30ExperimentInfoContributor contributor = new M30ExperimentInfoContributor(
                environment,
                Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC),
                false
        );
        Info.Builder builder = new Info.Builder();

        contributor.contribute(builder);

        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) builder.build().getDetails().get("m30Experiment");
        assertThat(detail.get("activeProfiles")).isEqualTo(List.of(
                "local", "performance-fixture", "local-experiment", "cache-off"
        ));
        assertThat(detail.get("fixedNow")).isEqualTo("2026-07-17T00:00:00Z");
        assertThat(detail.get("cacheMode")).isEqualTo("OFF");
        assertThat((Long) detail.get("serverProcessId")).isPositive();
    }
}
