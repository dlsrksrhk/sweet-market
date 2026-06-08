# Milestone 1 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원가입, 로그인, JWT 인증, 공통 예외 응답, 통합 테스트 기반을 갖춘 Sweet Market 백엔드 기반을 만든다.

**Architecture:** Spring Security와 JWT를 초기부터 실제 인증 계층으로 사용한다. 성공 응답과 에러 응답은 공통 형태로 통일하고, 인증된 사용자는 SecurityContext의 principal로 전달한다. 통합 테스트는 Testcontainers PostgreSQL과 MockMvc를 사용해 다른 PC에서도 같은 방식으로 실행되게 한다.

**Tech Stack:** Spring Boot, Spring MVC, Spring Security, Spring Data JPA, PostgreSQL, JJWT, Bean Validation, Lombok, JUnit 5, MockMvc, Testcontainers

---

## 범위

이 계획은 설계 문서의 Milestone 1만 다룬다.

완료 기준:

- 회원가입 API가 동작한다.
- 로그인 API가 JWT access token을 발급한다.
- JWT가 없는 요청은 보호 API에 접근할 수 없다.
- 유효한 JWT가 있는 요청은 보호 API에 접근할 수 있다.
- 검증 실패, 중복 이메일, 로그인 실패가 공통 에러 응답으로 반환된다.
- 통합 테스트는 로컬 PostgreSQL 설치 여부와 무관하게 Docker 기반 Testcontainers로 실행된다.

## 파일 구조

생성 또는 수정할 파일:

```text
backend/build.gradle
backend/src/main/resources/application.yaml

backend/src/main/java/com/sweet/market/common/api/ApiResponse.java
backend/src/main/java/com/sweet/market/common/error/ErrorCode.java
backend/src/main/java/com/sweet/market/common/error/ErrorResponse.java
backend/src/main/java/com/sweet/market/common/error/BusinessException.java
backend/src/main/java/com/sweet/market/common/error/GlobalExceptionHandler.java

backend/src/main/java/com/sweet/market/member/domain/Member.java
backend/src/main/java/com/sweet/market/member/domain/MemberRole.java
backend/src/main/java/com/sweet/market/member/repository/MemberRepository.java
backend/src/main/java/com/sweet/market/member/application/MemberService.java
backend/src/main/java/com/sweet/market/member/api/MemberController.java
backend/src/main/java/com/sweet/market/member/api/MemberMeResponse.java

backend/src/main/java/com/sweet/market/auth/api/AuthController.java
backend/src/main/java/com/sweet/market/auth/api/AuthResponse.java
backend/src/main/java/com/sweet/market/auth/api/LoginRequest.java
backend/src/main/java/com/sweet/market/auth/api/MemberResponse.java
backend/src/main/java/com/sweet/market/auth/api/SignupRequest.java
backend/src/main/java/com/sweet/market/auth/application/AuthService.java
backend/src/main/java/com/sweet/market/auth/security/AuthenticatedMember.java
backend/src/main/java/com/sweet/market/auth/security/JwtAuthenticationFilter.java
backend/src/main/java/com/sweet/market/auth/security/JwtProperties.java
backend/src/main/java/com/sweet/market/auth/security/JwtProvider.java
backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java

backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
backend/src/test/java/com/sweet/market/auth/AuthApiTest.java
backend/src/test/java/com/sweet/market/auth/SecurityApiTest.java
```

책임:

- `common/api`: 성공 응답 래퍼
- `common/error`: 공통 에러 코드, 예외, 전역 예외 처리
- `member/domain`: 회원 엔티티와 권한
- `member/repository`: 회원 JPA repository
- `member/application`: 인증된 회원 조회
- `member/api`: 인증 확인용 내 정보 API
- `auth/api`: 회원가입/로그인 request/response/controller
- `auth/application`: 회원가입/로그인 유스케이스
- `auth/security`: JWT 발급/검증, Security 설정, 인증 필터
- `support`: 통합 테스트 공통 기반

---

## Task 1: 의존성과 설정 추가

**Files:**

- Modify: `backend/build.gradle`
- Modify: `backend/src/main/resources/application.yaml`

- [ ] **Step 1: `build.gradle`에 웹, 보안, 검증, JWT, 테스트 의존성 추가**

`backend/build.gradle`의 `dependencies` 블록을 다음 내용으로 정리한다.

```gradle
dependencies {
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

- [ ] **Step 2: JWT 설정 추가**

`backend/src/main/resources/application.yaml` 하단에 다음 설정을 추가한다.

```yaml
jwt:
  secret: ${JWT_SECRET:sweet-market-local-development-secret-key-32bytes-minimum}
  access-token-validity-seconds: ${JWT_ACCESS_TOKEN_VALIDITY_SECONDS:3600}
```

- [ ] **Step 3: 의존성 해석 확인**

Run:

```powershell
cd backend
.\gradlew.bat dependencies --configuration runtimeClasspath
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: 커밋**

```powershell
git add backend/build.gradle backend/src/main/resources/application.yaml
git commit -m "chore: add web security jwt dependencies"
```

---

## Task 2: 공통 성공 응답과 에러 응답 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/common/api/ApiResponse.java`
- Create: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Create: `backend/src/main/java/com/sweet/market/common/error/ErrorResponse.java`
- Create: `backend/src/main/java/com/sweet/market/common/error/BusinessException.java`
- Create: `backend/src/main/java/com/sweet/market/common/error/GlobalExceptionHandler.java`

- [ ] **Step 1: 공통 응답과 예외 처리 테스트 작성 준비**

이 태스크에서는 먼저 공통 코드만 만든다. API 테스트는 Task 7에서 공통 응답 형태를 검증한다.

- [ ] **Step 2: `ApiResponse` 작성**

```java
package com.sweet.market.common.api;

public record ApiResponse<T>(
        T data
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data);
    }
}
```

- [ ] **Step 3: `ErrorCode` 작성**

```java
package com.sweet.market.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
```

- [ ] **Step 4: `ErrorResponse` 작성**

```java
package com.sweet.market.common.error;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<FieldErrorResponse> fieldErrors
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.message(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorResponse> fieldErrors) {
        return new ErrorResponse(errorCode.name(), errorCode.message(), fieldErrors);
    }

    public record FieldErrorResponse(
            String field,
            String message
    ) {
    }
}
```

- [ ] **Step 5: `BusinessException` 작성**

```java
package com.sweet.market.common.error;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
```

- [ ] **Step 6: `GlobalExceptionHandler` 작성**

```java
package com.sweet.market.common.error;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity
                .status(errorCode.status())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        List<ErrorResponse.FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldErrorResponse)
                .toList();

        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.status())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, fieldErrors));
    }

    private ErrorResponse.FieldErrorResponse toFieldErrorResponse(FieldError fieldError) {
        return new ErrorResponse.FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
```

- [ ] **Step 7: 컴파일 확인**

Run:

```powershell
cd backend
.\gradlew.bat compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: 커밋**

```powershell
git add backend/src/main/java/com/sweet/market/common
git commit -m "feat: add common api error responses"
```

---

## Task 3: 회원 도메인과 repository 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/member/domain/MemberRole.java`
- Create: `backend/src/main/java/com/sweet/market/member/domain/Member.java`
- Create: `backend/src/main/java/com/sweet/market/member/repository/MemberRepository.java`

- [ ] **Step 1: `MemberRole` 작성**

```java
package com.sweet.market.member.domain;

public enum MemberRole {
    MEMBER
}
```

- [ ] **Step 2: `Member` 엔티티 작성**

```java
package com.sweet.market.member.domain;

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
        return new Member(email, encodedPassword, nickname, MemberRole.MEMBER);
    }
}
```

- [ ] **Step 3: `MemberRepository` 작성**

```java
package com.sweet.market.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    Optional<Member> findByEmail(String email);
}
```

- [ ] **Step 4: 컴파일 확인**

Run:

```powershell
cd backend
.\gradlew.bat compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: 커밋**

```powershell
git add backend/src/main/java/com/sweet/market/member
git commit -m "feat: add member domain"
```

---

## Task 4: JWT provider와 Security 설정 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/auth/security/AuthenticatedMember.java`
- Create: `backend/src/main/java/com/sweet/market/auth/security/JwtProperties.java`
- Create: `backend/src/main/java/com/sweet/market/auth/security/JwtProvider.java`
- Create: `backend/src/main/java/com/sweet/market/auth/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java`

- [ ] **Step 1: `AuthenticatedMember` 작성**

```java
package com.sweet.market.auth.security;

public record AuthenticatedMember(
        Long id,
        String email
) {
}
```

- [ ] **Step 2: `JwtProperties` 작성**

```java
package com.sweet.market.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValiditySeconds
) {
}
```

- [ ] **Step 3: `JwtProvider` 작성**

```java
package com.sweet.market.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

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

    public String createAccessToken(Long memberId, String email) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenValiditySeconds);

        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("email", email)
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
            return new AuthenticatedMember(memberId, email);
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

- [ ] **Step 4: `JwtAuthenticationFilter` 작성**

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

        if (token != null) {
            authenticate(token);
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
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
```

- [ ] **Step 5: `SecurityConfig` 작성**

```java
package com.sweet.market.auth.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 6: 컴파일 확인**

Run:

```powershell
cd backend
.\gradlew.bat compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: 커밋**

```powershell
git add backend/src/main/java/com/sweet/market/auth/security
git commit -m "feat: add jwt security"
```

---

## Task 5: 회원가입/로그인 API 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/auth/api/SignupRequest.java`
- Create: `backend/src/main/java/com/sweet/market/auth/api/LoginRequest.java`
- Create: `backend/src/main/java/com/sweet/market/auth/api/MemberResponse.java`
- Create: `backend/src/main/java/com/sweet/market/auth/api/AuthResponse.java`
- Create: `backend/src/main/java/com/sweet/market/auth/application/AuthService.java`
- Create: `backend/src/main/java/com/sweet/market/auth/api/AuthController.java`

- [ ] **Step 1: `SignupRequest` 작성**

```java
package com.sweet.market.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 120, message = "이메일은 120자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 30, message = "닉네임은 30자 이하여야 합니다.")
        String nickname
) {
}
```

- [ ] **Step 2: `LoginRequest` 작성**

```java
package com.sweet.market.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
```

- [ ] **Step 3: `MemberResponse` 작성**

```java
package com.sweet.market.auth.api;

import com.sweet.market.member.domain.Member;

public record MemberResponse(
        Long id,
        String email,
        String nickname
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getNickname());
    }
}
```

- [ ] **Step 4: `AuthResponse` 작성**

```java
package com.sweet.market.auth.api;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        MemberResponse member
) {

    public static AuthResponse bearer(String accessToken, long expiresIn, MemberResponse member) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, member);
    }
}
```

- [ ] **Step 5: `AuthService` 작성**

```java
package com.sweet.market.auth.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.auth.api.AuthResponse;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.MemberResponse;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        Member member = Member.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname()
        );

        Member savedMember = memberRepository.save(member);
        return MemberResponse.from(savedMember);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }

        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getEmail());
        return AuthResponse.bearer(
                accessToken,
                jwtProvider.accessTokenValiditySeconds(),
                MemberResponse.from(member)
        );
    }
}
```

- [ ] **Step 6: `AuthController` 작성**

```java
package com.sweet.market.auth.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.application.AuthService;
import com.sweet.market.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
```

- [ ] **Step 7: 컴파일 확인**

Run:

```powershell
cd backend
.\gradlew.bat compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: 커밋**

```powershell
git add backend/src/main/java/com/sweet/market/auth
git commit -m "feat: add signup login api"
```

---

## Task 6: 인증된 회원 조회 API 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/member/api/MemberMeResponse.java`
- Create: `backend/src/main/java/com/sweet/market/member/application/MemberService.java`
- Create: `backend/src/main/java/com/sweet/market/member/api/MemberController.java`

- [ ] **Step 1: `MemberMeResponse` 작성**

```java
package com.sweet.market.member.api;

import com.sweet.market.member.domain.Member;

public record MemberMeResponse(
        Long id,
        String email,
        String nickname
) {

    public static MemberMeResponse from(Member member) {
        return new MemberMeResponse(member.getId(), member.getEmail(), member.getNickname());
    }
}
```

- [ ] **Step 2: `MemberService` 작성**

```java
package com.sweet.market.member.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.api.MemberMeResponse;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public MemberMeResponse findMe(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return MemberMeResponse.from(member);
    }
}
```

- [ ] **Step 3: `MemberController` 작성**

```java
package com.sweet.market.member.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.member.application.MemberService;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
    public ApiResponse<MemberMeResponse> me(Authentication authentication) {
        AuthenticatedMember authenticatedMember = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(memberService.findMe(authenticatedMember.id()));
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run:

```powershell
cd backend
.\gradlew.bat compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: 커밋**

```powershell
git add backend/src/main/java/com/sweet/market/member
git commit -m "feat: add current member api"
```

---

## Task 7: 통합 테스트 기반과 회원가입/로그인 테스트 추가

**Files:**

- Create: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`
- Create: `backend/src/test/java/com/sweet/market/auth/AuthApiTest.java`

- [ ] **Step 1: `IntegrationTestSupport` 작성**

```java
package com.sweet.market.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.member.repository.MemberRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("market_test")
            .withUsername("market")
            .withPassword("market");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MemberRepository memberRepository;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @AfterEach
    void cleanDatabase() {
        memberRepository.deleteAll();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
```

- [ ] **Step 2: `AuthApiTest` 작성**

```java
package com.sweet.market.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class AuthApiTest extends IntegrationTestSupport {

    @Test
    void 회원가입에_성공한다() throws Exception {
        SignupRequest request = new SignupRequest("seller@example.com", "password123", "seller");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.email").value("seller@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("seller"));
    }

    @Test
    void 중복_이메일로_회원가입하면_실패한다() throws Exception {
        SignupRequest request = new SignupRequest("seller@example.com", "password123", "seller");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
    }

    @Test
    void 잘못된_회원가입_요청은_검증_오류를_반환한다() throws Exception {
        SignupRequest request = new SignupRequest("not-email", "short", "");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void 로그인에_성공하면_JWT를_발급한다() throws Exception {
        SignupRequest signupRequest = new SignupRequest("buyer@example.com", "password123", "buyer");
        LoginRequest loginRequest = new LoginRequest("buyer@example.com", "password123");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.member.email").value("buyer@example.com"));
    }

    @Test
    void 비밀번호가_틀리면_로그인에_실패한다() throws Exception {
        SignupRequest signupRequest = new SignupRequest("buyer@example.com", "password123", "buyer");
        LoginRequest loginRequest = new LoginRequest("buyer@example.com", "wrong-password");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
    }
}
```

- [ ] **Step 3: 테스트 실행**

Run:

```powershell
cd backend
.\gradlew.bat test --tests com.sweet.market.auth.AuthApiTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: 커밋**

```powershell
git add backend/src/test/java/com/sweet/market/support backend/src/test/java/com/sweet/market/auth/AuthApiTest.java
git commit -m "test: add auth api integration tests"
```

---

## Task 8: 보호 API 접근 테스트 추가

**Files:**

- Create: `backend/src/test/java/com/sweet/market/auth/SecurityApiTest.java`

- [ ] **Step 1: `SecurityApiTest` 작성**

```java
package com.sweet.market.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
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
import com.sweet.market.support.IntegrationTestSupport;

class SecurityApiTest extends IntegrationTestSupport {

    @Test
    void JWT가_없으면_내_정보_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 유효한_JWT가_있으면_내_정보_API에_접근할_수_있다() throws Exception {
        String accessToken = signupAndLogin("buyer@example.com", "password123", "buyer");

        mockMvc.perform(get("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.email").value("buyer@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("buyer"));
    }

    @Test
    void 유효하지_않은_JWT가_있으면_내_정보_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isForbidden());
    }

    private String signupAndLogin(String email, String password, String nickname) throws Exception {
        SignupRequest signupRequest = new SignupRequest(email, password, nickname);
        LoginRequest loginRequest = new LoginRequest(email, password);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupRequest)))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginRequest)))
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

- [ ] **Step 2: 보호 API 테스트 실행**

Run:

```powershell
cd backend
.\gradlew.bat test --tests com.sweet.market.auth.SecurityApiTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 전체 테스트 실행**

Run:

```powershell
cd backend
.\gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: 커밋**

```powershell
git add backend/src/test/java/com/sweet/market/auth/SecurityApiTest.java
git commit -m "test: verify jwt protected api access"
```

---

## Task 9: 인증 실패 응답 정리

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java`

- [ ] **Step 1: 인증 실패 응답을 JSON으로 반환하도록 `SecurityConfig` 수정**

`SecurityConfig`에 필요한 import를 추가한다.

```java
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.common.error.ErrorResponse;
```

`securityFilterChain` 메서드 시그니처에 `ObjectMapper`를 추가한다.

```java
public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        ObjectMapper objectMapper
) throws Exception {
```

`build()` 호출 전 다음 설정을 추가한다.

```java
.exceptionHandling(exception -> exception
        .authenticationEntryPoint((request, response, authException) -> {
            response.setStatus(ErrorCode.AUTHENTICATION_FAILED.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.AUTHENTICATION_FAILED));
        })
        .accessDeniedHandler((request, response, accessDeniedException) -> {
            response.setStatus(ErrorCode.ACCESS_DENIED.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.ACCESS_DENIED));
        })
)
```

수정 후 `SecurityConfig`의 핵심 체인은 다음 흐름을 유지한다.

```java
return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
        )
        .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(ErrorCode.AUTHENTICATION_FAILED.status().value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.AUTHENTICATION_FAILED));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(ErrorCode.ACCESS_DENIED.status().value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.ACCESS_DENIED));
                })
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
```

- [ ] **Step 2: `SecurityApiTest` 기대값을 JSON 에러 응답으로 조정**

`JWT가_없으면_내_정보_API에_접근할_수_없다` 테스트의 기대값을 다음으로 맞춘다.

```java
mockMvc.perform(get("/api/members/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
```

`유효하지_않은_JWT가_있으면_내_정보_API에_접근할_수_없다` 테스트의 기대값을 다음으로 맞춘다.

```java
mockMvc.perform(get("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
```

- [ ] **Step 3: JWT filter가 invalid token에서 SecurityContext를 비우고 다음 filter로 넘기도록 수정**

`JwtAuthenticationFilter`의 `doFilterInternal`에서 invalid token 예외를 다음처럼 처리한다.

```java
try {
    if (token != null) {
        authenticate(token);
    }
} catch (JwtProvider.InvalidJwtException exception) {
    SecurityContextHolder.clearContext();
}
```

그 뒤 기존처럼 `filterChain.doFilter(request, response);`를 호출한다.

- [ ] **Step 4: 전체 테스트 실행**

Run:

```powershell
cd backend
.\gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: 커밋**

```powershell
git add backend/src/main/java/com/sweet/market/auth/security backend/src/test/java/com/sweet/market/auth/SecurityApiTest.java
git commit -m "feat: return json auth errors"
```

---

## Task 10: Milestone 1 최종 검증

**Files:**

- Verify only

- [ ] **Step 1: Docker Desktop 실행 상태 확인**

Run:

```powershell
docker version --format '{{.Server.Version}}'
```

Expected:

```text
28.1.1
```

서버 버전 숫자는 PC마다 다를 수 있다. 명령이 성공하면 충분하다.

- [ ] **Step 2: 전체 테스트 실행**

Run:

```powershell
cd backend
.\gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 애플리케이션 로컬 실행 확인**

Run:

```powershell
cd backend
docker compose up -d
.\gradlew.bat bootRun
```

Expected:

```text
Started MarketApplication
```

- [ ] **Step 4: 수동 API 확인**

다른 터미널에서 실행한다.

```powershell
$signupBody = @{
  email = "seller@example.com"
  password = "password123"
  nickname = "seller"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/signup" `
  -ContentType "application/json" `
  -Body $signupBody
```

Expected:

```text
data
----
@{id=1; email=seller@example.com; nickname=seller}
```

로그인 확인:

```powershell
$loginBody = @{
  email = "seller@example.com"
  password = "password123"
} | ConvertTo-Json

$loginResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body $loginBody

$loginResponse.data.accessToken
```

Expected:

```text
eyJ...
```

보호 API 확인:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/members/me" `
  -Headers @{ Authorization = "Bearer $($loginResponse.data.accessToken)" }
```

Expected:

```text
data
----
@{id=1; email=seller@example.com; nickname=seller}
```

- [ ] **Step 5: 최종 커밋 상태 확인**

Run:

```powershell
git status --short --branch
```

Expected:

```text
## main...origin/main
```

- [ ] **Step 6: 원격 push**

Run:

```powershell
git push
```

Expected:

```text
Everything up-to-date
```

이미 push할 커밋이 있으면 다음 형태가 나온다.

```text
main -> main
```

---

## Self-Review

Spec coverage:

- 공통 응답/예외 구조: Task 2, Task 9
- Spring Security + JWT: Task 1, Task 4, Task 9
- 회원가입/로그인: Task 3, Task 5, Task 7
- 테스트 fixture 구조: Task 7
- API 통합 테스트 기본 구조: Task 7, Task 8
- 인증된 API 호출: Task 6, Task 8, Task 10

Placeholder scan:

- 계획서에는 빈칸으로 남겨둔 항목이 없다.
- 모든 코드 변경 단계에는 구체적인 파일과 코드 조각을 포함한다.
- 모든 테스트 실행 단계에는 명령과 기대 결과를 포함한다.

Type consistency:

- `AuthenticatedMember.id()`는 `MemberController`에서 사용한다.
- `JwtProvider.InvalidJwtException`은 `JwtAuthenticationFilter`에서 사용한다.
- `ErrorCode` 이름은 테스트의 JSON path 기대값과 일치한다.
- `ApiResponse`의 성공 응답 형태는 테스트의 `$.data` 기대값과 일치한다.
