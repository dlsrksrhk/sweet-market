package com.sweet.market.discovery.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sweet.market.discovery.api.ActiveEventResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class ActiveEventCache {

    private static final String CACHE_KEY = "active-events";

    private final Cache<String, List<ActiveEventResponse>> cache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .recordStats()
            .build();

    public List<ActiveEventResponse> get(Supplier<List<ActiveEventResponse>> loader) {
        return cache.get(CACHE_KEY, ignored -> List.copyOf(loader.get()));
    }

    public void invalidate() {
        cache.invalidateAll();
    }
}
