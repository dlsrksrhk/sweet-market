package com.sweet.market.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties(WebCorsProperties.class)
public class WebCorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(WebCorsProperties properties) {
        CorsConfiguration productViewConfiguration = corsConfiguration(properties, true);
        CorsConfiguration configuration = corsConfiguration(properties, false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/products/*/views", productViewConfiguration);
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private CorsConfiguration corsConfiguration(WebCorsProperties properties, boolean allowCredentials) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(allowCredentials);
        return configuration;
    }
}
