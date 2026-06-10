# Milestone 7 Batch Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Batch 기반 정산 배치를 도입하고, ADMIN 전용 API로 `confirmedBefore`, `limit`, `chunkSize`를 받아 확정 주문의 자동 정산 job을 실행한다.

**Architecture:** `/api/admin/**`는 `MemberRole.ADMIN` JWT 권한으로 보호한다. `AdminSettlementBatchController`는 `JobLauncher`로 `settlementJob`을 실행하고, job은 `JdbcPagingItemReader<Long>`, `ItemProcessor<Long, Settlement>`, `ItemWriter<Settlement>`로 미정산 확정 주문을 chunk 단위 처리한다. retry 후 skip 정책과 기존 `settlements.order_id` unique 제약으로 운영형 idempotency를 만든다.

**Tech Stack:** Spring Boot, Spring Security, Spring Batch, Spring Data JPA, PostgreSQL, Hibernate, Lombok, JUnit 5, MockMvc, Spring Batch Test, Testcontainers

---

## Scope

완료 기준:

- `spring-boot-starter-batch`와 `spring-batch-test`를 추가한다.
- Spring Batch metadata schema가 테스트와 로컬 런타임에서 초기화된다.
- `MemberRole.ADMIN`이 추가된다.
- JWT access token에 role claim이 포함된다.
- 인증 principal이 `id`, `email`, `role`을 가진다.
- `/api/admin/**`는 ADMIN만 접근 가능하다.
- `Order.confirm()`이 `confirmedAt`을 기록한다.
- `POST /api/admin/batches/settlements`가 batch job을 실행한다.
- batch request는 `confirmedBefore`, `limit`, `chunkSize`를 받는다.
- `settlementJob`은 `JdbcPagingItemReader<Long>`로 정산 대상 order id를 읽는다.
- `settlementStep`은 job parameter의 `chunkSize`를 `@JobScope`로 받아 chunk 크기에 사용한다.
- 정산 대상은 `CONFIRMED`, `confirmedAt < confirmedBefore`, 미정산 주문으로 제한된다.
- 이미 정산된 주문이나 더 이상 정산 불가한 주문은 skip된다.
- 일시 실패는 retry 후 skip된다.
- 같은 parameter로 재실행해도 중복 정산이 생기지 않는다.
- 전체 backend 테스트가 통과한다.
- 모든 신규 JUnit `@Test` 메서드명은 Korean_with_underscores 형식이다.

Out of scope:

- scheduler
- 관리자 계정 생성 API
- admin UI
- 실패 상세 저장 테이블
- 분산 lock
- 정산 수수료 계산
- 정산 취소
- 외부 정산사 연동

## File Structure

생성 또는 수정할 파일:

```text
backend/build.gradle
backend/src/main/resources/application.yaml

backend/src/main/java/com/sweet/market/member/domain/MemberRole.java
backend/src/main/java/com/sweet/market/member/domain/Member.java
backend/src/main/java/com/sweet/market/auth/application/AuthService.java
backend/src/main/java/com/sweet/market/auth/security/AuthenticatedMember.java
backend/src/main/java/com/sweet/market/auth/security/JwtProvider.java
backend/src/main/java/com/sweet/market/auth/security/JwtAuthenticationFilter.java
backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java

backend/src/main/java/com/sweet/market/order/domain/Order.java
backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java

backend/src/main/java/com/sweet/market/settlement/batch/SettlementBatchConfig.java
backend/src/main/java/com/sweet/market/settlement/batch/SettlementBatchSkippableException.java
backend/src/main/java/com/sweet/market/settlement/batch/SettlementItemProcessor.java
backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchRequest.java
backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchResponse.java
backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchController.java

backend/src/test/java/com/sweet/market/auth/AdminSecurityApiTest.java
backend/src/test/java/com/sweet/market/order/domain/OrderTest.java
backend/src/test/java/com/sweet/market/settlement/batch/SettlementBatchJobTest.java
backend/src/test/java/com/sweet/market/settlement/batch/AdminSettlementBatchApiTest.java
backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
```

---

## Task 1: Spring Batch 의존성과 metadata 초기화 설정

**Files:**

- Modify: `backend/build.gradle`
- Modify: `backend/src/main/resources/application.yaml`
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`

- [ ] **Step 1: Spring Batch 의존성을 추가한다**

Modify `backend/build.gradle` dependencies block:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-batch'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'

    compileOnly 'org.projectlombok:lombok'

    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
    runtimeOnly 'org.postgresql:postgresql'

    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.batch:spring-batch-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'

    testCompileOnly 'org.projectlombok:lombok'

    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'

    testAnnotationProcessor 'org.projectlombok:lombok'
}
```

- [ ] **Step 2: 로컬 runtime에서 Spring Batch schema를 초기화하도록 설정한다**

Modify `backend/src/main/resources/application.yaml`. Keep the existing local `ddl-auto` and `jwt.secret` values if they differ in the working tree; only add the `spring.batch.jdbc.initialize-schema` section:

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: always
```

The file already has a top-level `spring:` block. Merge this under the existing `spring:` key instead of creating a second duplicate key.

- [ ] **Step 3: 테스트 환경에서도 Spring Batch schema를 초기화한다**

Modify `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java` dynamic properties:

```java
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.batch.jdbc.initialize-schema", () -> "always");
        registry.add("jwt.secret", () -> "sweet-market-test-secret-key-32bytes-minimum");
        registry.add("jwt.access-token-validity-seconds", () -> "3600");
    }
```

- [ ] **Step 4: 의존성 해석과 context compile을 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat compileJava testClasses
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: 커밋한다**

```powershell
git add backend/build.gradle backend/src/main/resources/application.yaml backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
git commit -m "build: add spring batch"
```

---

## Task 2: ADMIN role 기반 인증/인가 모델 추가

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/member/domain/MemberRole.java`
- Modify: `backend/src/main/java/com/sweet/market/member/domain/Member.java`
- Modify: `backend/src/main/java/com/sweet/market/auth/application/AuthService.java`
- Modify: `backend/src/main/java/com/sweet/market/auth/security/AuthenticatedMember.java`
- Modify: `backend/src/main/java/com/sweet/market/auth/security/JwtProvider.java`
- Modify: `backend/src/main/java/com/sweet/market/auth/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java`
- Create: `backend/src/test/java/com/sweet/market/auth/AdminSecurityApiTest.java`

- [ ] **Step 1: 실패하는 admin security 테스트를 작성한다**

Create `backend/src/test/java/com/sweet/market/auth/AdminSecurityApiTest.java`:

```java
package com.sweet.market.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.support.IntegrationTestSupport;

class AdminSecurityApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 관리자_API는_JWT가_없으면_접근할_수_없다() throws Exception {
        mockMvc.perform(post("/api/admin/batches/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedBefore": "2026-06-10T00:00:00",
                                  "limit": 100,
                                  "chunkSize": 20
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 일반_회원은_관리자_API에_접근할_수_없다() throws Exception {
        String memberToken = signupAndLogin("member@example.com", "password123", "member");

        mockMvc.perform(post("/api/admin/batches/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedBefore": "2026-06-10T00:00:00",
                                  "limit": 100,
                                  "chunkSize": 20
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 관리자_JWT는_ADMIN_authority를_가진다() throws Exception {
        memberRepository.save(Member.createAdmin(
                "admin@example.com",
                passwordEncoder.encode("password123"),
                "admin"
        ));

        String adminToken = login("admin@example.com", "password123");

        mockMvc.perform(post("/api/admin/batches/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedBefore": "2026-06-10T00:00:00",
                                  "limit": 100,
                                  "chunkSize": 20
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    private String signupAndLogin(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, password, nickname))))
                .andExpect(status().isCreated());
        return login(email, password);
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

Expected note: the admin request returns `404` in this task because the admin controller does not exist yet. That proves security allowed the ADMIN token through to routing.

- [ ] **Step 2: admin security 테스트 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.auth.AdminSecurityApiTest
```

Expected: compile FAIL because `Member.createAdmin` and role-aware JWT/principal do not exist.

- [ ] **Step 3: `MemberRole`에 ADMIN을 추가한다**

Modify `backend/src/main/java/com/sweet/market/member/domain/MemberRole.java`:

```java
package com.sweet.market.member.domain;

public enum MemberRole {
    MEMBER,
    ADMIN
}
```

- [ ] **Step 4: `Member`에 admin factory를 추가한다**

Modify `backend/src/main/java/com/sweet/market/member/domain/Member.java`:

```java
package com.sweet.market.member.domain;

import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    private Member(String email, String password, String nickname, MemberRole role) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
    }

    public static Member create(String email, String encodedPassword, String nickname) {
        return new Member(normalizeEmail(email), encodedPassword, nickname, MemberRole.MEMBER);
    }

    public static Member createAdmin(String email, String encodedPassword, String nickname) {
        return new Member(normalizeEmail(email), encodedPassword, nickname, MemberRole.ADMIN);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 5: `AuthenticatedMember`에 role을 추가한다**

Modify `backend/src/main/java/com/sweet/market/auth/security/AuthenticatedMember.java`:

```java
package com.sweet.market.auth.security;

import com.sweet.market.member.domain.MemberRole;

public record AuthenticatedMember(
        Long id,
        String email,
        MemberRole role
) {
}
```

- [ ] **Step 6: `JwtProvider`가 role claim을 쓰고 읽도록 수정한다**

Modify `backend/src/main/java/com/sweet/market/auth/security/JwtProvider.java`:

```java
package com.sweet.market.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.sweet.market.member.domain.MemberRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenValiditySeconds;

    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = properties.accessTokenValiditySeconds();
    }

    public String createAccessToken(Long memberId, String email, MemberRole role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenValiditySeconds);

        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public AuthenticatedMember parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long memberId = Long.valueOf(claims.getSubject());
            String email = claims.get("email", String.class);
            MemberRole role = MemberRole.valueOf(claims.get("role", String.class));
            return new AuthenticatedMember(memberId, email, role);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidJwtException();
        }
    }

    public long accessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    public static class InvalidJwtException extends RuntimeException {
    }
}
```

- [ ] **Step 7: `AuthService`가 role을 담아 token을 발급하도록 수정한다**

Modify the token creation line in `backend/src/main/java/com/sweet/market/auth/application/AuthService.java`:

```java
        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getEmail(), member.getRole());
```

- [ ] **Step 8: JWT authentication authority를 role에서 만든다**

Modify `backend/src/main/java/com/sweet/market/auth/security/JwtAuthenticationFilter.java`:

```java
package com.sweet.market.auth.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        try {
            if (token != null) {
                authenticate(token);
            }
        } catch (JwtProvider.InvalidJwtException exception) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }

    private void authenticate(String token) {
        AuthenticatedMember member = jwtProvider.parseAccessToken(token);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                member,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.role().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
```

- [ ] **Step 9: `/api/admin/**`를 ADMIN으로 제한한다**

Modify the authorization block in `backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java`:

```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
```

- [ ] **Step 10: admin security 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.auth.AdminSecurityApiTest
```

Expected: PASS.

- [ ] **Step 11: 전체 인증 회귀 테스트를 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.auth.*
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/member/domain/MemberRole.java backend/src/main/java/com/sweet/market/member/domain/Member.java backend/src/main/java/com/sweet/market/auth/application/AuthService.java backend/src/main/java/com/sweet/market/auth/security/AuthenticatedMember.java backend/src/main/java/com/sweet/market/auth/security/JwtProvider.java backend/src/main/java/com/sweet/market/auth/security/JwtAuthenticationFilter.java backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java backend/src/test/java/com/sweet/market/auth/AdminSecurityApiTest.java
git commit -m "feat: add admin role security"
```

---

## Task 3: 주문 구매 확정 시각 추가

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/order/domain/Order.java`
- Modify: `backend/src/test/java/com/sweet/market/order/domain/OrderTest.java`

- [ ] **Step 1: 실패하는 주문 도메인 테스트를 추가한다**

Append these tests to `backend/src/test/java/com/sweet/market/order/domain/OrderTest.java`:

```java
    @Test
    void 구매_확정하면_확정_시각을_기록한다() {
        Member seller = Member.create("seller-confirmed-at@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer-confirmed-at@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        Order order = Order.create(buyer, product);
        order.markPaid();
        order.startShipping();
        order.completeDelivery();

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isNotNull();
    }

    @Test
    void 구매_확정하지_않은_주문은_확정_시각이_없다() {
        Member seller = Member.create("seller-no-confirmed-at@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer-no-confirmed-at@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        Order order = Order.create(buyer, product);

        assertThat(order.getConfirmedAt()).isNull();
    }
```

- [ ] **Step 2: 주문 도메인 테스트 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.order.domain.OrderTest
```

Expected: compile FAIL because `Order.getConfirmedAt()` does not exist.

- [ ] **Step 3: `Order`에 `confirmedAt`을 추가한다**

Modify `backend/src/main/java/com/sweet/market/order/domain/Order.java`:

```java
package com.sweet.market.order.domain;

import java.time.LocalDateTime;

import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    private LocalDateTime canceledAt;

    private LocalDateTime confirmedAt;

    private Order(Member buyer, Product product, OrderStatus status, LocalDateTime orderedAt) {
        this.buyer = buyer;
        this.product = product;
        this.status = status;
        this.orderedAt = orderedAt;
    }

    public static Order create(Member buyer, Product product) {
        product.reserve();
        return new Order(buyer, product, OrderStatus.CREATED, LocalDateTime.now());
    }

    public void cancel() {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("Order cannot be canceled: " + status);
        }
        product.restoreOnSaleFromReservation();
        this.status = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public void markPaid() {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("Order cannot be paid: " + status);
        }
        this.status = OrderStatus.PAID;
    }

    public void cancelPaidOrder() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("Paid order cannot be canceled: " + status);
        }
        product.restoreOnSaleFromReservation();
        this.status = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public void startShipping() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("Order cannot start shipping: " + status);
        }
        this.status = OrderStatus.SHIPPING;
    }

    public void completeDelivery() {
        if (status != OrderStatus.SHIPPING) {
            throw new IllegalStateException("Order cannot complete delivery: " + status);
        }
        this.status = OrderStatus.DELIVERED;
    }

    public void confirm() {
        if (status != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Order cannot be confirmed: " + status);
        }
        product.markSoldOutFromReservation();
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long memberId) {
        return buyer.getId().equals(memberId);
    }
}
```

- [ ] **Step 4: 주문 도메인 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.order.domain.OrderTest
```

Expected: PASS.

- [ ] **Step 5: 주문 API 회귀 테스트를 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.order.*
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/order/domain/Order.java backend/src/test/java/com/sweet/market/order/domain/OrderTest.java
git commit -m "feat: record order confirmed time"
```

---

## Task 4: 정산 Batch job 구성

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/batch/SettlementBatchSkippableException.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/batch/SettlementItemProcessor.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/batch/SettlementBatchConfig.java`
- Create: `backend/src/test/java/com/sweet/market/settlement/batch/SettlementBatchJobTest.java`

- [ ] **Step 1: 실패하는 batch job 테스트를 작성한다**

Create `backend/src/test/java/com/sweet/market/settlement/batch/SettlementBatchJobTest.java`:

```java
package com.sweet.market.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

@SpringBatchTest
class SettlementBatchJobTest extends IntegrationTestSupport {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job settlementJob;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 확정_시각이_기준보다_이전인_미정산_주문을_정산한다() throws Exception {
        saveConfirmedAndSettledOrder("seller1@example.com", "buyer1@example.com");
        saveConfirmedOrder("seller2@example.com", "buyer2@example.com");

        JobExecution execution = launchSettlementJob(LocalDateTime.now().plusDays(1), 100, 10);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(settlementRepository.count()).isEqualTo(2);
        assertThat(execution.getStepExecutions())
                .singleElement()
                .satisfies(stepExecution -> {
                    assertThat(stepExecution.getReadCount()).isEqualTo(1);
                    assertThat(stepExecution.getWriteCount()).isEqualTo(1);
                    assertThat(stepExecution.getSkipCount()).isZero();
                });
    }

    @Test
    void 기준_시각_이후에_확정된_주문은_정산하지_않는다() throws Exception {
        saveConfirmedOrder("seller3@example.com", "buyer3@example.com");

        JobExecution execution = launchSettlementJob(LocalDateTime.now().minusDays(1), 100, 10);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(settlementRepository.count()).isZero();
        assertThat(execution.getStepExecutions())
                .singleElement()
                .satisfies(stepExecution -> {
                    assertThat(stepExecution.getReadCount()).isZero();
                    assertThat(stepExecution.getWriteCount()).isZero();
                });
    }

    @Test
    void 같은_조건으로_다시_실행해도_중복_정산하지_않는다() throws Exception {
        saveConfirmedOrder("seller4@example.com", "buyer4@example.com");
        saveConfirmedOrder("seller5@example.com", "buyer5@example.com");

        JobExecution firstExecution = launchSettlementJob(LocalDateTime.now().plusDays(1), 100, 10);
        JobExecution secondExecution = launchSettlementJob(LocalDateTime.now().plusDays(1), 100, 10);

        assertThat(firstExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(secondExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(settlementRepository.count()).isEqualTo(2);
        assertThat(secondExecution.getStepExecutions())
                .singleElement()
                .satisfies(stepExecution -> {
                    assertThat(stepExecution.getReadCount()).isZero();
                    assertThat(stepExecution.getWriteCount()).isZero();
                });
    }

    private JobExecution launchSettlementJob(LocalDateTime confirmedBefore, int limit, int chunkSize) throws Exception {
        jobLauncherTestUtils.setJob(settlementJob);
        JobParameters parameters = new JobParametersBuilder()
                .addString("confirmedBefore", confirmedBefore.toString())
                .addLong("limit", (long) limit)
                .addLong("chunkSize", (long) chunkSize)
                .addLong("requestedAt", System.nanoTime())
                .toJobParameters();
        return jobLauncherTestUtils.launchJob(parameters);
    }

    private Long saveConfirmedOrder(String sellerEmail, String buyerEmail) {
        return transactionTemplate.execute(status -> {
            Order order = createConfirmedOrder(sellerEmail, buyerEmail);
            return order.getId();
        });
    }

    private Long saveConfirmedAndSettledOrder(String sellerEmail, String buyerEmail) {
        return transactionTemplate.execute(status -> {
            Order order = createConfirmedOrder(sellerEmail, buyerEmail);
            settlementRepository.save(Settlement.create(order));
            return order.getId();
        });
    }

    private Order createConfirmedOrder(String sellerEmail, String buyerEmail) {
        Member seller = Member.create(sellerEmail, "encoded-password", sellerEmail.split("@")[0]);
        Member buyer = Member.create(buyerEmail, "encoded-password", buyerEmail.split("@")[0]);
        entityManager.persist(seller);
        entityManager.persist(buyer);

        Product product = Product.create(seller, "Batch Product", "Batch Description", 10_000L);
        entityManager.persist(product);

        Order order = Order.create(buyer, product);
        entityManager.persist(order);
        order.markPaid();
        order.startShipping();
        order.completeDelivery();
        order.confirm();
        return order;
    }
}
```

- [ ] **Step 2: batch job 테스트 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.settlement.batch.SettlementBatchJobTest
```

Expected: compile FAIL because `settlementJob` and batch classes do not exist.

- [ ] **Step 3: `OrderRepository`에 batch processor용 조회 메서드를 추가한다**

Modify `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`:

```java
package com.sweet.market.order.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"product", "product.seller"})
    Page<Order> findByBuyerIdOrderByIdDesc(Long buyerId, Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "product", "product.seller", "product.images"})
    Optional<Order> findWithBuyerAndProductById(Long id);

    @EntityGraph(attributePaths = {"product", "product.seller"})
    @Query("""
            select o
            from Order o
            where o.id = :orderId
            """)
    Optional<Order> findSettlementTargetById(@Param("orderId") Long orderId);
}
```

- [ ] **Step 4: skippable batch exception을 생성한다**

Create `backend/src/main/java/com/sweet/market/settlement/batch/SettlementBatchSkippableException.java`:

```java
package com.sweet.market.settlement.batch;

public class SettlementBatchSkippableException extends RuntimeException {

    public SettlementBatchSkippableException(String message) {
        super(message);
    }

    public SettlementBatchSkippableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 5: 정산 item processor를 생성한다**

Create `backend/src/main/java/com/sweet/market/settlement/batch/SettlementItemProcessor.java`:

```java
package com.sweet.market.settlement.batch;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;

@Component
public class SettlementItemProcessor implements ItemProcessor<Long, Settlement> {

    private final OrderRepository orderRepository;
    private final SettlementRepository settlementRepository;

    public SettlementItemProcessor(
            OrderRepository orderRepository,
            SettlementRepository settlementRepository
    ) {
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
    }

    @Override
    public Settlement process(Long orderId) {
        if (settlementRepository.existsByOrderId(orderId)) {
            throw new SettlementBatchSkippableException("Order already settled: " + orderId);
        }

        Order order = orderRepository.findSettlementTargetById(orderId)
                .orElseThrow(() -> new SettlementBatchSkippableException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new SettlementBatchSkippableException("Order is not confirmed: " + orderId);
        }

        try {
            return Settlement.create(order);
        } catch (IllegalStateException exception) {
            throw new SettlementBatchSkippableException("Order cannot be settled: " + orderId, exception);
        }
    }
}
```

- [ ] **Step 6: Spring Batch job config를 생성한다**

Create `backend/src/main/java/com/sweet/market/settlement/batch/SettlementBatchConfig.java`:

```java
package com.sweet.market.settlement.batch;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;

@Configuration
public class SettlementBatchConfig {

    public static final String SETTLEMENT_JOB_NAME = "settlementJob";
    public static final String SETTLEMENT_STEP_NAME = "settlementStep";

    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settlementStep) {
        return new JobBuilder(SETTLEMENT_JOB_NAME, jobRepository)
                .start(settlementStep)
                .build();
    }

    @Bean
    @JobScope
    public Step settlementStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcPagingItemReader<Long> settlementOrderIdReader,
            SettlementItemProcessor settlementItemProcessor,
            ItemWriter<Settlement> settlementWriter,
            @Value("#{jobParameters['chunkSize']}") Long chunkSize
    ) {
        return new StepBuilder(SETTLEMENT_STEP_NAME, jobRepository)
                .<Long, Settlement>chunk(chunkSize.intValue(), transactionManager)
                .reader(settlementOrderIdReader)
                .processor(settlementItemProcessor)
                .writer(settlementWriter)
                .faultTolerant()
                .retry(DataAccessException.class)
                .retryLimit(3)
                .skip(SettlementBatchSkippableException.class)
                .skip(DataAccessException.class)
                .skipLimit(Integer.MAX_VALUE)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<Long> settlementOrderIdReader(
            DataSource dataSource,
            @Value("#{jobParameters['confirmedBefore']}") String confirmedBefore,
            @Value("#{jobParameters['limit']}") Long limit,
            @Value("#{jobParameters['chunkSize']}") Long chunkSize
    ) {
        JdbcPagingItemReader<Long> reader = new JdbcPagingItemReader<>();
        reader.setName("settlementOrderIdReader");
        reader.setDataSource(dataSource);
        reader.setQueryProvider(settlementOrderIdQueryProvider());
        reader.setParameterValues(Map.of(
                "status", "CONFIRMED",
                "confirmedBefore", Timestamp.valueOf(LocalDateTime.parse(confirmedBefore))
        ));
        reader.setPageSize(chunkSize.intValue());
        reader.setMaxItemCount(limit.intValue());
        reader.setRowMapper((resultSet, rowNum) -> resultSet.getLong("id"));
        return reader;
    }

    @Bean
    public ItemWriter<Settlement> settlementWriter(SettlementRepository settlementRepository) {
        return chunk -> settlementRepository.saveAll(chunk.getItems());
    }

    private PagingQueryProvider settlementOrderIdQueryProvider() {
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("select o.id");
        queryProvider.setFromClause("from orders o");
        queryProvider.setWhereClause("""
                where o.status = :status
                  and o.confirmed_at < :confirmedBefore
                  and not exists (
                      select 1
                      from settlements s
                      where s.order_id = o.id
                  )
                """);
        queryProvider.setSortKeys(Map.of("o.id", Order.ASCENDING));
        return queryProvider;
    }
}
```

- [ ] **Step 7: batch job 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.settlement.batch.SettlementBatchJobTest
```

Expected: PASS.

- [ ] **Step 8: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java backend/src/main/java/com/sweet/market/settlement/batch/SettlementBatchSkippableException.java backend/src/main/java/com/sweet/market/settlement/batch/SettlementItemProcessor.java backend/src/main/java/com/sweet/market/settlement/batch/SettlementBatchConfig.java backend/src/test/java/com/sweet/market/settlement/batch/SettlementBatchJobTest.java
git commit -m "feat: add settlement batch job"
```

---

## Task 5: ADMIN settlement batch trigger API 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchRequest.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchResponse.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchController.java`
- Create: `backend/src/test/java/com/sweet/market/settlement/batch/AdminSettlementBatchApiTest.java`

- [ ] **Step 1: 실패하는 admin batch API 테스트를 작성한다**

Create `backend/src/test/java/com/sweet/market/settlement/batch/AdminSettlementBatchApiTest.java`:

```java
package com.sweet.market.settlement.batch;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.settlement.repository.SettlementRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

class AdminSettlementBatchApiTest extends IntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 관리자는_정산_배치를_실행한다() throws Exception {
        String adminToken = createAdminAndLogin();
        saveConfirmedOrder("batch-api-seller@example.com", "batch-api-buyer@example.com");

        mockMvc.perform(post("/api/admin/batches/settlements")
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
                .andExpect(jsonPath("$.data.jobExecutionId").isNumber())
                .andExpect(jsonPath("$.data.jobName").value("settlementJob"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.parameters.confirmedBefore").exists())
                .andExpect(jsonPath("$.data.parameters.limit").value(100))
                .andExpect(jsonPath("$.data.parameters.chunkSize").value(20));

        assertThat(settlementRepository.count()).isEqualTo(1);
    }

    @Test
    void chunkSize가_limit보다_크면_검증에_실패한다() throws Exception {
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/admin/batches/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedBefore": "2026-06-10T00:00:00",
                                  "limit": 10,
                                  "chunkSize": 20
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String createAdminAndLogin() throws Exception {
        memberRepository.save(Member.createAdmin(
                "batch-admin@example.com",
                passwordEncoder.encode("password123"),
                "batchAdmin"
        ));

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("batch-admin@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }

    private Long saveConfirmedOrder(String sellerEmail, String buyerEmail) {
        return transactionTemplate.execute(status -> {
            Member seller = Member.create(sellerEmail, "encoded-password", sellerEmail.split("@")[0]);
            Member buyer = Member.create(buyerEmail, "encoded-password", buyerEmail.split("@")[0]);
            entityManager.persist(seller);
            entityManager.persist(buyer);

            Product product = Product.create(seller, "Batch API Product", "Batch API Description", 10_000L);
            entityManager.persist(product);

            Order order = Order.create(buyer, product);
            entityManager.persist(order);
            order.markPaid();
            order.startShipping();
            order.completeDelivery();
            order.confirm();
            return order.getId();
        });
    }
}
```

Add this missing static import at the top of the file:

```java
import static org.assertj.core.api.Assertions.assertThat;
```

- [ ] **Step 2: admin batch API 테스트 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.settlement.batch.AdminSettlementBatchApiTest
```

Expected: compile FAIL because request/response/controller classes do not exist.

- [ ] **Step 3: admin batch request DTO를 생성한다**

Create `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchRequest.java`:

```java
package com.sweet.market.settlement.batch;

import java.time.LocalDateTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminSettlementBatchRequest(
        @NotNull LocalDateTime confirmedBefore,
        @NotNull @Positive Integer limit,
        @NotNull @Positive Integer chunkSize
) {

    @AssertTrue(message = "chunkSize must not be greater than limit")
    public boolean isChunkSizeNotGreaterThanLimit() {
        if (limit == null || chunkSize == null) {
            return true;
        }
        return chunkSize <= limit;
    }
}
```

- [ ] **Step 4: admin batch response DTO를 생성한다**

Create `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchResponse.java`:

```java
package com.sweet.market.settlement.batch;

import java.time.LocalDateTime;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;

public record AdminSettlementBatchResponse(
        Long jobExecutionId,
        String jobName,
        BatchStatus status,
        Parameters parameters
) {

    public static AdminSettlementBatchResponse from(JobExecution jobExecution, AdminSettlementBatchRequest request) {
        return new AdminSettlementBatchResponse(
                jobExecution.getId(),
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus(),
                new Parameters(request.confirmedBefore(), request.limit(), request.chunkSize())
        );
    }

    public record Parameters(
            LocalDateTime confirmedBefore,
            Integer limit,
            Integer chunkSize
    ) {
    }
}
```

- [ ] **Step 5: admin batch controller를 생성한다**

Create `backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchController.java`:

```java
package com.sweet.market.settlement.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/batches/settlements")
public class AdminSettlementBatchController {

    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    public AdminSettlementBatchController(JobLauncher jobLauncher, Job settlementJob) {
        this.jobLauncher = jobLauncher;
        this.settlementJob = settlementJob;
    }

    @PostMapping
    public ApiResponse<AdminSettlementBatchResponse> launch(
            @Valid @RequestBody AdminSettlementBatchRequest request
    ) throws Exception {
        JobExecution jobExecution = jobLauncher.run(settlementJob, jobParameters(request));
        return ApiResponse.ok(AdminSettlementBatchResponse.from(jobExecution, request));
    }

    private JobParameters jobParameters(AdminSettlementBatchRequest request) {
        return new JobParametersBuilder()
                .addString("confirmedBefore", request.confirmedBefore().toString())
                .addLong("limit", request.limit().longValue())
                .addLong("chunkSize", request.chunkSize().longValue())
                .addLong("requestedAt", System.nanoTime())
                .toJobParameters();
    }
}
```

- [ ] **Step 6: admin batch API 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.settlement.batch.AdminSettlementBatchApiTest
```

Expected: PASS.

- [ ] **Step 7: admin security 테스트 기대값을 controller 존재 상태에 맞춘다**

Modify the final assertion in `backend/src/test/java/com/sweet/market/auth/AdminSecurityApiTest.java` admin-token test from `status().isNotFound()` to:

```java
                .andExpect(status().isOk());
```

- [ ] **Step 8: admin 관련 테스트를 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.auth.AdminSecurityApiTest --tests com.sweet.market.settlement.batch.AdminSettlementBatchApiTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchRequest.java backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchResponse.java backend/src/main/java/com/sweet/market/settlement/batch/AdminSettlementBatchController.java backend/src/test/java/com/sweet/market/settlement/batch/AdminSettlementBatchApiTest.java backend/src/test/java/com/sweet/market/auth/AdminSecurityApiTest.java
git commit -m "feat: add admin settlement batch api"
```

---

## Task 6: retry/skip 동작 검증 보강

**Files:**

- Modify: `backend/src/test/java/com/sweet/market/settlement/batch/SettlementBatchJobTest.java`

- [ ] **Step 1: processor skip 동작 테스트를 추가한다**

Append this test to `backend/src/test/java/com/sweet/market/settlement/batch/SettlementBatchJobTest.java`:

```java
    @Test
    void reader_이후_이미_정산된_주문은_skip된다() throws Exception {
        Long orderId = saveConfirmedAndSettledOrder("seller-skip@example.com", "buyer-skip@example.com");

        JobExecution execution = launchSettlementJobWithForcedOrderId(orderId, 10);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(settlementRepository.count()).isEqualTo(1);
        assertThat(execution.getStepExecutions())
                .singleElement()
                .satisfies(stepExecution -> {
                    assertThat(stepExecution.getReadCount()).isEqualTo(1);
                    assertThat(stepExecution.getWriteCount()).isZero();
                    assertThat(stepExecution.getSkipCount()).isEqualTo(1);
                });
    }

    private JobExecution launchSettlementJobWithForcedOrderId(Long orderId, int chunkSize) throws Exception {
        jobLauncherTestUtils.setJob(settlementJob);
        JobParameters parameters = new JobParametersBuilder()
                .addString("confirmedBefore", LocalDateTime.now().plusDays(1).toString())
                .addLong("limit", 100L)
                .addLong("chunkSize", (long) chunkSize)
                .addLong("forcedOrderId", orderId)
                .addLong("requestedAt", System.nanoTime())
                .toJobParameters();
        return jobLauncherTestUtils.launchJob(parameters);
    }
```

- [ ] **Step 2: skip 테스트 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.settlement.batch.SettlementBatchJobTest
```

Expected: FAIL because `forcedOrderId` is not used by the reader.

- [ ] **Step 3: reader에 테스트 전용 forcedOrderId job parameter를 지원한다**

Modify the `settlementOrderIdReader` and query provider in `backend/src/main/java/com/sweet/market/settlement/batch/SettlementBatchConfig.java`:

```java
    @Bean
    @StepScope
    public JdbcPagingItemReader<Long> settlementOrderIdReader(
            DataSource dataSource,
            @Value("#{jobParameters['confirmedBefore']}") String confirmedBefore,
            @Value("#{jobParameters['limit']}") Long limit,
            @Value("#{jobParameters['chunkSize']}") Long chunkSize,
            @Value("#{jobParameters['forcedOrderId']}") Long forcedOrderId
    ) {
        JdbcPagingItemReader<Long> reader = new JdbcPagingItemReader<>();
        reader.setName("settlementOrderIdReader");
        reader.setDataSource(dataSource);
        reader.setQueryProvider(settlementOrderIdQueryProvider(forcedOrderId != null));
        reader.setParameterValues(parameterValues(confirmedBefore, forcedOrderId));
        reader.setPageSize(chunkSize.intValue());
        reader.setMaxItemCount(limit.intValue());
        reader.setRowMapper((resultSet, rowNum) -> resultSet.getLong("id"));
        return reader;
    }

    private Map<String, Object> parameterValues(String confirmedBefore, Long forcedOrderId) {
        if (forcedOrderId != null) {
            return Map.of("forcedOrderId", forcedOrderId);
        }
        return Map.of(
                "status", "CONFIRMED",
                "confirmedBefore", Timestamp.valueOf(LocalDateTime.parse(confirmedBefore))
        );
    }

    private PagingQueryProvider settlementOrderIdQueryProvider(boolean forced) {
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("select o.id");
        queryProvider.setFromClause("from orders o");
        if (forced) {
            queryProvider.setWhereClause("where o.id = :forcedOrderId");
        } else {
            queryProvider.setWhereClause("""
                    where o.status = :status
                      and o.confirmed_at < :confirmedBefore
                      and not exists (
                          select 1
                          from settlements s
                          where s.order_id = o.id
                      )
                    """);
        }
        queryProvider.setSortKeys(Map.of("o.id", Order.ASCENDING));
        return queryProvider;
    }
```

This parameter is only used by tests to make a race-like skip deterministic. Do not expose it through the admin API request.

- [ ] **Step 4: retry/skip 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.settlement.batch.SettlementBatchJobTest
```

Expected: PASS.

- [ ] **Step 5: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/settlement/batch/SettlementBatchConfig.java backend/src/test/java/com/sweet/market/settlement/batch/SettlementBatchJobTest.java
git commit -m "test: cover settlement batch skip behavior"
```

---

## Task 7: 전체 검증과 마무리

**Files:**

- Verify all milestone files listed in File Structure.

- [ ] **Step 1: focused 테스트를 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.auth.AdminSecurityApiTest --tests com.sweet.market.settlement.batch.* --tests com.sweet.market.order.domain.OrderTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: 전체 backend 테스트를 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: 테스트 이름 규칙을 확인한다**

Run:

```powershell
rg -n "void [a-zA-Z0-9]+\(" backend\src\test\java
```

Expected: only helper or lifecycle methods are reported, not new `@Test` methods.

- [ ] **Step 4: 작업트리 상태를 확인한다**

Run:

```powershell
git status --short --branch
```

Expected shape:

```text
## main...origin/main [ahead N]
 M backend/src/main/resources/application.yaml
?? docs/superpowers/handoffs/2026-06-09-milestone-3-handoff.md
```

`backend/src/main/resources/application.yaml` already has local development changes. Stage it only if the diff contains the intentional Spring Batch schema addition and no accidental local secret/DDL churn.

- [ ] **Step 5: 마무리 커밋이 필요한지 확인한다**

If task-by-task commits already contain all milestone changes, no extra commit is needed. If any milestone files are still modified, commit only those files:

```powershell
git add backend/build.gradle backend/src/main/resources/application.yaml backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java backend/src/main/java/com/sweet/market/member/domain/MemberRole.java backend/src/main/java/com/sweet/market/member/domain/Member.java backend/src/main/java/com/sweet/market/auth/application/AuthService.java backend/src/main/java/com/sweet/market/auth/security/AuthenticatedMember.java backend/src/main/java/com/sweet/market/auth/security/JwtProvider.java backend/src/main/java/com/sweet/market/auth/security/JwtAuthenticationFilter.java backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java backend/src/main/java/com/sweet/market/order/domain/Order.java backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java backend/src/main/java/com/sweet/market/settlement/batch backend/src/test/java/com/sweet/market/auth/AdminSecurityApiTest.java backend/src/test/java/com/sweet/market/settlement/batch
git commit -m "feat: add spring batch settlement automation"
```

---

## Self-Review

- Spec coverage: Spring Batch 의존성, metadata 초기화, ADMIN role, JWT role claim, admin API, `confirmedBefore/limit/chunkSize`, `Order.confirmedAt`, `JdbcPagingItemReader`, retry/skip, idempotent 재실행, 보안 테스트, batch 테스트를 모두 task에 배치했다.
- Placeholder scan: 미완성 표식이나 빈 구현 지시를 남기지 않았다.
- Type consistency: `AdminSettlementBatchRequest`, `AdminSettlementBatchResponse`, `AdminSettlementBatchController`, `SettlementBatchConfig`, `SettlementItemProcessor`, `SettlementBatchSkippableException`, `findSettlementTargetById`, `confirmedAt` 이름을 일관되게 사용했다.
- Test naming: 신규 `@Test` 메서드는 모두 Korean_with_underscores 형식이다. Helper method는 camelCase를 유지한다.
