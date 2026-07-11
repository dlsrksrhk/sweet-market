# Test Application Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every Gradle test run use a repository-owned, test-only Spring Boot configuration without requiring a profile or depending on the local main `application.yaml`.

**Architecture:** Add `backend/src/test/resources/application.yaml`, which takes precedence on the Gradle test runtime classpath. Keep database connection values out of the file so the existing `@DynamicPropertySource` methods remain authoritative, while preserving per-test Flyway and Hibernate overrides.

**Tech Stack:** Java 21, Spring Boot 3.5, JUnit 5, AssertJ, Gradle, YAML, Testcontainers PostgreSQL

## Global Constraints

- `./gradlew test` must require no Spring profile or extra JVM option.
- Database URL, username, and password must continue to come from Testcontainers through `@DynamicPropertySource`.
- JUnit `@Test` method names must be Korean with underscores.
- Do not modify `backend/src/main/resources/application.yaml`.
- Test image files must stay under `backend/build/test-product-images`.

---

### Task 1: Add the automatically loaded test application configuration

**Files:**
- Create: `backend/src/test/java/com/sweet/market/support/TestApplicationConfigurationTest.java`
- Create: `backend/src/test/resources/application.yaml`

**Interfaces:**
- Consumes: Spring test runtime classpath precedence and the existing `@DynamicPropertySource` database overrides.
- Produces: A classpath `application.yaml` whose `product.images.upload-root` is `build/test-product-images`, whose Flyway baseline version is `0`, and whose JWT secret is valid for tests.

- [ ] **Step 1: Write the failing classpath configuration test**

Create `backend/src/test/java/com/sweet/market/support/TestApplicationConfigurationTest.java`:

```java
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
```

- [ ] **Step 2: Run the focused test and confirm it fails against the main configuration**

Run from `backend`:

```powershell
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat test --tests com.sweet.market.support.TestApplicationConfigurationTest --rerun-tasks
```

Expected: FAIL because the current test classpath resolves the main configuration, where `product.images.upload-root` is not `build/test-product-images`.

- [ ] **Step 3: Add the test-only Spring Boot configuration**

Create `backend/src/test/resources/application.yaml`:

```yaml
spring:
  application:
    name: market-test
  sql:
    init:
      mode: always
      schema-locations: classpath:schema-batch-postgresql.sql
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB
  batch:
    job:
      enabled: false
    jdbc:
      initialize-schema: never
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: false
        highlight_sql: false
        default_batch_fetch_size: 100
    open-in-view: false
  task:
    scheduling:
      enabled: false

jwt:
  secret: sweet-market-test-secret-key-32bytes-minimum
  access-token-validity-seconds: 3600

product:
  images:
    upload-root: build/test-product-images
    temp-dir: temp
    public-dir: public
    temp-expiration: 60m
    cleanup-cron: "0 */10 * * * *"
    max-file-size: 5MB

web:
  cors:
    allowed-origins:
      - http://localhost:5173
```

Do not add datasource connection values. The three Spring context test bootstraps already provide their Testcontainers connection dynamically.

- [ ] **Step 4: Run the focused test and confirm it passes**

Run from `backend`:

```powershell
.\gradlew.bat test --tests com.sweet.market.support.TestApplicationConfigurationTest --rerun-tasks
```

Expected: BUILD SUCCESSFUL with one passing test.

- [ ] **Step 5: Run the complete backend test suite without a profile**

Run from `backend`:

```powershell
.\gradlew.bat test --rerun-tasks
```

Expected: BUILD SUCCESSFUL with all existing tests plus the new configuration test passing, and zero failures or errors.

- [ ] **Step 6: Check the patch and commit only the test configuration change**

Run from the repository root:

```powershell
git diff --check
git add backend/src/test/resources/application.yaml backend/src/test/java/com/sweet/market/support/TestApplicationConfigurationTest.java
git commit -m "test: isolate application configuration"
```

Expected: `git diff --check` exits with code 0 and the commit contains exactly the two new files.
