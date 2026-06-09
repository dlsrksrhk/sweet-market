# Milestone 3 Order Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 구매자가 판매 중인 상품을 주문해 상품을 예약 상태로 바꾸고, 주문 취소 시 상품을 다시 판매 중 상태로 복구하는 주문 도메인을 만든다.

**Architecture:** `order` 패키지는 기존 `product` 패키지와 같은 `api/application/domain/repository/query` 구조를 따른다. 주문 생성과 취소는 `OrderService`의 트랜잭션 안에서 `Order`와 `Product` aggregate 상태를 함께 변경한다. 같은 상품에 대한 동시 주문은 `Product`의 `@Version` 필드로 optimistic locking을 걸어 방어한다.

**Tech Stack:** Spring Boot, Spring MVC, Spring Security, Spring Data JPA, PostgreSQL, Bean Validation, Lombok, JUnit 5, MockMvc, Testcontainers

---

## Scope

이 계획은 설계 문서의 Milestone 3만 다룬다.

완료 기준:

- 인증된 구매자는 `ON_SALE` 상품을 주문할 수 있다.
- 주문 생성 시 `Order`는 `CREATED` 상태로 저장된다.
- 주문 생성 시 대상 `Product`는 `RESERVED` 상태로 변경된다.
- 이미 `RESERVED`, `SOLD_OUT`, `HIDDEN` 상태인 상품은 주문할 수 없다.
- 주문자만 자기 주문을 취소할 수 있다.
- 주문 취소 시 `Order`는 `CANCELED` 상태로 변경된다.
- 주문 취소 시 대상 `Product`는 `ON_SALE` 상태로 복구된다.
- dirty checking 실험 테스트가 주문 취소와 상품 상태 복구를 관찰한다.
- optimistic locking 실험 테스트가 같은 상품 동시 주문 충돌을 관찰한다.
- 예약된 상품은 판매자 수정, 숨김, 이미지 변경을 막아 주문 취소가 상품을 판매 중 상태로 복구할 수 있게 한다.
- 전체 backend 테스트가 통과한다.
- 모든 신규 JUnit `@Test` 메서드명은 Korean_with_underscores 형식을 따른다.

Out of scope:

- 주문 목록 조회와 주문 상세 조회 API
- 구매 확정
- 결제, 배송, 정산
- 판매자가 자기 상품을 주문하지 못하게 막는 정책
- 재고 수량, 다중 주문 가능 상품, 장바구니
- 동시성 실패 재시도

## File Structure

생성 또는 수정할 파일:

```text
backend/src/main/java/com/sweet/market/common/error/ErrorCode.java
backend/src/main/java/com/sweet/market/common/error/GlobalExceptionHandler.java

backend/src/main/java/com/sweet/market/product/domain/Product.java

backend/src/main/java/com/sweet/market/order/domain/Order.java
backend/src/main/java/com/sweet/market/order/domain/OrderStatus.java
backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java
backend/src/main/java/com/sweet/market/order/application/OrderService.java
backend/src/main/java/com/sweet/market/order/api/OrderController.java
backend/src/main/java/com/sweet/market/order/api/OrderCreateRequest.java
backend/src/main/java/com/sweet/market/order/api/OrderResponse.java

backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
backend/src/test/java/com/sweet/market/product/domain/ProductTest.java
backend/src/test/java/com/sweet/market/order/domain/OrderTest.java
backend/src/test/java/com/sweet/market/order/OrderApiTest.java
backend/src/test/java/com/sweet/market/jpalab/DirtyCheckingTest.java
backend/src/test/java/com/sweet/market/jpalab/OptimisticLockTest.java
```

책임:

- `common/error`: 주문 도메인 에러 코드와 optimistic locking 충돌 응답
- `product/domain`: 상품 예약, 예약 복구, 버전 필드
- `order/domain`: 주문 aggregate와 주문 상태 전이
- `order/repository`: 주문 쓰기 흐름에 필요한 JPA repository
- `order/application`: 인증 사용자 기준 주문 생성, 취소, 권한 검증
- `order/api`: HTTP request/response와 controller
- `jpalab`: dirty checking과 optimistic locking 관찰 테스트

---

## Task 1: 주문 에러 코드와 동시성 충돌 응답 추가

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Modify: `backend/src/main/java/com/sweet/market/common/error/GlobalExceptionHandler.java`

- [ ] **Step 1: 주문 에러 코드를 추가한다**

`backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`를 다음 내용으로 교체한다.

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
    PRODUCT_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 이미지를 찾을 수 없습니다."),
    PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중인 상품만 주문할 수 있습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "주문에 대한 권한이 없습니다."),
    ORDER_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "취소할 수 없는 주문 상태입니다."),
    ORDER_CONFLICT(HttpStatus.CONFLICT, "이미 다른 거래에서 처리된 주문 요청입니다.");

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

- [ ] **Step 2: optimistic locking 충돌을 공통 에러로 매핑한다**

`backend/src/main/java/com/sweet/market/common/error/GlobalExceptionHandler.java`를 다음 내용으로 교체한다.

```java
package com.sweet.market.common.error;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailureException(
            ObjectOptimisticLockingFailureException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.ORDER_CONFLICT.status())
                .body(ErrorResponse.of(ErrorCode.ORDER_CONFLICT));
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

- [ ] **Step 3: 컴파일을 확인한다**

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

- [ ] **Step 4: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/common/error/ErrorCode.java backend/src/main/java/com/sweet/market/common/error/GlobalExceptionHandler.java
git commit -m "chore: prepare order error handling"
```

---

## Task 2: 상품 예약 상태 전이와 version 필드 추가

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/product/domain/Product.java`
- Modify: `backend/src/test/java/com/sweet/market/product/domain/ProductTest.java`

- [ ] **Step 1: 상품 도메인 테스트를 먼저 추가한다**

`backend/src/test/java/com/sweet/market/product/domain/ProductTest.java`의 마지막 테스트 아래에 다음 테스트를 추가한다.

```java
    @Test
    void 판매중_상품을_예약한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        product.reserve();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.RESERVED);
    }

    @Test
    void 예약_상품을_판매중으로_복구한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.reserve();

        product.restoreOnSaleFromReservation();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 판매중이_아닌_상품은_예약할_수_없다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.hide();

        assertThatThrownBy(product::reserve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Product is not on sale: HIDDEN");
    }

    @Test
    void 예약_상태가_아닌_상품은_판매중으로_복구할_수_없다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        assertThatThrownBy(product::restoreOnSaleFromReservation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Product is not reserved: ON_SALE");
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.domain.ProductTest"
```

Expected:

```text
Compilation failed because Product.reserve() and Product.restoreOnSaleFromReservation() do not exist.
```

- [ ] **Step 3: Product에 version과 상태 전이 메서드를 추가한다**

`backend/src/main/java/com/sweet/market/product/domain/Product.java`를 다음 내용으로 교체한다.

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
import jakarta.persistence.Version;
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

    @Version
    @Column(nullable = false)
    private Long version;

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

    public void reserve() {
        if (status != ProductStatus.ON_SALE) {
            throw new IllegalStateException("Product is not on sale: " + status);
        }
        this.status = ProductStatus.RESERVED;
    }

    public void restoreOnSaleFromReservation() {
        if (status != ProductStatus.RESERVED) {
            throw new IllegalStateException("Product is not reserved: " + status);
        }
        this.status = ProductStatus.ON_SALE;
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

- [ ] **Step 4: 상품 도메인 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.domain.ProductTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/product/domain/Product.java backend/src/test/java/com/sweet/market/product/domain/ProductTest.java
git commit -m "feat: add product reservation transitions"
```

---

## Task 3: 주문 aggregate와 repository 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/order/domain/OrderStatus.java`
- Create: `backend/src/main/java/com/sweet/market/order/domain/Order.java`
- Create: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`
- Create: `backend/src/test/java/com/sweet/market/order/domain/OrderTest.java`
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`

- [ ] **Step 1: 주문 도메인 테스트를 먼저 작성한다**

`backend/src/test/java/com/sweet/market/order/domain/OrderTest.java`를 생성한다.

```java
package com.sweet.market.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;

class OrderTest {

    @Test
    void 주문을_생성하면_상품이_예약된다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        Order order = Order.create(buyer, product);

        assertThat(order.getBuyer()).isSameAs(buyer);
        assertThat(order.getProduct()).isSameAs(product);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getOrderedAt()).isNotNull();
        assertThat(order.getCanceledAt()).isNull();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.RESERVED);
    }

    @Test
    void 주문을_취소하면_상품이_판매중으로_복구된다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        Order order = Order.create(buyer, product);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCanceledAt()).isNotNull();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 이미_취소된_주문은_다시_취소할_수_없다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Member buyer = Member.create("buyer@example.com", "encoded-password", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        Order order = Order.create(buyer, product);
        order.cancel();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order cannot be canceled: CANCELED");
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.order.domain.OrderTest"
```

Expected:

```text
Compilation failed because the order domain package does not exist.
```

- [ ] **Step 3: OrderStatus를 생성한다**

`backend/src/main/java/com/sweet/market/order/domain/OrderStatus.java`를 생성한다.

```java
package com.sweet.market.order.domain;

public enum OrderStatus {
    CREATED,
    PAID,
    SHIPPING,
    DELIVERED,
    CONFIRMED,
    CANCELED
}
```

- [ ] **Step 4: Order aggregate를 생성한다**

`backend/src/main/java/com/sweet/market/order/domain/Order.java`를 생성한다.

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

    public boolean isOwnedBy(Long memberId) {
        return buyer.getId().equals(memberId);
    }
}
```

- [ ] **Step 5: OrderRepository를 생성한다**

`backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`를 생성한다.

```java
package com.sweet.market.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"buyer", "product", "product.seller", "product.images"})
    Optional<Order> findWithBuyerAndProductById(Long id);
}
```

- [ ] **Step 6: 테스트 정리 대상 테이블에 orders를 추가한다**

`backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`의 `cleanUp()` 메서드를 다음처럼 수정한다.

```java
    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE orders, product_images, products, members RESTART IDENTITY CASCADE");
    }
```

- [ ] **Step 7: 주문 도메인 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.order.domain.OrderTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: 전체 컴파일을 확인한다**

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

- [ ] **Step 9: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/order backend/src/test/java/com/sweet/market/order/domain/OrderTest.java backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
git commit -m "feat: add order aggregate"
```

---

## Task 4: 주문 생성과 취소 서비스 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/order/application/OrderService.java`

- [ ] **Step 1: OrderService를 생성한다**

`backend/src/main/java/com/sweet/market/order/application/OrderService.java`를 생성한다.

```java
package com.sweet.market.order.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.api.OrderResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            MemberRepository memberRepository
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public OrderResponse create(Long buyerId, Long productId) {
        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Product product = productRepository.findWithSellerAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        Order order;
        try {
            order = Order.create(buyer, product);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }

        Order savedOrder = orderRepository.save(order);
        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse cancel(Long buyerId, Long orderId) {
        Order order = orderRepository.findWithBuyerAndProductById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        try {
            order.cancel();
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        return OrderResponse.from(order);
    }
}
```

- [ ] **Step 2: 컴파일 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat compileJava
```

Expected:

```text
Compilation failed because OrderResponse does not exist.
```

이 실패는 다음 Task에서 API DTO를 추가하며 해결한다.

---

## Task 5: 주문 API DTO와 controller 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/order/api/OrderCreateRequest.java`
- Create: `backend/src/main/java/com/sweet/market/order/api/OrderResponse.java`
- Create: `backend/src/main/java/com/sweet/market/order/api/OrderController.java`

- [ ] **Step 1: 주문 생성 요청 DTO를 생성한다**

`backend/src/main/java/com/sweet/market/order/api/OrderCreateRequest.java`를 생성한다.

```java
package com.sweet.market.order.api;

import jakarta.validation.constraints.NotNull;

public record OrderCreateRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId
) {
}
```

- [ ] **Step 2: 주문 응답 DTO를 생성한다**

`backend/src/main/java/com/sweet/market/order/api/OrderResponse.java`를 생성한다.

```java
package com.sweet.market.order.api;

import java.time.LocalDateTime;

import com.sweet.market.order.domain.Order;

public record OrderResponse(
        Long id,
        Long buyerId,
        String buyerNickname,
        Long productId,
        Long sellerId,
        String sellerNickname,
        String productTitle,
        long productPrice,
        String status,
        String productStatus,
        LocalDateTime orderedAt,
        LocalDateTime canceledAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getBuyer().getId(),
                order.getBuyer().getNickname(),
                order.getProduct().getId(),
                order.getProduct().getSeller().getId(),
                order.getProduct().getSeller().getNickname(),
                order.getProduct().getTitle(),
                order.getProduct().getPrice(),
                order.getStatus().name(),
                order.getProduct().getStatus().name(),
                order.getOrderedAt(),
                order.getCanceledAt()
        );
    }
}
```

- [ ] **Step 3: 주문 controller를 생성한다**

`backend/src/main/java/com/sweet/market/order/api/OrderController.java`를 생성한다.

```java
package com.sweet.market.order.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.order.application.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(
            Authentication authentication,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(orderService.create(member.id(), request.productId()));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancel(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(orderService.cancel(member.id(), orderId));
    }
}
```

- [ ] **Step 4: 컴파일을 확인한다**

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

- [ ] **Step 5: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/order
git commit -m "feat: add order service and api"
```

---

## Task 6: 주문 API 통합 테스트 추가

**Files:**

- Create: `backend/src/test/java/com/sweet/market/order/OrderApiTest.java`

- [ ] **Step 1: 주문 API 통합 테스트를 작성한다**

`backend/src/test/java/com/sweet/market/order/OrderApiTest.java`를 생성한다.

```java
package com.sweet.market.order;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
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

class OrderApiTest extends IntegrationTestSupport {

    @Test
    void 주문_생성에_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.buyerNickname").value("buyer"))
                .andExpect(jsonPath("$.data.sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.productStatus").value("RESERVED"))
                .andExpect(jsonPath("$.data.orderedAt").exists())
                .andExpect(jsonPath("$.data.canceledAt").doesNotExist());

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 주문_생성은_JWT가_필요하다() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 존재하지_않는_상품은_주문할_수_없다() throws Exception {
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 999
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 판매중이_아닌_상품은_주문할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d
                                }
                                """.formatted(productId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_ON_SALE"));
    }

    @Test
    void 주문자는_주문_취소에_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.data.canceledAt").exists());

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    @Test
    void 주문자가_아니면_주문_취소에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
    }

    @Test
    void 이미_취소한_주문은_다시_취소할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CANCEL_NOT_ALLOWED"));
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

    private Long createOrder(String accessToken, Long productId) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }
}
```

- [ ] **Step 2: 주문 API 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.order.OrderApiTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 상품 API 회귀 테스트를 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.ProductApiTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: 커밋한다**

```powershell
git add backend/src/test/java/com/sweet/market/order/OrderApiTest.java
git commit -m "test: add order api coverage"
```

---

## Task 7: dirty checking 실험 테스트 추가

**Files:**

- Create: `backend/src/test/java/com/sweet/market/jpalab/DirtyCheckingTest.java`

- [ ] **Step 1: dirty checking 테스트를 작성한다**

`backend/src/test/java/com/sweet/market/jpalab/DirtyCheckingTest.java`를 생성한다.

```java
package com.sweet.market.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

class DirtyCheckingTest extends IntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @Transactional
    void dirty_checking은_주문_취소와_상품_상태_복구를_update로_반영한다() {
        Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "seller"));
        Member buyer = memberRepository.save(Member.create("buyer@example.com", "encoded-password", "buyer"));
        Product product = productRepository.save(Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L));
        Order order = orderRepository.save(Order.create(buyer, product));
        entityManager.flush();
        entityManager.clear();

        Order foundOrder = orderRepository.findWithBuyerAndProductById(order.getId()).orElseThrow();

        foundOrder.cancel();
        entityManager.flush();
        entityManager.clear();

        Order canceledOrder = orderRepository.findWithBuyerAndProductById(order.getId()).orElseThrow();
        Product restoredProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(restoredProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }
}
```

- [ ] **Step 2: dirty checking 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.jpalab.DirtyCheckingTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 커밋한다**

```powershell
git add backend/src/test/java/com/sweet/market/jpalab/DirtyCheckingTest.java
git commit -m "test: add dirty checking lab"
```

---

## Task 8: optimistic locking 실험 테스트 추가

**Files:**

- Create: `backend/src/test/java/com/sweet/market/jpalab/OptimisticLockTest.java`

- [ ] **Step 1: optimistic locking 테스트를 작성한다**

`backend/src/test/java/com/sweet/market/jpalab/OptimisticLockTest.java`를 생성한다.

```java
package com.sweet.market.jpalab;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;

class OptimisticLockTest extends IntegrationTestSupport {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void 같은_상품을_두_트랜잭션이_예약하면_나중_커밋이_optimistic_lock으로_실패한다() {
        Long productId = saveProduct();
        EntityManager firstEntityManager = entityManagerFactory.createEntityManager();
        EntityManager secondEntityManager = entityManagerFactory.createEntityManager();
        EntityTransaction firstTransaction = firstEntityManager.getTransaction();
        EntityTransaction secondTransaction = secondEntityManager.getTransaction();

        try {
            firstTransaction.begin();
            secondTransaction.begin();

            Product firstProduct = firstEntityManager.find(Product.class, productId);
            Product secondProduct = secondEntityManager.find(Product.class, productId);

            firstProduct.reserve();
            secondProduct.reserve();

            firstTransaction.commit();

            assertThatThrownBy(secondTransaction::commit)
                    .isInstanceOf(RollbackException.class)
                    .hasRootCauseInstanceOf(OptimisticLockException.class);
        } finally {
            rollbackIfActive(firstTransaction);
            rollbackIfActive(secondTransaction);
            firstEntityManager.close();
            secondEntityManager.close();
        }
    }

    private Long saveProduct() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "seller"));
            Product product = productRepository.save(Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L));
            return product.getId();
        });
    }

    private void rollbackIfActive(EntityTransaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
```

- [ ] **Step 2: optimistic locking 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.jpalab.OptimisticLockTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 커밋한다**

```powershell
git add backend/src/test/java/com/sweet/market/jpalab/OptimisticLockTest.java
git commit -m "test: add optimistic locking lab"
```

---

## Task 9: 전체 검증과 마무리

**Files:**

- Verify only

- [ ] **Step 1: 전체 backend 테스트를 실행한다**

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

- [ ] **Step 2: 테스트 이름 규칙을 확인한다**

Run:

```powershell
cd ..
rg -n "void [a-zA-Z0-9]+\\(" backend\\src\\test\\java
```

Expected:

```text
backend\src\test\java\com\sweet\market\support\IntegrationTestSupport.java:39:    static void overrideProperties(DynamicPropertyRegistry registry) {
backend\src\test\java\com\sweet\market\support\IntegrationTestSupport.java:49:    void cleanUp() {
```

위 두 메서드는 `@Test`가 아닌 helper/lifecycle 메서드라서 허용된다.

- [ ] **Step 3: 작업트리 상태를 확인한다**

Run:

```powershell
git status --short --branch
```

Expected:

```text
## main...origin/main [ahead N]
 M backend/src/main/resources/application.yaml
```

`backend/src/main/resources/application.yaml` 변경은 사용자 로컬 설정이다. 이 계획의 구현 커밋에 포함하지 않는다.

- [ ] **Step 4: 마무리 커밋이 필요한지 확인한다**

계획의 각 Task에서 이미 커밋했다면 추가 커밋은 만들지 않는다. 남은 변경이 Milestone 3 구현 파일뿐이면 다음처럼 커밋한다.

```powershell
git add backend/src/main/java/com/sweet/market/common/error/ErrorCode.java `
  backend/src/main/java/com/sweet/market/common/error/GlobalExceptionHandler.java `
  backend/src/main/java/com/sweet/market/product/domain/Product.java `
  backend/src/main/java/com/sweet/market/order `
  backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java `
  backend/src/test/java/com/sweet/market/product/domain/ProductTest.java `
  backend/src/test/java/com/sweet/market/order `
  backend/src/test/java/com/sweet/market/jpalab/DirtyCheckingTest.java `
  backend/src/test/java/com/sweet/market/jpalab/OptimisticLockTest.java
git commit -m "feat: add order reservation flow"
```

---

## Self-Review

- Spec coverage: Milestone 3의 주문 생성, 주문 취소, 상품 `ON_SALE` to `RESERVED` 전이, 취소 시 상품 복구, dirty checking 실험, optimistic locking 실험이 각각 Task 3-8에 포함되어 있다.
- Placeholder scan: 빈칸 지시, 구현 지연 지시, 모호한 에러 처리 지시 문구가 없다.
- Type consistency: `Order`, `OrderStatus`, `OrderRepository`, `OrderService`, `OrderCreateRequest`, `OrderResponse`, `OrderController` 이름과 패키지가 모든 Task에서 일치한다.
- Test naming: 모든 신규 `@Test` 메서드명은 Korean_with_underscores 형식이다.
- Scope check: 조회 API, 결제, 배송, 구매 확정, 정산은 Milestone 3 범위 밖으로 남겼다.
