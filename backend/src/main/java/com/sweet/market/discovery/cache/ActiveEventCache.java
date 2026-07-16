package com.sweet.market.discovery.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sweet.market.discovery.api.ActiveEventResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class ActiveEventCache {

    private static final String CACHE_KEY = "active-events";

    private final boolean enabled;
    private final Cache<String, List<ActiveEventResponse>> cache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .recordStats()
            .build();

    @Autowired
    public ActiveEventCache(@Value("${discovery.active-event-cache.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public ActiveEventCache() {
        this(true);
    }

    public List<ActiveEventResponse> get(Supplier<List<ActiveEventResponse>> loader) {
        if (!enabled) {
            return List.copyOf(loader.get());
        }
        return cache.get(CACHE_KEY, ignored -> List.copyOf(loader.get()));
    }

    public void invalidate() {
        cache.invalidateAll();
    }

    public void bindMetricsTo(MeterRegistry meterRegistry) {
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "discovery.active-events");
    }
}
