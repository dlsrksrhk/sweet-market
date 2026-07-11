# Test Application Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every Gradle test run use a repository-owned Spring Boot configuration that needs no profile or external JWT secret and actually disables scheduling infrastructure.

**Architecture:** Keep test runtime values in `backend/src/test/resources/application.yaml`, ahead of the main YAML on the test classpath. Database connection values remain absent so existing `@DynamicPropertySource` Testcontainers overrides stay authoritative. Move scheduling activation out of `MarketApplication` and into the existing scheduling configuration, guarded by the application-owned `market.scheduling.enabled` property with an enabled-by-default fallback.

**Tech Stack:** Java 21, Spring Boot 3.5, JUnit 5, AssertJ, Gradle, YAML, Testcontainers PostgreSQL

## Global Constraints

- `./gradlew test` must require no Spring profile, external JWT secret, or extra JVM option.
- Database URL, username, and password must continue to come from Testcontainers through `@DynamicPropertySource`.
- `market.scheduling.enabled=false` must prevent creation of Spring scheduling infrastructure even under the `local` profile.
- Omitting `market.scheduling.enabled` must preserve existing non-test behavior by enabling scheduling.
- JUnit `@Test` method names must be Korean with underscores.
- Do not modify `backend/src/main/resources/application.yaml`.
- Test image files must stay under `backend/build/test-product-images`.

---

### Task 1: Add the automatically loaded test application configuration and effective scheduling switch

**Files:**
- Create: `backend/src/test/java/com/sweet/market/support/TestApplicationConfigurationTest.java`
- Create: `backend/src/test/resources/application.yaml`
- Create: `backend/src/test/java/com/sweet/market/order/scheduler/OrderAutoConfirmSchedulingConfigTest.java`
- Modify: `backend/src/main/java/com/sweet/market/MarketApplication.java`
- Modify: `backend/src/main/java/com/sweet/market/order/scheduler/OrderAutoConfirmSchedulingConfig.java`
- Modify: `backend/src/test/java/com/sweet/market/store/migration/StoreFreshDatabaseStartupTest.java`
- Modify: `backend/src/test/java/com/sweet/market/store/migration/StoreSpringBootFlywayTest.java`
- Modify: `docs/superpowers/specs/2026-07-11-test-application-configuration-design.md`
- Modify: `docs/superpowers/plans/2026-07-11-test-application-configuration.md`

**Interfaces:**
- Consumes: Spring test runtime classpath precedence and existing `@DynamicPropertySource` database overrides.
- Produces: Test-only configuration with Flyway baseline version `0`, JWT secret `sweet-market-test-secret-key-32bytes-minimum`, upload root `build/test-product-images`, and `market.scheduling.enabled=false`.
- Produces: Conditional scheduling activation that is enabled when `market.scheduling.enabled=true` or missing and disabled when it is `false`.

- [ ] **Step 1: Write failing configuration assertions**

Extend `TestApplicationConfigurationTest` to assert the application-owned scheduling flag and the absence of fixed datasource credentials:

```java
assertThat(properties.getProperty("spring.datasource.url")).isNull();
assertThat(properties.getProperty("spring.datasource.username")).isNull();
assertThat(properties.getProperty("spring.datasource.password")).isNull();
assertThat(properties.getProperty("market.scheduling.enabled")).isEqualTo(false);
```

- [ ] **Step 2: Write failing context-level scheduling tests**

Create `OrderAutoConfirmSchedulingConfigTest` with an `ApplicationContextRunner` that loads only `OrderAutoConfirmSchedulingConfig`. Assert that `ScheduledAnnotationBeanPostProcessor` is absent for `market.scheduling.enabled=false` with the `local` profile and present once when the property is omitted.

```java
private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(OrderAutoConfirmSchedulingConfig.class);

@Test
void 스케줄링을_비활성화하면_local에서도_스케줄러_인프라를_생성하지_않는다() {
    contextRunner
            .withPropertyValues(
                    "spring.profiles.active=local",
                    "market.scheduling.enabled=false"
            )
            .run(context -> assertThat(context)
                    .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
}

@Test
void 스케줄링은_기본값으로_활성화된다() {
    contextRunner.run(context -> assertThat(context)
            .hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
}
```

- [ ] **Step 3: Run the focused tests and verify RED**

Run from `backend` with JDK 21:

```powershell
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat test --tests com.sweet.market.support.TestApplicationConfigurationTest --tests com.sweet.market.order.scheduler.OrderAutoConfirmSchedulingConfigTest --rerun-tasks
```

Expected: three assertion failures: the YAML lacks `market.scheduling.enabled`, disabled local scheduling still creates infrastructure, and default non-profile scheduling does not create infrastructure through the profile-restricted configuration.

- [ ] **Step 4: Add the test-only YAML values**

Keep the existing test-only Flyway, JPA, Batch, JWT, image, multipart, and CORS values. Do not add any `spring.datasource` values. Replace the ineffective Spring scheduling property with:

```yaml
market:
  scheduling:
    enabled: false
```

- [ ] **Step 5: Route scheduling through the conditional configuration**

Remove `@EnableScheduling` and its import from `MarketApplication`. In `OrderAutoConfirmSchedulingConfig`, replace the `local`/`dev` profile condition with:

```java
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "market.scheduling",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderAutoConfirmSchedulingConfig {
}
```

This configuration becomes the single scheduling activation boundary. The default remains enabled, and no local/dev activation path can bypass `market.scheduling.enabled=false`.

Replace the stale `spring.task.scheduling.enabled=false` overrides in `StoreFreshDatabaseStartupTest` and `StoreSpringBootFlywayTest` with `market.scheduling.enabled=false` so their explicit context bootstrap settings use the same effective boundary.

- [ ] **Step 6: Run the focused tests and verify GREEN**

Run the same focused command from Step 3.

Expected: `BUILD SUCCESSFUL` with all three tests passing.

- [ ] **Step 7: Run the complete backend test suite without a profile or external JWT secret**

```powershell
.\gradlew.bat test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` with zero failures and errors.

- [ ] **Step 8: Review and commit the complete approved scope**

From the repository root:

```powershell
git diff --check
git add backend/src/main/java/com/sweet/market/MarketApplication.java backend/src/main/java/com/sweet/market/order/scheduler/OrderAutoConfirmSchedulingConfig.java backend/src/test/java/com/sweet/market/order/scheduler/OrderAutoConfirmSchedulingConfigTest.java backend/src/test/java/com/sweet/market/store/migration/StoreFreshDatabaseStartupTest.java backend/src/test/java/com/sweet/market/store/migration/StoreSpringBootFlywayTest.java backend/src/test/java/com/sweet/market/support/TestApplicationConfigurationTest.java backend/src/test/resources/application.yaml docs/superpowers/specs/2026-07-11-test-application-configuration-design.md docs/superpowers/plans/2026-07-11-test-application-configuration.md
git commit -m "fix: disable scheduling in tests"
```

Expected: `git diff --check` exits with code 0, and the commit contains only the nine authorized files listed above.
