package com.sweet.market.discovery.experiment;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("local-experiment")
public class M30ExperimentInfoContributor implements InfoContributor {

    private final Environment environment;
    private final Clock clock;
    private final boolean cacheEnabled;

    public M30ExperimentInfoContributor(
            Environment environment,
            @Qualifier("discoveryClock") Clock clock,
            @Value("${discovery.active-event-cache.enabled:true}") boolean cacheEnabled
    ) {
        this.environment = environment;
        this.clock = clock;
        this.cacheEnabled = cacheEnabled;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("activeProfiles", List.of(environment.getActiveProfiles()));
        detail.put("fixedNow", clock.instant().toString());
        detail.put("cacheMode", cacheEnabled ? "ON" : "OFF");
        detail.put("serverProcessId", ProcessHandle.current().pid());
        builder.withDetail("m30Experiment", detail);
    }
}
