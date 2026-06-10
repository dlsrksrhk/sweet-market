# Milestone 6 Query Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주문 목록/상세 조회 API를 추가하고, 상품/주문/정산 조회의 N+1을 JPA lab에서 재현한 뒤 production query path가 최적화되어 있음을 검증한다.

**Architecture:** 조회 기능은 쓰기 service와 분리한다. `OrderQueryService`는 인증 구매자 기준 주문 목록/상세 조회를 담당하고 DTO를 반환한다. N+1 재현은 production repository에 학습용 메서드를 추가하지 않고 JPA lab에서 `EntityManager` JPQL로 naive path를 만든다. 최적화된 production path는 `@EntityGraph`를 적용한 repository method와 query service를 통해 검증한다.

**Tech Stack:** Spring Boot, Spring MVC, Spring Security, Spring Data JPA, Hibernate Statistics, PostgreSQL, Lombok, JUnit 5, MockMvc, Testcontainers

---

## Scope

완료 기준:

- 인증된 구매자는 `GET /api/orders/me`로 자신의 주문 목록을 페이지로 조회한다.
- 인증된 구매자는 `GET /api/orders/{orderId}`로 자신의 주문 상세를 조회한다.
- 다른 사용자는 주문 상세를 조회할 수 없다.
- 상품 목록 조회는 seller만 entity graph로 로딩하고, paged list에서 product images collection을 함께 로딩하지 않는다.
- 정산 목록 조회는 order, product, seller를 한 번에 로딩한다.
- 상품, 주문, 정산 각각에 대해 naive path의 N+1을 Hibernate `Statistics`로 관찰한다.
- 상품, 주문, 정산 각각에 대해 optimized path의 query count가 낮게 유지됨을 검증한다.
- 전체 backend 테스트가 통과한다.
- 모든 신규 JUnit `@Test` 메서드명은 Korean_with_underscores 형식을 따른다.

Out of scope:

- 주문 검색 조건
- 판매자 주문 조회
- 관리자 주문 조회
- DTO projection 전환
- cursor pagination
- 정확한 SQL 문자열 assert
- frontend UI

## File Structure

생성 또는 수정할 파일:

```text
backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java

backend/src/main/java/com/sweet/market/order/api/OrderController.java
backend/src/main/java/com/sweet/market/order/api/OrderSummaryResponse.java
backend/src/main/java/com/sweet/market/order/query/OrderQueryService.java
backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java

backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java

backend/src/test/java/com/sweet/market/order/OrderQueryApiTest.java
backend/src/test/java/com/sweet/market/jpalab/QueryOptimizationTestSupport.java
backend/src/test/java/com/sweet/market/jpalab/ProductQueryOptimizationTest.java
backend/src/test/java/com/sweet/market/jpalab/OrderQueryOptimizationTest.java
backend/src/test/java/com/sweet/market/jpalab/SettlementQueryOptimizationTest.java
```

---

## Task 1: 상품 목록 조회 entity graph를 list 용도에 맞게 좁힌다

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Create: `backend/src/test/java/com/sweet/market/jpalab/QueryOptimizationTestSupport.java`
- Create: `backend/src/test/java/com/sweet/market/jpalab/ProductQueryOptimizationTest.java`

- [ ] **Step 1: Hibernate statistics helper를 생성한다**

Create `backend/src/test/java/com/sweet/market/jpalab/QueryOptimizationTestSupport.java`:

```java
package com.sweet.market.jpalab;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;

import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

public abstract class QueryOptimizationTestSupport extends IntegrationTestSupport {

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    protected void resetStatistics() {
        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    protected long queryCount() {
        return statistics().getPrepareStatementCount();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
```

- [ ] **Step 2: 실패하는 상품 조회 최적화 테스트를 작성한다**

Create `backend/src/test/java/com/sweet/market/jpalab/ProductQueryOptimizationTest.java`:

```java
package com.sweet.market.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.api.ProductSummaryResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.query.ProductQueryService;
import com.sweet.market.product.repository.ProductRepository;

import jakarta.persistence.PersistenceUnitUtil;

class ProductQueryOptimizationTest extends QueryOptimizationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductQueryService productQueryService;

    @Test
    @Transactional
    void 상품_목록_naive_조회는_seller_N_plus_1이_발생한다() {
        saveProductsWithDifferentSellers();
        flushAndClear();
        resetStatistics();

        List<Product> products = entityManager.createQuery("""
                        select p
                        from Product p
                        where p.status = :status
                        order by p.id desc
                        """, Product.class)
                .setParameter("status", ProductStatus.ON_SALE)
                .getResultList();

        List<ProductSummaryResponse> responses = products.stream()
                .map(ProductSummaryResponse::from)
                .toList();

        assertThat(responses).hasSize(3);
        assertThat(queryCount()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @Transactional
    void 상품_목록_최적화_조회는_seller를_함께_로딩한다() {
        saveProductsWithDifferentSellers();
        flushAndClear();
        resetStatistics();

        List<ProductSummaryResponse> responses = productQueryService.findOnSaleProducts(PageRequest.of(0, 10))
                .getContent();

        assertThat(responses).hasSize(3);
        assertThat(queryCount()).isLessThanOrEqualTo(2);
    }

    @Test
    @Transactional
    void 상품_목록_최적화_조회는_images를_로딩하지_않는다() {
        saveProductsWithDifferentSellers();
        flushAndClear();

        List<Product> products = productRepository.findByStatusOrderByIdDesc(
                        ProductStatus.ON_SALE,
                        PageRequest.of(0, 10)
                )
                .getContent();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil();

        assertThat(products).hasSize(3);
        assertThat(products).allSatisfy(product -> {
            assertThat(persistenceUnitUtil.isLoaded(product, "seller")).isTrue();
            assertThat(persistenceUnitUtil.isLoaded(product, "images")).isFalse();
        });
    }

    private void saveProductsWithDifferentSellers() {
        for (int index = 1; index <= 3; index++) {
            Member seller = memberRepository.save(Member.create(
                    "seller" + index + "@example.com",
                    "encoded-password",
                    "seller" + index
            ));
            productRepository.save(Product.create(seller, "Product " + index, "description", 10_000L * index));
        }
    }
}
```

- [ ] **Step 3: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.jpalab.ProductQueryOptimizationTest"
```

Expected:

```text
상품_목록_최적화_조회는_images를_로딩하지_않는다 fails because the current product list entity graph includes images.
```

- [ ] **Step 4: 상품 목록 entity graph를 seller 전용으로 좁힌다**

Modify `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`:

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

    @EntityGraph(attributePaths = "seller")
    Page<Product> findByStatusOrderByIdDesc(ProductStatus status, Pageable pageable);
}
```

- [ ] **Step 5: 상품 조회 최적화 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.jpalab.ProductQueryOptimizationTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java `
  backend/src/test/java/com/sweet/market/jpalab/QueryOptimizationTestSupport.java `
  backend/src/test/java/com/sweet/market/jpalab/ProductQueryOptimizationTest.java
git commit -m "test: add product query optimization lab"
```

---

## Task 2: 주문 목록과 상세 조회 API를 추가한다

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/order/api/OrderController.java`
- Create: `backend/src/main/java/com/sweet/market/order/api/OrderSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/order/query/OrderQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`
- Create: `backend/src/test/java/com/sweet/market/order/OrderQueryApiTest.java`

- [ ] **Step 1: 실패하는 주문 조회 API 테스트를 작성한다**

Create `backend/src/test/java/com/sweet/market/order/OrderQueryApiTest.java`:

```java
package com.sweet.market.order;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
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

class OrderQueryApiTest extends IntegrationTestSupport {

    @Test
    void 구매자는_자신의_주문_목록만_조회한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-buyer@example.com", "password123", "otherBuyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long otherProductId = createProduct(sellerToken, "iPhone");
        Long orderId = createOrder(buyerToken, productId);
        createOrder(otherBuyerToken, otherProductId);

        mockMvc.perform(get("/api/orders/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(orderId))
                .andExpect(jsonPath("$.data.content[0].productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.content[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.content[0].productStatus").value("RESERVED"));
    }

    @Test
    void 구매자는_자신의_주문_상세를_조회한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.buyerNickname").value("buyer"))
                .andExpect(jsonPath("$.data.sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.productStatus").value("RESERVED"));
    }

    @Test
    void 다른_사용자의_주문_상세는_조회할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-buyer@example.com", "password123", "otherBuyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherBuyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
    }

    @Test
    void 주문_목록_조회는_JWT가_필요하다() throws Exception {
        mockMvc.perform(get("/api/orders/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
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

    private Long createProduct(String accessToken, String title) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "description",
                                  "price": 2000000,
                                  "imageUrls": [
                                    "https://example.com/product.jpg"
                                  ]
                                }
                                """.formatted(title)))
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

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.order.OrderQueryApiTest"
```

Expected:

```text
GET /api/orders/me and GET /api/orders/{orderId} return 404 because read endpoints do not exist.
```

- [ ] **Step 3: 주문 요약 DTO를 생성한다**

Create `backend/src/main/java/com/sweet/market/order/api/OrderSummaryResponse.java`:

```java
package com.sweet.market.order.api;

import java.time.LocalDateTime;

import com.sweet.market.order.domain.Order;

public record OrderSummaryResponse(
        Long id,
        Long productId,
        String productTitle,
        long productPrice,
        Long sellerId,
        String sellerNickname,
        String status,
        String productStatus,
        LocalDateTime orderedAt
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getProduct().getId(),
                order.getProduct().getTitle(),
                order.getProduct().getPrice(),
                order.getProduct().getSeller().getId(),
                order.getProduct().getSeller().getNickname(),
                order.getStatus().name(),
                order.getProduct().getStatus().name(),
                order.getOrderedAt()
        );
    }
}
```

- [ ] **Step 4: 주문 repository에 조회용 메서드를 추가한다**

Modify `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`:

```java
package com.sweet.market.order.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"buyer", "product", "product.seller", "product.images"})
    Optional<Order> findWithBuyerAndProductById(Long id);

    @EntityGraph(attributePaths = {"product", "product.seller"})
    Page<Order> findByBuyerIdOrderByIdDesc(Long buyerId, Pageable pageable);
}
```

- [ ] **Step 5: `OrderQueryService`를 생성한다**

Create `backend/src/main/java/com/sweet/market/order/query/OrderQueryService.java`:

```java
package com.sweet.market.order.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.api.OrderResponse;
import com.sweet.market.order.api.OrderSummaryResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> findMine(Long buyerId, Pageable pageable) {
        return orderRepository.findByBuyerIdOrderByIdDesc(buyerId, pageable)
                .map(OrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse findOne(Long buyerId, Long orderId) {
        Order order = orderRepository.findWithBuyerAndProductById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return OrderResponse.from(order);
    }
}
```

- [ ] **Step 6: `OrderController`에 조회 endpoint를 추가한다**

Modify imports and constructor in `backend/src/main/java/com/sweet/market/order/api/OrderController.java`, then add these methods:

```java
    @GetMapping("/me")
    public ApiResponse<Page<OrderSummaryResponse>> findMine(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(orderQueryService.findMine(member.id(), pageable));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> findOne(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(orderQueryService.findOne(member.id(), orderId));
    }
```

Required imports:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import com.sweet.market.order.query.OrderQueryService;
```

Constructor should accept both services:

```java
    private final OrderService orderService;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderService orderService, OrderQueryService orderQueryService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
    }
```

- [ ] **Step 7: 주문 조회 API 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.order.OrderQueryApiTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/order/api/OrderController.java `
  backend/src/main/java/com/sweet/market/order/api/OrderSummaryResponse.java `
  backend/src/main/java/com/sweet/market/order/query/OrderQueryService.java `
  backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java `
  backend/src/test/java/com/sweet/market/order/OrderQueryApiTest.java
git commit -m "feat: add order query api"
```

---

## Task 3: 주문 목록 조회 N+1 재현과 최적화 검증을 추가한다

**Files:**

- Create: `backend/src/test/java/com/sweet/market/jpalab/OrderQueryOptimizationTest.java`

- [ ] **Step 1: 실패하는 주문 조회 최적화 테스트를 작성한다**

Create `backend/src/test/java/com/sweet/market/jpalab/OrderQueryOptimizationTest.java`:

```java
package com.sweet.market.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.api.OrderSummaryResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.query.OrderQueryService;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;

class OrderQueryOptimizationTest extends QueryOptimizationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderQueryService orderQueryService;

    @Test
    @Transactional
    void 주문_목록_naive_조회는_product_seller_N_plus_1이_발생한다() {
        Member buyer = saveOrdersWithDifferentSellers();
        flushAndClear();
        resetStatistics();

        List<Order> orders = entityManager.createQuery("""
                        select o
                        from Order o
                        where o.buyer.id = :buyerId
                        order by o.id desc
                        """, Order.class)
                .setParameter("buyerId", buyer.getId())
                .getResultList();

        List<OrderSummaryResponse> responses = orders.stream()
                .map(OrderSummaryResponse::from)
                .toList();

        assertThat(responses).hasSize(3);
        assertThat(queryCount()).isGreaterThanOrEqualTo(7);
    }

    @Test
    @Transactional
    void 주문_목록_최적화_조회는_product와_seller를_함께_로딩한다() {
        Member buyer = saveOrdersWithDifferentSellers();
        flushAndClear();
        resetStatistics();

        List<OrderSummaryResponse> responses = orderQueryService.findMine(buyer.getId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(responses).hasSize(3);
        assertThat(queryCount()).isLessThanOrEqualTo(2);
    }

    private Member saveOrdersWithDifferentSellers() {
        Member buyer = memberRepository.save(Member.create("buyer@example.com", "encoded-password", "buyer"));
        for (int index = 1; index <= 3; index++) {
            Member seller = memberRepository.save(Member.create(
                    "seller" + index + "@example.com",
                    "encoded-password",
                    "seller" + index
            ));
            Product product = productRepository.save(Product.create(seller, "Product " + index, "description", 10_000L));
            orderRepository.save(Order.create(buyer, product));
        }
        return buyer;
    }
}
```

- [ ] **Step 2: 주문 조회 최적화 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.jpalab.OrderQueryOptimizationTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 커밋한다**

```powershell
git add backend/src/test/java/com/sweet/market/jpalab/OrderQueryOptimizationTest.java
git commit -m "test: add order query optimization lab"
```

---

## Task 4: 정산 목록 조회 N+1 재현과 최적화 검증을 추가한다

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java`
- Create: `backend/src/test/java/com/sweet/market/jpalab/SettlementQueryOptimizationTest.java`

- [ ] **Step 1: 정산 repository entity graph를 필요한 연관으로 정리한다**

Modify `SettlementRepository.findBySellerIdOrderByIdDesc` graph:

```java
    @EntityGraph(attributePaths = {"order", "order.product", "seller"})
    List<Settlement> findBySellerIdOrderByIdDesc(Long sellerId);
```

Keep `findWithOrderByOrderId` unchanged if write response paths still need `order.product.seller`.

- [ ] **Step 2: 실패하는 정산 조회 최적화 테스트를 작성한다**

Create `backend/src/test/java/com/sweet/market/jpalab/SettlementQueryOptimizationTest.java`:

```java
package com.sweet.market.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.sweet.market.settlement.api.SettlementResponse;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.query.SettlementQueryService;
import com.sweet.market.settlement.repository.SettlementRepository;

class SettlementQueryOptimizationTest extends QueryOptimizationTestSupport {

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
    private SettlementQueryService settlementQueryService;

    @Test
    @Transactional
    void 정산_목록_naive_조회는_order_product_N_plus_1이_발생한다() {
        Member seller = saveSettlements();
        flushAndClear();
        resetStatistics();

        List<Settlement> settlements = entityManager.createQuery("""
                        select s
                        from Settlement s
                        where s.seller.id = :sellerId
                        order by s.id desc
                        """, Settlement.class)
                .setParameter("sellerId", seller.getId())
                .getResultList();

        List<SettlementResponse> responses = settlements.stream()
                .map(SettlementResponse::from)
                .toList();

        assertThat(responses).hasSize(3);
        assertThat(queryCount()).isGreaterThanOrEqualTo(7);
    }

    @Test
    @Transactional
    void 정산_목록_최적화_조회는_order와_product를_함께_로딩한다() {
        Member seller = saveSettlements();
        flushAndClear();
        resetStatistics();

        List<SettlementResponse> responses = settlementQueryService.findMine(seller.getId());

        assertThat(responses).hasSize(3);
        assertThat(queryCount()).isLessThanOrEqualTo(1);
    }

    private Member saveSettlements() {
        Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "seller"));
        for (int index = 1; index <= 3; index++) {
            Member buyer = memberRepository.save(Member.create(
                    "buyer" + index + "@example.com",
                    "encoded-password",
                    "buyer" + index
            ));
            Product product = productRepository.save(Product.create(seller, "Product " + index, "description", 10_000L));
            Order order = orderRepository.save(Order.create(buyer, product));
            paymentRepository.save(Payment.approve(order, "payment-" + index));
            Delivery delivery = deliveryRepository.save(Delivery.start(order, "tracking-" + index));
            delivery.complete();
            order.confirm();
            settlementRepository.save(Settlement.create(order));
        }
        return seller;
    }
}
```

- [ ] **Step 3: 정산 조회 최적화 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.jpalab.SettlementQueryOptimizationTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: 커밋한다**

```powershell
git add backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java `
  backend/src/test/java/com/sweet/market/jpalab/SettlementQueryOptimizationTest.java
git commit -m "test: add settlement query optimization lab"
```

---

## Task 5: 전체 검증과 마무리

**Files:**

- Verify only

- [ ] **Step 1: focused API와 lab 테스트를 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test `
  --tests "com.sweet.market.order.OrderQueryApiTest" `
  --tests "com.sweet.market.jpalab.ProductQueryOptimizationTest" `
  --tests "com.sweet.market.jpalab.OrderQueryOptimizationTest" `
  --tests "com.sweet.market.jpalab.SettlementQueryOptimizationTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: 전체 backend 테스트를 강제 재실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --rerun-tasks
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 테스트 이름 규칙을 확인한다**

Run:

```powershell
cd ..
rg -n "void [a-zA-Z0-9]+\(" backend\src\test\java
```

Expected: only helper/lifecycle methods are reported, such as:

```text
backend\src\test\java\com\sweet\market\support\IntegrationTestSupport.java:39:    static void overrideProperties(DynamicPropertyRegistry registry) {
backend\src\test\java\com\sweet\market\support\IntegrationTestSupport.java:49:    void cleanUp() {
backend\src\test\java\com\sweet\market\jpalab\OptimisticLockTest.java:76:    private void rollbackIfActive(EntityTransaction transaction) {
```

- [ ] **Step 4: 작업트리 상태를 확인한다**

Run:

```powershell
git status --short --branch
```

Expected:

```text
## codex/milestone-5-confirm-settlement [ahead N]
 M backend/src/main/resources/application.yaml
?? docs/superpowers/handoffs/2026-06-09-milestone-3-handoff.md
```

The `application.yaml` modification and milestone 3 handoff file are existing local state and must not be included in Milestone 6 commits.

---

## Self-Review

- Spec coverage: order list API, order detail API, product/order/settlement N+1 reproduction, and optimized query verification are each covered by tasks.
- Placeholder scan: no placeholder markers or vague deferred-work instructions are used.
- Type consistency: `OrderSummaryResponse`, `OrderQueryService`, `QueryOptimizationTestSupport`, and test class names match across all tasks.
- Test naming: every new `@Test` method shown uses Korean_with_underscores.
- Scope check: no seller order management, admin reads, cursor pagination, DTO projection migration, or frontend work is included.
