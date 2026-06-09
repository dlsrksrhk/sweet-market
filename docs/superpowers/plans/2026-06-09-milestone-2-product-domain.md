# Milestone 2 Product Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 판매자가 상품과 상품 이미지를 등록, 수정, 숨김 처리할 수 있고 구매자가 판매 중인 상품 목록과 상세를 조회할 수 있는 상품 도메인을 만든다.

**Architecture:** `product` 패키지는 Milestone 1과 같은 `api/application/domain/repository/query` 구조를 따른다. 쓰기 흐름은 `ProductService`가 `Product` aggregate를 변경하고, 조회 흐름은 `ProductQueryService`가 응답 DTO를 조립한다. 상품 이미지는 `Product`의 자식 엔티티로 두고 `cascade = ALL`, `orphanRemoval = true`를 사용해 JPA 생명주기 실험까지 연결한다.

**Tech Stack:** Spring Boot, Spring MVC, Spring Security, Spring Data JPA, PostgreSQL, Bean Validation, Lombok, JUnit 5, MockMvc, Testcontainers

---

## Scope

이 계획은 설계 문서의 Milestone 2만 다룬다.

완료 기준:

- 인증된 판매자는 상품을 등록할 수 있다.
- 상품 등록 시 이미지 URL을 함께 추가할 수 있다.
- 상품 소유자만 상품을 수정하거나 숨김 처리할 수 있다.
- 상품 소유자만 상품 이미지를 추가하거나 삭제할 수 있다.
- 구매자는 `ON_SALE` 상품 목록과 상세를 조회할 수 있다.
- 숨김 처리된 상품은 목록과 공개 상세 조회에서 제외된다.
- 상품 이미지의 cascade persist와 orphan removal 동작을 학습 테스트로 확인한다.
- 전체 backend 테스트가 통과한다.
- 로컬 `bootRun` 실행 시 `JWT_SECRET` 환경변수를 반드시 설정한다.

Out of scope:

- 주문 생성과 상품 예약 상태 전이
- 결제, 배송, 정산
- 상품 검색, 정렬 옵션, 카테고리, 찜
- 실제 파일 업로드

## File Structure

생성 또는 수정할 파일:

```text
backend/src/main/java/com/sweet/market/common/error/ErrorCode.java

backend/src/main/java/com/sweet/market/product/domain/Product.java
backend/src/main/java/com/sweet/market/product/domain/ProductImage.java
backend/src/main/java/com/sweet/market/product/domain/ProductStatus.java
backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java
backend/src/main/java/com/sweet/market/product/repository/ProductImageRepository.java

backend/src/main/java/com/sweet/market/product/api/ProductController.java
backend/src/main/java/com/sweet/market/product/api/ProductCreateRequest.java
backend/src/main/java/com/sweet/market/product/api/ProductUpdateRequest.java
backend/src/main/java/com/sweet/market/product/api/ProductImageAddRequest.java
backend/src/main/java/com/sweet/market/product/api/ProductResponse.java
backend/src/main/java/com/sweet/market/product/api/ProductSummaryResponse.java
backend/src/main/java/com/sweet/market/product/api/ProductImageResponse.java

backend/src/main/java/com/sweet/market/product/application/ProductService.java
backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java

backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java

backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
backend/src/test/java/com/sweet/market/product/domain/ProductTest.java
backend/src/test/java/com/sweet/market/product/ProductApiTest.java
backend/src/test/java/com/sweet/market/jpalab/CascadeOrphanRemovalTest.java
```

책임:

- `product/domain`: 상품 aggregate, 이미지 자식 엔티티, 상태 전이
- `product/repository`: 쓰기/조회에 필요한 JPA repository
- `product/application`: 인증 사용자 기준 상품 등록, 수정, 숨김, 이미지 추가/삭제
- `product/query`: 공개 상품 목록/상세 조회 DTO 조립
- `product/api`: HTTP request/response와 controller
- `jpalab`: cascade persist와 orphan removal 관찰 테스트

---

## Task 1: 상품 에러 코드와 테스트 정리 기반 추가

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`

- [ ] **Step 1: 상품 에러 코드를 추가한다**

`backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`를 다음처럼 수정한다.

```java
package com.sweet.market.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    PRODUCT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "상품에 대한 권한이 없습니다."),
    PRODUCT_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 이미지를 찾을 수 없습니다.");

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

- [ ] **Step 2: 컴파일을 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat compileTestJava
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/common/error/ErrorCode.java
git commit -m "chore: prepare product error codes"
```

---

## Task 2: 상품 aggregate와 repository 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/product/domain/ProductStatus.java`
- Create: `backend/src/main/java/com/sweet/market/product/domain/ProductImage.java`
- Create: `backend/src/main/java/com/sweet/market/product/domain/Product.java`
- Create: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Create: `backend/src/main/java/com/sweet/market/product/repository/ProductImageRepository.java`
- Create: `backend/src/test/java/com/sweet/market/product/domain/ProductTest.java`
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`

- [ ] **Step 1: 상품 도메인 테스트를 먼저 작성한다**

`backend/src/test/java/com/sweet/market/product/domain/ProductTest.java`를 생성한다.

```java
package com.sweet.market.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.member.domain.Member;

class ProductTest {

    @Test
    void createProductWithImages() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");

        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.addImage("https://example.com/macbook-1.jpg");
        product.addImage("https://example.com/macbook-2.jpg");

        assertThat(product.getSeller()).isSameAs(seller);
        assertThat(product.getTitle()).isEqualTo("MacBook Pro");
        assertThat(product.getDescription()).isEqualTo("M3 laptop");
        assertThat(product.getPrice()).isEqualTo(2_000_000L);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product.getImages()).hasSize(2);
        assertThat(product.getImages()).extracting(ProductImage::getImageUrl)
                .containsExactly("https://example.com/macbook-1.jpg", "https://example.com/macbook-2.jpg");
    }

    @Test
    void updateProduct() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        product.update("iPhone", "15 Pro", 1_200_000L);

        assertThat(product.getTitle()).isEqualTo("iPhone");
        assertThat(product.getDescription()).isEqualTo("15 Pro");
        assertThat(product.getPrice()).isEqualTo(1_200_000L);
    }

    @Test
    void hideProduct() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        product.hide();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    void removeImageByIdFailsWhenImageDoesNotExist() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.addImage("https://example.com/macbook-1.jpg");

        assertThatThrownBy(() -> product.removeImage(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product image not found: 999");
    }
}
```

- [ ] **Step 2: 실패하는 테스트를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.domain.ProductTest
```

Expected:

```text
Compilation failed
```

`Product`, `ProductImage`, `ProductStatus`가 아직 없어서 실패한다.

- [ ] **Step 3: `ProductStatus`를 작성한다**

`backend/src/main/java/com/sweet/market/product/domain/ProductStatus.java`를 생성한다.

```java
package com.sweet.market.product.domain;

public enum ProductStatus {
    ON_SALE,
    RESERVED,
    SOLD_OUT,
    HIDDEN
}
```

- [ ] **Step 4: `ProductImage`를 작성한다**

`backend/src/main/java/com/sweet/market/product/domain/ProductImage.java`를 생성한다.

```java
package com.sweet.market.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "product_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    private ProductImage(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public static ProductImage create(String imageUrl) {
        return new ProductImage(imageUrl);
    }

    void assignProduct(Product product) {
        this.product = product;
    }
}
```

- [ ] **Step 5: `Product`를 작성한다**

`backend/src/main/java/com/sweet/market/product/domain/Product.java`를 생성한다.

```java
package com.sweet.market.product.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sweet.market.member.domain.Member;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    private Product(Member seller, String title, String description, long price, ProductStatus status) {
        this.seller = seller;
        this.title = title;
        this.description = description;
        this.price = price;
        this.status = status;
    }

    public static Product create(Member seller, String title, String description, long price) {
        return new Product(seller, title, description, price, ProductStatus.ON_SALE);
    }

    public void update(String title, String description, long price) {
        this.title = title;
        this.description = description;
        this.price = price;
    }

    public void hide() {
        this.status = ProductStatus.HIDDEN;
    }

    public boolean isOwnedBy(Long memberId) {
        return seller.getId().equals(memberId);
    }

    public List<ProductImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    public ProductImage addImage(String imageUrl) {
        ProductImage image = ProductImage.create(imageUrl);
        image.assignProduct(this);
        images.add(image);
        return image;
    }

    public void removeImage(Long imageId) {
        boolean removed = images.removeIf(image -> imageId.equals(image.getId()));
        if (!removed) {
            throw new IllegalArgumentException("Product image not found: " + imageId);
        }
    }
}
```

- [ ] **Step 6: repository를 작성한다**

`backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`를 생성한다.

```java
package com.sweet.market.product.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"seller", "images"})
    Optional<Product> findWithSellerAndImagesById(Long id);

    @EntityGraph(attributePaths = {"seller", "images"})
    Optional<Product> findWithSellerAndImagesByIdAndStatus(Long id, ProductStatus status);

    @EntityGraph(attributePaths = {"seller", "images"})
    Page<Product> findByStatusOrderByIdDesc(ProductStatus status, Pageable pageable);
}
```

`backend/src/main/java/com/sweet/market/product/repository/ProductImageRepository.java`를 생성한다.

```java
package com.sweet.market.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.product.domain.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
}
```

- [ ] **Step 7: 통합 테스트 DB cleanup 대상에 상품 테이블을 추가한다**

`backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`의 `cleanUp()` 메서드를 다음처럼 수정한다.

```java
@AfterEach
void cleanUp() {
    jdbcTemplate.execute("TRUNCATE TABLE product_images, products, members RESTART IDENTITY CASCADE");
}
```

- [ ] **Step 8: 도메인 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.domain.ProductTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 9: 전체 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 10: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/product backend/src/test/java/com/sweet/market/product backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
git commit -m "feat: add product aggregate"
```

---

## Task 3: 상품 등록 API 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/product/api/ProductCreateRequest.java`
- Create: `backend/src/main/java/com/sweet/market/product/api/ProductImageResponse.java`
- Create: `backend/src/main/java/com/sweet/market/product/api/ProductResponse.java`
- Create: `backend/src/main/java/com/sweet/market/product/api/ProductController.java`
- Create: `backend/src/main/java/com/sweet/market/product/application/ProductService.java`
- Create: `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`

- [ ] **Step 1: 상품 등록 API 통합 테스트를 작성한다**

`backend/src/test/java/com/sweet/market/product/ProductApiTest.java`를 생성한다.

```java
package com.sweet.market.product;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
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

class ProductApiTest extends IntegrationTestSupport {

    @Test
    void createProductSucceeds() throws Exception {
        String accessToken = signupAndLogin("seller@example.com", "password123", "seller");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "imageUrls": [
                                    "https://example.com/macbook-1.jpg",
                                    "https://example.com/macbook-2.jpg"
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.sellerId").value(1))
                .andExpect(jsonPath("$.data.sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.title").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.description").value("M3 laptop"))
                .andExpect(jsonPath("$.data.price").value(2000000))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.images", hasSize(2)))
                .andExpect(jsonPath("$.data.images[0].imageUrl").value("https://example.com/macbook-1.jpg"))
                .andExpect(jsonPath("$.data.images[1].imageUrl").value("https://example.com/macbook-2.jpg"));
    }

    @Test
    void createProductRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "imageUrls": []
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void createProductValidationFails() throws Exception {
        String accessToken = signupAndLogin("seller@example.com", "password123", "seller");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "description": "",
                                  "price": 0,
                                  "imageUrls": ["not-url"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
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

- [ ] **Step 2: 실패하는 테스트를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.ProductApiTest
```

Expected:

```text
404 Not Found
```

`/api/products` controller가 아직 없어서 실패한다.

- [ ] **Step 3: 상품 생성 request를 작성한다**

`backend/src/main/java/com/sweet/market/product/api/ProductCreateRequest.java`를 생성한다.

```java
package com.sweet.market.product.api;

import java.util.List;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotBlank
        @Size(max = 100)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @Positive
        long price,

        @NotNull
        @Size(max = 10)
        List<@NotBlank @URL @Size(max = 500) String> imageUrls
) {
}
```

- [ ] **Step 4: 상품 response DTO를 작성한다**

`backend/src/main/java/com/sweet/market/product/api/ProductImageResponse.java`를 생성한다.

```java
package com.sweet.market.product.api;

import com.sweet.market.product.domain.ProductImage;

public record ProductImageResponse(
        Long id,
        String imageUrl
) {

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getImageUrl());
    }
}
```

`backend/src/main/java/com/sweet/market/product/api/ProductResponse.java`를 생성한다.

```java
package com.sweet.market.product.api;

import java.util.List;

import com.sweet.market.product.domain.Product;

public record ProductResponse(
        Long id,
        Long sellerId,
        String sellerNickname,
        String title,
        String description,
        long price,
        String status,
        List<ProductImageResponse> images
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getNickname(),
                product.getTitle(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus().name(),
                product.getImages().stream()
                        .map(ProductImageResponse::from)
                        .toList()
        );
    }
}
```

- [ ] **Step 5: `ProductService`의 등록 기능을 작성한다**

`backend/src/main/java/com/sweet/market/product/application/ProductService.java`를 생성한다.

```java
package com.sweet.market.product.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.api.ProductCreateRequest;
import com.sweet.market.product.api.ProductResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    public ProductService(ProductRepository productRepository, MemberRepository memberRepository) {
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public ProductResponse create(Long sellerId, ProductCreateRequest request) {
        Member seller = memberRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Product product = Product.create(seller, request.title(), request.description(), request.price());
        request.imageUrls().forEach(product::addImage);

        Product savedProduct = productRepository.save(product);
        return ProductResponse.from(savedProduct);
    }
}
```

- [ ] **Step 6: `ProductController`의 등록 API를 작성한다**

`backend/src/main/java/com/sweet/market/product/api/ProductController.java`를 생성한다.

```java
package com.sweet.market.product.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.product.application.ProductService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(
            Authentication authentication,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productService.create(member.id(), request));
    }
}
```

- [ ] **Step 7: 상품 등록 API 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.ProductApiTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: 전체 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 9: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/product backend/src/test/java/com/sweet/market/product/ProductApiTest.java
git commit -m "feat: add product creation api"
```

---

## Task 4: 상품 수정과 숨김 API 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/product/api/ProductUpdateRequest.java`
- Modify: `backend/src/main/java/com/sweet/market/product/application/ProductService.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductController.java`
- Modify: `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`

- [ ] **Step 1: 상품 수정과 숨김 테스트를 추가한다**

`backend/src/test/java/com/sweet/market/product/ProductApiTest.java`에 import를 추가한다.

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
```

테스트 클래스에 다음 테스트를 추가한다.

```java
@Test
void updateProductSucceedsForOwner() throws Exception {
    String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
    Long productId = createProduct(accessToken);

    mockMvc.perform(patch("/api/products/{productId}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "iPhone 15 Pro",
                              "description": "Natural titanium",
                              "price": 1200000
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(productId))
            .andExpect(jsonPath("$.data.title").value("iPhone 15 Pro"))
            .andExpect(jsonPath("$.data.description").value("Natural titanium"))
            .andExpect(jsonPath("$.data.price").value(1200000));
}

@Test
void updateProductFailsForNonOwner() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String otherToken = signupAndLogin("other@example.com", "password123", "other");
    Long productId = createProduct(sellerToken);

    mockMvc.perform(patch("/api/products/{productId}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "iPhone 15 Pro",
                              "description": "Natural titanium",
                              "price": 1200000
                            }
                            """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));
}

@Test
void hideProductSucceedsForOwner() throws Exception {
    String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
    Long productId = createProduct(accessToken);

    mockMvc.perform(delete("/api/products/{productId}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(productId))
            .andExpect(jsonPath("$.data.status").value("HIDDEN"));
}

@Test
void hideProductFailsForNonOwner() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String otherToken = signupAndLogin("other@example.com", "password123", "other");
    Long productId = createProduct(sellerToken);

    mockMvc.perform(delete("/api/products/{productId}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));
}
```

테스트 클래스 하단에 helper를 추가한다.

```java
private Long createProduct(String accessToken) throws Exception {
    String response = mockMvc.perform(post("/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "MacBook Pro",
                              "description": "M3 laptop",
                              "price": 2000000,
                              "imageUrls": [
                                "https://example.com/macbook-1.jpg"
                              ]
                            }
                            """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("id").asLong();
}
```

- [ ] **Step 2: 실패하는 테스트를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.ProductApiTest
```

Expected:

```text
405 Method Not Allowed
```

`PATCH /api/products/{productId}`와 `DELETE /api/products/{productId}`가 아직 없어서 실패한다.

- [ ] **Step 3: 상품 수정 request를 작성한다**

`backend/src/main/java/com/sweet/market/product/api/ProductUpdateRequest.java`를 생성한다.

```java
package com.sweet.market.product.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @Positive
        long price
) {
}
```

- [ ] **Step 4: `ProductService`에 수정과 숨김 기능을 추가한다**

`backend/src/main/java/com/sweet/market/product/application/ProductService.java`를 다음 내용으로 교체한다.

```java
package com.sweet.market.product.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.api.ProductCreateRequest;
import com.sweet.market.product.api.ProductResponse;
import com.sweet.market.product.api.ProductUpdateRequest;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    public ProductService(ProductRepository productRepository, MemberRepository memberRepository) {
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public ProductResponse create(Long sellerId, ProductCreateRequest request) {
        Member seller = memberRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Product product = Product.create(seller, request.title(), request.description(), request.price());
        request.imageUrls().forEach(product::addImage);

        Product savedProduct = productRepository.save(product);
        return ProductResponse.from(savedProduct);
    }

    @Transactional
    public ProductResponse update(Long sellerId, Long productId, ProductUpdateRequest request) {
        Product product = findProductForOwner(sellerId, productId);
        product.update(request.title(), request.description(), request.price());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse hide(Long sellerId, Long productId) {
        Product product = findProductForOwner(sellerId, productId);
        product.hide();
        return ProductResponse.from(product);
    }

    private Product findProductForOwner(Long sellerId, Long productId) {
        Product product = productRepository.findWithSellerAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(sellerId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
        return product;
    }
}
```

- [ ] **Step 5: `ProductController`에 수정과 숨김 endpoint를 추가한다**

`backend/src/main/java/com/sweet/market/product/api/ProductController.java`를 다음 내용으로 교체한다.

```java
package com.sweet.market.product.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.product.application.ProductService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(
            Authentication authentication,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productService.create(member.id(), request));
    }

    @PatchMapping("/{productId}")
    public ApiResponse<ProductResponse> update(
            Authentication authentication,
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productService.update(member.id(), productId, request));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<ProductResponse> hide(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productService.hide(member.id(), productId));
    }
}
```

- [ ] **Step 6: 상품 수정과 숨김 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.ProductApiTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: 전체 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/product backend/src/test/java/com/sweet/market/product/ProductApiTest.java
git commit -m "feat: add product update and hide api"
```

---

## Task 5: 공개 상품 목록과 상세 조회 API 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/product/api/ProductSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductController.java`
- Modify: `backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java`
- Modify: `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`

- [ ] **Step 1: 공개 조회 테스트를 추가한다**

`backend/src/test/java/com/sweet/market/product/ProductApiTest.java`에 import를 추가한다.

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
```

테스트 클래스에 다음 테스트를 추가한다.

```java
@Test
void listOnSaleProductsWithoutJwt() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    createProduct(sellerToken);

    mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", hasSize(1)))
            .andExpect(jsonPath("$.data.content[0].title").value("MacBook Pro"))
            .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"))
            .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value("https://example.com/macbook-1.jpg"));
}

@Test
void getOnSaleProductWithoutJwt() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    Long productId = createProduct(sellerToken);

    mockMvc.perform(get("/api/products/{productId}", productId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(productId))
            .andExpect(jsonPath("$.data.title").value("MacBook Pro"))
            .andExpect(jsonPath("$.data.images", hasSize(1)));
}

@Test
void hiddenProductIsExcludedFromPublicListAndDetail() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    Long productId = createProduct(sellerToken);

    mockMvc.perform(delete("/api/products/{productId}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", hasSize(0)));

    mockMvc.perform(get("/api/products/{productId}", productId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
}
```

- [ ] **Step 2: 실패하는 테스트를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.ProductApiTest
```

Expected:

```text
401 Unauthorized
```

`GET /api/products`가 아직 공개 허용되지 않았고 controller endpoint도 없어서 실패한다.

- [ ] **Step 3: 상품 요약 response를 작성한다**

`backend/src/main/java/com/sweet/market/product/api/ProductSummaryResponse.java`를 생성한다.

```java
package com.sweet.market.product.api;

import com.sweet.market.product.domain.Product;

public record ProductSummaryResponse(
        Long id,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        String status,
        String thumbnailUrl
) {

    public static ProductSummaryResponse from(Product product) {
        String thumbnailUrl = product.getImages().isEmpty()
                ? null
                : product.getImages().get(0).getImageUrl();

        return new ProductSummaryResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getNickname(),
                product.getTitle(),
                product.getPrice(),
                product.getStatus().name(),
                thumbnailUrl
        );
    }
}
```

- [ ] **Step 4: `ProductQueryService`를 작성한다**

`backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java`를 생성한다.

```java
package com.sweet.market.product.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.api.ProductResponse;
import com.sweet.market.product.api.ProductSummaryResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class ProductQueryService {

    private final ProductRepository productRepository;

    public ProductQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findOnSaleProducts(Pageable pageable) {
        return productRepository.findByStatusOrderByIdDesc(ProductStatus.ON_SALE, pageable)
                .map(ProductSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse findOnSaleProduct(Long productId) {
        Product product = productRepository.findWithSellerAndImagesByIdAndStatus(productId, ProductStatus.ON_SALE)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductResponse.from(product);
    }
}
```

- [ ] **Step 5: `ProductController`에 조회 endpoint를 추가한다**

`backend/src/main/java/com/sweet/market/product/api/ProductController.java`를 다음 내용으로 교체한다.

```java
package com.sweet.market.product.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.product.application.ProductService;
import com.sweet.market.product.query.ProductQueryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ProductQueryService productQueryService;

    public ProductController(ProductService productService, ProductQueryService productQueryService) {
        this.productService = productService;
        this.productQueryService = productQueryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(
            Authentication authentication,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productService.create(member.id(), request));
    }

    @GetMapping
    public ApiResponse<Page<ProductSummaryResponse>> list(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(productQueryService.findOnSaleProducts(pageable));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> get(@PathVariable Long productId) {
        return ApiResponse.ok(productQueryService.findOnSaleProduct(productId));
    }

    @PatchMapping("/{productId}")
    public ApiResponse<ProductResponse> update(
            Authentication authentication,
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productService.update(member.id(), productId, request));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<ProductResponse> hide(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productService.hide(member.id(), productId));
    }
}
```

- [ ] **Step 6: `GET /api/products/**`를 공개 허용한다**

`backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java`의 `authorizeHttpRequests` 설정을 다음처럼 수정한다.

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
        .requestMatchers("/error").permitAll()
        .anyRequest().authenticated()
)
```

- [ ] **Step 7: 공개 조회 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.ProductApiTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: 전체 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 9: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java backend/src/main/java/com/sweet/market/product backend/src/test/java/com/sweet/market/product/ProductApiTest.java
git commit -m "feat: add public product query api"
```

---

## Task 6: 상품 이미지 추가와 삭제 API 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/product/api/ProductImageAddRequest.java`
- Modify: `backend/src/main/java/com/sweet/market/product/application/ProductService.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductController.java`
- Modify: `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`

- [ ] **Step 1: 이미지 추가와 삭제 테스트를 추가한다**

`backend/src/test/java/com/sweet/market/product/ProductApiTest.java`에 다음 테스트를 추가한다.

```java
@Test
void addProductImageSucceedsForOwner() throws Exception {
    String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
    Long productId = createProduct(accessToken);

    mockMvc.perform(post("/api/products/{productId}/images", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "imageUrl": "https://example.com/macbook-2.jpg"
                            }
                            """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.images", hasSize(2)))
            .andExpect(jsonPath("$.data.images[1].imageUrl").value("https://example.com/macbook-2.jpg"));
}

@Test
void addProductImageFailsForNonOwner() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String otherToken = signupAndLogin("other@example.com", "password123", "other");
    Long productId = createProduct(sellerToken);

    mockMvc.perform(post("/api/products/{productId}/images", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "imageUrl": "https://example.com/macbook-2.jpg"
                            }
                            """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));
}

@Test
void removeProductImageSucceedsForOwner() throws Exception {
    String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
    Long productId = createProduct(accessToken);
    Long imageId = getFirstImageId(productId);

    mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, imageId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.images", hasSize(0)));
}

@Test
void removeProductImageFailsWhenImageDoesNotBelongToProduct() throws Exception {
    String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
    Long productId = createProduct(accessToken);

    mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, 999L)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_NOT_FOUND"));
}
```

테스트 클래스 하단에 helper를 추가한다.

```java
private Long getFirstImageId(Long productId) throws Exception {
    String response = mockMvc.perform(get("/api/products/{productId}", productId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("images").get(0).path("id").asLong();
}
```

- [ ] **Step 2: 실패하는 테스트를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.ProductApiTest
```

Expected:

```text
405 Method Not Allowed
```

이미지 endpoint가 아직 없어서 실패한다.

- [ ] **Step 3: 이미지 추가 request를 작성한다**

`backend/src/main/java/com/sweet/market/product/api/ProductImageAddRequest.java`를 생성한다.

```java
package com.sweet.market.product.api;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductImageAddRequest(
        @NotBlank
        @URL
        @Size(max = 500)
        String imageUrl
) {
}
```

- [ ] **Step 4: `ProductService`에 이미지 추가와 삭제 기능을 추가한다**

`backend/src/main/java/com/sweet/market/product/application/ProductService.java`에 다음 메서드를 추가한다.

```java
@Transactional
public ProductResponse addImage(Long sellerId, Long productId, ProductImageAddRequest request) {
    Product product = findProductForOwner(sellerId, productId);
    product.addImage(request.imageUrl());
    return ProductResponse.from(product);
}

@Transactional
public ProductResponse removeImage(Long sellerId, Long productId, Long imageId) {
    Product product = findProductForOwner(sellerId, productId);
    try {
        product.removeImage(imageId);
    } catch (IllegalArgumentException exception) {
        throw new BusinessException(ErrorCode.PRODUCT_IMAGE_NOT_FOUND);
    }
    return ProductResponse.from(product);
}
```

같은 파일 상단에 import를 추가한다.

```java
import com.sweet.market.product.api.ProductImageAddRequest;
```

- [ ] **Step 5: `ProductController`에 이미지 endpoint를 추가한다**

`backend/src/main/java/com/sweet/market/product/api/ProductController.java`에 다음 메서드를 추가한다.

```java
@PostMapping("/{productId}/images")
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<ProductResponse> addImage(
        Authentication authentication,
        @PathVariable Long productId,
        @Valid @RequestBody ProductImageAddRequest request
) {
    AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
    return ApiResponse.ok(productService.addImage(member.id(), productId, request));
}

@DeleteMapping("/{productId}/images/{imageId}")
public ApiResponse<ProductResponse> removeImage(
        Authentication authentication,
        @PathVariable Long productId,
        @PathVariable Long imageId
) {
    AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
    return ApiResponse.ok(productService.removeImage(member.id(), productId, imageId));
}
```

- [ ] **Step 6: 이미지 API 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.ProductApiTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: 전체 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/product backend/src/test/java/com/sweet/market/product/ProductApiTest.java
git commit -m "feat: add product image api"
```

---

## Task 7: 상품 이미지 cascade와 orphan removal 실험 테스트 추가

**Files:**

- Create: `backend/src/test/java/com/sweet/market/jpalab/CascadeOrphanRemovalTest.java`

- [ ] **Step 1: JPA 실험 테스트를 작성한다**

`backend/src/test/java/com/sweet/market/jpalab/CascadeOrphanRemovalTest.java`를 생성한다.

```java
package com.sweet.market.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductImageRepository;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

class CascadeOrphanRemovalTest extends IntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Test
    @Transactional
    void cascadePersistSavesProductImagesWithProduct() {
        Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "seller"));
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.addImage("https://example.com/macbook-1.jpg");
        product.addImage("https://example.com/macbook-2.jpg");

        productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        Product foundProduct = productRepository.findWithSellerAndImagesById(product.getId()).orElseThrow();

        assertThat(foundProduct.getImages()).hasSize(2);
        assertThat(productImageRepository.count()).isEqualTo(2);
    }

    @Test
    @Transactional
    void orphanRemovalDeletesImageWhenRemovedFromProductCollection() {
        Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "seller"));
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.addImage("https://example.com/macbook-1.jpg");
        product.addImage("https://example.com/macbook-2.jpg");
        productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        Product foundProduct = productRepository.findWithSellerAndImagesById(product.getId()).orElseThrow();
        Long imageId = foundProduct.getImages().get(0).getId();

        foundProduct.removeImage(imageId);
        entityManager.flush();
        entityManager.clear();

        assertThat(productImageRepository.existsById(imageId)).isFalse();
        assertThat(productImageRepository.count()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: JPA 실험 테스트를 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.jpalab.CascadeOrphanRemovalTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 전체 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: 커밋한다**

```powershell
git add backend/src/test/java/com/sweet/market/jpalab/CascadeOrphanRemovalTest.java
git commit -m "test: add product image jpa lab"
```

---

## Task 8: Milestone 2 최종 검증

**Files:**

- Verify only

- [ ] **Step 1: 전체 테스트를 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: 로컬 PostgreSQL을 실행한다**

Run:

```powershell
cd backend
docker compose up -d
docker compose ps
```

Expected:

```text
market-postgres   postgres:17-alpine   Up ... (healthy)   0.0.0.0:15432->5432/tcp
```

- [ ] **Step 3: `JWT_SECRET`을 설정하고 애플리케이션을 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-development-secret-key-32bytes-minimum'
.\gradlew.bat bootRun
```

Expected:

```text
Started MarketApplication
```

- [ ] **Step 4: 상품 등록 API를 수동 확인한다**

다른 PowerShell 터미널에서 실행한다.

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

$loginBody = @{
  email = "seller@example.com"
  password = "password123"
} | ConvertTo-Json

$loginResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body $loginBody

$productBody = @{
  title = "MacBook Pro"
  description = "M3 laptop"
  price = 2000000
  imageUrls = @("https://example.com/macbook-1.jpg")
} | ConvertTo-Json

$productResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/products" `
  -ContentType "application/json" `
  -Headers @{ Authorization = "Bearer $($loginResponse.data.accessToken)" } `
  -Body $productBody

$productResponse.data
```

Expected:

```text
id             : 1
sellerId       : 1
sellerNickname : seller
title          : MacBook Pro
description    : M3 laptop
price          : 2000000
status         : ON_SALE
images         : {@{id=1; imageUrl=https://example.com/macbook-1.jpg}}
```

- [ ] **Step 5: 공개 상품 조회 API를 수동 확인한다**

Run:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/products"

Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/products/$($productResponse.data.id)"
```

Expected:

```text
data
----
@{content=System.Object[]; pageable=...}
```

상세 조회 응답의 `data.title`은 `MacBook Pro`이고 `data.status`는 `ON_SALE`이다.

- [ ] **Step 6: 최종 git 상태를 확인한다**

Run:

```powershell
git status --short --branch
```

Expected:

```text
## main...origin/main
```

작업 브랜치에서 실행했다면 브랜치 이름이 다를 수 있다. 변경 파일이 남아 있으면 커밋하거나 의도적으로 남긴 이유를 기록한다.

---

## Self-Review

Spec coverage:

- 상품 등록: Task 3
- 상품 수정: Task 4
- 상품 삭제 또는 숨김: Task 4에서 `DELETE /api/products/{productId}`를 숨김 처리로 구현
- 상품 이미지 추가/삭제: Task 3의 등록 시 이미지 추가, Task 6의 이미지 추가/삭제 endpoint
- 상품 목록/상세 조회: Task 5
- 상품 이미지 cascade와 orphan removal 실험: Task 7
- 판매자는 상품을 등록하고 구매자는 상품을 조회: Task 3과 Task 5

Placeholder scan:

- 빈 구현 지시 없이 파일별 코드와 실행 명령을 포함했다.
- 각 테스트 단계에는 기대 실패 또는 성공 결과를 명시했다.
- Task 6의 메서드 추가는 기존 Task 4의 `ProductService`와 `ProductController` 구조를 전제로 하며, 필요한 import와 메서드 본문을 포함했다.

Type consistency:

- `Product.id`, `ProductImage.id`, `Member.id`는 모두 `Long`이다.
- `Product.price`는 `long`이고 request/response도 `long`을 사용한다.
- `ProductStatus` 문자열은 `status().name()`으로 반환한다.
- `ProductRepository.findWithSellerAndImagesById`와 `findWithSellerAndImagesByIdAndStatus`는 service/query에서 같은 이름으로 사용한다.
- `Product.removeImage(Long imageId)`의 `IllegalArgumentException`은 application service에서 `PRODUCT_IMAGE_NOT_FOUND`로 변환한다.
