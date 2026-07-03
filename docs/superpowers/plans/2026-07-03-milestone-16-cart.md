# Milestone 16 Cart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a buyer cart so authenticated buyers can add products, review selected cart items, and convert selected items into one existing-style order per product.

**Architecture:** Add a focused `cart` backend package with a buyer-owned `CartItem`, idempotent add/remove APIs, a paged buyer cart query, and an all-or-nothing checkout service that reuses the existing one-product `Order` model. Extend product read DTOs with viewer-specific `carted`, then add web cart toggles and a protected `/me/cart` page.

**Tech Stack:** Spring Boot, Spring Data JPA, PostgreSQL/Testcontainers, JUnit 5, MockMvc, React, TypeScript, TanStack Query, React Router, Vite.

---

## Pre-Execution Notes

- Execute implementation in an isolated worktree when possible.
- Keep `backend/src/main/resources/application.yaml` untouched; it has an existing local-only development change.
- New JUnit `@Test` method names must be Korean with underscores.
- Use JDK 21 for backend commands.
- Do not add quantity, coupons, order grouping, wishlist-to-cart automation, or public cart counts.

Recommended execution setup:

```powershell
git status --short --branch --untracked-files=all
git worktree add .worktrees/milestone-16-cart -b codex/milestone-16-cart main
cd .worktrees/milestone-16-cart
```

Expected: new worktree on `codex/milestone-16-cart`. If the branch or worktree already exists, inspect it first instead of recreating it.

## File Structure

Create backend cart package:

- Create: `backend/src/main/java/com/sweet/market/cart/domain/CartItem.java` - cart row entity owned by a buyer and linked to one product.
- Create: `backend/src/main/java/com/sweet/market/cart/repository/CartItemRepository.java` - existence checks, idempotent delete, cart page projection, checkout loading.
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartResponse.java` - add/remove response with `productId` and `carted`.
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartItemResponse.java` - `/api/me/cart` page item.
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartCheckoutRequest.java` - selected `cartItemIds`.
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartCheckoutResponse.java` - generated order summaries.
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartController.java` - product-scoped add/remove endpoints.
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartQueryController.java` - `/api/me/cart` endpoint.
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartCheckoutController.java` - checkout endpoint.
- Create: `backend/src/main/java/com/sweet/market/cart/application/CartService.java` - add/remove and checkout write behavior.
- Create: `backend/src/main/java/com/sweet/market/cart/query/CartQueryService.java` - read-only cart page query.
- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java` - cart error codes.
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductSummaryResponse.java` - add `carted`.
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductResponse.java` - add `carted`.
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java` - project `carted`.
- Modify: `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java` - inject `CartItemRepository` and resolve detail `carted`.
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java` - truncate `cart_items`.

Create backend tests:

- Create: `backend/src/test/java/com/sweet/market/cart/CartApiTest.java`.
- Create: `backend/src/test/java/com/sweet/market/cart/CartCheckoutApiTest.java`.

Create/modify web files:

- Create: `web/src/features/cart/cartApi.ts` - cart API types and calls.
- Create: `web/src/features/cart/CartToggle.tsx` - product-card/detail cart toggle.
- Create: `web/src/pages/MyCartPage.tsx` - protected buyer cart page with checkboxes and checkout.
- Modify: `web/src/features/products/productApi.ts` - add `carted` to product types.
- Modify: `web/src/pages/HomePage.tsx` - show cart toggle on product cards.
- Modify: `web/src/pages/ProductDetailPage.tsx` - show cart toggle and invalidate cart after immediate order.
- Modify: `web/src/app/router.tsx` - add `/me/cart`.
- Modify: `web/src/shared/layout/Shell.tsx` - add `장바구니` nav link.
- Modify: `web/src/features/auth/AuthProvider.tsx` - clear `my-cart` query on auth changes.
- Modify: `web/src/shared/styles.css` - cart button/page styles.

---

### Task 1: Backend Cart Entity And Repository

**Files:**
- Create: `backend/src/main/java/com/sweet/market/cart/domain/CartItem.java`
- Create: `backend/src/main/java/com/sweet/market/cart/repository/CartItemRepository.java`
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`
- Test: `backend/src/test/java/com/sweet/market/cart/CartApiTest.java`

- [ ] **Step 1: Write the failing add/idempotency tests**

Create `backend/src/test/java/com/sweet/market/cart/CartApiTest.java` with this initial content:

```java
package com.sweet.market.cart;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

class CartApiTest extends IntegrationTestSupport {

    @Test
    void 판매중_상품을_장바구니에_담을_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.carted").value(true));

        assertThat(countCartItems(productId)).isEqualTo(1);
    }

    @Test
    void 같은_상품을_두_번_담아도_장바구니_항목은_하나만_생긴다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carted").value(true));

        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.carted").value(true));

        assertThat(countCartItems(productId)).isEqualTo(1);
    }

    @Test
    void 장바구니에서_상품을_제거할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.carted").value(false));

        assertThat(countCartItems(productId)).isZero();
    }

    @Test
    void 없는_장바구니_항목을_제거해도_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(delete("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.carted").value(false));
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
        Long uploadId = uploadImage(accessToken, "cart-product.jpg");

        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
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
                                """.formatted(uploadId)))
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

    private long countCartItems(Long productId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_items WHERE product_id = ?",
                Long.class,
                productId
        );
        return count == null ? 0 : count;
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
.\gradlew.bat --no-daemon test --tests "com.sweet.market.cart.CartApiTest"
```

Expected: FAIL because `/api/products/{productId}/cart` does not exist or `cart_items` table is missing.

- [ ] **Step 3: Create `CartItem` entity**

Create `backend/src/main/java/com/sweet/market/cart/domain/CartItem.java`:

```java
package com.sweet.market.cart.domain;

import java.time.LocalDateTime;

import com.sweet.market.member.domain.Member;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_items_buyer_product",
                columnNames = {"buyer_id", "product_id"}
        ),
        indexes = {
                @Index(name = "idx_cart_items_product_id", columnList = "product_id"),
                @Index(name = "idx_cart_items_buyer_created_id", columnList = "buyer_id, created_at, id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private CartItem(Member buyer, Product product) {
        this.buyer = buyer;
        this.product = product;
        this.createdAt = LocalDateTime.now();
    }

    public static CartItem create(Member buyer, Product product) {
        return new CartItem(buyer, product);
    }
}
```

- [ ] **Step 4: Create repository**

Create `backend/src/main/java/com/sweet/market/cart/repository/CartItemRepository.java`:

```java
package com.sweet.market.cart.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.cart.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    boolean existsByBuyerIdAndProductId(Long buyerId, Long productId);

    Optional<CartItem> findByBuyerIdAndProductId(Long buyerId, Long productId);

    long deleteByBuyerIdAndProductId(Long buyerId, Long productId);
}
```

- [ ] **Step 5: Add cart table to integration cleanup**

Modify `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java` cleanup SQL:

```java
jdbcTemplate.execute("TRUNCATE TABLE settlements, deliveries, payments, orders, cart_items, wishlist_items, product_image_uploads, product_images, products, members RESTART IDENTITY CASCADE");
```

- [ ] **Step 6: Run test and confirm controller is still missing**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.cart.CartApiTest"
```

Expected: FAIL with 404 or missing handler for cart endpoints, not with table cleanup errors.

- [ ] **Step 7: Commit**

```powershell
git add -- backend/src/main/java/com/sweet/market/cart/domain/CartItem.java backend/src/main/java/com/sweet/market/cart/repository/CartItemRepository.java backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java backend/src/test/java/com/sweet/market/cart/CartApiTest.java
git commit -m "test: cover cart add remove basics"
```

---

### Task 2: Backend Cart Add And Remove API

**Files:**
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartResponse.java`
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartController.java`
- Create: `backend/src/main/java/com/sweet/market/cart/application/CartService.java`
- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Test: `backend/src/test/java/com/sweet/market/cart/CartApiTest.java`

- [ ] **Step 1: Add failing validation tests**

Append these tests to `CartApiTest`:

```java
@Test
void 장바구니_담기는_JWT가_필요하다() throws Exception {
    mockMvc.perform(post("/api/products/{productId}/cart", 1L))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
}

@Test
void 장바구니_제거는_JWT가_필요하다() throws Exception {
    mockMvc.perform(delete("/api/products/{productId}/cart", 1L))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
}

@Test
void 자기_상품은_장바구니에_담을_수_없다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    Long productId = createProduct(sellerToken);

    mockMvc.perform(post("/api/products/{productId}/cart", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CART_OWN_PRODUCT_NOT_ALLOWED"));
}

@Test
void 판매중이_아닌_상품은_새로_장바구니에_담을_수_없다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken);

    mockMvc.perform(delete("/api/products/{productId}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isOk());

    mockMvc.perform(post("/api/products/{productId}/cart", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CART_PRODUCT_NOT_ON_SALE"));
}

@Test
void 존재하지_않는_상품은_장바구니에서_제거할_수_없다() throws Exception {
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");

    mockMvc.perform(delete("/api/products/{productId}/cart", 999999L)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.cart.CartApiTest"
```

Expected: FAIL because error codes and API implementation are missing.

- [ ] **Step 3: Add error codes**

Modify `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java` by adding these enum values after wishlist errors:

```java
CART_OWN_PRODUCT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "자기 상품은 장바구니에 담을 수 없습니다."),
CART_PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중인 상품만 장바구니에 담을 수 있습니다."),
CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."),
CART_CHECKOUT_EMPTY(HttpStatus.BAD_REQUEST, "주문할 장바구니 항목을 선택해주세요."),
CART_CHECKOUT_INVALID_ITEMS(HttpStatus.BAD_REQUEST, "선택한 장바구니 항목이 올바르지 않습니다."),
CART_CHECKOUT_NOT_ALLOWED(HttpStatus.CONFLICT, "주문할 수 없는 장바구니 항목이 포함되어 있습니다."),
```

- [ ] **Step 4: Add response DTO**

Create `backend/src/main/java/com/sweet/market/cart/api/CartResponse.java`:

```java
package com.sweet.market.cart.api;

public record CartResponse(
        Long productId,
        boolean carted
) {
}
```

- [ ] **Step 5: Add service**

Create `backend/src/main/java/com/sweet/market/cart/application/CartService.java`:

```java
package com.sweet.market.cart.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.cart.api.CartResponse;
import com.sweet.market.cart.domain.CartItem;
import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final TransactionTemplate insertTransaction;

    public CartService(
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            MemberRepository memberRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CartResponse add(Long buyerId, Long productId) {
        Product product = productRepository.findWithSellerById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (cartItemRepository.existsByBuyerIdAndProductId(buyerId, productId)) {
            return new CartResponse(productId, true);
        }

        validateCartable(buyerId, product);

        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        try {
            insertTransaction.executeWithoutResult(status ->
                    cartItemRepository.saveAndFlush(CartItem.create(buyer, product))
            );
        } catch (DataIntegrityViolationException exception) {
            if (cartItemRepository.findByBuyerIdAndProductId(buyerId, productId).isEmpty()) {
                throw exception;
            }
        }

        return new CartResponse(productId, true);
    }

    @Transactional
    public CartResponse remove(Long buyerId, Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        cartItemRepository.deleteByBuyerIdAndProductId(buyerId, productId);
        return new CartResponse(productId, false);
    }

    private void validateCartable(Long buyerId, Product product) {
        if (product.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.CART_OWN_PRODUCT_NOT_ALLOWED);
        }
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.CART_PRODUCT_NOT_ON_SALE);
        }
    }
}
```

- [ ] **Step 6: Add controller**

Create `backend/src/main/java/com/sweet/market/cart/api/CartController.java`:

```java
package com.sweet.market.cart.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.cart.application.CartService;
import com.sweet.market.common.api.ApiResponse;

@RestController
@RequestMapping("/api/products/{productId}/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ApiResponse<CartResponse> add(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(cartService.add(member.id(), productId));
    }

    @DeleteMapping
    public ApiResponse<CartResponse> remove(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(cartService.remove(member.id(), productId));
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.cart.CartApiTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add -- backend/src/main/java/com/sweet/market/cart backend/src/main/java/com/sweet/market/common/error/ErrorCode.java backend/src/test/java/com/sweet/market/cart/CartApiTest.java
git commit -m "feat: add cart add remove api"
```

---

### Task 3: Backend Cart Query API

**Files:**
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartItemResponse.java`
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartQueryController.java`
- Create: `backend/src/main/java/com/sweet/market/cart/query/CartQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/cart/repository/CartItemRepository.java`
- Test: `backend/src/test/java/com/sweet/market/cart/CartApiTest.java`

- [ ] **Step 1: Add failing cart list tests**

Append to `CartApiTest`:

```java
@Test
void 내_장바구니는_최근에_담은_순서로_조회된다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
    Long buyerId = findMemberIdByEmail("buyer@example.com");
    Long oldProductId = createProduct(sellerToken, "Old MacBook Pro", "old-cart.jpg");
    Long recentProductId = createProduct(sellerToken, "Recent MacBook Pro", "recent-cart.jpg");

    addCart(buyerToken, oldProductId);
    addCart(buyerToken, recentProductId);
    updateCartedAt(buyerId, oldProductId, "2026-01-01 10:00:00");
    updateCartedAt(buyerId, recentProductId, "2026-01-02 10:00:00");

    mockMvc.perform(get("/api/me/cart")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.content[0].cartItemId").value(findCartItemId(buyerId, recentProductId)))
            .andExpect(jsonPath("$.data.content[0].productId").value(recentProductId))
            .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"))
            .andExpect(jsonPath("$.data.content[0].title").value("Recent MacBook Pro"))
            .andExpect(jsonPath("$.data.content[0].price").value(2000000))
            .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
            .andExpect(jsonPath("$.data.content[0].thumbnailUrl", not(blankOrNullString())))
            .andExpect(jsonPath("$.data.content[0].checkoutAvailable").value(true))
            .andExpect(jsonPath("$.data.content[0].unavailableReason").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].cartedAt").value("2026-01-02T10:00:00"))
            .andExpect(jsonPath("$.data.content[1].productId").value(oldProductId))
            .andExpect(jsonPath("$.data.content[1].cartedAt").value("2026-01-01T10:00:00"));
}

@Test
void 내_장바구니는_구매할_수_없는_상품도_보여주되_선택_불가로_표시한다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
    Long buyerId = findMemberIdByEmail("buyer@example.com");
    Long onSaleProductId = createProduct(sellerToken, "On Sale Product", "on-sale-cart.jpg");
    Long reservedProductId = createProduct(sellerToken, "Reserved Product", "reserved-cart.jpg");
    Long soldOutProductId = createProduct(sellerToken, "Sold Out Product", "sold-out-cart.jpg");
    Long hiddenProductId = createProduct(sellerToken, "Hidden Product", "hidden-cart.jpg");

    addCart(buyerToken, onSaleProductId);
    addCart(buyerToken, reservedProductId);
    addCart(buyerToken, soldOutProductId);
    addCart(buyerToken, hiddenProductId);
    updateProductStatus(reservedProductId, "RESERVED");
    updateProductStatus(soldOutProductId, "SOLD_OUT");
    updateProductStatus(hiddenProductId, "HIDDEN");
    updateCartedAt(buyerId, onSaleProductId, "2026-01-01 10:00:00");
    updateCartedAt(buyerId, reservedProductId, "2026-01-02 10:00:00");
    updateCartedAt(buyerId, soldOutProductId, "2026-01-03 10:00:00");
    updateCartedAt(buyerId, hiddenProductId, "2026-01-04 10:00:00");

    mockMvc.perform(get("/api/me/cart")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(4))
            .andExpect(jsonPath("$.data.totalElements").value(4))
            .andExpect(jsonPath("$.data.content[0].productId").value(hiddenProductId))
            .andExpect(jsonPath("$.data.content[0].checkoutAvailable").value(false))
            .andExpect(jsonPath("$.data.content[0].unavailableReason").value("HIDDEN"))
            .andExpect(jsonPath("$.data.content[1].productId").value(soldOutProductId))
            .andExpect(jsonPath("$.data.content[1].checkoutAvailable").value(false))
            .andExpect(jsonPath("$.data.content[1].unavailableReason").value("SOLD_OUT"))
            .andExpect(jsonPath("$.data.content[2].productId").value(reservedProductId))
            .andExpect(jsonPath("$.data.content[2].checkoutAvailable").value(false))
            .andExpect(jsonPath("$.data.content[2].unavailableReason").value("RESERVED"))
            .andExpect(jsonPath("$.data.content[3].productId").value(onSaleProductId))
            .andExpect(jsonPath("$.data.content[3].checkoutAvailable").value(true))
            .andExpect(jsonPath("$.data.content[3].unavailableReason").doesNotExist());
}

@Test
void 내_장바구니_조회는_JWT가_필요하다() throws Exception {
    mockMvc.perform(get("/api/me/cart"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
}
```

Add helper methods to `CartApiTest`:

```java
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

private void addCart(String accessToken, Long productId) throws Exception {
    mockMvc.perform(post("/api/products/{productId}/cart", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk());
}

private Long findMemberIdByEmail(String email) {
    return jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE email = ?",
            Long.class,
            email
    );
}

private Long findCartItemId(Long buyerId, Long productId) {
    return jdbcTemplate.queryForObject(
            "SELECT id FROM cart_items WHERE buyer_id = ? AND product_id = ?",
            Long.class,
            buyerId,
            productId
    );
}

private void updateCartedAt(Long buyerId, Long productId, String cartedAt) {
    jdbcTemplate.update(
            "UPDATE cart_items SET created_at = ? WHERE buyer_id = ? AND product_id = ?",
            java.sql.Timestamp.valueOf(cartedAt),
            buyerId,
            productId
    );
}

private void updateProductStatus(Long productId, String status) {
    jdbcTemplate.update(
            "UPDATE products SET status = ? WHERE id = ?",
            status,
            productId
    );
}
```

Change existing `createProduct(String accessToken)` helper to delegate:

```java
private Long createProduct(String accessToken) throws Exception {
    return createProduct(accessToken, "MacBook Pro", "cart-product.jpg");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.cart.CartApiTest"
```

Expected: FAIL because `/api/me/cart` does not exist.

- [ ] **Step 3: Create response DTO**

Create `backend/src/main/java/com/sweet/market/cart/api/CartItemResponse.java`:

```java
package com.sweet.market.cart.api;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sweet.market.product.domain.ProductStatus;

public record CartItemResponse(
        Long cartItemId,
        Long productId,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        String status,
        String thumbnailUrl,
        LocalDateTime cartedAt,
        boolean checkoutAvailable,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String unavailableReason
) {

    public CartItemResponse(
            Long cartItemId,
            Long productId,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl,
            LocalDateTime cartedAt,
            boolean checkoutAvailable,
            String unavailableReason
    ) {
        this(cartItemId, productId, sellerId, sellerNickname, title, price, status.name(), thumbnailUrl, cartedAt, checkoutAvailable, unavailableReason);
    }
}
```

- [ ] **Step 4: Add cart page query to repository**

Modify `CartItemRepository`:

```java
package com.sweet.market.cart.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.cart.api.CartItemResponse;
import com.sweet.market.cart.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    boolean existsByBuyerIdAndProductId(Long buyerId, Long productId);

    Optional<CartItem> findByBuyerIdAndProductId(Long buyerId, Long productId);

    long deleteByBuyerIdAndProductId(Long buyerId, Long productId);

    @Query(value = """
            select new com.sweet.market.cart.api.CartItemResponse(
                ci.id,
                p.id,
                seller.id,
                seller.nickname,
                p.title,
                p.price,
                p.status,
                coalesce(
                    (
                        select min(representativeImage.imageUrl)
                        from ProductImage representativeImage
                        where representativeImage.product = p
                          and representativeImage.representative = true
                          and representativeImage.sortOrder = (
                              select min(firstRepresentativeImage.sortOrder)
                              from ProductImage firstRepresentativeImage
                              where firstRepresentativeImage.product = p
                                and firstRepresentativeImage.representative = true
                          )
                    ),
                    (
                        select min(orderedImage.imageUrl)
                        from ProductImage orderedImage
                        where orderedImage.product = p
                          and orderedImage.sortOrder = (
                              select min(firstImage.sortOrder)
                              from ProductImage firstImage
                              where firstImage.product = p
                          )
                    )
                ),
                ci.createdAt,
                case
                    when p.status = com.sweet.market.product.domain.ProductStatus.ON_SALE and seller.id <> :buyerId then true
                    else false
                end,
                case
                    when seller.id = :buyerId then 'OWN_PRODUCT'
                    when p.status <> com.sweet.market.product.domain.ProductStatus.ON_SALE then p.status
                    else null
                end
            )
            from CartItem ci
            join ci.product p
            join p.seller seller
            where ci.buyer.id = :buyerId
            order by ci.createdAt desc, ci.id desc
            """,
            countQuery = """
            select count(ci)
            from CartItem ci
            where ci.buyer.id = :buyerId
            """)
    Page<CartItemResponse> findPageByBuyerId(@Param("buyerId") Long buyerId, Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "product", "product.seller", "product.images"})
    @Query("""
            select ci
            from CartItem ci
            where ci.id in :ids
            """)
    List<CartItem> findAllWithBuyerProductSellerImagesByIdIn(@Param("ids") List<Long> ids);
}
```

- [ ] **Step 5: Add query service**

Create `backend/src/main/java/com/sweet/market/cart/query/CartQueryService.java`:

```java
package com.sweet.market.cart.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.cart.api.CartItemResponse;
import com.sweet.market.cart.repository.CartItemRepository;

@Service
public class CartQueryService {

    private final CartItemRepository cartItemRepository;

    public CartQueryService(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional(readOnly = true)
    public Page<CartItemResponse> findMine(Long buyerId, Pageable pageable) {
        return cartItemRepository.findPageByBuyerId(buyerId, pageable);
    }
}
```

- [ ] **Step 6: Add query controller**

Create `backend/src/main/java/com/sweet/market/cart/api/CartQueryController.java`:

```java
package com.sweet.market.cart.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.cart.query.CartQueryService;
import com.sweet.market.common.api.ApiResponse;

@RestController
@RequestMapping("/api/me/cart")
public class CartQueryController {

    private final CartQueryService cartQueryService;

    public CartQueryController(CartQueryService cartQueryService) {
        this.cartQueryService = cartQueryService;
    }

    @GetMapping
    public ApiResponse<Page<CartItemResponse>> listMine(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(cartQueryService.findMine(member.id(), pageable));
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.cart.CartApiTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add -- backend/src/main/java/com/sweet/market/cart backend/src/test/java/com/sweet/market/cart/CartApiTest.java
git commit -m "feat: add buyer cart query api"
```

---

### Task 4: Backend Cart Checkout

**Files:**
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartCheckoutRequest.java`
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartCheckoutResponse.java`
- Create: `backend/src/main/java/com/sweet/market/cart/api/CartCheckoutController.java`
- Modify: `backend/src/main/java/com/sweet/market/cart/application/CartService.java`
- Modify: `backend/src/main/java/com/sweet/market/cart/repository/CartItemRepository.java`
- Test: `backend/src/test/java/com/sweet/market/cart/CartCheckoutApiTest.java`

- [ ] **Step 1: Write failing checkout tests**

Create `backend/src/test/java/com/sweet/market/cart/CartCheckoutApiTest.java`:

```java
package com.sweet.market.cart;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

class CartCheckoutApiTest extends IntegrationTestSupport {

    @Test
    void 선택한_장바구니_항목을_상품별_주문으로_전환한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long firstProductId = createProduct(sellerToken, "First Product", "first.jpg");
        Long secondProductId = createProduct(sellerToken, "Second Product", "second.jpg");

        addCart(buyerToken, firstProductId);
        addCart(buyerToken, secondProductId);
        Long firstCartItemId = findCartItemId(buyerId, firstProductId);
        Long secondCartItemId = findCartItemId(buyerId, secondProductId);

        mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d, %d]
                                }
                                """.formatted(firstCartItemId, secondCartItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders.length()").value(2))
                .andExpect(jsonPath("$.data.orders[0].id").isNumber())
                .andExpect(jsonPath("$.data.orders[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.orders[0].productStatus").value("RESERVED"))
                .andExpect(jsonPath("$.data.orders[1].id").isNumber())
                .andExpect(jsonPath("$.data.orders[1].status").value("CREATED"))
                .andExpect(jsonPath("$.data.orders[1].productStatus").value("RESERVED"));

        assertThat(countOrders()).isEqualTo(2);
        assertThat(countCartItems()).isZero();
        assertThat(findProductStatus(firstProductId)).isEqualTo("RESERVED");
        assertThat(findProductStatus(secondProductId)).isEqualTo("RESERVED");
    }

    @Test
    void 선택한_항목_중_하나라도_구매_불가이면_아무_주문도_생성하지_않는다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long availableProductId = createProduct(sellerToken, "Available Product", "available.jpg");
        Long hiddenProductId = createProduct(sellerToken, "Hidden Product", "hidden.jpg");

        addCart(buyerToken, availableProductId);
        addCart(buyerToken, hiddenProductId);
        Long availableCartItemId = findCartItemId(buyerId, availableProductId);
        Long hiddenCartItemId = findCartItemId(buyerId, hiddenProductId);

        mockMvc.perform(delete("/api/products/{productId}", hiddenProductId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d, %d]
                                }
                                """.formatted(availableCartItemId, hiddenCartItemId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_NOT_ALLOWED"));

        assertThat(countOrders()).isZero();
        assertThat(countCartItems()).isEqualTo(2);
        assertThat(findProductStatus(availableProductId)).isEqualTo("ON_SALE");
        assertThat(findProductStatus(hiddenProductId)).isEqualTo("HIDDEN");
    }

    @Test
    void 빈_장바구니_체크아웃은_실패한다() throws Exception {
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");

        mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_EMPTY"));
    }

    @Test
    void 중복된_장바구니_항목으로_체크아웃할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long productId = createProduct(sellerToken, "Duplicate Product", "duplicate.jpg");
        addCart(buyerToken, productId);
        Long cartItemId = findCartItemId(buyerId, productId);

        mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d, %d]
                                }
                                """.formatted(cartItemId, cartItemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_INVALID_ITEMS"));
    }

    @Test
    void 다른_구매자의_장바구니_항목은_체크아웃할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-buyer@example.com", "password123", "otherBuyer");
        Long buyerId = findMemberIdByEmail("buyer@example.com");
        Long productId = createProduct(sellerToken, "Private Product", "private.jpg");
        addCart(buyerToken, productId);
        Long cartItemId = findCartItemId(buyerId, productId);

        mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherBuyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d]
                                }
                                """.formatted(cartItemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_INVALID_ITEMS"));
    }

    @Test
    void 장바구니_체크아웃은_JWT가_필요하다() throws Exception {
        mockMvc.perform(post("/api/me/cart/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [1]
                                }
                                """))
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

    private void addCart(String accessToken, Long productId) throws Exception {
        mockMvc.perform(post("/api/products/{productId}/cart", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private Long findMemberIdByEmail(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private Long findCartItemId(Long buyerId, Long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM cart_items WHERE buyer_id = ? AND product_id = ?",
                Long.class,
                buyerId,
                productId
        );
    }

    private long countOrders() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        return count == null ? 0 : count;
    }

    private long countCartItems() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cart_items", Long.class);
        return count == null ? 0 : count;
    }

    private String findProductStatus(Long productId) {
        return jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, productId);
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
.\gradlew.bat --no-daemon test --tests "com.sweet.market.cart.CartCheckoutApiTest"
```

Expected: FAIL because `/api/me/cart/checkout` does not exist.

- [ ] **Step 3: Add checkout request and response DTOs**

Create `backend/src/main/java/com/sweet/market/cart/api/CartCheckoutRequest.java`:

```java
package com.sweet.market.cart.api;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record CartCheckoutRequest(
        @NotNull(message = "장바구니 항목 ID는 필수입니다.")
        List<Long> cartItemIds
) {
}
```

Create `backend/src/main/java/com/sweet/market/cart/api/CartCheckoutResponse.java`:

```java
package com.sweet.market.cart.api;

import java.util.List;

import com.sweet.market.order.api.OrderSummaryResponse;

public record CartCheckoutResponse(
        List<OrderSummaryResponse> orders
) {
}
```

- [ ] **Step 4: Add checkout method to service**

Append this method and imports to `CartService`.

Add imports:

```java
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sweet.market.cart.api.CartCheckoutResponse;
import com.sweet.market.order.api.OrderSummaryResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
```

Add `OrderRepository` field and constructor parameter:

```java
private final OrderRepository orderRepository;
```

Constructor parameter:

```java
OrderRepository orderRepository,
```

Constructor assignment:

```java
this.orderRepository = orderRepository;
```

Add method:

```java
@Transactional
public CartCheckoutResponse checkout(Long buyerId, List<Long> cartItemIds) {
    if (cartItemIds == null || cartItemIds.isEmpty()) {
        throw new BusinessException(ErrorCode.CART_CHECKOUT_EMPTY);
    }

    Set<Long> uniqueIds = new HashSet<>(cartItemIds);
    if (uniqueIds.size() != cartItemIds.size()) {
        throw new BusinessException(ErrorCode.CART_CHECKOUT_INVALID_ITEMS);
    }

    List<CartItem> cartItems = cartItemRepository.findAllWithBuyerProductSellerImagesByIdIn(cartItemIds);
    if (cartItems.size() != cartItemIds.size()) {
        throw new BusinessException(ErrorCode.CART_CHECKOUT_INVALID_ITEMS);
    }

    boolean hasForeignItem = cartItems.stream()
            .anyMatch(cartItem -> !cartItem.getBuyer().getId().equals(buyerId));
    if (hasForeignItem) {
        throw new BusinessException(ErrorCode.CART_CHECKOUT_INVALID_ITEMS);
    }

    boolean hasUnavailableItem = cartItems.stream()
            .anyMatch(cartItem -> cartItem.getProduct().isOwnedBy(buyerId)
                    || cartItem.getProduct().getStatus() != ProductStatus.ON_SALE);
    if (hasUnavailableItem) {
        throw new BusinessException(ErrorCode.CART_CHECKOUT_NOT_ALLOWED);
    }

    List<Order> orders = cartItems.stream()
            .map(cartItem -> Order.create(cartItem.getBuyer(), cartItem.getProduct()))
            .map(orderRepository::save)
            .toList();

    cartItemRepository.deleteAll(cartItems);

    return new CartCheckoutResponse(orders.stream()
            .map(OrderSummaryResponse::from)
            .toList());
}
```

- [ ] **Step 5: Add checkout controller**

Create `backend/src/main/java/com/sweet/market/cart/api/CartCheckoutController.java`:

```java
package com.sweet.market.cart.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.cart.application.CartService;
import com.sweet.market.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me/cart/checkout")
public class CartCheckoutController {

    private final CartService cartService;

    public CartCheckoutController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ApiResponse<CartCheckoutResponse> checkout(
            Authentication authentication,
            @Valid @RequestBody CartCheckoutRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(cartService.checkout(member.id(), request.cartItemIds()));
    }
}
```

- [ ] **Step 6: Run checkout tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.cart.CartCheckoutApiTest"
```

Expected: PASS.

- [ ] **Step 7: Run cart API tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.cart.*"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add -- backend/src/main/java/com/sweet/market/cart backend/src/test/java/com/sweet/market/cart/CartCheckoutApiTest.java
git commit -m "feat: checkout selected cart items"
```

---

### Task 5: Product Read Enrichment With `carted`

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductSummaryResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java`
- Test: `backend/src/test/java/com/sweet/market/cart/CartApiTest.java`

- [ ] **Step 1: Add failing product read tests**

Append to `CartApiTest`:

```java
@Test
void 로그인한_사용자는_상품_목록에서_장바구니_상태를_본다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
    Long cartedProductId = createProduct(sellerToken, "Carted Product", "carted.jpg");
    Long otherProductId = createProduct(sellerToken, "Other Product", "other.jpg");

    addCart(buyerToken, cartedProductId);

    mockMvc.perform(get("/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(otherProductId))
            .andExpect(jsonPath("$.data.content[0].carted").value(false))
            .andExpect(jsonPath("$.data.content[1].id").value(cartedProductId))
            .andExpect(jsonPath("$.data.content[1].carted").value(true));
}

@Test
void 로그인한_사용자는_상품_상세에서_장바구니_상태를_본다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "Detail Carted Product", "detail-carted.jpg");

    addCart(buyerToken, productId);

    mockMvc.perform(get("/api/products/{productId}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(productId))
            .andExpect(jsonPath("$.data.carted").value(true));
}

@Test
void 익명_사용자는_상품_목록과_상세에서_장바구니_상태를_false로_본다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    Long productId = createProduct(sellerToken, "Anonymous Product", "anonymous.jpg");

    mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(productId))
            .andExpect(jsonPath("$.data.content[0].carted").value(false));

    mockMvc.perform(get("/api/products/{productId}", productId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(productId))
            .andExpect(jsonPath("$.data.carted").value(false));
}
```

Add `get` static import:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.cart.CartApiTest"
```

Expected: FAIL because `carted` is missing.

- [ ] **Step 3: Update product DTOs**

Modify `ProductSummaryResponse` record components:

```java
public record ProductSummaryResponse(
        Long id,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        String status,
        String thumbnailUrl,
        long wishlistCount,
        boolean wishlisted,
        boolean carted
) {
```

Update constructors and factory:

```java
public ProductSummaryResponse(
        Long id,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        ProductStatus status,
        String thumbnailUrl
) {
    this(id, sellerId, sellerNickname, title, price, status.name(), thumbnailUrl, 0, false, false);
}

public ProductSummaryResponse(
        Long id,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        ProductStatus status,
        String thumbnailUrl,
        long wishlistCount,
        boolean wishlisted,
        boolean carted
) {
    this(id, sellerId, sellerNickname, title, price, status.name(), thumbnailUrl, wishlistCount, wishlisted, carted);
}

public static ProductSummaryResponse from(Product product) {
    return from(product, 0, false, false);
}

public static ProductSummaryResponse from(Product product, long wishlistCount, boolean wishlisted) {
    return from(product, wishlistCount, wishlisted, false);
}

public static ProductSummaryResponse from(Product product, long wishlistCount, boolean wishlisted, boolean carted) {
    String thumbnailUrl = product.getImages().stream()
            .filter(ProductImage::isRepresentative)
            .findFirst()
            .or(() -> product.getImages().stream().findFirst())
            .map(ProductImage::getImageUrl)
            .orElse(null);

    return new ProductSummaryResponse(
            product.getId(),
            product.getSeller().getId(),
            product.getSeller().getNickname(),
            product.getTitle(),
            product.getPrice(),
            product.getStatus().name(),
            thumbnailUrl,
            wishlistCount,
            wishlisted,
            carted
    );
}
```

Modify `ProductResponse` record components and factories:

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
        boolean carted
) {

    public static ProductResponse from(Product product) {
        return from(product, 0, false, false);
    }

    public static ProductResponse from(Product product, long wishlistCount, boolean wishlisted) {
        return from(product, wishlistCount, wishlisted, false);
    }

    public static ProductResponse from(Product product, long wishlistCount, boolean wishlisted, boolean carted) {
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
                carted
        );
    }
}
```

- [ ] **Step 4: Update product repository projection**

Modify `ProductRepository.findPublicSummariesByStatusOrderByIdDesc` projection by adding this final constructor argument after wishlisted:

```java
case
    when :viewerId is null then false
    when (
        select count(cartItem)
        from CartItem cartItem
        where cartItem.product = p
          and cartItem.buyer.id = :viewerId
    ) > 0 then true
    else false
end
```

The constructor call should now end with:

```java
(
    select count(allItem)
    from WishlistItem allItem
    where allItem.product = p
),
case
    when :viewerId is null then false
    when (
        select count(viewerItem)
        from WishlistItem viewerItem
        where viewerItem.product = p
          and viewerItem.buyer.id = :viewerId
    ) > 0 then true
    else false
end,
case
    when :viewerId is null then false
    when (
        select count(cartItem)
        from CartItem cartItem
        where cartItem.product = p
          and cartItem.buyer.id = :viewerId
    ) > 0 then true
    else false
end
```

- [ ] **Step 5: Update product query service**

Modify `ProductQueryService` constructor and fields:

```java
private final ProductRepository productRepository;
private final WishlistItemRepository wishlistItemRepository;
private final CartItemRepository cartItemRepository;

public ProductQueryService(
        ProductRepository productRepository,
        WishlistItemRepository wishlistItemRepository,
        CartItemRepository cartItemRepository
) {
    this.productRepository = productRepository;
    this.wishlistItemRepository = wishlistItemRepository;
    this.cartItemRepository = cartItemRepository;
}
```

Add import:

```java
import com.sweet.market.cart.repository.CartItemRepository;
```

Modify detail response:

```java
long wishlistCount = wishlistItemRepository.countByProductId(productId);
boolean wishlisted = viewerId != null && wishlistItemRepository.existsByBuyerIdAndProductId(viewerId, productId);
boolean carted = viewerId != null && cartItemRepository.existsByBuyerIdAndProductId(viewerId, productId);
return ProductResponse.from(product, wishlistCount, wishlisted, carted);
```

- [ ] **Step 6: Run product and cart tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.cart.*" --tests "com.sweet.market.product.*" --tests "com.sweet.market.wishlist.*"
```

Expected: PASS.

- [ ] **Step 7: Update product summary response unit test constructors**

Open `backend/src/test/java/com/sweet/market/product/api/ProductSummaryResponseTest.java` and update every direct `new ProductSummaryResponse(...)` call to pass `carted=false` as the final argument. Use this shape:

```java
new ProductSummaryResponse(
        productId,
        sellerId,
        sellerNickname,
        title,
        price,
        status,
        thumbnailUrl,
        wishlistCount,
        wishlisted,
        false
)
```

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests "com.sweet.market.product.api.ProductSummaryResponseTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add -- backend/src/main/java/com/sweet/market/product backend/src/main/java/com/sweet/market/cart backend/src/test/java/com/sweet/market/cart/CartApiTest.java backend/src/test/java/com/sweet/market/product backend/src/test/java/com/sweet/market/wishlist
git commit -m "feat: expose product cart state"
```

---

### Task 6: Web Cart API And Toggle

**Files:**
- Create: `web/src/features/cart/cartApi.ts`
- Create: `web/src/features/cart/CartToggle.tsx`
- Modify: `web/src/features/products/productApi.ts`
- Modify: `web/src/pages/HomePage.tsx`
- Modify: `web/src/pages/ProductDetailPage.tsx`
- Modify: `web/src/features/auth/AuthProvider.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Update product API types**

Modify `web/src/features/products/productApi.ts`:

```ts
export type ProductSummary = {
  id: number;
  sellerId: number;
  sellerNickname: string;
  title: string;
  price: number;
  status: ProductStatus;
  thumbnailUrl: string | null;
  wishlistCount: number;
  wishlisted: boolean;
  carted: boolean;
};
```

No separate `carted` field is needed on `Product` because `Product` extends `Omit<ProductSummary, 'thumbnailUrl'>`.

- [ ] **Step 2: Create cart API client**

Create `web/src/features/cart/cartApi.ts`:

```ts
import { api } from '../../shared/api/http';
import { type OrderSummary } from '../orders/orderApi';
import { type Page, type ProductStatus } from '../products/productApi';

export type CartResponse = {
  productId: number;
  carted: boolean;
};

export type CartItem = {
  cartItemId: number;
  productId: number;
  sellerId: number;
  sellerNickname: string;
  title: string;
  price: number;
  status: ProductStatus;
  thumbnailUrl: string | null;
  cartedAt: string;
  checkoutAvailable: boolean;
  unavailableReason: string | null;
};

export type CartCheckoutResponse = {
  orders: OrderSummary[];
};

export function addCart(productId: number) {
  return api<CartResponse>(`/api/products/${productId}/cart`, {
    method: 'POST',
  });
}

export function removeCart(productId: number) {
  return api<CartResponse>(`/api/products/${productId}/cart`, {
    method: 'DELETE',
  });
}

export function getMyCart() {
  return api<Page<CartItem>>('/api/me/cart');
}

export function checkoutCart(cartItemIds: number[]) {
  return api<CartCheckoutResponse>('/api/me/cart/checkout', {
    method: 'POST',
    body: JSON.stringify({ cartItemIds }),
  });
}
```

- [ ] **Step 3: Create cart toggle component**

Create `web/src/features/cart/CartToggle.tsx`:

```tsx
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { addCart, removeCart, type CartResponse } from './cartApi';

type CartToggleProps = {
  productId: number;
  sellerId: number;
  carted: boolean;
  onChanged?: (response: CartResponse) => void;
};

export function CartToggle({ productId, sellerId, carted, onChanged }: CartToggleProps) {
  const { loading, member } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isOwnProduct = member?.id === sellerId;

  const mutation = useMutation({
    mutationFn: (currentCarted: boolean) => (currentCarted ? removeCart(productId) : addCart(productId)),
    onSuccess: async (response) => {
      onChanged?.(response);

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['products', productId] }),
        queryClient.invalidateQueries({ queryKey: ['my-cart'] }),
      ]);
    },
  });

  const latestResponse = mutation.data?.productId === productId ? mutation.data : null;
  const displayedCarted = latestResponse?.carted ?? carted;

  if (isOwnProduct) {
    return <span className="cart-own-product">내 상품</span>;
  }

  return (
    <button
      type="button"
      className={displayedCarted ? 'cart-button cart-button-active' : 'cart-button'}
      disabled={loading || mutation.isPending}
      aria-pressed={displayedCarted}
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();

        if (loading) {
          return;
        }

        if (!member) {
          navigate('/login', { state: { from: `${location.pathname}${location.search}${location.hash}` } });
          return;
        }

        mutation.mutate(displayedCarted);
      }}
    >
      {displayedCarted ? '장바구니 담김' : '장바구니'}
    </button>
  );
}
```

- [ ] **Step 4: Add product card toggle**

Modify `web/src/pages/HomePage.tsx`.

Add import:

```ts
import { CartToggle } from '../features/cart/CartToggle';
```

Replace the `product-card-wishlist` block with:

```tsx
<div className="product-card-actions">
  <WishlistToggle
    productId={product.id}
    sellerId={product.sellerId}
    wishlisted={product.wishlisted}
    wishlistCount={product.wishlistCount}
  />
  <CartToggle productId={product.id} sellerId={product.sellerId} carted={product.carted} />
</div>
```

- [ ] **Step 5: Add product detail toggle**

Modify `web/src/pages/ProductDetailPage.tsx`.

Add imports:

```ts
import { CartToggle } from '../features/cart/CartToggle';
import { type CartResponse } from '../features/cart/cartApi';
```

Add state:

```ts
const [cartState, setCartState] = useState<CartResponse | null>(null);
```

Reset state in the existing effect:

```ts
useEffect(() => {
  setWishlistState(null);
  setCartState(null);
}, [product?.id, product?.wishlisted, product?.wishlistCount, product?.carted]);
```

Add displayed cart:

```ts
const displayedCart = cartState?.productId === product.id ? cartState : null;
```

Add `CartToggle` near `WishlistToggle`:

```tsx
<CartToggle
  productId={product.id}
  sellerId={product.sellerId}
  carted={displayedCart?.carted ?? product.carted}
  onChanged={setCartState}
/>
```

Add cart invalidation to immediate order success:

```ts
queryClient.invalidateQueries({ queryKey: ['my-cart'] }),
```

- [ ] **Step 6: Clear cart query on auth changes**

Modify `web/src/features/auth/AuthProvider.tsx`:

```ts
const authenticatedPrivateQueryKeys = [
  ['my-orders'],
  ['my-products'],
  ['my-settlements'],
  ['seller-dashboard-report'],
  ['my-wishlist'],
  ['my-cart'],
  ['products'],
] as const;
```

- [ ] **Step 7: Add minimal cart button styles**

Append to relevant button styles in `web/src/shared/styles.css`:

```css
.product-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px 14px;
}

.cart-button,
.cart-own-product {
  border-radius: 999px;
  font-size: 0.85rem;
  font-weight: 700;
  line-height: 1;
  min-height: 34px;
  padding: 9px 12px;
}

.cart-button {
  border: 1px solid #1f2937;
  background: #ffffff;
  color: #1f2937;
  cursor: pointer;
}

.cart-button-active {
  background: #1f2937;
  color: #ffffff;
}

.cart-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.cart-own-product {
  align-items: center;
  background: #f3f4f6;
  color: #6b7280;
  display: inline-flex;
}
```

- [ ] **Step 8: Build web**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add -- web/src/features/cart web/src/features/products/productApi.ts web/src/pages/HomePage.tsx web/src/pages/ProductDetailPage.tsx web/src/features/auth/AuthProvider.tsx web/src/shared/styles.css
git commit -m "feat: add cart toggles"
```

---

### Task 7: Web Cart Page And Checkout

**Files:**
- Create: `web/src/pages/MyCartPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/shared/layout/Shell.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Create cart page**

Create `web/src/pages/MyCartPage.tsx`:

```tsx
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { checkoutCart, getMyCart, removeCart, type CartItem } from '../features/cart/cartApi';
import { toProductImageSrc } from '../features/products/productApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

export function MyCartPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const { data, error, isLoading } = useQuery({
    queryKey: ['my-cart'],
    queryFn: getMyCart,
  });

  const removeMutation = useMutation({
    mutationFn: (productId: number) => removeCart(productId),
    onSuccess: async (response) => {
      setSelectedIds((current) => current.filter((cartItemId) => {
        const item = cartItems.find((cartItem) => cartItem.cartItemId === cartItemId);
        return item?.productId !== response.productId;
      }));
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['my-cart'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['products', response.productId] }),
      ]);
    },
  });

  const checkoutMutation = useMutation({
    mutationFn: () => checkoutCart(selectedIds),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['my-cart'] }),
        queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
      ]);
      navigate('/me/orders');
    },
  });

  const cartItems = data?.content ?? [];
  const selectableIds = useMemo(
    () => cartItems.filter((item) => item.checkoutAvailable).map((item) => item.cartItemId),
    [cartItems],
  );
  const selectedTotal = cartItems
    .filter((item) => selectedIds.includes(item.cartItemId))
    .reduce((sum, item) => sum + item.price, 0);
  const allSelectableSelected = selectableIds.length > 0 && selectableIds.every((id) => selectedIds.includes(id));

  if (isLoading) {
    return <p className="status-text">장바구니를 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="장바구니를 불러오지 못했습니다." />;
  }

  function toggleItem(cartItemId: number) {
    setSelectedIds((current) =>
      current.includes(cartItemId)
        ? current.filter((id) => id !== cartItemId)
        : [...current, cartItemId],
    );
  }

  function toggleAllSelectable() {
    setSelectedIds(allSelectableSelected ? [] : selectableIds);
  }

  return (
    <section className="list-page">
      <div className="list-page-header">
        <h1>장바구니</h1>
        <p>구매할 상품을 선택해 주문으로 전환합니다.</p>
      </div>
      {checkoutMutation.isError ? <p className="error-text">{toErrorMessage(checkoutMutation.error)}</p> : null}
      {cartItems.length === 0 ? (
        <EmptyState title="장바구니가 비었습니다" description="관심 있는 판매 상품을 장바구니에 담아보세요." />
      ) : (
        <>
          <div className="cart-toolbar">
            <label>
              <input
                type="checkbox"
                checked={allSelectableSelected}
                disabled={selectableIds.length === 0}
                onChange={toggleAllSelectable}
              />
              구매 가능 상품 전체 선택
            </label>
            <strong>{currencyFormatter.format(selectedTotal)}원</strong>
            <button
              type="button"
              className="text-button"
              disabled={selectedIds.length === 0 || checkoutMutation.isPending}
              onClick={() => checkoutMutation.mutate()}
            >
              {checkoutMutation.isPending ? '주문 생성 중' : '선택 상품 주문하기'}
            </button>
          </div>
          <div className="cart-list" role="list" aria-label="장바구니 목록">
            {cartItems.map((item) => (
              <CartCard
                item={item}
                key={item.cartItemId}
                checked={selectedIds.includes(item.cartItemId)}
                removePending={removeMutation.isPending}
                onToggle={() => toggleItem(item.cartItemId)}
                onRemove={() => removeMutation.mutate(item.productId)}
              />
            ))}
          </div>
        </>
      )}
    </section>
  );
}

type CartCardProps = {
  item: CartItem;
  checked: boolean;
  removePending: boolean;
  onToggle: () => void;
  onRemove: () => void;
};

function CartCard({ item, checked, removePending, onToggle, onRemove }: CartCardProps) {
  const thumbnailSrc = toProductImageSrc(item.thumbnailUrl);
  const mainContent = (
    <>
      {thumbnailSrc ? (
        <img className="wishlist-card-thumb" src={thumbnailSrc} alt="" />
      ) : (
        <div className="wishlist-card-thumb wishlist-card-thumb-fallback">Sweet Market</div>
      )}
      <div className="wishlist-card-body">
        <div className="wishlist-card-title-row">
          <h2>{item.title}</h2>
          <StatusBadge status={item.status} />
        </div>
        <strong>{currencyFormatter.format(item.price)}원</strong>
        <span>{item.sellerNickname}</span>
        {!item.checkoutAvailable ? <span className="muted-text">{formatUnavailableReason(item.unavailableReason)}</span> : null}
      </div>
    </>
  );

  return (
    <article className="wishlist-card cart-card" role="listitem">
      <label className="cart-select">
        <input type="checkbox" checked={checked} disabled={!item.checkoutAvailable} onChange={onToggle} />
      </label>
      {item.status === 'ON_SALE' ? (
        <Link className="wishlist-card-main" to={`/products/${item.productId}`}>
          {mainContent}
        </Link>
      ) : (
        <div className="wishlist-card-main">{mainContent}</div>
      )}
      <div className="wishlist-card-actions">
        <button type="button" className="text-button danger-button" disabled={removePending} onClick={onRemove}>
          제거
        </button>
      </div>
    </article>
  );
}

function formatUnavailableReason(reason: string | null) {
  switch (reason) {
    case 'RESERVED':
      return '예약된 상품입니다.';
    case 'SOLD_OUT':
      return '판매완료된 상품입니다.';
    case 'HIDDEN':
      return '숨김 처리된 상품입니다.';
    case 'OWN_PRODUCT':
      return '내 상품은 주문할 수 없습니다.';
    default:
      return '현재 구매할 수 없는 상품입니다.';
  }
}

function toErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? '장바구니 주문을 처리하지 못했습니다.';
}
```

- [ ] **Step 2: Add route**

Modify `web/src/app/router.tsx`.

Add import:

```ts
import { MyCartPage } from '../pages/MyCartPage';
```

Add route after `me/wishlist`:

```tsx
<Route
  path="me/cart"
  element={
    <RequireAuth>
      <MyCartPage />
    </RequireAuth>
  }
/>
```

- [ ] **Step 3: Add nav link**

Modify `web/src/shared/layout/Shell.tsx`:

```tsx
<NavLink to="/me/wishlist">찜한 상품</NavLink>
<NavLink to="/me/cart">장바구니</NavLink>
<NavLink to="/me/orders">내 주문</NavLink>
```

- [ ] **Step 4: Add cart page styles**

Append to `web/src/shared/styles.css`:

```css
.cart-toolbar {
  align-items: center;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 14px;
}

.cart-toolbar label,
.cart-select {
  align-items: center;
  display: inline-flex;
  gap: 8px;
}

.cart-list {
  display: grid;
  gap: 12px;
}

.cart-card {
  grid-template-columns: auto minmax(0, 1fr) auto;
}

.cart-select {
  padding-left: 12px;
}
```

Add this inside the existing mobile media query:

```css
.cart-card {
  grid-template-columns: 1fr;
}

.cart-select {
  padding: 12px 12px 0;
}
```

- [ ] **Step 5: Build web**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add -- web/src/pages/MyCartPage.tsx web/src/app/router.tsx web/src/shared/layout/Shell.tsx web/src/shared/styles.css
git commit -m "feat: add buyer cart page"
```

---

### Task 8: Full Verification And Handoff

**Files:**
- Create: `docs/superpowers/handoffs/2026-07-03-milestone-16-cart-handoff.md`

- [ ] **Step 1: Run full backend tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```

Expected: PASS.

- [ ] **Step 2: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 3: Run whitespace check**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 4: Inspect git status**

Run:

```powershell
git status --short --branch --untracked-files=all
```

Expected: only intentional milestone files are changed or the branch is clean. Do not stage `backend/src/main/resources/application.yaml` from the main checkout if it appears as a pre-existing local-only change.

- [ ] **Step 5: Create handoff**

Create `docs/superpowers/handoffs/2026-07-03-milestone-16-cart-handoff.md`:

```markdown
# Milestone 16 Cart Handoff

## Completed

- Added buyer cart items with buyer/product uniqueness.
- Added idempotent cart add and remove APIs.
- Added `GET /api/me/cart` with available and unavailable cart rows.
- Added all-or-nothing cart checkout that creates one order per selected product.
- Added viewer-specific `carted` to product list and detail responses.
- Added web cart toggles on product cards and product detail.
- Added the authenticated `/me/cart` page with multi-select checkout.
- Navigated successful checkout to `/me/orders`.

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
  - `git status --short --branch --untracked-files=all`

## Local Notes

- Cart checkout intentionally preserves the existing one-product `Order` model.
- A selected cart checkout is all-or-nothing.
- Unavailable cart rows remain visible so buyers can remove them manually.
- Cart counts are not public.

## Follow-Up Candidates

- Reviews after confirmed purchases.
- Cancellation and refund flow.
- Product relisting and availability.
- Wishlist notifications.
```

- [ ] **Step 6: Commit handoff**

```powershell
git add -- docs/superpowers/handoffs/2026-07-03-milestone-16-cart-handoff.md
git commit -m "docs: add milestone 16 cart handoff"
```

- [ ] **Step 7: Final status**

Run:

```powershell
git log --oneline --decorate -n 12
git status --short --branch --untracked-files=all
```

Expected: branch contains milestone commits and no unintended files are staged.

---

## Self-Review

- Spec coverage: cart entity, uniqueness, add/remove, cart query, unavailable item visibility, all-or-nothing checkout, generated order response, product `carted`, web card/detail toggles, `/me/cart`, and verification are all covered.
- Placeholder scan: no placeholder markers or open-ended validation instructions remain.
- Type consistency: backend uses `CartItem`, `CartResponse`, `CartItemResponse`, `CartCheckoutRequest`, and `CartCheckoutResponse`; web uses `CartResponse`, `CartItem`, and `CartCheckoutResponse`.
- Scope check: order grouping, quantities, public cart counts, wishlist automation, relisting, and notifications remain out of scope.
