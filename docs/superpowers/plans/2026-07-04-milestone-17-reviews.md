# Milestone 17 Reviews Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add buyer reviews for confirmed purchases and show product/seller rating trust signals on product detail pages.

**Architecture:** Add a focused `review` backend package with a `Review` entity tied one-to-one to `Order`, a protected order-scoped create API, and public product-scoped review reads. Extend product detail and my-order read models, then add a compact inline review form on `/me/orders` and review summary/list UI on product detail.

**Tech Stack:** Spring Boot, Spring Data JPA, PostgreSQL/Testcontainers, JUnit 5, MockMvc, React, TypeScript, TanStack Query, React Router, Vite.

---

## Pre-Execution Notes

- Execute implementation in an isolated worktree when possible.
- Keep `backend/src/main/resources/application.yaml` untouched; it has an existing local-only development change.
- New JUnit `@Test` method names must be Korean with underscores.
- Use JDK 21 for backend commands.
- Do not add review edit/delete, review images, moderation, seller replies, a My Reviews page, or product-card ratings.
- The design spec is `docs/superpowers/specs/2026-07-04-milestone-17-reviews-design.md`.

Recommended execution setup:

```powershell
git status --short --branch --untracked-files=all
git worktree add .worktrees/milestone-17-reviews -b codex/milestone-17-reviews main
cd .worktrees/milestone-17-reviews
```

Expected: new worktree on `codex/milestone-17-reviews`. If the branch or worktree already exists, inspect it first instead of recreating it.

## File Structure

Create backend review package:

- Create: `backend/src/main/java/com/sweet/market/review/domain/Review.java` - review row for one confirmed order.
- Create: `backend/src/main/java/com/sweet/market/review/repository/ReviewRepository.java` - duplicate checks, product review page, product/seller summaries.
- Create: `backend/src/main/java/com/sweet/market/review/api/ReviewCreateRequest.java` - rating/content validation.
- Create: `backend/src/main/java/com/sweet/market/review/api/ReviewResponse.java` - write response.
- Create: `backend/src/main/java/com/sweet/market/review/api/ProductReviewResponse.java` - product review list item.
- Create: `backend/src/main/java/com/sweet/market/review/api/ReviewController.java` - protected `POST /api/orders/{orderId}/review`.
- Create: `backend/src/main/java/com/sweet/market/review/api/ProductReviewController.java` - public `GET /api/products/{productId}/reviews`.
- Create: `backend/src/main/java/com/sweet/market/review/application/ReviewService.java` - review creation write rules.
- Create: `backend/src/main/java/com/sweet/market/review/query/ProductReviewQueryService.java` - product visibility and paged review reads.
- Create: `backend/src/main/java/com/sweet/market/review/query/ReviewSummary.java` - count/average pair for product and seller summaries.

Modify backend files:

- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java` - add review error codes.
- Modify: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java` - add review creation fetch and reviewed projection.
- Modify: `backend/src/main/java/com/sweet/market/order/query/OrderQueryService.java` - map `reviewed` into my-order summaries.
- Modify: `backend/src/main/java/com/sweet/market/order/api/OrderSummaryResponse.java` - add `reviewed`.
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductResponse.java` - add review summary fields.
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java` - add buyer-visible detail fetch.
- Modify: `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java` - broaden product detail visibility and attach summaries.
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java` - truncate `reviews`.

Create backend tests:

- Create: `backend/src/test/java/com/sweet/market/review/ReviewApiTest.java`.
- Create: `backend/src/test/java/com/sweet/market/review/ProductReviewApiTest.java`.

Create/modify web files:

- Create: `web/src/features/reviews/reviewApi.ts` - review API types and calls.
- Modify: `web/src/features/orders/orderApi.ts` - add `reviewed` to order summaries.
- Modify: `web/src/features/products/productApi.ts` - add product review summary fields and product review list API.
- Modify: `web/src/pages/MyOrdersPage.tsx` - add inline review form for confirmed unreviewed orders.
- Modify: `web/src/pages/ProductDetailPage.tsx` - add review summary and latest review list.
- Modify: `web/src/shared/styles.css` - add review form/list styles.

---

### Task 1: Backend Review Creation Tests

**Files:**
- Create: `backend/src/test/java/com/sweet/market/review/ReviewApiTest.java`

- [ ] **Step 1: Write failing review creation API tests**

Create `backend/src/test/java/com/sweet/market/review/ReviewApiTest.java`:

```java
package com.sweet.market.review;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class ReviewApiTest extends IntegrationTestSupport {

    @Test
    void 구매확정_주문에_리뷰를_작성할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro", "review-create.jpg");
        Long orderId = createConfirmedOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/review", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5,
                                  "content": "거래가 빠르고 상품 설명도 정확했어요."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.buyerNickname").value("buyer"))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.content").value("거래가 빠르고 상품 설명도 정확했어요."))
                .andExpect(jsonPath("$.data.createdAt", not(blankOrNullString())));

        assertThat(countReviewsByOrderId(orderId)).isEqualTo(1);
    }

    @Test
    void 다른_구매자의_주문에는_리뷰를_작성할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken, "MacBook Pro", "review-access.jpg");
        Long orderId = createConfirmedOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/review", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5,
                                  "content": "다른 구매자 주문 리뷰입니다."
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_ACCESS_DENIED"));

        assertThat(countReviewsByOrderId(orderId)).isZero();
    }

    @Test
    void 구매확정이_아닌_주문에는_리뷰를_작성할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro", "review-status.jpg");
        Long orderId = createDeliveredOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/review", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 4,
                                  "content": "아직 확정되지 않은 주문 리뷰입니다."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ORDER_NOT_CONFIRMED"));
    }

    @Test
    void 같은_주문에는_리뷰를_한_번만_작성할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro", "review-duplicate.jpg");
        Long orderId = createConfirmedOrder(buyerToken, productId);

        createReview(buyerToken, orderId, 5, "첫 번째 리뷰 내용입니다.");

        mockMvc.perform(post("/api/orders/{orderId}/review", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 3,
                                  "content": "두 번째 리뷰는 허용되지 않습니다."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_DUPLICATE"));

        assertThat(countReviewsByOrderId(orderId)).isEqualTo(1);
    }

    @Test
    void 리뷰_평점은_1점부터_5점까지_가능하다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro", "review-rating.jpg");
        Long orderId = createConfirmedOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/review", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 6,
                                  "content": "평점 범위를 벗어난 리뷰입니다."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));
    }

    @Test
    void 리뷰_내용은_10자_이상이어야_한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro", "review-content.jpg");
        Long orderId = createConfirmedOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/review", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 4,
                                  "content": "짧음"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
    }

    @Test
    void 내_주문_목록은_리뷰_작성_여부를_포함한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long unreviewedProductId = createProduct(sellerToken, "Unreviewed Product", "unreviewed.jpg");
        Long reviewedProductId = createProduct(sellerToken, "Reviewed Product", "reviewed.jpg");
        Long unreviewedOrderId = createConfirmedOrder(buyerToken, unreviewedProductId);
        Long reviewedOrderId = createConfirmedOrder(buyerToken, reviewedProductId);

        createReview(buyerToken, reviewedOrderId, 5, "이미 작성한 리뷰 내용입니다.");

        mockMvc.perform(get("/api/orders/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(reviewedOrderId))
                .andExpect(jsonPath("$.data.content[0].reviewed").value(true))
                .andExpect(jsonPath("$.data.content[1].id").value(unreviewedOrderId))
                .andExpect(jsonPath("$.data.content[1].reviewed").value(false));
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

    private Long createProduct(String accessToken, String title, String fileName) throws Exception {
        Long uploadId = uploadImage(accessToken, fileName);

        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(title, uploadId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private Long uploadImage(String accessToken, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        String response = mockMvc.perform(multipart("/api/product-image-uploads")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
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

    private Long createDeliveredOrder(String accessToken, Long productId) throws Exception {
        Long orderId = createOrder(accessToken, productId);
        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/{orderId}/start", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/{orderId}/complete", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        return orderId;
    }

    private Long createConfirmedOrder(String accessToken, Long productId) throws Exception {
        Long orderId = createDeliveredOrder(accessToken, productId);
        mockMvc.perform(post("/api/orders/{orderId}/confirm", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        return orderId;
    }

    private void createReview(String accessToken, Long orderId, int rating, String content) throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/review", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": %d,
                                  "content": "%s"
                                }
                                """.formatted(rating, content)))
                .andExpect(status().isCreated());
    }

    private long countReviewsByOrderId(Long orderId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE order_id = ?",
                Long.class,
                orderId
        );
        return count == null ? 0 : count;
    }
}
```

- [ ] **Step 2: Run the new test class to verify it fails**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.review.ReviewApiTest"
```

Expected: FAIL because `/api/orders/{orderId}/review`, review DTOs, and the `reviews` table do not exist yet.

- [ ] **Step 3: Commit the failing tests**

```powershell
git add -- backend/src/test/java/com/sweet/market/review/ReviewApiTest.java
git commit -m "test: cover review creation rules"
```

Expected: commit succeeds with only the new test file staged.

---

### Task 2: Backend Review Creation Implementation

**Files:**
- Create: `backend/src/main/java/com/sweet/market/review/domain/Review.java`
- Create: `backend/src/main/java/com/sweet/market/review/repository/ReviewRepository.java`
- Create: `backend/src/main/java/com/sweet/market/review/api/ReviewCreateRequest.java`
- Create: `backend/src/main/java/com/sweet/market/review/api/ReviewResponse.java`
- Create: `backend/src/main/java/com/sweet/market/review/api/ReviewController.java`
- Create: `backend/src/main/java/com/sweet/market/review/application/ReviewService.java`
- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Modify: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/order/query/OrderQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/order/api/OrderSummaryResponse.java`
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`

- [ ] **Step 1: Add review error codes**

Modify `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java` by adding these enum entries after `ORDER_CONFLICT`:

```java
    REVIEW_ACCESS_DENIED(HttpStatus.FORBIDDEN, "리뷰를 작성할 수 없는 주문입니다."),
    REVIEW_ORDER_NOT_CONFIRMED(HttpStatus.CONFLICT, "구매 확정된 주문만 리뷰를 작성할 수 있습니다."),
    REVIEW_DUPLICATE(HttpStatus.CONFLICT, "이미 리뷰를 작성한 주문입니다."),
```

Keep the semicolon on the final enum entry in the file.

- [ ] **Step 2: Create the Review entity**

Create `backend/src/main/java/com/sweet/market/review/domain/Review.java`:

```java
package com.sweet.market.review.domain;

import java.time.LocalDateTime;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reviews_order",
                columnNames = "order_id"
        ),
        indexes = {
                @Index(name = "idx_reviews_product_created_id", columnList = "product_id, created_at, id"),
                @Index(name = "idx_reviews_seller_id", columnList = "seller_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Review(Order order, int rating, String content) {
        this.order = order;
        this.buyer = order.getBuyer();
        this.product = order.getProduct();
        this.seller = order.getProduct().getSeller();
        this.rating = rating;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public static Review create(Order order, int rating, String content) {
        return new Review(order, rating, content);
    }
}
```

- [ ] **Step 3: Create review request and response DTOs**

Create `backend/src/main/java/com/sweet/market/review/api/ReviewCreateRequest.java`:

```java
package com.sweet.market.review.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewCreateRequest(
        @Min(1) @Max(5) int rating,
        @NotBlank @Size(min = 10, max = 500) String content
) {
}
```

Create `backend/src/main/java/com/sweet/market/review/api/ReviewResponse.java`:

```java
package com.sweet.market.review.api;

import java.time.LocalDateTime;

import com.sweet.market.review.domain.Review;

public record ReviewResponse(
        Long reviewId,
        Long orderId,
        Long productId,
        Long buyerId,
        String buyerNickname,
        int rating,
        String content,
        LocalDateTime createdAt
) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getOrder().getId(),
                review.getProduct().getId(),
                review.getBuyer().getId(),
                review.getBuyer().getNickname(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}
```

- [ ] **Step 4: Create ReviewRepository**

Create `backend/src/main/java/com/sweet/market/review/repository/ReviewRepository.java`:

```java
package com.sweet.market.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.review.domain.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderId(Long orderId);

    long countByOrderId(Long orderId);
}
```

- [ ] **Step 5: Add order fetch for review creation**

Modify `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java` by adding:

```java
    @EntityGraph(attributePaths = {"buyer", "product", "product.seller"})
    Optional<Order> findReviewTargetById(Long id);
```

- [ ] **Step 6: Implement ReviewService**

Create `backend/src/main/java/com/sweet/market/review/application/ReviewService.java`:

```java
package com.sweet.market.review.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.review.api.ReviewResponse;
import com.sweet.market.review.domain.Review;
import com.sweet.market.review.repository.ReviewRepository;

@Service
public class ReviewService {

    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;

    public ReviewService(OrderRepository orderRepository, ReviewRepository reviewRepository) {
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public ReviewResponse create(Long buyerId, Long orderId, int rating, String content) {
        Order order = orderRepository.findReviewTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.REVIEW_ORDER_NOT_CONFIRMED);
        }
        if (reviewRepository.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.REVIEW_DUPLICATE);
        }

        try {
            Review review = reviewRepository.saveAndFlush(Review.create(order, rating, content));
            return ReviewResponse.from(review);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.REVIEW_DUPLICATE);
        }
    }
}
```

- [ ] **Step 7: Create ReviewController**

Create `backend/src/main/java/com/sweet/market/review/api/ReviewController.java`:

```java
package com.sweet.market.review.api;

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
import com.sweet.market.review.application.ReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders/{orderId}/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> create(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(reviewService.create(member.id(), orderId, request.rating(), request.content()));
    }
}
```

- [ ] **Step 8: Add reviewed to order summaries**

Modify `backend/src/main/java/com/sweet/market/order/api/OrderSummaryResponse.java`:

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
        LocalDateTime orderedAt,
        boolean reviewed
) {

    public static OrderSummaryResponse from(Order order) {
        return from(order, false);
    }

    public static OrderSummaryResponse from(Order order, boolean reviewed) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getProduct().getId(),
                order.getProduct().getTitle(),
                order.getProduct().getPrice(),
                order.getProduct().getSeller().getId(),
                order.getProduct().getSeller().getNickname(),
                order.getStatus().name(),
                order.getProduct().getStatus().name(),
                order.getOrderedAt(),
                reviewed
        );
    }
}
```

Modify `backend/src/main/java/com/sweet/market/order/query/OrderQueryService.java` to inject `ReviewRepository` and map each summary with an existence check:

```java
private final ReviewRepository reviewRepository;

public OrderQueryService(OrderRepository orderRepository, ReviewRepository reviewRepository) {
    this.orderRepository = orderRepository;
    this.reviewRepository = reviewRepository;
}
```

Replace the `findMine` mapping with:

```java
return orderRepository.findByBuyerIdOrderByIdDesc(buyerId, pageable)
        .map(order -> OrderSummaryResponse.from(order, reviewRepository.existsByOrderId(order.getId())));
```

Add this import:

```java
import com.sweet.market.review.repository.ReviewRepository;
```

- [ ] **Step 9: Include reviews in test cleanup**

Modify `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java` so `cleanUp()` truncates `reviews` before `orders`:

```java
jdbcTemplate.execute("TRUNCATE TABLE settlements, deliveries, payments, reviews, orders, cart_items, wishlist_items, product_image_uploads, product_images, products, members RESTART IDENTITY CASCADE");
```

- [ ] **Step 10: Run review creation tests**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.review.ReviewApiTest"
```

Expected: PASS for `ReviewApiTest`.

- [ ] **Step 11: Commit review creation implementation**

```powershell
git add -- backend/src/main/java/com/sweet/market/common/error/ErrorCode.java backend/src/main/java/com/sweet/market/order backend/src/main/java/com/sweet/market/review backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
git commit -m "feat: add confirmed order reviews"
```

Expected: commit succeeds and does not include `backend/src/main/resources/application.yaml`.

---

### Task 3: Product Review Reads And Rating Summaries

**Files:**
- Create: `backend/src/test/java/com/sweet/market/review/ProductReviewApiTest.java`
- Create: `backend/src/main/java/com/sweet/market/review/api/ProductReviewResponse.java`
- Create: `backend/src/main/java/com/sweet/market/review/api/ProductReviewController.java`
- Create: `backend/src/main/java/com/sweet/market/review/query/ProductReviewQueryService.java`
- Create: `backend/src/main/java/com/sweet/market/review/query/ReviewSummary.java`
- Modify: `backend/src/main/java/com/sweet/market/review/repository/ReviewRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java`

- [ ] **Step 1: Write failing product review read tests**

Create `backend/src/test/java/com/sweet/market/review/ProductReviewApiTest.java` with tests for these behaviors:

```java
@Test
void 상품_상세는_상품과_판매자_리뷰_요약을_포함한다() throws Exception
```

This test should:

- Create one seller, two buyers, and two products owned by the same seller.
- Confirm one order for the first product and one order for the second product.
- Create reviews with ratings `5` and `3`.
- `GET /api/products/{productId}` for the first product.
- Expect `reviewCount=1`, `averageRating=5.0`, `sellerReviewCount=2`, and `sellerAverageRating=4.0`.

```java
@Test
void 상품_리뷰는_최신순으로_조회된다() throws Exception
```

This test should:

- Create two confirmed orders for the same product by two buyers.
- Create two reviews.
- Update review `created_at` values through `jdbcTemplate` to `2026-01-01 10:00:00` and `2026-01-02 10:00:00`.
- `GET /api/products/{productId}/reviews`.
- Expect the newer review at `content[0]` and older review at `content[1]`.

```java
@Test
void 상품_리뷰는_페이지로_조회된다() throws Exception
```

This test should:

- Create two reviews for the same product.
- `GET /api/products/{productId}/reviews?size=1&page=0`.
- Expect `content.length()=1`, `totalElements=2`, and `totalPages=2`.

```java
@Test
void 판매완료_상품도_상세와_리뷰를_조회할_수_있다() throws Exception
```

This test should:

- Create one confirmed order, which makes the product `SOLD_OUT`.
- Create a review.
- `GET /api/products/{productId}` returns `200`.
- `GET /api/products/{productId}/reviews` returns `200`.

```java
@Test
void 숨김_상품은_상세와_리뷰를_공개_조회할_수_없다() throws Exception
```

This test should:

- Create a product and hide it with `DELETE /api/products/{productId}` as the seller.
- `GET /api/products/{productId}` returns `404` with `PRODUCT_NOT_FOUND`.
- `GET /api/products/{productId}/reviews` returns `404` with `PRODUCT_NOT_FOUND`.

```java
@Test
void 상품_목록은_판매완료_상품을_포함하지_않는다() throws Exception
```

This test should:

- Create an `ON_SALE` product and a confirmed-order `SOLD_OUT` product.
- `GET /api/products`.
- Expect only the `ON_SALE` product in the page.

Use helper methods from `ReviewApiTest` structure directly in this test class so the file is self-contained.

- [ ] **Step 2: Run product review tests to verify they fail**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.review.ProductReviewApiTest"
```

Expected: FAIL because product review endpoint and rating summary fields do not exist.

- [ ] **Step 3: Add product review DTO and summary record**

Create `backend/src/main/java/com/sweet/market/review/api/ProductReviewResponse.java`:

```java
package com.sweet.market.review.api;

import java.time.LocalDateTime;

import com.sweet.market.review.domain.Review;

public record ProductReviewResponse(
        Long reviewId,
        Long orderId,
        Long productId,
        Long buyerId,
        String buyerNickname,
        int rating,
        String content,
        LocalDateTime createdAt
) {

    public static ProductReviewResponse from(Review review) {
        return new ProductReviewResponse(
                review.getId(),
                review.getOrder().getId(),
                review.getProduct().getId(),
                review.getBuyer().getId(),
                review.getBuyer().getNickname(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}
```

Create `backend/src/main/java/com/sweet/market/review/query/ReviewSummary.java`:

```java
package com.sweet.market.review.query;

public record ReviewSummary(
        long reviewCount,
        Double averageRating
) {

    public static ReviewSummary empty() {
        return new ReviewSummary(0, null);
    }
}
```

- [ ] **Step 4: Extend ReviewRepository for reads and summaries**

Modify `backend/src/main/java/com/sweet/market/review/repository/ReviewRepository.java`:

```java
package com.sweet.market.review.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.review.domain.Review;
import com.sweet.market.review.query.ReviewSummary;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderId(Long orderId);

    long countByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "product", "buyer"})
    Page<Review> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    @Query("""
            select new com.sweet.market.review.query.ReviewSummary(
                count(r),
                avg(r.rating)
            )
            from Review r
            where r.product.id = :productId
            """)
    ReviewSummary summarizeByProductId(@Param("productId") Long productId);

    @Query("""
            select new com.sweet.market.review.query.ReviewSummary(
                count(r),
                avg(r.rating)
            )
            from Review r
            where r.seller.id = :sellerId
            """)
    ReviewSummary summarizeBySellerId(@Param("sellerId") Long sellerId);
}
```

- [ ] **Step 5: Broaden product detail visibility and add summaries**

Modify `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java` by adding:

```java
    @EntityGraph(attributePaths = {"seller", "images"})
    @Query("""
            select p
            from Product p
            where p.id = :id
              and p.status <> com.sweet.market.product.domain.ProductStatus.HIDDEN
            """)
    Optional<Product> findBuyerVisibleDetailById(@Param("id") Long id);
```

Modify `backend/src/main/java/com/sweet/market/product/api/ProductResponse.java` to include summary fields:

```java
public record ProductResponse(
        Long id,
        Long sellerId,
        String sellerNickname,
        String title,
        String description,
        long price,
        String status,
        List<ProductImageResponse> images,
        long wishlistCount,
        boolean wishlisted,
        boolean carted,
        long reviewCount,
        Double averageRating,
        long sellerReviewCount,
        Double sellerAverageRating
)
```

Add an overload:

```java
public static ProductResponse from(
        Product product,
        long wishlistCount,
        boolean wishlisted,
        boolean carted,
        long reviewCount,
        Double averageRating,
        long sellerReviewCount,
        Double sellerAverageRating
) {
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
                    .toList(),
            wishlistCount,
            wishlisted,
            carted,
            reviewCount,
            averageRating,
            sellerReviewCount,
            sellerAverageRating
    );
}
```

Keep existing `from(...)` methods by delegating to the new overload with `0, null, 0, null`.

Modify `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java`:

- Inject `ReviewRepository`.
- Replace `findWithSellerAndImagesByIdAndStatus(productId, ProductStatus.ON_SALE)` with `findBuyerVisibleDetailById(productId)`.
- After wishlist/cart values, load:

```java
ReviewSummary productSummary = reviewRepository.summarizeByProductId(productId);
ReviewSummary sellerSummary = reviewRepository.summarizeBySellerId(product.getSeller().getId());
```

- Return:

```java
return ProductResponse.from(
        product,
        wishlistCount,
        wishlisted,
        carted,
        productSummary.reviewCount(),
        productSummary.averageRating(),
        sellerSummary.reviewCount(),
        sellerSummary.averageRating()
);
```

Add imports:

```java
import com.sweet.market.review.query.ReviewSummary;
import com.sweet.market.review.repository.ReviewRepository;
```

- [ ] **Step 6: Add product review query service and controller**

Create `backend/src/main/java/com/sweet/market/review/query/ProductReviewQueryService.java`:

```java
package com.sweet.market.review.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.review.api.ProductReviewResponse;
import com.sweet.market.review.repository.ReviewRepository;

@Service
public class ProductReviewQueryService {

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;

    public ProductReviewQueryService(ProductRepository productRepository, ReviewRepository reviewRepository) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductReviewResponse> findByProductId(Long productId, Pageable pageable) {
        productRepository.findBuyerVisibleDetailById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        return reviewRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId, pageable)
                .map(ProductReviewResponse::from);
    }
}
```

Create `backend/src/main/java/com/sweet/market/review/api/ProductReviewController.java`:

```java
package com.sweet.market.review.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.review.query.ProductReviewQueryService;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ProductReviewController {

    private final ProductReviewQueryService productReviewQueryService;

    public ProductReviewController(ProductReviewQueryService productReviewQueryService) {
        this.productReviewQueryService = productReviewQueryService;
    }

    @GetMapping
    public ApiResponse<Page<ProductReviewResponse>> list(
            @PathVariable Long productId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(productReviewQueryService.findByProductId(productId, pageable));
    }
}
```

- [ ] **Step 7: Run product review tests**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.review.ProductReviewApiTest"
```

Expected: PASS for `ProductReviewApiTest`.

- [ ] **Step 8: Run all review tests**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.review.*"
```

Expected: PASS for both review test classes.

- [ ] **Step 9: Commit product review reads**

```powershell
git add -- backend/src/main/java/com/sweet/market/product backend/src/main/java/com/sweet/market/review backend/src/test/java/com/sweet/market/review/ProductReviewApiTest.java
git commit -m "feat: expose product review summaries"
```

Expected: commit succeeds and does not include `backend/src/main/resources/application.yaml`.

---

### Task 4: Web Review API Types

**Files:**
- Create: `web/src/features/reviews/reviewApi.ts`
- Modify: `web/src/features/orders/orderApi.ts`
- Modify: `web/src/features/products/productApi.ts`

- [ ] **Step 1: Add web review API module**

Create `web/src/features/reviews/reviewApi.ts`:

```ts
import { api } from '../../shared/api/http';
import { type Page } from '../products/productApi';

export type Review = {
  reviewId: number;
  orderId: number;
  productId: number;
  buyerId: number;
  buyerNickname: string;
  rating: number;
  content: string;
  createdAt: string;
};

export type ReviewCreateInput = {
  rating: number;
  content: string;
};

export function createReview(orderId: number, input: ReviewCreateInput) {
  return api<Review>(`/api/orders/${orderId}/review`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function getProductReviews(productId: number) {
  return api<Page<Review>>(`/api/products/${productId}/reviews`);
}
```

- [ ] **Step 2: Extend order and product frontend types**

Modify `web/src/features/orders/orderApi.ts`:

```ts
export type OrderSummary = {
  id: number;
  productId: number;
  productTitle: string;
  productPrice: number;
  sellerId: number;
  sellerNickname: string;
  status: OrderStatus;
  productStatus: string;
  orderedAt: string;
  reviewed: boolean;
};
```

Modify `web/src/features/products/productApi.ts`:

```ts
export type Product = Omit<ProductSummary, 'thumbnailUrl'> & {
  description: string;
  images: ProductImage[];
  reviewCount: number;
  averageRating: number | null;
  sellerReviewCount: number;
  sellerAverageRating: number | null;
};
```

- [ ] **Step 3: Run web typecheck through build**

```powershell
cd web
npm run build
```

Expected: build passes or fails only where pages still need to consume the new API.

- [ ] **Step 4: Commit API type changes**

```powershell
git add -- web/src/features/reviews/reviewApi.ts web/src/features/orders/orderApi.ts web/src/features/products/productApi.ts
git commit -m "feat: add review web api types"
```

Expected: commit succeeds with only web API/type files staged.

---

### Task 5: My Orders Inline Review Form

**Files:**
- Modify: `web/src/pages/MyOrdersPage.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Add review mutation and pending state**

Modify imports in `web/src/pages/MyOrdersPage.tsx`:

```ts
import { type FormEvent, useRef, useState } from 'react';
import { createReview } from '../features/reviews/reviewApi';
```

Add state inside `MyOrdersPage`:

```ts
const [reviewingOrderId, setReviewingOrderId] = useState<number | null>(null);
const [reviewRating, setReviewRating] = useState(5);
const [reviewContent, setReviewContent] = useState('');
```

Add mutation:

```ts
const reviewMutation = useMutation({
  mutationFn: (order: OrderSummary) =>
    createReview(order.id, {
      rating: reviewRating,
      content: reviewContent,
    }),
  onSuccess: async (_review, order) => {
    setReviewingOrderId(null);
    setReviewRating(5);
    setReviewContent('');
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
      queryClient.invalidateQueries({ queryKey: ['products'] }),
      queryClient.invalidateQueries({ queryKey: ['products', order.productId] }),
      queryClient.invalidateQueries({ queryKey: ['product-reviews', order.productId] }),
    ]);
  },
});
```

Include `reviewMutation.error` in `actionError`.

- [ ] **Step 2: Add submit helper**

Add inside `MyOrdersPage`:

```ts
function submitReview(event: FormEvent<HTMLFormElement>, order: OrderSummary) {
  event.preventDefault();

  if (reviewMutation.isPending) {
    return;
  }

  reviewMutation.mutate(order);
}
```

- [ ] **Step 3: Render review actions for confirmed orders**

Replace the `CONFIRMED` branch in `renderOrderActions`:

```tsx
case 'CONFIRMED':
  if (order.reviewed) {
    return <span className="muted-text">리뷰 작성 완료</span>;
  }

  return (
    <button
      type="button"
      className="text-button"
      onClick={() => {
        setReviewingOrderId(order.id);
        setReviewRating(5);
        setReviewContent('');
      }}
    >
      리뷰 작성
    </button>
  );
case 'CANCELED':
  return <span className="muted-text">진행 가능한 작업이 없습니다.</span>;
```

- [ ] **Step 4: Render inline review form in each order card**

Inside the order card JSX, immediately after `<div className="record-actions">{renderOrderActions(order)}</div>`, add:

```tsx
{reviewingOrderId === order.id ? (
  <form className="review-form" onSubmit={(event) => submitReview(event, order)}>
    <fieldset className="rating-field" disabled={reviewMutation.isPending}>
      <legend>평점</legend>
      {[1, 2, 3, 4, 5].map((rating) => (
        <button
          key={rating}
          type="button"
          className={rating <= reviewRating ? 'rating-button rating-button-selected' : 'rating-button'}
          onClick={() => setReviewRating(rating)}
          aria-pressed={rating <= reviewRating}
        >
          {rating}
        </button>
      ))}
    </fieldset>
    <label>
      리뷰 내용
      <textarea
        value={reviewContent}
        minLength={10}
        maxLength={500}
        required
        rows={4}
        onChange={(event) => setReviewContent(event.target.value)}
      />
    </label>
    <div className="review-form-actions">
      <button type="submit" className="text-button" disabled={reviewMutation.isPending}>
        {reviewMutation.isPending ? '등록 중' : '등록'}
      </button>
      <button
        type="button"
        className="text-button secondary-button"
        disabled={reviewMutation.isPending}
        onClick={() => setReviewingOrderId(null)}
      >
        취소
      </button>
    </div>
  </form>
) : null}
```

- [ ] **Step 5: Add review form CSS**

Append to `web/src/shared/styles.css` near record styles:

```css
.review-form {
  display: grid;
  gap: 14px;
  border-top: 1px solid #dfe6ee;
  padding-top: 18px;
}

.review-form label {
  display: grid;
  gap: 8px;
  color: #52616f;
  font-weight: 800;
}

.review-form textarea {
  width: 100%;
  min-height: 112px;
  resize: vertical;
  border: 1px solid #cfd9e3;
  border-radius: 8px;
  padding: 12px 14px;
  color: #172026;
}

.rating-field {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  border: 0;
  padding: 0;
  margin: 0;
}

.rating-field legend {
  width: 100%;
  margin-bottom: 2px;
  color: #52616f;
  font-weight: 800;
}

.rating-button {
  width: 40px;
  height: 40px;
  border: 1px solid #cfd9e3;
  border-radius: 8px;
  background: #ffffff;
  color: #52616f;
  font-weight: 800;
}

.rating-button-selected {
  border-color: #172026;
  background: #172026;
  color: #ffffff;
}

.review-form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
```

- [ ] **Step 6: Run web build**

```powershell
cd web
npm run build
```

Expected: build passes.

- [ ] **Step 7: Commit my orders review form**

```powershell
git add -- web/src/pages/MyOrdersPage.tsx web/src/shared/styles.css
git commit -m "feat: add order review form"
```

Expected: commit succeeds with the order page and stylesheet changes.

---

### Task 6: Product Detail Review UI

**Files:**
- Modify: `web/src/pages/ProductDetailPage.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Add product reviews query**

Modify imports in `web/src/pages/ProductDetailPage.tsx`:

```ts
import { getProductReviews } from '../features/reviews/reviewApi';
```

Add formatter near existing formatters:

```ts
const ratingFormatter = new Intl.NumberFormat('ko-KR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
});
```

Add query after the product query:

```ts
const { data: reviews } = useQuery({
  queryKey: ['product-reviews', parsedProductId],
  queryFn: () => getProductReviews(parsedProductId ?? 0),
  enabled: hasValidProductId,
});
```

- [ ] **Step 2: Render review summary and list**

Inside `ProductDetailPage`, after the product actions and error messages, add:

```tsx
<section className="product-review-section" aria-labelledby="product-review-title">
  <div className="review-summary">
    <div>
      <h2 id="product-review-title">상품 리뷰</h2>
      <strong>{formatRating(product.averageRating)}</strong>
      <span>{product.reviewCount}개 리뷰</span>
    </div>
    <div>
      <h2>판매자 평점</h2>
      <strong>{formatRating(product.sellerAverageRating)}</strong>
      <span>{product.sellerReviewCount}개 리뷰</span>
    </div>
  </div>
  {reviews?.content.length ? (
    <div className="review-list">
      {reviews.content.map((review) => (
        <article className="review-item" key={review.reviewId}>
          <div className="review-item-heading">
            <strong>{review.buyerNickname}</strong>
            <span>{formatRating(review.rating)}</span>
          </div>
          <p>{review.content}</p>
          <time dateTime={review.createdAt}>{dateFormatter.format(new Date(review.createdAt))}</time>
        </article>
      ))}
    </div>
  ) : (
    <p className="muted-text">아직 작성된 리뷰가 없습니다.</p>
  )}
</section>
```

Add helper:

```ts
function formatRating(value: number | null) {
  if (value === null) {
    return '-';
  }

  return ratingFormatter.format(value);
}
```

- [ ] **Step 3: Add product review CSS**

Append to `web/src/shared/styles.css` near product detail styles:

```css
.product-review-section {
  display: grid;
  gap: 18px;
  border-top: 1px solid #dfe6ee;
  padding-top: 22px;
}

.review-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.review-summary > div {
  display: grid;
  gap: 6px;
  border: 1px solid #dfe6ee;
  border-radius: 8px;
  padding: 14px;
  background: #f8fafc;
}

.review-summary h2 {
  margin: 0;
  color: #52616f;
  font-size: 14px;
}

.review-summary strong {
  font-size: 24px;
  color: #172026;
}

.review-summary span,
.review-item time {
  color: #637282;
  font-size: 14px;
}

.review-list {
  display: grid;
  gap: 12px;
}

.review-item {
  display: grid;
  gap: 8px;
  border: 1px solid #dfe6ee;
  border-radius: 8px;
  padding: 14px;
}

.review-item-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.review-item p {
  margin: 0;
  color: #52616f;
  line-height: 1.7;
  white-space: pre-wrap;
}
```

Inside the existing mobile media query, add:

```css
  .review-summary {
    grid-template-columns: 1fr;
  }
```

- [ ] **Step 4: Run web build**

```powershell
cd web
npm run build
```

Expected: build passes.

- [ ] **Step 5: Commit product detail review UI**

```powershell
git add -- web/src/pages/ProductDetailPage.tsx web/src/shared/styles.css
git commit -m "feat: show product reviews"
```

Expected: commit succeeds with only product detail review UI changes.

---

### Task 7: Final Verification And Handoff

**Files:**
- Create: `docs/superpowers/handoffs/2026-07-04-milestone-17-reviews-handoff.md`

- [ ] **Step 1: Run backend full test suite**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```

Expected: all backend tests pass.

- [ ] **Step 2: Run web build**

```powershell
cd web
npm run build
```

Expected: Vite build completes successfully.

- [ ] **Step 3: Run whitespace check**

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 4: Verify git status excludes local application yaml**

```powershell
git status --short --branch
```

Expected: milestone files are committed or intentionally staged for the handoff commit. `backend/src/main/resources/application.yaml` may appear as a pre-existing local modification in the main checkout and must not be staged.

- [ ] **Step 5: Create handoff**

Create `docs/superpowers/handoffs/2026-07-04-milestone-17-reviews-handoff.md`:

```markdown
# Milestone 17 Reviews Handoff

## Completed

- Added buyer reviews for confirmed orders.
- Enforced one review per order.
- Added product review list API.
- Added product and seller rating summaries to product detail.
- Added reviewed state to my order summaries.
- Broadened public product detail reads to buyer-visible `ON_SALE`, `RESERVED`, and `SOLD_OUT` products.
- Kept public product listing limited to `ON_SALE` products.
- Added inline review creation on `/me/orders`.
- Added review summary and latest reviews on product detail.

## Verification

- Backend full suite passed:
  - `cd backend`
  - `$env:JAVA_HOME='C:\java\jdk-21'`
  - `$env:PATH="$env:JAVA_HOME\bin;$env:PATH"`
  - `$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`
  - `.\gradlew.bat --no-daemon test`
- Web build passed:
  - `cd web`
  - `npm run build`
- Repo checks passed:
  - `git diff --check`

## Local Notes

- Review edit/delete, review images, moderation, seller replies, My Reviews, and product-card ratings remain out of scope.
- `backend/src/main/resources/application.yaml` has a pre-existing local-only development change and was not touched.

## Follow-Up Candidates

- Cancellation and refund flow.
- Review edit and delete.
- Review images.
- Seller replies.
- Product card rating snippets.
```

- [ ] **Step 6: Commit handoff**

```powershell
git add -- docs/superpowers/handoffs/2026-07-04-milestone-17-reviews-handoff.md
git commit -m "docs: add milestone 17 reviews handoff"
```

Expected: branch contains milestone commits and no unintended files are staged.

## Self-Review

- Spec coverage: creation rules, duplicate prevention, product review reads, product/seller summaries, my-order `reviewed`, product detail visibility, web review form, product detail reviews, and final verification all have tasks.
- Scope check: edit/delete, images, moderation, seller replies, My Reviews, and product-card ratings are excluded.
- Type consistency: backend uses `reviewId`, `orderId`, `productId`, `buyerId`, `buyerNickname`, `rating`, `content`, `createdAt`; web uses the same names.
- Product visibility: product list remains `ON_SALE`; product detail and reviews allow `ON_SALE`, `RESERVED`, and `SOLD_OUT`; `HIDDEN` returns `PRODUCT_NOT_FOUND`.
