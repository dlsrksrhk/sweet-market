package com.sweet.market.product.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sweet.market.product.storage.ProductImageStorageProperties;

@Configuration
@EnableConfigurationProperties(ProductImageStorageProperties.class)
public class ProductImageWebConfig implements WebMvcConfigurer {

    private final ProductImageStorageProperties properties;

    public ProductImageWebConfig(ProductImageStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/products/temp/**")
                .addResourceLocations(resourceLocation(properties.tempPath()));
        registry.addResourceHandler("/uploads/products/public/**")
                .addResourceLocations(resourceLocation(properties.publicPath()));
    }

    private String resourceLocation(java.nio.file.Path path) {
        String location = path.toUri().toString();
        if (location.endsWith("/")) {
            return location;
        }
        return location + "/";
    }
}
