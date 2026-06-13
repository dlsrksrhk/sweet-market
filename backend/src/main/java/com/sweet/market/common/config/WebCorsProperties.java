package com.sweet.market.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "web.cors")
public record WebCorsProperties(
        List<String> allowedOrigins
) {

    public List<String> allowedOrigins() {
        return allowedOrigins == null ? List.of() : allowedOrigins;
    }
}
