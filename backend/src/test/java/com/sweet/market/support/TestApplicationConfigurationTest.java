package com.sweet.market.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class TestApplicationConfigurationTest {

    @Test
    void 테스트_전용_application_yaml을_클래스패스에서_우선_사용한다() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("test-application", new ClassPathResource("application.yaml"));

        assertThat(propertySources).hasSize(1);
        PropertySource<?> properties = propertySources.getFirst();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(properties.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo(true);
        assertThat(properties.getProperty("spring.flyway.baseline-version")).isEqualTo(0);
        assertThat(properties.getProperty("product.images.upload-root"))
                .isEqualTo("build/test-product-images");
        assertThat(properties.getProperty("jwt.secret"))
                .isEqualTo("sweet-market-test-secret-key-32bytes-minimum");
    }
}
