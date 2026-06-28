# Milestone 15 Wishlist Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build buyer wishlist add/remove, wishlist-aware product reads, and a buyer wishlist web page.

**Architecture:** Add a focused `wishlist` backend package that owns wishlist relationship writes and wishlist-specific reads. Product list/detail queries keep their existing product package ownership, but ask wishlist repositories for count and viewer state enrichment. The web reuses product card and thumbnail patterns, with a small wishlist toggle component shared by home, detail, and the wishlist page.

**Tech Stack:** Spring Boot, Spring Security, Spring Data JPA, PostgreSQL/Testcontainers, JUnit 5, MockMvc, React, TypeScript, React Query, Vite.

---

## File Structure

Backend files:

- Create `backend/src/main/java/com/sweet/market/wishlist/domain/WishlistItem.java`: buyer-product relationship entity with `createdAt`.
- Create `backend/src/main/java/com/sweet/market/wishlist/repository/WishlistItemRepository.java`: uniqueness, add/remove lookups, count/state queries, and wishlist page projection query.
- Create `backend/src/main/java/com/sweet/market/wishlist/api/WishlistResponse.java`: response after add/remove.
- Create `backend/src/main/java/com/sweet/market/wishlist/api/WishlistItemResponse.java`: row response for `/api/me/wishlist`.
- Create `backend/src/main/java/com/sweet/market/wishlist/api/WishlistController.java`: `POST`, `DELETE`, and `GET` endpoints.
- Create `backend/src/main/java/com/sweet/market/wishlist/application/WishlistService.java`: write rules and idempotency.
- Create `backend/src/main/java/com/sweet/market/wishlist/query/WishlistQueryService.java`: paged wishlist reads.
- Modify `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`: add wishlist error codes.
- Modify `backend/src/main/java/com/sweet/market/product/api/ProductSummaryResponse.java`: add `wishlistCount` and `wishlisted`.
- Modify `backend/src/main/java/com/sweet/market/product/api/ProductResponse.java`: add `wishlistCount` and `wishlisted`.
- Modify `backend/src/main/java/com/sweet/market/product/api/ProductController.java`: accept optional authentication for public reads.
- Modify `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java`: enrich product list/detail summaries with wishlist count and viewer state.
- Modify `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`: truncate `wishlist_items`.
- Create `backend/src/test/java/com/sweet/market/wishlist/WishlistApiTest.java`: endpoint, visibility, idempotency, and read model tests.
- Modify `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`: assert anonymous wishlist fields on list/detail.

Web files:

- Modify `web/src/features/products/productApi.ts`: add wishlist fields, wishlist response types, and API functions.
- Create `web/src/features/wishlist/WishlistToggle.tsx`: reusable wishlist action.
- Create `web/src/pages/MyWishlistPage.tsx`: buyer wishlist page.
- Modify `web/src/pages/HomePage.tsx`: render card-level wishlist action and count.
- Modify `web/src/pages/ProductDetailPage.tsx`: render detail wishlist action and count.
- Modify `web/src/app/router.tsx`: add protected `/me/wishlist` route.
- Modify `web/src/shared/layout/Shell.tsx`: add authenticated wishlist navigation link.
- Modify `web/src/features/auth/AuthProvider.tsx`: clear `my-wishlist` query data on auth changes.
- Modify `web/src/shared/styles.css`: add compact wishlist button/card/page styles.

---

### Task 1: Backend Wishlist Domain And Repository

**Files:**
- Create: `backend/src/main/java/com/sweet/market/wishlist/domain/WishlistItem.java`
- Create: `backend/src/main/java/com/sweet/market/wishlist/repository/WishlistItemRepository.java`
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`
- Test: `backend/src/test/java/com/sweet/market/wishlist/WishlistApiTest.java`

- [ ] **Step 1: Write failing repository/domain coverage through an API-shaped integration test**

Create `backend/src/test/java/com/sweet/market/wishlist/WishlistApiTest.java` with the class skeleton and the first test. Keep test method names Korean_with_underscores.

```java
package com.sweet.market.wishlist;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
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

class WishlistApiTest extends IntegrationTestSupport {

    @Test
    void 판매중_상품을_찜할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller-wish-add@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer-wish-add@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");

        mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.wishlisted").value(true))
                .andExpect(jsonPath("$.data.wishlistCount").value(1));
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
                                  "description": "wishlist product",
                                  "price": 2000000,
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(title, uploadImage(accessToken, title + ".jpg"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private Long uploadImage(String accessToken, String fileName) throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file",
                fileName,
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/product-image-uploads")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.wishlist.WishlistApiTest
```

Expected: FAIL because `/api/products/{productId}/wishlist` does not exist.

- [ ] **Step 3: Add the entity and repository**

Create `WishlistItem.java`:

```java
package com.sweet.market.wishlist.domain;

import java.time.LocalDateTime;

import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
        name = "wishlist_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_wishlist_items_buyer_product", columnNames = {"buyer_id", "product_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishlistItem {

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

    private WishlistItem(Member buyer, Product product, LocalDateTime createdAt) {
        this.buyer = buyer;
        this.product = product;
        this.createdAt = createdAt;
    }

    public static WishlistItem create(Member buyer, Product product) {
        return new WishlistItem(buyer, product, LocalDateTime.now());
    }
}
```

Create `WishlistItemRepository.java`:

```java
package com.sweet.market.wishlist.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.wishlist.domain.WishlistItem;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    Optional<WishlistItem> findByBuyerIdAndProductId(Long buyerId, Long productId);

    boolean existsByBuyerIdAndProductId(Long buyerId, Long productId);

    long countByProductId(Long productId);

    @Query("""
            select w.product.id
            from WishlistItem w
            where w.buyer.id = :buyerId
              and w.product.id in :productIds
            """)
    Set<Long> findWishedProductIds(@Param("buyerId") Long buyerId, @Param("productIds") Collection<Long> productIds);

    @Query("""
            select w.product.id, count(w)
            from WishlistItem w
            where w.product.id in :productIds
            group by w.product.id
            """)
    List<Object[]> countByProductIds(@Param("productIds") Collection<Long> productIds);

    @Query("""
            select count(w)
            from WishlistItem w
            where w.buyer.id = :buyerId
            """)
    long countByBuyerId(@Param("buyerId") Long buyerId);

    @Query("""
            select w.id
            from WishlistItem w
            join w.product p
            where w.buyer.id = :buyerId
              and p.status in :statuses
            """)
    List<Long> findVisibleIdsByBuyerId(
            @Param("buyerId") Long buyerId,
            @Param("statuses") Collection<ProductStatus> statuses
    );
}
```

- [ ] **Step 4: Update test cleanup**

Modify `IntegrationTestSupport.cleanUp()` so `wishlist_items` is truncated before products and members:

```java
jdbcTemplate.execute("TRUNCATE TABLE settlements, deliveries, payments, orders, wishlist_items, product_image_uploads, product_images, products, members RESTART IDENTITY CASCADE");
```

- [ ] **Step 5: Run the test again**

Run the same command from Step 2.

Expected: FAIL still occurs because the service/controller do not exist, but Hibernate now creates `wishlist_items` successfully.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/wishlist/domain/WishlistItem.java backend/src/main/java/com/sweet/market/wishlist/repository/WishlistItemRepository.java backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java backend/src/test/java/com/sweet/market/wishlist/WishlistApiTest.java
git commit -m "test: start wishlist api coverage"
```

---

### Task 2: Wishlist Add And Remove API

**Files:**
- Create: `backend/src/main/java/com/sweet/market/wishlist/api/WishlistResponse.java`
- Create: `backend/src/main/java/com/sweet/market/wishlist/api/WishlistController.java`
- Create: `backend/src/main/java/com/sweet/market/wishlist/application/WishlistService.java`
- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Modify: `backend/src/test/java/com/sweet/market/wishlist/WishlistApiTest.java`

- [ ] **Step 1: Add failing API tests for idempotency and write rules**

Append these tests to `WishlistApiTest`:

```java
@Test
void 같은_상품을_두_번_찜해도_성공한다() throws Exception {
    String sellerToken = signupAndLogin("seller-wish-duplicate@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-wish-duplicate@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "Duplicate MacBook");

    mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk());

    mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.wishlisted").value(true))
            .andExpect(jsonPath("$.data.wishlistCount").value(1));
}

@Test
void 찜을_해제할_수_있다() throws Exception {
    String sellerToken = signupAndLogin("seller-wish-remove@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-wish-remove@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "Remove MacBook");

    mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk());

    mockMvc.perform(delete("/api/products/{productId}/wishlist", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.productId").value(productId))
            .andExpect(jsonPath("$.data.wishlisted").value(false))
            .andExpect(jsonPath("$.data.wishlistCount").value(0));
}

@Test
void 없는_찜을_해제해도_성공한다() throws Exception {
    String sellerToken = signupAndLogin("seller-wish-remove-missing@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-wish-remove-missing@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "Missing Remove MacBook");

    mockMvc.perform(delete("/api/products/{productId}/wishlist", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.wishlisted").value(false))
            .andExpect(jsonPath("$.data.wishlistCount").value(0));
}

@Test
void 자기_상품은_찜할_수_없다() throws Exception {
    String sellerToken = signupAndLogin("seller-wish-own@example.com", "password123", "seller");
    Long productId = createProduct(sellerToken, "Own MacBook");

    mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("WISHLIST_OWN_PRODUCT_NOT_ALLOWED"));
}

@Test
void 판매중이_아닌_상품은_새로_찜할_수_없다() throws Exception {
    String sellerToken = signupAndLogin("seller-wish-not-sale@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-wish-not-sale@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "Reserved MacBook");
    jdbcTemplate.update("UPDATE products SET status = 'RESERVED' WHERE id = ?", productId);

    mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("WISHLIST_PRODUCT_NOT_ON_SALE"));
}
```

Add static imports:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
```

- [ ] **Step 2: Run failing tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.wishlist.WishlistApiTest
```

Expected: FAIL because wishlist service/controller/error codes are missing.

- [ ] **Step 3: Add error codes**

Modify `ErrorCode.java` near product errors:

```java
WISHLIST_PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중인 상품만 찜할 수 있습니다."),
WISHLIST_OWN_PRODUCT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "자기 상품은 찜할 수 없습니다."),
```

- [ ] **Step 4: Add response record**

Create `WishlistResponse.java`:

```java
package com.sweet.market.wishlist.api;

public record WishlistResponse(
        Long productId,
        boolean wishlisted,
        long wishlistCount
) {
}
```

- [ ] **Step 5: Add service**

Create `WishlistService.java`:

```java
package com.sweet.market.wishlist.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.wishlist.api.WishlistResponse;
import com.sweet.market.wishlist.domain.WishlistItem;
import com.sweet.market.wishlist.repository.WishlistItemRepository;

@Service
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;

    public WishlistService(
            WishlistItemRepository wishlistItemRepository,
            MemberRepository memberRepository,
            ProductRepository productRepository
    ) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public WishlistResponse add(Long buyerId, Long productId) {
        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Product product = productRepository.findWithSellerAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        validateAddable(buyerId, product);

        if (!wishlistItemRepository.existsByBuyerIdAndProductId(buyerId, productId)) {
            try {
                wishlistItemRepository.save(WishlistItem.create(buyer, product));
            } catch (DataIntegrityViolationException ignored) {
                wishlistItemRepository.findByBuyerIdAndProductId(buyerId, productId)
                        .orElseThrow(() -> ignored);
            }
        }

        return new WishlistResponse(productId, true, wishlistItemRepository.countByProductId(productId));
    }

    @Transactional
    public WishlistResponse remove(Long buyerId, Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        wishlistItemRepository.findByBuyerIdAndProductId(buyerId, productId)
                .ifPresent(wishlistItemRepository::delete);
        return new WishlistResponse(productId, false, wishlistItemRepository.countByProductId(productId));
    }

    private void validateAddable(Long buyerId, Product product) {
        if (product.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.WISHLIST_OWN_PRODUCT_NOT_ALLOWED);
        }
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.WISHLIST_PRODUCT_NOT_ON_SALE);
        }
    }
}
```

- [ ] **Step 6: Add controller**

Create `WishlistController.java`:

```java
package com.sweet.market.wishlist.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.wishlist.application.WishlistService;
import com.sweet.market.wishlist.query.WishlistQueryService;

@RestController
@RequestMapping("/api")
public class WishlistController {

    private final WishlistService wishlistService;
    private final WishlistQueryService wishlistQueryService;

    public WishlistController(WishlistService wishlistService, WishlistQueryService wishlistQueryService) {
        this.wishlistService = wishlistService;
        this.wishlistQueryService = wishlistQueryService;
    }

    @PostMapping("/products/{productId}/wishlist")
    public ApiResponse<WishlistResponse> add(Authentication authentication, @PathVariable Long productId) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(wishlistService.add(member.id(), productId));
    }

    @DeleteMapping("/products/{productId}/wishlist")
    public ApiResponse<WishlistResponse> remove(Authentication authentication, @PathVariable Long productId) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(wishlistService.remove(member.id(), productId));
    }

    @GetMapping("/me/wishlist")
    public ApiResponse<Page<WishlistItemResponse>> listMine(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(wishlistQueryService.findMine(member.id(), pageable));
    }
}
```

The `WishlistItemResponse` and `WishlistQueryService` imports will fail until Task 3. Create temporary compilable stubs now:

```java
package com.sweet.market.wishlist.api;

import java.time.LocalDateTime;

public record WishlistItemResponse(
        Long wishlistItemId,
        Long productId,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        String status,
        String thumbnailUrl,
        boolean wishlisted,
        long wishlistCount,
        LocalDateTime wishedAt
) {
}
```

```java
package com.sweet.market.wishlist.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.sweet.market.wishlist.api.WishlistItemResponse;

@Service
public class WishlistQueryService {

    public Page<WishlistItemResponse> findMine(Long buyerId, Pageable pageable) {
        return Page.empty(pageable);
    }
}
```

- [ ] **Step 7: Run tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.wishlist.WishlistApiTest
```

Expected: PASS for add/remove tests except wishlist page tests that are not written yet.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/common/error/ErrorCode.java backend/src/main/java/com/sweet/market/wishlist backend/src/test/java/com/sweet/market/wishlist/WishlistApiTest.java
git commit -m "feat: add wishlist toggle api"
```

---

### Task 3: Buyer Wishlist Page Backend Query

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/wishlist/api/WishlistItemResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/wishlist/query/WishlistQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/wishlist/repository/WishlistItemRepository.java`
- Modify: `backend/src/test/java/com/sweet/market/wishlist/WishlistApiTest.java`

- [ ] **Step 1: Add failing wishlist page tests**

Append these tests to `WishlistApiTest`:

```java
@Test
void 내_찜_목록은_최근_찜한_순서로_조회된다() throws Exception {
    String sellerToken = signupAndLogin("seller-wish-list@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-wish-list@example.com", "password123", "buyer");
    Long oldProductId = createProduct(sellerToken, "Old MacBook");
    Long newProductId = createProduct(sellerToken, "New MacBook");

    mockMvc.perform(post("/api/products/{productId}/wishlist", oldProductId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk());
    jdbcTemplate.update("UPDATE wishlist_items SET created_at = now() - interval '1 day' WHERE product_id = ?", oldProductId);
    mockMvc.perform(post("/api/products/{productId}/wishlist", newProductId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/me/wishlist")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].productId").value(newProductId))
            .andExpect(jsonPath("$.data.content[0].wishlisted").value(true))
            .andExpect(jsonPath("$.data.content[0].wishlistCount").value(1))
            .andExpect(jsonPath("$.data.content[1].productId").value(oldProductId));
}

@Test
void 내_찜_목록은_예약과_판매완료를_보여주고_숨김은_제외한다() throws Exception {
    String sellerToken = signupAndLogin("seller-wish-visible@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-wish-visible@example.com", "password123", "buyer");
    Long reservedProductId = createProduct(sellerToken, "Reserved MacBook");
    Long soldOutProductId = createProduct(sellerToken, "Sold MacBook");
    Long hiddenProductId = createProduct(sellerToken, "Hidden MacBook");

    mockMvc.perform(post("/api/products/{productId}/wishlist", reservedProductId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk());
    mockMvc.perform(post("/api/products/{productId}/wishlist", soldOutProductId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk());
    mockMvc.perform(post("/api/products/{productId}/wishlist", hiddenProductId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk());

    jdbcTemplate.update("UPDATE products SET status = 'RESERVED' WHERE id = ?", reservedProductId);
    jdbcTemplate.update("UPDATE products SET status = 'SOLD_OUT' WHERE id = ?", soldOutProductId);
    jdbcTemplate.update("UPDATE products SET status = 'HIDDEN' WHERE id = ?", hiddenProductId);

    mockMvc.perform(get("/api/me/wishlist")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.content[?(@.productId == %d)]".formatted(reservedProductId)).exists())
            .andExpect(jsonPath("$.data.content[?(@.productId == %d)]".formatted(soldOutProductId)).exists());
}
```

Add static import:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
```

- [ ] **Step 2: Run failing wishlist tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.wishlist.WishlistApiTest
```

Expected: FAIL because the stub query returns an empty page.

- [ ] **Step 3: Implement repository projection query**

Replace `findVisibleIdsByBuyerId` with a paged projection query in `WishlistItemRepository`:

```java
@Query(value = """
        select new com.sweet.market.wishlist.api.WishlistItemResponse(
            w.id,
            p.id,
            s.id,
            s.nickname,
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
            true,
            (
                select count(countItem)
                from WishlistItem countItem
                where countItem.product = p
            ),
            w.createdAt
        )
        from WishlistItem w
        join w.product p
        join p.seller s
        where w.buyer.id = :buyerId
          and p.status in :statuses
        order by w.createdAt desc, w.id desc
        """,
        countQuery = """
        select count(w)
        from WishlistItem w
        join w.product p
        where w.buyer.id = :buyerId
          and p.status in :statuses
        """)
Page<WishlistItemResponse> findVisibleWishlistByBuyerId(
        @Param("buyerId") Long buyerId,
        @Param("statuses") Collection<ProductStatus> statuses,
        Pageable pageable
);
```

Add import:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
```

- [ ] **Step 4: Implement query service**

Replace `WishlistQueryService` with:

```java
package com.sweet.market.wishlist.query;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.wishlist.api.WishlistItemResponse;
import com.sweet.market.wishlist.repository.WishlistItemRepository;

@Service
public class WishlistQueryService {

    private static final List<ProductStatus> BUYER_VISIBLE_WISHLIST_STATUSES = List.of(
            ProductStatus.ON_SALE,
            ProductStatus.RESERVED,
            ProductStatus.SOLD_OUT
    );

    private final WishlistItemRepository wishlistItemRepository;

    public WishlistQueryService(WishlistItemRepository wishlistItemRepository) {
        this.wishlistItemRepository = wishlistItemRepository;
    }

    @Transactional(readOnly = true)
    public Page<WishlistItemResponse> findMine(Long buyerId, Pageable pageable) {
        return wishlistItemRepository.findVisibleWishlistByBuyerId(
                buyerId,
                BUYER_VISIBLE_WISHLIST_STATUSES,
                pageable
        );
    }
}
```

- [ ] **Step 5: Run wishlist tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.wishlist.WishlistApiTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/wishlist backend/src/test/java/com/sweet/market/wishlist/WishlistApiTest.java
git commit -m "feat: add buyer wishlist query"
```

---

### Task 4: Product Read Model Wishlist Enrichment

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductSummaryResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductController.java`
- Modify: `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java`
- Modify: `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/wishlist/WishlistApiTest.java`

- [ ] **Step 1: Add failing product read tests**

In `WishlistApiTest`, append:

```java
@Test
void 로그인한_사용자는_상품_목록에서_찜_상태와_수를_본다() throws Exception {
    String sellerToken = signupAndLogin("seller-product-wished@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-product-wished@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "Wished Product");

    mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(productId))
            .andExpect(jsonPath("$.data.content[0].wishlisted").value(true))
            .andExpect(jsonPath("$.data.content[0].wishlistCount").value(1));
}

@Test
void 로그인한_사용자는_상품_상세에서_찜_상태와_수를_본다() throws Exception {
    String sellerToken = signupAndLogin("seller-detail-wished@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-detail-wished@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "Detail Wished Product");

    mockMvc.perform(post("/api/products/{productId}/wishlist", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/products/{productId}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.wishlisted").value(true))
            .andExpect(jsonPath("$.data.wishlistCount").value(1));
}
```

In `ProductApiTest`, update anonymous list/detail tests to expect default fields:

```java
.andExpect(jsonPath("$.data.content[0].wishlisted").value(false))
.andExpect(jsonPath("$.data.content[0].wishlistCount").value(0))
```

and:

```java
.andExpect(jsonPath("$.data.wishlisted").value(false))
.andExpect(jsonPath("$.data.wishlistCount").value(0))
```

- [ ] **Step 2: Run failing tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.wishlist.WishlistApiTest --tests com.sweet.market.product.ProductApiTest
```

Expected: FAIL because product responses do not include wishlist fields.

- [ ] **Step 3: Extend response records**

Modify `ProductSummaryResponse` to include fields and factory overload:

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
        boolean wishlisted
) {

    public ProductSummaryResponse(
            Long id,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl
    ) {
        this(id, sellerId, sellerNickname, title, price, status.name(), thumbnailUrl, 0, false);
    }

    public static ProductSummaryResponse from(Product product) {
        return from(product, 0, false);
    }

    public static ProductSummaryResponse from(Product product, long wishlistCount, boolean wishlisted) {
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
                wishlisted
        );
    }
}
```

Modify `ProductResponse` similarly:

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
        boolean wishlisted
) {

    public static ProductResponse from(Product product) {
        return from(product, 0, false);
    }

    public static ProductResponse from(Product product, long wishlistCount, boolean wishlisted) {
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
                wishlisted
        );
    }
}
```

- [ ] **Step 4: Add enrichment helpers to product query service**

Modify `ProductQueryService` constructor and methods:

```java
private final WishlistItemRepository wishlistItemRepository;

public ProductQueryService(ProductRepository productRepository, WishlistItemRepository wishlistItemRepository) {
    this.productRepository = productRepository;
    this.wishlistItemRepository = wishlistItemRepository;
}

@Transactional(readOnly = true)
public Page<ProductSummaryResponse> findOnSaleProducts(Long viewerId, Pageable pageable) {
    Page<Product> products = productRepository.findByStatusOrderByIdDesc(ProductStatus.ON_SALE, pageable);
    List<Long> productIds = products.stream()
            .map(Product::getId)
            .toList();
    Map<Long, Long> counts = wishlistCounts(productIds);
    Set<Long> wishedProductIds = wishedProductIds(viewerId, productIds);

    return products.map(product -> ProductSummaryResponse.from(
            product,
            counts.getOrDefault(product.getId(), 0L),
            wishedProductIds.contains(product.getId())
    ));
}

@Transactional(readOnly = true)
public ProductResponse findOnSaleProduct(Long viewerId, Long productId) {
    Product product = productRepository.findWithSellerAndImagesByIdAndStatus(productId, ProductStatus.ON_SALE)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    long wishlistCount = wishlistItemRepository.countByProductId(productId);
    boolean wishlisted = viewerId != null && wishlistItemRepository.existsByBuyerIdAndProductId(viewerId, productId);
    return ProductResponse.from(product, wishlistCount, wishlisted);
}

private Map<Long, Long> wishlistCounts(List<Long> productIds) {
    if (productIds.isEmpty()) {
        return Map.of();
    }
    return wishlistItemRepository.countByProductIds(productIds).stream()
            .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
}

private Set<Long> wishedProductIds(Long viewerId, List<Long> productIds) {
    if (viewerId == null || productIds.isEmpty()) {
        return Set.of();
    }
    return wishlistItemRepository.findWishedProductIds(viewerId, productIds);
}
```

Add imports:

```java
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.sweet.market.wishlist.repository.WishlistItemRepository;
```

Keep `findMine(Long sellerId, Pageable pageable)` unchanged. Seller-owned lists can use the default `0/false` constructor from the existing projection.

- [ ] **Step 5: Pass optional viewer id from controller**

Modify `ProductController` public reads:

```java
@GetMapping
public ApiResponse<Page<ProductSummaryResponse>> list(
        Authentication authentication,
        @PageableDefault(size = 20) Pageable pageable
) {
    return ApiResponse.ok(productQueryService.findOnSaleProducts(authenticatedMemberId(authentication), pageable));
}

@GetMapping("/{productId}")
public ApiResponse<ProductResponse> get(Authentication authentication, @PathVariable Long productId) {
    return ApiResponse.ok(productQueryService.findOnSaleProduct(authenticatedMemberId(authentication), productId));
}

private Long authenticatedMemberId(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedMember member)) {
        return null;
    }
    return member.id();
}
```

- [ ] **Step 6: Run targeted tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.wishlist.WishlistApiTest --tests com.sweet.market.product.ProductApiTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/product backend/src/main/java/com/sweet/market/wishlist backend/src/test/java/com/sweet/market/product/ProductApiTest.java backend/src/test/java/com/sweet/market/wishlist/WishlistApiTest.java
git commit -m "feat: enrich product reads with wishlist state"
```

---

### Task 5: Web API Types And Wishlist Toggle Component

**Files:**
- Modify: `web/src/features/products/productApi.ts`
- Create: `web/src/features/wishlist/WishlistToggle.tsx`
- Modify: `web/src/features/auth/AuthProvider.tsx`

- [ ] **Step 1: Extend product API types and functions**

Modify `productApi.ts`:

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
};

export type Product = Omit<ProductSummary, 'thumbnailUrl'> & {
  description: string;
  images: ProductImage[];
};

export type WishlistResponse = {
  productId: number;
  wishlisted: boolean;
  wishlistCount: number;
};

export type WishlistItem = {
  wishlistItemId: number;
  productId: number;
  sellerId: number;
  sellerNickname: string;
  title: string;
  price: number;
  status: ProductStatus;
  thumbnailUrl: string | null;
  wishlisted: boolean;
  wishlistCount: number;
  wishedAt: string;
};

export function addWishlist(productId: number) {
  return api<WishlistResponse>(`/api/products/${productId}/wishlist`, {
    method: 'POST',
  });
}

export function removeWishlist(productId: number) {
  return api<WishlistResponse>(`/api/products/${productId}/wishlist`, {
    method: 'DELETE',
  });
}

export function getMyWishlist() {
  return api<Page<WishlistItem>>('/api/me/wishlist');
}
```

- [ ] **Step 2: Add authenticated private query key**

Modify `AuthProvider.tsx`:

```ts
const authenticatedPrivateQueryKeys = [
  ['my-orders'],
  ['my-products'],
  ['my-settlements'],
  ['seller-dashboard-report'],
  ['my-wishlist'],
] as const;
```

- [ ] **Step 3: Create reusable toggle**

Create `WishlistToggle.tsx`:

```tsx
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { addWishlist, removeWishlist, type WishlistResponse } from '../products/productApi';

type WishlistToggleProps = {
  productId: number;
  sellerId: number;
  wishlisted: boolean;
  wishlistCount: number;
  onChanged?: (response: WishlistResponse) => void;
};

export function WishlistToggle({ productId, sellerId, wishlisted, wishlistCount, onChanged }: WishlistToggleProps) {
  const { member } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isOwnProduct = member?.id === sellerId;

  const mutation = useMutation({
    mutationFn: () => (wishlisted ? removeWishlist(productId) : addWishlist(productId)),
    onSuccess: async (response) => {
      onChanged?.(response);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['products', productId] }),
        queryClient.invalidateQueries({ queryKey: ['my-wishlist'] }),
      ]);
    },
  });

  if (!member) {
    return (
      <button
        type="button"
        className="wishlist-button"
        aria-label="로그인하고 찜하기"
        onClick={(event) => {
          event.preventDefault();
          navigate('/login', { state: { from: `${location.pathname}${location.search}` } });
        }}
      >
        <span aria-hidden="true">♡</span>
        <span>{wishlistCount}</span>
      </button>
    );
  }

  if (isOwnProduct) {
    return (
      <span className="wishlist-own-product" aria-label="내 상품">
        내 상품
      </span>
    );
  }

  return (
    <button
      type="button"
      className={`wishlist-button${wishlisted ? ' wishlist-button-active' : ''}`}
      aria-label={wishlisted ? '찜 해제' : '찜하기'}
      disabled={mutation.isPending}
      onClick={(event) => {
        event.preventDefault();
        mutation.mutate();
      }}
    >
      <span aria-hidden="true">{wishlisted ? '♥' : '♡'}</span>
      <span>{mutation.data?.wishlistCount ?? wishlistCount}</span>
    </button>
  );
}
```

- [ ] **Step 4: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add web/src/features/products/productApi.ts web/src/features/wishlist/WishlistToggle.tsx web/src/features/auth/AuthProvider.tsx
git commit -m "feat: add wishlist web api"
```

---

### Task 6: Web Product List And Detail Wishlist UX

**Files:**
- Modify: `web/src/pages/HomePage.tsx`
- Modify: `web/src/pages/ProductDetailPage.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Update home product cards**

Modify imports in `HomePage.tsx`:

```ts
import { WishlistToggle } from '../features/wishlist/WishlistToggle';
```

Replace each card `Link` with an article that has a nested product link and separate wishlist action:

```tsx
<article className="product-card" key={product.id}>
  <Link className="product-card-link" to={`/products/${product.id}`}>
    <ProductThumb product={product} />
    <div className="product-card-body">
      <div className="product-card-title-row">
        <h2>{product.title}</h2>
        <StatusBadge status={product.status} />
      </div>
      <strong>{currencyFormatter.format(product.price)}원</strong>
      <span>{product.sellerNickname}</span>
    </div>
  </Link>
  <div className="product-card-wishlist">
    <WishlistToggle
      productId={product.id}
      sellerId={product.sellerId}
      wishlisted={product.wishlisted}
      wishlistCount={product.wishlistCount}
    />
  </div>
</article>
```

- [ ] **Step 2: Update product detail local state**

In `ProductDetailPage.tsx`, import `useState`, `useEffect`, and `WishlistToggle`:

```ts
import { useEffect, useState } from 'react';
import { WishlistToggle } from '../features/wishlist/WishlistToggle';
import { type WishlistResponse } from '../features/products/productApi';
```

Add local wishlist state after the query:

```tsx
const [wishlistState, setWishlistState] = useState<WishlistResponse | null>(null);

useEffect(() => {
  if (!product) {
    setWishlistState(null);
    return;
  }

  setWishlistState({
    productId: product.id,
    wishlisted: product.wishlisted,
    wishlistCount: product.wishlistCount,
  });
}, [product]);
```

Render the toggle near the heading, after seller nickname:

```tsx
<WishlistToggle
  productId={product.id}
  sellerId={product.sellerId}
  wishlisted={wishlistState?.wishlisted ?? product.wishlisted}
  wishlistCount={wishlistState?.wishlistCount ?? product.wishlistCount}
  onChanged={setWishlistState}
/>
```

- [ ] **Step 3: Add styles**

Append focused CSS:

```css
.product-card {
  position: relative;
}

.product-card-link {
  display: block;
}

.product-card-wishlist {
  position: absolute;
  top: 10px;
  right: 10px;
}

.wishlist-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  border: 1px solid #dfe6ee;
  border-radius: 999px;
  padding: 7px 10px;
  background: rgb(255 255 255 / 92%);
  color: #52616f;
  cursor: pointer;
  font-weight: 900;
  box-shadow: 0 6px 16px rgb(20 38 58 / 10%);
}

.wishlist-button-active {
  border-color: #f4b4ad;
  color: #b42318;
}

.wishlist-button:disabled {
  cursor: wait;
  opacity: 0.7;
}

.wishlist-own-product {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  border: 1px solid #dfe6ee;
  border-radius: 999px;
  padding: 7px 10px;
  background: #eef2f6;
  color: #637282;
  font-size: 13px;
  font-weight: 900;
}
```

- [ ] **Step 4: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add web/src/pages/HomePage.tsx web/src/pages/ProductDetailPage.tsx web/src/shared/styles.css
git commit -m "feat: show wishlist actions on products"
```

---

### Task 7: Buyer Wishlist Web Page

**Files:**
- Create: `web/src/pages/MyWishlistPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/shared/layout/Shell.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Create wishlist page**

Create `MyWishlistPage.tsx`:

```tsx
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getMyWishlist, toProductImageSrc, type WishlistItem } from '../features/products/productApi';
import { WishlistToggle } from '../features/wishlist/WishlistToggle';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

export function MyWishlistPage() {
  const { data, error, isLoading } = useQuery({
    queryKey: ['my-wishlist'],
    queryFn: getMyWishlist,
  });

  if (isLoading) {
    return <p className="status-text">찜한 상품을 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="찜한 상품을 불러오지 못했습니다." />;
  }

  const items = data?.content ?? [];

  return (
    <section className="list-page">
      <header className="list-page-header">
        <h1>찜한 상품</h1>
        <p>관심 있는 상품을 다시 확인해보세요.</p>
      </header>
      {items.length === 0 ? (
        <EmptyState title="찜한 상품이 없습니다" description="상품 목록에서 관심 있는 상품을 저장해보세요." />
      ) : (
        <div className="wishlist-list">
          {items.map((item) => (
            <WishlistCard key={item.wishlistItemId} item={item} />
          ))}
        </div>
      )}
    </section>
  );
}

type WishlistCardProps = {
  item: WishlistItem;
};

function WishlistCard({ item }: WishlistCardProps) {
  const thumbnailSrc = toProductImageSrc(item.thumbnailUrl);
  const cardContent = (
    <>
      {thumbnailSrc ? (
        <img className="wishlist-card-thumb" src={thumbnailSrc} alt="" />
      ) : (
        <div className="wishlist-card-thumb wishlist-card-thumb-fallback">Sweet Market</div>
      )}
      <div>
        <div className="product-card-title-row">
          <h2>{item.title}</h2>
          <StatusBadge status={item.status} />
        </div>
        <strong>{currencyFormatter.format(item.price)}원</strong>
        <span>판매자 {item.sellerNickname}</span>
      </div>
    </>
  );

  return (
    <article className="wishlist-card">
      {item.status === 'ON_SALE' ? (
        <Link className="wishlist-card-main" to={`/products/${item.productId}`}>
          {cardContent}
        </Link>
      ) : (
        <div className="wishlist-card-main wishlist-card-main-disabled">{cardContent}</div>
      )}
      <div className="wishlist-card-actions">
        <WishlistToggle
          productId={item.productId}
          sellerId={item.sellerId}
          wishlisted={item.wishlisted}
          wishlistCount={item.wishlistCount}
        />
        {item.status !== 'ON_SALE' ? <p className="status-text">현재 구매할 수 없는 상품입니다.</p> : null}
      </div>
    </article>
  );
}
```

- [ ] **Step 2: Add route**

Modify `router.tsx` imports:

```ts
import { MyWishlistPage } from '../pages/MyWishlistPage';
```

Add route near other `/me` routes:

```tsx
<Route
  path="me/wishlist"
  element={
    <RequireAuth>
      <MyWishlistPage />
    </RequireAuth>
  }
/>
```

- [ ] **Step 3: Add nav link**

Modify `Shell.tsx` authenticated nav:

```tsx
<NavLink to="/me/wishlist">찜</NavLink>
```

Place it before the existing `내 주문` link.

- [ ] **Step 4: Add wishlist page styles**

Append:

```css
.wishlist-list {
  display: grid;
  gap: 14px;
}

.wishlist-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  border: 1px solid #dfe6ee;
  border-radius: 8px;
  padding: 14px;
  background: #ffffff;
  box-shadow: 0 8px 22px rgb(20 38 58 / 7%);
}

.wishlist-card-main {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  min-width: 0;
}

.wishlist-card-main h2 {
  margin: 0;
  overflow-wrap: anywhere;
}

.wishlist-card-main strong {
  display: block;
  margin-top: 8px;
  font-size: 20px;
}

.wishlist-card-main span {
  display: block;
  margin-top: 6px;
  color: #637282;
}

.wishlist-card-main-disabled {
  cursor: default;
}

.wishlist-card-thumb {
  width: 120px;
  aspect-ratio: 4 / 3;
  border-radius: 8px;
  object-fit: cover;
  background: #e9eef4;
}

.wishlist-card-thumb-fallback {
  display: grid;
  place-items: center;
  color: #637282;
  font-size: 12px;
  font-weight: 900;
}

.wishlist-card-actions {
  display: grid;
  justify-items: end;
  gap: 8px;
}
```

In the existing mobile media query, add:

```css
.wishlist-card,
.wishlist-card-main {
  grid-template-columns: 1fr;
}

.wishlist-card-actions {
  justify-items: start;
}
```

- [ ] **Step 5: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add web/src/pages/MyWishlistPage.tsx web/src/app/router.tsx web/src/shared/layout/Shell.tsx web/src/shared/styles.css
git commit -m "feat: add buyer wishlist page"
```

---

### Task 8: Full Verification And Handoff

**Files:**
- Create: `docs/superpowers/handoffs/2026-06-28-milestone-15-wishlist-handoff.md`
- Verify: entire backend and web app

- [ ] **Step 1: Run full backend test suite**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: TypeScript checks and Vite build complete successfully.

- [ ] **Step 3: Run diff check**

Run from repository root:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 4: Write handoff**

Create `docs/superpowers/handoffs/2026-06-28-milestone-15-wishlist-handoff.md`:

```markdown
# Milestone 15 Wishlist Handoff

## Completed

- Added buyer wishlist item relationship with uniqueness by buyer and product.
- Added idempotent wishlist add and remove APIs.
- Added buyer wishlist page API sorted by newest saved item first.
- Kept reserved and sold-out wished products visible while hiding hidden products.
- Added wishlist count and viewer state to product list and detail reads.
- Added web wishlist toggles on product cards and product detail.
- Added `/me/wishlist` page.

## Verification

- Backend tests: `.\gradlew.bat --no-daemon test`
- Web build: `npm run build`
- Diff check: `git diff --check`

## Notes

- New wishlist additions are limited to `ON_SALE` products.
- Existing wishlist rows are preserved when products become `RESERVED`, `SOLD_OUT`, or `HIDDEN`.
- Hidden products are omitted from buyer wishlist responses.
- Future restock, relisting, and notification work remains out of scope.
```

- [ ] **Step 5: Check final status**

Run:

```powershell
git status --short --branch --untracked-files=all
```

Expected: only intended Milestone 15 files are modified or added before commit.

- [ ] **Step 6: Commit**

```powershell
git add backend web docs/superpowers/handoffs/2026-06-28-milestone-15-wishlist-handoff.md
git commit -m "feat: add wishlist"
```

If previous tasks already committed feature slices, use this final commit only for the handoff:

```powershell
git add docs/superpowers/handoffs/2026-06-28-milestone-15-wishlist-handoff.md
git commit -m "docs: add milestone 15 handoff"
```

---

## Final Verification Checklist

- [ ] `backend/src/main/resources/application.yaml` in the main checkout was not staged, overwritten, reset, or discarded.
- [ ] New JUnit `@Test` methods use Korean_with_underscores.
- [ ] `wishlist_items` is included in integration test cleanup.
- [ ] `POST /api/products/{productId}/wishlist` requires authentication.
- [ ] `DELETE /api/products/{productId}/wishlist` requires authentication.
- [ ] `GET /api/me/wishlist` requires authentication.
- [ ] Anonymous `GET /api/products` still works.
- [ ] Anonymous `GET /api/products/{productId}` still works.
- [ ] Anonymous product responses include `wishlisted=false`.
- [ ] Wishlist page hides `HIDDEN` products and keeps `RESERVED` and `SOLD_OUT`.
- [ ] Web build passes after route, nav, and component changes.
