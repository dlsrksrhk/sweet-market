package com.sweet.market.catalog.query;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogCursorProductionConfigurationTest {

    private static final String LOCAL_SECRET = "sweet-market-local-catalog-cursor-secret-key-32bytes-minimum";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CatalogCursorPropertiesConfiguration.class)
            .withInitializer(context -> {
                context.getEnvironment().getPropertySources()
                        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                context.getEnvironment().getPropertySources().addLast(productionApplicationProperties());
            });

    @Test
    void 프로덕션_기본_시크릿을_바인딩한다() {
        contextRunner.run(context -> {
            CatalogCursorProperties properties = context.getBean(CatalogCursorProperties.class);

            assertThat(properties.secret()).isEqualTo(LOCAL_SECRET);
            assertThat(properties.maxAge()).isEqualTo(Duration.ofMinutes(30));
        });
    }

    @Test
    void 카탈로그_커서_시크릿_구성은_기본값보다_우선한다() {
        contextRunner
                .withPropertyValues("catalog.cursor.secret=overridden-catalog-cursor-secret-key-32bytes")
                .run(context -> assertThat(context.getBean(CatalogCursorProperties.class).secret())
                        .isEqualTo("overridden-catalog-cursor-secret-key-32bytes"));
    }

    private static PropertySource<?> productionApplicationProperties() {
        try {
            List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                    .load("production-application", new FileSystemResource("src/main/resources/application.yaml"));
            return propertySources.getFirst();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CatalogCursorProperties.class)
    static class CatalogCursorPropertiesConfiguration {
    }
}
