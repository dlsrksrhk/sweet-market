package com.sweet.market.discovery.metrics;

import com.sweet.market.discovery.cache.ActiveEventCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class DiscoveryMetrics {

    private final MeterRegistry meterRegistry;

    public DiscoveryMetrics(MeterRegistry meterRegistry, ActiveEventCache activeEventCache) {
        this.meterRegistry = meterRegistry;
        activeEventCache.bindMetricsTo(meterRegistry);
    }

    public <T> T catalog(Supplier<T> supplier) {
        return record("catalog", supplier);
    }

    public <T> T events(Supplier<T> supplier) {
        return record("events", supplier);
    }

    public <T> T popularity(Supplier<T> supplier) {
        return record("popularity", supplier);
    }

    public <T> T detail(Supplier<T> supplier) {
        return record("detail", supplier);
    }

    private <T> T record(String endpoint, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return supplier.get();
        } finally {
            sample.stop(Timer.builder("discovery.read.duration")
                    .description("Discovery read endpoint duration")
                    .tag("endpoint", endpoint)
                    .register(meterRegistry));
        }
    }
}
