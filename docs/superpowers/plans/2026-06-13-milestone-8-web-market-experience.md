# Milestone 8 Web Market Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a React web app that uses the existing Sweet Market backend like a real market service, with local demo data, member workflows, and a small admin settlement batch console.

**Architecture:** Keep `backend` as the REST API and add a new `web` Vite app at the repository root. Backend changes are limited to local demo support, CORS, current-member role exposure, seller product reads, and Spring Batch execution history reads. The web app uses route guards, a small fetch client, TanStack Query server state, and simple CSS components.

**Tech Stack:** Java 21, Spring Boot, Spring Security, Spring Batch metadata, PostgreSQL, JUnit 5, Testcontainers, React, Vite, TypeScript, React Router, TanStack Query, React Hook Form.

---

## Scope Notes

- Do not start production deployment work.
- Do not add refresh tokens, OAuth, file upload storage, scheduler, distributed lock, or batch stop/restart APIs.
- Keep JUnit `@Test` method names Korean_with_underscores.
- Backend commands run from `backend` with JDK 21 and `JWT_SECRET`.
- The existing local change in `backend/src/main/resources/application.yaml` may be user-local. Inspect it before staging and stage only intentional changes.

Recommended backend test environment:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
```

---

## File Structure

Backend files to create:

```text
backend/src/main/java/com/sweet/market/common/config/WebCorsProperties.java
backend/src/main/java/com/sweet/market/common/config/WebCorsConfig.java
backend/src/main/java/com/sweet/market/demo/DemoDataInitializer.java
backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchExecutionDetailResponse.java
backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchExecutionSummaryResponse.java
backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchHistoryService.java
backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java
backend/src/test/java/com/sweet/market/product/ProductSellerApiTest.java
backend/src/test/java/com/sweet/market/settlement/batch/AdminSettlementBatchHistoryApiTest.java
```

Backend files to modify:

```text
backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java
backend/src/main/java/com/sweet/market/member/api/MemberMeResponse.java
backend/src/main/java/com/sweet/market/product/api/ProductController.java
backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java
backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java
backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchController.java
backend/src/main/resources/application.yaml
```

Web files to create:

```text
web/package.json
web/index.html
web/tsconfig.json
web/tsconfig.node.json
web/vite.config.ts
web/src/main.tsx
web/src/app/App.tsx
web/src/app/router.tsx
web/src/app/providers.tsx
web/src/shared/api/http.ts
web/src/shared/layout/Shell.tsx
web/src/shared/components/EmptyState.tsx
web/src/shared/components/ErrorState.tsx
web/src/shared/components/StatusBadge.tsx
web/src/shared/styles.css
web/src/features/auth/authApi.ts
web/src/features/auth/AuthProvider.tsx
web/src/features/auth/RequireAuth.tsx
web/src/features/auth/RequireAdmin.tsx
web/src/features/products/productApi.ts
web/src/features/orders/orderApi.ts
web/src/features/payments/paymentApi.ts
web/src/features/deliveries/deliveryApi.ts
web/src/features/settlements/settlementApi.ts
web/src/features/admin/adminBatchApi.ts
web/src/pages/HomePage.tsx
web/src/pages/LoginPage.tsx
web/src/pages/SignupPage.tsx
web/src/pages/ProductDetailPage.tsx
web/src/pages/ProductFormPage.tsx
web/src/pages/MyOrdersPage.tsx
web/src/pages/MySalesPage.tsx
web/src/pages/MySettlementsPage.tsx
web/src/pages/AdminBatchPage.tsx
```

Documentation files to modify:

```text
AGENTS.md
```

---

## Task 1: Current Member Role And Local CORS

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/member/api/MemberMeResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java`
- Create: `backend/src/main/java/com/sweet/market/common/config/WebCorsProperties.java`
- Create: `backend/src/main/java/com/sweet/market/common/config/WebCorsConfig.java`
- Modify: `backend/src/main/resources/application.yaml`
- Test: existing auth/member tests

- [ ] **Step 1: Confirm current member response lacks role**

Run:

```powershell
rg -n "record MemberMeResponse|role" backend\src\main\java\com\sweet\market\member backend\src\test\java\com\sweet\market
```

Expected: `MemberMeResponse` has no `role` field.

- [ ] **Step 2: Add role to current member response**

Modify `backend/src/main/java/com/sweet/market/member/api/MemberMeResponse.java`:

```java
package com.sweet.market.member.api;

import com.sweet.market.member.domain.Member;

public record MemberMeResponse(
        Long id,
        String email,
        String nickname,
        String role
) {

    public static MemberMeResponse from(Member member) {
        return new MemberMeResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole().name()
        );
    }
}
```

- [ ] **Step 3: Add local CORS properties**

Create `backend/src/main/java/com/sweet/market/common/config/WebCorsProperties.java`:

```java
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
```

- [ ] **Step 4: Add CORS configuration source**

Create `backend/src/main/java/com/sweet/market/common/config/WebCorsConfig.java`:

```java
package com.sweet.market.common.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(WebCorsProperties.class)
public class WebCorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(WebCorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

- [ ] **Step 5: Enable CORS in security**

Modify `backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java` by adding `.cors(cors -> { })` after `.csrf(csrf -> csrf.disable())`:

```java
return http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> { })
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

- [ ] **Step 6: Add local web origin property**

Modify `backend/src/main/resources/application.yaml` by adding this top-level property:

```yaml
web:
  cors:
    allowed-origins:
      - http://localhost:5173
```

Before staging, run:

```powershell
git diff -- backend/src/main/resources/application.yaml
```

Expected: diff contains only intended Milestone 8 CORS property unless the user explicitly approves staging local changes.

- [ ] **Step 7: Run focused backend tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.auth.* --tests com.sweet.market.MarketApplicationTests
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

Stage only intended files:

```powershell
git add backend/src/main/java/com/sweet/market/member/api/MemberMeResponse.java `
        backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java `
        backend/src/main/java/com/sweet/market/common/config/WebCorsProperties.java `
        backend/src/main/java/com/sweet/market/common/config/WebCorsConfig.java `
        backend/src/main/resources/application.yaml
git commit -m "feat: expose member role and local web cors"
```

---

## Task 2: Seller Product Read API

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductController.java`
- Modify: `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Test: `backend/src/test/java/com/sweet/market/product/ProductSellerApiTest.java`

- [ ] **Step 1: Write seller product API test**

Create `backend/src/test/java/com/sweet/market/product/ProductSellerApiTest.java`:

```java
package com.sweet.market.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.product.api.ProductCreateRequest;
import com.sweet.market.support.IntegrationTestSupport;

class ProductSellerApiTest extends IntegrationTestSupport {

    @Test
    void 판매자는_내_판매_상품을_조회할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller-products@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer-products@example.com", "password123", "buyer");

        createProduct(sellerToken, "Seller Product", 10_000L);
        createProduct(buyerToken, "Buyer Product", 20_000L);

        mockMvc.perform(get("/api/products/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Seller Product"));
    }

    private void createProduct(String token, String title, long price) throws Exception {
        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProductCreateRequest(title, title + " description", price))))
                .andExpect(status().isCreated());
    }

    private String signupAndLogin(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, password, nickname))))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.product.ProductSellerApiTest
```

Expected: FAIL with `404` for `/api/products/me`.

- [ ] **Step 3: Add repository method**

Modify `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`:

```java
Page<Product> findBySellerIdOrderByIdDesc(Long sellerId, Pageable pageable);
```

Keep existing methods unchanged.

- [ ] **Step 4: Add query service method**

Modify `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java`:

```java
@Transactional(readOnly = true)
public Page<ProductSummaryResponse> findMine(Long sellerId, Pageable pageable) {
    return productRepository.findBySellerIdOrderByIdDesc(sellerId, pageable)
            .map(ProductSummaryResponse::from);
}
```

- [ ] **Step 5: Add controller endpoint before `/{productId}`**

Modify `backend/src/main/java/com/sweet/market/product/api/ProductController.java`:

```java
@GetMapping("/me")
public ApiResponse<Page<ProductSummaryResponse>> listMine(
        Authentication authentication,
        @PageableDefault(size = 20) Pageable pageable
) {
    AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
    return ApiResponse.ok(productQueryService.findMine(member.id(), pageable));
}
```

Place this method above `@GetMapping("/{productId}")` so `/me` is not treated as a path variable.

- [ ] **Step 6: Run focused test**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.product.ProductSellerApiTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/product/api/ProductController.java `
        backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java `
        backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java `
        backend/src/test/java/com/sweet/market/product/ProductSellerApiTest.java
git commit -m "feat: add seller product listing api"
```

---

## Task 3: Admin Settlement Batch History API

**Files:**
- Create: `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchExecutionSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchExecutionDetailResponse.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchHistoryService.java`
- Modify: `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchController.java`
- Test: `backend/src/test/java/com/sweet/market/settlement/batch/AdminSettlementBatchHistoryApiTest.java`

- [ ] **Step 1: Write admin batch history API test**

Create `backend/src/test/java/com/sweet/market/settlement/batch/AdminSettlementBatchHistoryApiTest.java`:

```java
package com.sweet.market.settlement.batch;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.support.IntegrationTestSupport;

@TestPropertySource(properties = "spring.batch.job.enabled=false")
class AdminSettlementBatchHistoryApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 관리자는_정산_배치_실행_이력을_조회할_수_있다() throws Exception {
        String adminToken = createAdminAndLogin("history-admin@example.com");
        Long executionId = launchSettlementBatch(adminToken);

        mockMvc.perform(get("/api/admin/batches/settlements/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].executionId").value(executionId))
                .andExpect(jsonPath("$.data[0].jobName").value("settlementJob"))
                .andExpect(jsonPath("$.data[0].status").exists());
    }

    @Test
    void 관리자는_정산_배치_실행_상세를_조회할_수_있다() throws Exception {
        String adminToken = createAdminAndLogin("history-detail-admin@example.com");
        Long executionId = launchSettlementBatch(adminToken);

        mockMvc.perform(get("/api/admin/batches/settlements/executions/{executionId}", executionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionId").value(executionId))
                .andExpect(jsonPath("$.data.parameters.limit").value(100))
                .andExpect(jsonPath("$.data.step.readCount").isNumber())
                .andExpect(jsonPath("$.data.step.writeCount").isNumber())
                .andExpect(jsonPath("$.data.step.skipCount").isNumber());
    }

    @Test
    void 일반_회원은_정산_배치_이력을_조회할_수_없다() throws Exception {
        String memberToken = createMemberAndLogin("history-member@example.com");

        mockMvc.perform(get("/api/admin/batches/settlements/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private Long launchSettlementBatch(String adminToken) throws Exception {
        String response = mockMvc.perform(post("/api/admin/batches/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedBefore": "%s",
                                  "limit": 100,
                                  "chunkSize": 20
                                }
                                """.formatted(LocalDateTime.now().plusDays(1))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("jobExecutionId").asLong();
    }

    private String createAdminAndLogin(String email) throws Exception {
        memberRepository.save(Member.createAdmin(email, passwordEncoder.encode("password123"), "admin"));
        return login(email, "password123");
    }

    private String createMemberAndLogin(String email) throws Exception {
        memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), "member"));
        return login(email, "password123");
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.settlement.batch.AdminSettlementBatchHistoryApiTest
```

Expected: FAIL with `404` for history endpoints.

- [ ] **Step 3: Add summary response**

Create `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchExecutionSummaryResponse.java`:

```java
package com.sweet.market.settlement.batch;

import java.time.LocalDateTime;

public record AdminSettlementBatchExecutionSummaryResponse(
        Long executionId,
        String jobName,
        String status,
        String exitCode,
        LocalDateTime createTime,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
```

- [ ] **Step 4: Add detail response**

Create `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchExecutionDetailResponse.java`:

```java
package com.sweet.market.settlement.batch;

import java.time.LocalDateTime;
import java.util.List;

public record AdminSettlementBatchExecutionDetailResponse(
        Long executionId,
        String jobName,
        String status,
        String exitCode,
        LocalDateTime createTime,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Parameters parameters,
        Step step,
        List<String> failureMessages
) {

    public record Parameters(
            String confirmedBefore,
            Long limit,
            Long chunkSize
    ) {
    }

    public record Step(
            int readCount,
            int writeCount,
            int skipCount,
            int rollbackCount
    ) {
    }
}
```

- [ ] **Step 5: Add history service**

Create `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchHistoryService.java`:

```java
package com.sweet.market.settlement.batch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSettlementBatchHistoryService {

    private final JdbcTemplate jdbcTemplate;

    public AdminSettlementBatchHistoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<AdminSettlementBatchExecutionSummaryResponse> findRecent(int size) {
        int boundedSize = Math.max(1, Math.min(size, 100));
        return jdbcTemplate.query("""
                        select e.job_execution_id, i.job_name, e.status, e.exit_code,
                               e.create_time, e.start_time, e.end_time
                        from batch_job_execution e
                        join batch_job_instance i on e.job_instance_id = i.job_instance_id
                        where i.job_name = 'settlementJob'
                        order by e.job_execution_id desc
                        limit ?
                        """,
                (rs, rowNum) -> summary(rs),
                boundedSize);
    }

    @Transactional(readOnly = true)
    public AdminSettlementBatchExecutionDetailResponse findOne(Long executionId) {
        AdminSettlementBatchExecutionSummaryResponse summary = jdbcTemplate.queryForObject("""
                        select e.job_execution_id, i.job_name, e.status, e.exit_code,
                               e.create_time, e.start_time, e.end_time
                        from batch_job_execution e
                        join batch_job_instance i on e.job_instance_id = i.job_instance_id
                        where i.job_name = 'settlementJob'
                          and e.job_execution_id = ?
                        """,
                (rs, rowNum) -> summary(rs),
                executionId);

        AdminSettlementBatchExecutionDetailResponse.Parameters parameters = parameters(executionId);
        AdminSettlementBatchExecutionDetailResponse.Step step = step(executionId);
        List<String> failures = failureMessages(executionId);

        return new AdminSettlementBatchExecutionDetailResponse(
                summary.executionId(),
                summary.jobName(),
                summary.status(),
                summary.exitCode(),
                summary.createTime(),
                summary.startTime(),
                summary.endTime(),
                parameters,
                step,
                failures
        );
    }

    private AdminSettlementBatchExecutionSummaryResponse summary(ResultSet rs) throws SQLException {
        return new AdminSettlementBatchExecutionSummaryResponse(
                rs.getLong("job_execution_id"),
                rs.getString("job_name"),
                rs.getString("status"),
                rs.getString("exit_code"),
                toLocalDateTime(rs.getTimestamp("create_time")),
                toLocalDateTime(rs.getTimestamp("start_time")),
                toLocalDateTime(rs.getTimestamp("end_time"))
        );
    }

    private AdminSettlementBatchExecutionDetailResponse.Parameters parameters(Long executionId) {
        List<ParameterRow> rows = jdbcTemplate.query("""
                        select parameter_name, parameter_value
                        from batch_job_execution_params
                        where job_execution_id = ?
                        """,
                (rs, rowNum) -> new ParameterRow(rs.getString("parameter_name"), rs.getString("parameter_value")),
                executionId);

        return new AdminSettlementBatchExecutionDetailResponse.Parameters(
                find(rows, "confirmedBefore"),
                parseLong(find(rows, "limit")),
                parseLong(find(rows, "chunkSize"))
        );
    }

    private AdminSettlementBatchExecutionDetailResponse.Step step(Long executionId) {
        return jdbcTemplate.queryForObject("""
                        select coalesce(sum(read_count), 0) as read_count,
                               coalesce(sum(write_count), 0) as write_count,
                               coalesce(sum(skip_count), 0) as skip_count,
                               coalesce(sum(rollback_count), 0) as rollback_count
                        from batch_step_execution
                        where job_execution_id = ?
                        """,
                (rs, rowNum) -> new AdminSettlementBatchExecutionDetailResponse.Step(
                        rs.getInt("read_count"),
                        rs.getInt("write_count"),
                        rs.getInt("skip_count"),
                        rs.getInt("rollback_count")
                ),
                executionId);
    }

    private List<String> failureMessages(Long executionId) {
        return jdbcTemplate.query("""
                        select exit_message
                        from batch_step_execution
                        where job_execution_id = ?
                          and exit_message is not null
                          and exit_message <> ''
                        order by step_execution_id asc
                        """,
                (rs, rowNum) -> rs.getString("exit_message"),
                executionId);
    }

    private String find(List<ParameterRow> rows, String name) {
        return rows.stream()
                .filter(row -> row.name().equals(name))
                .map(ParameterRow::value)
                .findFirst()
                .orElse(null);
    }

    private Long parseLong(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record ParameterRow(String name, String value) {
    }
}
```

- [ ] **Step 6: Add controller endpoints**

Modify `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchController.java`:

```java
private final AdminSettlementBatchHistoryService historyService;

public AdminSettlementBatchController(
        JobLauncher jobLauncher,
        Job settlementJob,
        AdminSettlementBatchHistoryService historyService
) {
    this.jobLauncher = jobLauncher;
    this.settlementJob = settlementJob;
    this.historyService = historyService;
}

@GetMapping("/executions")
public ApiResponse<List<AdminSettlementBatchExecutionSummaryResponse>> executions(
        @RequestParam(defaultValue = "20") int size
) {
    return ApiResponse.ok(historyService.findRecent(size));
}

@GetMapping("/executions/{executionId}")
public ApiResponse<AdminSettlementBatchExecutionDetailResponse> execution(
        @PathVariable Long executionId
) {
    return ApiResponse.ok(historyService.findOne(executionId));
}
```

Add imports:

```java
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
```

- [ ] **Step 7: Run focused tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.settlement.batch.AdminSettlementBatchHistoryApiTest --tests com.sweet.market.settlement.batch.AdminSettlementBatchApiTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchExecutionSummaryResponse.java `
        backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchExecutionDetailResponse.java `
        backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchHistoryService.java `
        backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchController.java `
        backend/src/test/java/com/sweet/market/settlement/batch/AdminSettlementBatchHistoryApiTest.java
git commit -m "feat: add settlement batch history api"
```

---

## Task 4: Local Demo Seed Data

**Files:**
- Create: `backend/src/main/java/com/sweet/market/demo/DemoDataInitializer.java`
- Test: `backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java`
- Modify: `backend/src/main/resources/application.yaml`
- Use existing repositories:
  - `backend/src/main/java/com/sweet/market/member/repository/MemberRepository.java`
  - `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
  - `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`
  - `backend/src/main/java/com/sweet/market/payment/repository/PaymentRepository.java`
  - `backend/src/main/java/com/sweet/market/delivery/repository/DeliveryRepository.java`
  - `backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java`

- [ ] **Step 1: Write seed idempotency test**

Create `backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java`:

```java
package com.sweet.market.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.repository.PaymentRepository;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.settlement.repository.SettlementRepository;
import com.sweet.market.support.IntegrationTestSupport;

class DemoDataInitializerTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 데모_데이터는_반복_실행해도_중복_생성되지_않는다() {
        DemoDataInitializer initializer = new DemoDataInitializer(
                memberRepository,
                productRepository,
                orderRepository,
                paymentRepository,
                deliveryRepository,
                settlementRepository,
                passwordEncoder
        );

        initializer.run();
        long memberCount = memberRepository.count();
        long productCount = productRepository.count();
        long orderCount = orderRepository.count();
        long paymentCount = paymentRepository.count();
        long deliveryCount = deliveryRepository.count();
        long settlementCount = settlementRepository.count();

        initializer.run();

        assertThat(memberRepository.count()).isEqualTo(memberCount);
        assertThat(productRepository.count()).isEqualTo(productCount);
        assertThat(orderRepository.count()).isEqualTo(orderCount);
        assertThat(paymentRepository.count()).isEqualTo(paymentCount);
        assertThat(deliveryRepository.count()).isEqualTo(deliveryCount);
        assertThat(settlementRepository.count()).isEqualTo(settlementCount);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.demo.DemoDataInitializerTest
```

Expected: compile FAIL because `DemoDataInitializer` does not exist.

- [ ] **Step 3: Implement seed initializer**

Create `backend/src/main/java/com/sweet/market/demo/DemoDataInitializer.java`:

```java
package com.sweet.market.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.repository.PaymentRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;

@Component
@Profile({"local", "dev"})
public class DemoDataInitializer implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String PASSWORD = "password123";

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryRepository deliveryRepository;
    private final SettlementRepository settlementRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(
            MemberRepository memberRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            DeliveryRepository deliveryRepository,
            SettlementRepository settlementRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.deliveryRepository = deliveryRepository;
        this.settlementRepository = settlementRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        run();
    }

    @Transactional
    public void run() {
        if (memberRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        memberRepository.save(Member.createAdmin(ADMIN_EMAIL, encodedPassword, "관리자"));
        Member seller1 = memberRepository.save(Member.create("seller1@example.com", encodedPassword, "달콤상점"));
        Member seller2 = memberRepository.save(Member.create("seller2@example.com", encodedPassword, "과일가게"));
        Member buyer1 = memberRepository.save(Member.create("buyer1@example.com", encodedPassword, "민트구매자"));
        Member buyer2 = memberRepository.save(Member.create("buyer2@example.com", encodedPassword, "체리구매자"));

        productRepository.save(Product.create(seller1, "빈티지 머그컵", "상태 좋은 머그컵입니다.", 12_000L));
        productRepository.save(Product.create(seller1, "무선 키보드", "조용한 타건감의 키보드입니다.", 45_000L));

        Product createdProduct = productRepository.save(Product.create(seller2, "가죽 카드지갑", "주문 생성 상태를 볼 수 있는 상품입니다.", 18_000L));
        orderRepository.save(Order.create(buyer1, createdProduct));

        Product paidProduct = productRepository.save(Product.create(seller2, "캠핑 랜턴", "결제 완료 상태를 볼 수 있는 상품입니다.", 30_000L));
        Order paidOrder = orderRepository.save(Order.create(buyer1, paidProduct));
        paymentRepository.save(Payment.approve(paidOrder, "demo-payment-paid"));

        Product shippingProduct = productRepository.save(Product.create(seller1, "접이식 의자", "배송중 상태를 볼 수 있는 상품입니다.", 22_000L));
        Order shippingOrder = orderRepository.save(Order.create(buyer2, shippingProduct));
        paymentRepository.save(Payment.approve(shippingOrder, "demo-payment-shipping"));
        deliveryRepository.save(Delivery.start(shippingOrder, "DEMO-SHIPPING"));

        Product deliveredProduct = productRepository.save(Product.create(seller1, "미니 선풍기", "배송 완료 상태를 볼 수 있는 상품입니다.", 16_000L));
        Order deliveredOrder = orderRepository.save(Order.create(buyer2, deliveredProduct));
        paymentRepository.save(Payment.approve(deliveredOrder, "demo-payment-delivered"));
        Delivery delivered = deliveryRepository.save(Delivery.start(deliveredOrder, "DEMO-DELIVERED"));
        delivered.complete();

        Product unsettledProduct = productRepository.save(Product.create(seller2, "원목 트레이", "정산 배치 대상이 되는 상품입니다.", 25_000L));
        Order unsettledOrder = orderRepository.save(Order.create(buyer1, unsettledProduct));
        paymentRepository.save(Payment.approve(unsettledOrder, "demo-payment-unsettled"));
        Delivery unsettledDelivery = deliveryRepository.save(Delivery.start(unsettledOrder, "DEMO-UNSETTLED"));
        unsettledDelivery.complete();
        unsettledOrder.confirm();

        Product settledProduct = productRepository.save(Product.create(seller2, "독서등", "정산 완료 상태를 볼 수 있는 상품입니다.", 19_000L));
        Order settledOrder = orderRepository.save(Order.create(buyer2, settledProduct));
        paymentRepository.save(Payment.approve(settledOrder, "demo-payment-settled"));
        Delivery settledDelivery = deliveryRepository.save(Delivery.start(settledOrder, "DEMO-SETTLED"));
        settledDelivery.complete();
        settledOrder.confirm();
        settlementRepository.save(Settlement.create(settledOrder));
    }
}
```

- [ ] **Step 4: Add local profile activation note**

Modify `backend/src/main/resources/application.yaml` if the project uses a local profile block. If not, do not force a profile in the file. Run local seed manually with:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

Expected: app starts and creates demo accounts when connected to local PostgreSQL.

- [ ] **Step 5: Run focused test**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.demo.DemoDataInitializerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/demo/DemoDataInitializer.java `
        backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java
git commit -m "feat: add local demo seed data"
```

---

## Task 5: Web App Scaffold

**Files:**
- Create all basic files under `web`
- Modify: `.gitignore`
- Modify: `AGENTS.md`

- [ ] **Step 1: Create Vite package files**

Create `web/package.json`:

```json
{
  "name": "sweet-market-web",
  "private": true,
  "version": "0.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite --host localhost --port 5173",
    "build": "tsc -b && vite build",
    "preview": "vite preview --host localhost --port 4173"
  },
  "dependencies": {
    "@tanstack/react-query": "^5.80.0",
    "react": "^19.1.0",
    "react-dom": "^19.1.0",
    "react-hook-form": "^7.58.0",
    "react-router-dom": "^7.6.0"
  },
  "devDependencies": {
    "@types/react": "^19.1.0",
    "@types/react-dom": "^19.1.0",
    "@vitejs/plugin-react": "^4.5.0",
    "typescript": "^5.8.0",
    "vite": "^6.3.0"
  }
}
```

- [ ] **Step 2: Create TypeScript and Vite config**

Create `web/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "allowJs": false,
    "skipLibCheck": true,
    "esModuleInterop": true,
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "forceConsistentCasingInFileNames": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx"
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

Create `web/tsconfig.node.json`:

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "allowSyntheticDefaultImports": true
  },
  "include": ["vite.config.ts"]
}
```

Create `web/vite.config.ts`:

```ts
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  server: {
    host: 'localhost',
    port: 5173,
  },
});
```

- [ ] **Step 3: Create index and starter app**

Create `web/index.html`:

```html
<!doctype html>
<html lang="ko">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Sweet Market</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

Create `web/src/main.tsx`:

```tsx
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import './shared/styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
```

Create `web/src/app/App.tsx`:

```tsx
export function App() {
  return (
    <main className="app-shell">
      <h1>Sweet Market</h1>
      <p>중고거래 흐름을 학습하는 JPA 마켓입니다.</p>
    </main>
  );
}
```

Create `web/src/shared/styles.css`:

```css
:root {
  font-family:
    Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  color: #1c1b1a;
  background: #f8f6f2;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
}

button,
input,
textarea {
  font: inherit;
}

.app-shell {
  min-height: 100vh;
  padding: 32px;
}
```

- [ ] **Step 4: Install dependencies**

Run:

```powershell
cd web
npm install
```

Expected: `package-lock.json` is created and install succeeds.

- [ ] **Step 5: Build web app**

Run:

```powershell
cd web
npm run build
```

Expected: build succeeds.

- [ ] **Step 6: Update ignore and agent notes**

Modify `.gitignore`:

```text
.worktrees/
.idea/
.superpowers/
web/node_modules/
web/dist/
```

Modify `AGENTS.md` under backend notes or a new web section:

```markdown
## Web Execution

- The web app lives in `web`.
- Use Node.js from the local environment.
- Recommended commands:

```powershell
cd web
npm install
npm run build
npm run dev
```
```

- [ ] **Step 7: Commit**

```powershell
git add .gitignore AGENTS.md web
git commit -m "build: scaffold web app"
```

---

## Task 6: Web API Client, Auth, And Routing

**Files:**
- Create: `web/src/shared/api/http.ts`
- Create: `web/src/app/providers.tsx`
- Replace: `web/src/app/App.tsx`
- Create: `web/src/app/router.tsx`
- Create: auth files under `web/src/features/auth`
- Create: `web/src/shared/layout/Shell.tsx`
- Create: `web/src/pages/LoginPage.tsx`
- Create: `web/src/pages/SignupPage.tsx`
- Create: `web/src/pages/HomePage.tsx`

- [ ] **Step 1: Add HTTP client**

Create `web/src/shared/api/http.ts`:

```ts
export type ApiError = {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
};

type ApiResponse<T> = {
  data: T;
};

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

let accessToken: string | null = localStorage.getItem('sweet-market-token');

export function setAccessToken(token: string | null) {
  accessToken = token;
  if (token) {
    localStorage.setItem('sweet-market-token', token);
  } else {
    localStorage.removeItem('sweet-market-token');
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Content-Type', 'application/json');
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
  });

  const text = await response.text();
  const body = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw normalizeError(body);
  }

  return (body as ApiResponse<T>).data;
}

function normalizeError(body: unknown): ApiError {
  if (typeof body === 'object' && body !== null) {
    const value = body as { code?: string; message?: string; errors?: Record<string, string> };
    return {
      code: value.code ?? 'UNKNOWN_ERROR',
      message: value.message ?? '요청을 처리하지 못했습니다.',
      fieldErrors: value.errors,
    };
  }
  return {
    code: 'UNKNOWN_ERROR',
    message: '요청을 처리하지 못했습니다.',
  };
}
```

- [ ] **Step 2: Add auth API and provider**

Create `web/src/features/auth/authApi.ts`:

```ts
import { api } from '../../shared/api/http';

export type MemberRole = 'MEMBER' | 'ADMIN';

export type CurrentMember = {
  id: number;
  email: string;
  nickname: string;
  role: MemberRole;
};

export type AuthResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
};

export function login(email: string, password: string) {
  return api<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export function signup(email: string, password: string, nickname: string) {
  return api<CurrentMember>('/api/auth/signup', {
    method: 'POST',
    body: JSON.stringify({ email, password, nickname }),
  });
}

export function getCurrentMember() {
  return api<CurrentMember>('/api/members/me');
}
```

Create `web/src/features/auth/AuthProvider.tsx` with a React context exposing `member`, `login`, `signup`, and `logout`. Use `setAccessToken` and `getCurrentMember` after login.

- [ ] **Step 3: Add route guards**

Create `web/src/features/auth/RequireAuth.tsx`:

```tsx
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';

export function RequireAuth() {
  const { member, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <p>로그인 상태를 확인하고 있습니다.</p>;
  }

  if (!member) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <Outlet />;
}
```

Create `web/src/features/auth/RequireAdmin.tsx`:

```tsx
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthProvider';

export function RequireAdmin() {
  const { member, loading } = useAuth();

  if (loading) {
    return <p>권한을 확인하고 있습니다.</p>;
  }

  if (!member) {
    return <Navigate to="/login" replace />;
  }

  if (member.role !== 'ADMIN') {
    return <p>관리자만 접근할 수 있습니다.</p>;
  }

  return <Outlet />;
}
```

- [ ] **Step 4: Add providers and router**

Create `web/src/app/providers.tsx` with `QueryClientProvider`, `BrowserRouter`, and `AuthProvider`.

Create `web/src/app/router.tsx` with routes:

```tsx
import { createBrowserRouter } from 'react-router-dom';
import { RequireAdmin } from '../features/auth/RequireAdmin';
import { RequireAuth } from '../features/auth/RequireAuth';
import { HomePage } from '../pages/HomePage';
import { LoginPage } from '../pages/LoginPage';
import { SignupPage } from '../pages/SignupPage';

export const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/me/orders', element: <p>내 주문</p> },
      { path: '/me/sales', element: <p>내 판매</p> },
      { path: '/me/settlements', element: <p>내 정산</p> },
    ],
  },
  {
    element: <RequireAdmin />,
    children: [{ path: '/admin/batches/settlements', element: <p>정산 배치</p> }],
  },
]);
```

- [ ] **Step 5: Build**

Run:

```powershell
cd web
npm run build
```

Expected: build succeeds.

- [ ] **Step 6: Commit**

```powershell
git add web/src
git commit -m "feat: add web auth and routing foundation"
```

---

## Task 7: Web Product Experience

**Files:**
- Create: `web/src/features/products/productApi.ts`
- Modify: `web/src/pages/HomePage.tsx`
- Create: `web/src/pages/ProductDetailPage.tsx`
- Create: `web/src/pages/ProductFormPage.tsx`
- Modify: `web/src/app/router.tsx`
- Create: `web/src/shared/components/EmptyState.tsx`
- Create: `web/src/shared/components/ErrorState.tsx`
- Create: `web/src/shared/components/StatusBadge.tsx`

- [ ] **Step 1: Add product API module**

Create `web/src/features/products/productApi.ts`:

```ts
import { api } from '../../shared/api/http';

export type Page<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ProductSummary = {
  id: number;
  sellerId: number;
  sellerNickname: string;
  title: string;
  price: number;
  status: string;
  thumbnailUrl: string | null;
};

export type Product = ProductSummary & {
  description: string;
  images: { id: number; imageUrl: string; sortOrder: number }[];
};

export function getProducts() {
  return api<Page<ProductSummary>>('/api/products');
}

export function getMyProducts() {
  return api<Page<ProductSummary>>('/api/products/me');
}

export function getProduct(productId: number) {
  return api<Product>(`/api/products/${productId}`);
}

export function createProduct(input: { title: string; description: string; price: number }) {
  return api<Product>('/api/products', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateProduct(productId: number, input: { title: string; description: string; price: number }) {
  return api<Product>(`/api/products/${productId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export function hideProduct(productId: number) {
  return api<Product>(`/api/products/${productId}`, {
    method: 'DELETE',
  });
}
```

- [ ] **Step 2: Implement product list page**

Modify `web/src/pages/HomePage.tsx` to call `useQuery({ queryKey: ['products'], queryFn: getProducts })`, render product cards, and link to `/products/:id`.

- [ ] **Step 3: Implement product detail page**

Create `web/src/pages/ProductDetailPage.tsx` with:

- Product detail query.
- Order button for logged-in users.
- Edit link for the seller.
- Login link for unauthenticated users.

- [ ] **Step 4: Implement product form page**

Create `web/src/pages/ProductFormPage.tsx` with `react-hook-form` for title, description, and price. Use `createProduct` for `/products/new` and `updateProduct` for `/products/:id/edit`.

- [ ] **Step 5: Wire product routes**

Modify `web/src/app/router.tsx`:

```tsx
{ path: '/products/:productId', element: <ProductDetailPage /> },
{
  element: <RequireAuth />,
  children: [
    { path: '/products/new', element: <ProductFormPage /> },
    { path: '/products/:productId/edit', element: <ProductFormPage /> }
  ]
}
```

- [ ] **Step 6: Build**

Run:

```powershell
cd web
npm run build
```

Expected: build succeeds.

- [ ] **Step 7: Commit**

```powershell
git add web/src
git commit -m "feat: add web product experience"
```

---

## Task 8: Web Transaction And My Pages

**Files:**
- Create API modules under `web/src/features/orders`, `payments`, `deliveries`, `settlements`
- Create/modify pages: `MyOrdersPage.tsx`, `MySalesPage.tsx`, `MySettlementsPage.tsx`, `ProductDetailPage.tsx`
- Modify: `web/src/app/router.tsx`

- [ ] **Step 1: Add order, payment, delivery, settlement API modules**

Create modules with these functions:

```ts
// orderApi.ts
createOrder(productId: number)
getMyOrders()
getOrder(orderId: number)
cancelOrder(orderId: number)
confirmOrder(orderId: number)

// paymentApi.ts
approvePayment(orderId: number)
cancelPayment(orderId: number)

// deliveryApi.ts
startDelivery(orderId: number)
completeDelivery(orderId: number)

// settlementApi.ts
createSettlement(orderId: number)
getMySettlements()
```

Each function should call the matching backend endpoint from the existing controllers.

- [ ] **Step 2: Add order creation to product detail**

Modify `ProductDetailPage.tsx` so the order button calls `createOrder(productId)`, invalidates `['products']`, and navigates to `/me/orders`.

- [ ] **Step 3: Implement MyOrdersPage**

Create `web/src/pages/MyOrdersPage.tsx` showing order status and action buttons:

```text
CREATED -> cancel
CREATED or PAID-ready flow -> approve payment
PAID -> start delivery
SHIPPING -> complete delivery
DELIVERED -> confirm purchase
CONFIRMED -> show completed state
```

Buttons should call mutations and invalidate `['my-orders']`.

- [ ] **Step 4: Implement MySalesPage**

Create `web/src/pages/MySalesPage.tsx` using `getMyProducts()`. Show product status, price, edit link, and hide action.

- [ ] **Step 5: Implement MySettlementsPage**

Create `web/src/pages/MySettlementsPage.tsx` using `getMySettlements()`. Show product title, amount, status, and settledAt.

- [ ] **Step 6: Build**

Run:

```powershell
cd web
npm run build
```

Expected: build succeeds.

- [ ] **Step 7: Commit**

```powershell
git add web/src
git commit -m "feat: add web transaction workflows"
```

---

## Task 9: Web Admin Settlement Batch Console

**Files:**
- Create: `web/src/features/admin/adminBatchApi.ts`
- Create: `web/src/pages/AdminBatchPage.tsx`
- Modify: `web/src/app/router.tsx`

- [ ] **Step 1: Add admin batch API module**

Create `web/src/features/admin/adminBatchApi.ts`:

```ts
import { api } from '../../shared/api/http';

export type BatchLaunchRequest = {
  confirmedBefore: string;
  limit: number;
  chunkSize: number;
};

export type BatchExecutionSummary = {
  executionId: number;
  jobName: string;
  status: string;
  exitCode: string;
  createTime: string;
  startTime: string | null;
  endTime: string | null;
};

export type BatchExecutionDetail = BatchExecutionSummary & {
  parameters: {
    confirmedBefore: string;
    limit: number;
    chunkSize: number;
  };
  step: {
    readCount: number;
    writeCount: number;
    skipCount: number;
    rollbackCount: number;
  };
  failureMessages: string[];
};

export function launchSettlementBatch(input: BatchLaunchRequest) {
  return api('/api/admin/batches/settlements', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function getSettlementBatchExecutions() {
  return api<BatchExecutionSummary[]>('/api/admin/batches/settlements/executions?size=20');
}

export function getSettlementBatchExecution(executionId: number) {
  return api<BatchExecutionDetail>(`/api/admin/batches/settlements/executions/${executionId}`);
}
```

- [ ] **Step 2: Implement AdminBatchPage**

Create `web/src/pages/AdminBatchPage.tsx`:

- Form fields: `confirmedBefore`, `limit`, `chunkSize`.
- Default values: tomorrow local date-time, `100`, `20`.
- Launch button calls mutation.
- On success invalidate `['admin', 'settlement-batch-executions']`.
- Render recent execution table.
- Click row to load detail.

- [ ] **Step 3: Build**

Run:

```powershell
cd web
npm run build
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```powershell
git add web/src
git commit -m "feat: add web admin batch console"
```

---

## Task 10: Styling, Demo Instructions, And Full Verification

**Files:**
- Modify: `web/src/shared/styles.css`
- Modify: `AGENTS.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Polish market layout**

Modify `web/src/shared/styles.css` to include:

```css
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 32px;
  border-bottom: 1px solid #e3ded5;
  background: #fffdf8;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.product-card {
  border: 1px solid #e3ded5;
  border-radius: 8px;
  background: #fffdf8;
  padding: 16px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 8px;
  background: #eef4ef;
  color: #245b36;
  font-size: 12px;
  font-weight: 700;
}
```

- [ ] **Step 2: Add demo credentials to AGENTS.md**

Modify `AGENTS.md`:

```markdown
## Demo Accounts

When the backend runs with the `local` or `dev` profile after Milestone 8, demo seed data provides:

- `admin@example.com` / `password123`
- `seller1@example.com` / `password123`
- `seller2@example.com` / `password123`
- `buyer1@example.com` / `password123`
- `buyer2@example.com` / `password123`
```

- [ ] **Step 3: Run full backend verification**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon cleanTest test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run test naming check**

Run:

```powershell
rg -U -n "@Test(\r?\n|\s)*\s*void [a-zA-Z0-9]+\(" backend\src\test\java
```

Expected: no output. `rg` may exit with code `1` when there are no matches.

- [ ] **Step 5: Run web verification**

Run:

```powershell
cd web
npm run build
```

Expected: build succeeds.

- [ ] **Step 6: Manual local smoke test**

Start backend:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

Start web:

```powershell
cd web
npm run dev
```

Open:

```text
http://localhost:5173/
```

Verify:

```text
buyer1@example.com can log in
buyer can browse product and create order
buyer can approve payment, complete delivery, and confirm purchase
admin@example.com can log in
admin can launch settlement batch
admin can see recent execution history
```

- [ ] **Step 7: Commit final polish**

```powershell
git add AGENTS.md web/src/shared/styles.css
git commit -m "docs: add web demo instructions"
```

- [ ] **Step 8: Final status**

Run:

```powershell
git status --short --branch
git log --oneline -8
```

Expected: working tree contains no unexpected changes. If `backend/src/main/resources/application.yaml` still has user-local edits unrelated to Milestone 8, leave them unstaged and mention them in the handoff.

---

## Self-Review

- Spec coverage: The plan covers local demo seed data, batch history API, CORS, React/Vite/TypeScript setup, auth, role-aware routing, product pages, transaction pages, seller pages, settlement pages, admin batch page, backend tests, web build, and manual smoke verification.
- Scope check: Scheduler, distributed lock, refresh tokens, OAuth, deployment, file storage, and heavy UI libraries are excluded.
- Type consistency: Backend response names use `AdminSettlementBatchExecutionSummaryResponse` and `AdminSettlementBatchExecutionDetailResponse`; frontend types use matching field names. Current member role is exposed as `role`.
