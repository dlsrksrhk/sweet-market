# Milestone 11 Admin Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a new admin operations console where admins can search products, orders, and members, inspect details, and hide products while preserving existing domain rules.

**Architecture:** Backend work adds domain-local admin API packages under product, order, and member. List APIs use DTO projection queries; detail APIs fetch only the required aggregate context; product hide reuses `Product.hide()`. Frontend work adds `/admin/operations`, a typed admin operations API client, and dense search/detail panels that match the existing settlement operations style.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, Spring Security, JUnit 5, MockMvc, Testcontainers PostgreSQL, Vite React TypeScript, TanStack Query, React Hook Form.

---

## File Structure

- Create: `backend/src/test/java/com/sweet/market/product/admin/AdminProductOperationsApiTest.java`
  - MockMvc integration tests for admin product search, detail, hide, and security.
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductController.java`
  - `/api/admin/products` entry point.
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductSearchRequest.java`
  - Product search query parameters.
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductSummaryResponse.java`
  - Product list projection DTO.
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductDetailResponse.java`
  - Product detail DTO.
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductQueryService.java`
  - Read service for admin product list/detail.
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductService.java`
  - Admin product write service for hide.
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
  - Add admin product projection search and product count query.
- Create: `backend/src/test/java/com/sweet/market/order/admin/AdminOrderOperationsApiTest.java`
  - MockMvc integration tests for admin order search, detail, settlement existence, and security.
- Create: `backend/src/main/java/com/sweet/market/order/admin/AdminOrderController.java`
  - `/api/admin/orders` entry point.
- Create: `backend/src/main/java/com/sweet/market/order/admin/AdminOrderSearchRequest.java`
  - Order search query parameters.
- Create: `backend/src/main/java/com/sweet/market/order/admin/AdminOrderSummaryResponse.java`
  - Order list projection DTO.
- Create: `backend/src/main/java/com/sweet/market/order/admin/AdminOrderDetailResponse.java`
  - Order detail DTO.
- Create: `backend/src/main/java/com/sweet/market/order/admin/AdminOrderQueryService.java`
  - Read service for admin order list/detail.
- Modify: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`
  - Add admin order projection search and buyer order count query.
- Create: `backend/src/test/java/com/sweet/market/member/admin/AdminMemberOperationsApiTest.java`
  - MockMvc integration tests for admin member search, detail counts, and security.
- Create: `backend/src/main/java/com/sweet/market/member/admin/AdminMemberController.java`
  - `/api/admin/members` entry point.
- Create: `backend/src/main/java/com/sweet/market/member/admin/AdminMemberSearchRequest.java`
  - Member search query parameters.
- Create: `backend/src/main/java/com/sweet/market/member/admin/AdminMemberSummaryResponse.java`
  - Member list projection DTO.
- Create: `backend/src/main/java/com/sweet/market/member/admin/AdminMemberDetailResponse.java`
  - Member detail DTO.
- Create: `backend/src/main/java/com/sweet/market/member/admin/AdminMemberQueryService.java`
  - Read service for admin member list/detail.
- Modify: `backend/src/main/java/com/sweet/market/member/repository/MemberRepository.java`
  - Add admin member projection search.
- Create: `web/src/features/admin/adminOperationsApi.ts`
  - Typed client for admin product, order, and member operations.
- Create: `web/src/pages/AdminOperationsPage.tsx`
  - New admin operations UI.
- Modify: `web/src/app/router.tsx`
  - Add `/admin/operations`.
- Modify: `web/src/shared/layout/Shell.tsx`
  - Point the admin nav link to `/admin/operations`.
- Modify: `web/src/shared/styles.css`
  - Add reusable admin operations table, row, pagination, and detail styles for the new page.

Do not stage or overwrite `backend/src/main/resources/application.yaml`; it has an existing local-only development change.

---

### Task 1: Backend Admin Product Operations

**Files:**
- Create: `backend/src/test/java/com/sweet/market/product/admin/AdminProductOperationsApiTest.java`
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductController.java`
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductSearchRequest.java`
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductDetailResponse.java`
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductQueryService.java`
- Create: `backend/src/main/java/com/sweet/market/product/admin/AdminProductService.java`
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`

- [ ] **Step 1: Write the failing admin product API test**

Create `backend/src/test/java/com/sweet/market/product/admin/AdminProductOperationsApiTest.java`:

```java
package com.sweet.market.product.admin;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

class AdminProductOperationsApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 관리자는_상품_목록을_필터_없이_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-product-list@example.com");
        ProductFixture first = createProduct("first", "Keyboard first");
        ProductFixture second = createProduct("second", "Monitor second");

        mockMvc.perform(get("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productId").value(second.productId()))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(second.sellerId()))
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller-second"))
                .andExpect(jsonPath("$.data.content[0].title").value("Monitor second"))
                .andExpect(jsonPath("$.data.content[0].price").value(30000))
                .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").isEmpty())
                .andExpect(jsonPath("$.data.content[1].productId").value(first.productId()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void 관리자는_판매자_ID로_상품을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-product-seller@example.com");
        createProduct("seller-other", "Other Product");
        ProductFixture target = createProduct("seller-target", "Target Product");

        mockMvc.perform(get("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("sellerId", target.sellerId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productId").value(target.productId()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_상품_상태로_상품을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-product-status@example.com");
        ProductFixture hidden = createProduct("hidden", "Hidden Product");
        hideProduct(hidden.productId());
        createProduct("visible", "Visible Product");

        mockMvc.perform(get("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("status", "HIDDEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productId").value(hidden.productId()))
                .andExpect(jsonPath("$.data.content[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_키워드로_상품을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-product-keyword@example.com");
        createProduct("keyword-other", "Desk Lamp");
        ProductFixture target = createProduct("keyword-target", "Gaming Keyboard");

        mockMvc.perform(get("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("keyword", "Keyboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productId").value(target.productId()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_상품_상세를_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-product-detail@example.com");
        ProductFixture product = createProduct("detail", "Detail Product");

        mockMvc.perform(get("/api/admin/products/{productId}", product.productId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.productId()))
                .andExpect(jsonPath("$.data.sellerId").value(product.sellerId()))
                .andExpect(jsonPath("$.data.sellerNickname").value("seller-detail"))
                .andExpect(jsonPath("$.data.title").value("Detail Product"))
                .andExpect(jsonPath("$.data.description").value("description detail"))
                .andExpect(jsonPath("$.data.price").value(30000))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.imageUrls").isArray());
    }

    @Test
    void 관리자는_상품을_숨긴다() throws Exception {
        String adminToken = createAdminAndLogin("admin-product-hide@example.com");
        ProductFixture product = createProduct("hide", "Hide Product");

        mockMvc.perform(post("/api/admin/products/{productId}/hide", product.productId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.productId()))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
    }

    @Test
    void 이미_숨김_상품을_다시_숨겨도_숨김_상태를_유지한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-product-hide-repeat@example.com");
        ProductFixture product = createProduct("hide-repeat", "Hide Repeat Product");

        mockMvc.perform(post("/api/admin/products/{productId}/hide", product.productId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        mockMvc.perform(post("/api/admin/products/{productId}/hide", product.productId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
    }

    @Test
    void 예약중_상품은_관리자가_숨길_수_없다() throws Exception {
        String adminToken = createAdminAndLogin("admin-product-reserved@example.com");
        ProductFixture product = createReservedProduct("reserved");

        mockMvc.perform(post("/api/admin/products/{productId}/hide", product.productId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CHANGE_NOT_ALLOWED"));
    }

    @Test
    void 일반_회원은_관리자_상품_목록에_접근할_수_없다() throws Exception {
        String memberToken = createMemberAndLogin("member-product-admin@example.com");

        mockMvc.perform(get("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 인증되지_않은_사용자는_관리자_상품_목록에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
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

    private ProductFixture createProduct(String suffix, String title) {
        return transactionTemplate.execute(status -> {
            Member seller = Member.create("seller-product-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
            entityManager.persist(seller);
            Product product = Product.create(seller, title, "description " + suffix, 30000L);
            entityManager.persist(product);
            entityManager.flush();
            return new ProductFixture(product.getId(), seller.getId());
        });
    }

    private ProductFixture createReservedProduct(String suffix) {
        return transactionTemplate.execute(status -> {
            Member seller = Member.create("seller-product-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
            Member buyer = Member.create("buyer-product-" + suffix + "@example.com", "encoded-password", "buyer-" + suffix);
            entityManager.persist(seller);
            entityManager.persist(buyer);
            Product product = Product.create(seller, "Reserved Product " + suffix, "description " + suffix, 30000L);
            entityManager.persist(product);
            Order.create(buyer, product);
            entityManager.flush();
            return new ProductFixture(product.getId(), seller.getId());
        });
    }

    private void hideProduct(Long productId) {
        transactionTemplate.executeWithoutResult(status -> {
            Product product = entityManager.find(Product.class, productId);
            product.hide();
        });
    }

    private record ProductFixture(Long productId, Long sellerId) {
    }
}
```

- [ ] **Step 2: Run the product API test and verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.admin.AdminProductOperationsApiTest
```

Expected: FAIL with compilation errors or 404 responses because the admin product API does not exist.

- [ ] **Step 3: Add admin product request and response records**

Create `backend/src/main/java/com/sweet/market/product/admin/AdminProductSearchRequest.java`:

```java
package com.sweet.market.product.admin;

import com.sweet.market.product.domain.ProductStatus;

public record AdminProductSearchRequest(
        Long sellerId,
        ProductStatus status,
        String keyword
) {

    public String normalizedKeyword() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
```

Create `backend/src/main/java/com/sweet/market/product/admin/AdminProductSummaryResponse.java`:

```java
package com.sweet.market.product.admin;

import com.sweet.market.product.domain.ProductStatus;

public record AdminProductSummaryResponse(
        Long productId,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        String status,
        String thumbnailUrl
) {

    public AdminProductSummaryResponse(
            Long productId,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl
    ) {
        this(productId, sellerId, sellerNickname, title, price, status.name(), thumbnailUrl);
    }
}
```

Create `backend/src/main/java/com/sweet/market/product/admin/AdminProductDetailResponse.java`:

```java
package com.sweet.market.product.admin;

import java.util.List;

import com.sweet.market.product.domain.Product;

public record AdminProductDetailResponse(
        Long productId,
        Long sellerId,
        String sellerNickname,
        String title,
        String description,
        long price,
        String status,
        List<String> imageUrls
) {

    public static AdminProductDetailResponse from(Product product) {
        return new AdminProductDetailResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getNickname(),
                product.getTitle(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus().name(),
                product.getImages().stream()
                        .map(image -> image.getImageUrl())
                        .toList()
        );
    }
}
```

- [ ] **Step 4: Add admin product repository queries**

Modify `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java` by adding these imports:

```java
import com.sweet.market.product.admin.AdminProductSummaryResponse;
```

Add these methods inside `ProductRepository`:

```java
    @Query(value = """
            select new com.sweet.market.product.admin.AdminProductSummaryResponse(
                p.id,
                s.id,
                s.nickname,
                p.title,
                p.price,
                p.status,
                (
                    select min(i.imageUrl)
                    from ProductImage i
                    where i.product = p
                )
            )
            from Product p
            join p.seller s
            where (:sellerId is null or s.id = :sellerId)
              and (:status is null or p.status = :status)
              and (:keyword is null or lower(p.title) like lower(concat('%', :keyword, '%')))
            order by p.id desc
            """,
            countQuery = """
            select count(p)
            from Product p
            join p.seller s
            where (:sellerId is null or s.id = :sellerId)
              and (:status is null or p.status = :status)
              and (:keyword is null or lower(p.title) like lower(concat('%', :keyword, '%')))
            """)
    Page<AdminProductSummaryResponse> searchAdminProducts(
            @Param("sellerId") Long sellerId,
            @Param("status") ProductStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    long countBySellerId(Long sellerId);
```

- [ ] **Step 5: Add admin product services and controller**

Create `backend/src/main/java/com/sweet/market/product/admin/AdminProductQueryService.java`:

```java
package com.sweet.market.product.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class AdminProductQueryService {

    private final ProductRepository productRepository;

    public AdminProductQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminProductSummaryResponse> search(AdminProductSearchRequest request, Pageable pageable) {
        return productRepository.searchAdminProducts(
                request.sellerId(),
                request.status(),
                request.normalizedKeyword(),
                pageable
        );
    }

    @Transactional(readOnly = true)
    public AdminProductDetailResponse findDetail(Long productId) {
        Product product = productRepository.findWithSellerAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return AdminProductDetailResponse.from(product);
    }
}
```

Create `backend/src/main/java/com/sweet/market/product/admin/AdminProductService.java`:

```java
package com.sweet.market.product.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class AdminProductService {

    private final ProductRepository productRepository;

    public AdminProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public AdminProductDetailResponse hide(Long productId) {
        Product product = productRepository.findWithSellerAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        try {
            product.hide();
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_CHANGE_NOT_ALLOWED);
        }
        return AdminProductDetailResponse.from(product);
    }
}
```

Create `backend/src/main/java/com/sweet/market/product/admin/AdminProductController.java`:

```java
package com.sweet.market.product.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final AdminProductQueryService adminProductQueryService;
    private final AdminProductService adminProductService;

    public AdminProductController(
            AdminProductQueryService adminProductQueryService,
            AdminProductService adminProductService
    ) {
        this.adminProductQueryService = adminProductQueryService;
        this.adminProductService = adminProductService;
    }

    @GetMapping
    public ApiResponse<Page<AdminProductSummaryResponse>> search(
            @ModelAttribute AdminProductSearchRequest request,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(adminProductQueryService.search(request, pageable));
    }

    @GetMapping("/{productId}")
    public ApiResponse<AdminProductDetailResponse> detail(@PathVariable Long productId) {
        return ApiResponse.ok(adminProductQueryService.findDetail(productId));
    }

    @PostMapping("/{productId}/hide")
    public ApiResponse<AdminProductDetailResponse> hide(@PathVariable Long productId) {
        return ApiResponse.ok(adminProductService.hide(productId));
    }
}
```

- [ ] **Step 6: Run the product API test and commit**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.product.admin.AdminProductOperationsApiTest
```

Expected: PASS.

Commit:

```powershell
git add backend/src/test/java/com/sweet/market/product/admin/AdminProductOperationsApiTest.java backend/src/main/java/com/sweet/market/product/admin/AdminProductController.java backend/src/main/java/com/sweet/market/product/admin/AdminProductSearchRequest.java backend/src/main/java/com/sweet/market/product/admin/AdminProductSummaryResponse.java backend/src/main/java/com/sweet/market/product/admin/AdminProductDetailResponse.java backend/src/main/java/com/sweet/market/product/admin/AdminProductQueryService.java backend/src/main/java/com/sweet/market/product/admin/AdminProductService.java backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java
git commit -m "feat: add admin product operations api"
```

---

### Task 2: Backend Admin Order Operations

**Files:**
- Create: `backend/src/test/java/com/sweet/market/order/admin/AdminOrderOperationsApiTest.java`
- Create: `backend/src/main/java/com/sweet/market/order/admin/AdminOrderController.java`
- Create: `backend/src/main/java/com/sweet/market/order/admin/AdminOrderSearchRequest.java`
- Create: `backend/src/main/java/com/sweet/market/order/admin/AdminOrderSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/order/admin/AdminOrderDetailResponse.java`
- Create: `backend/src/main/java/com/sweet/market/order/admin/AdminOrderQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`

- [ ] **Step 1: Write the failing admin order API test**

Create `backend/src/test/java/com/sweet/market/order/admin/AdminOrderOperationsApiTest.java` with `IntegrationTestSupport`, `MemberRepository`, `PasswordEncoder`, `EntityManager`, and `TransactionTemplate` fields. Include `createAdminAndLogin`, `createMemberAndLogin`, and `login` helpers with the same bodies shown in Task 1. Add a fixture helper that persists seller, buyer, product, and order in one transaction, and include these test methods with Korean names:

```java
@Test
void 관리자는_주문_목록을_필터_없이_조회한다() throws Exception

@Test
void 관리자는_구매자_ID로_주문을_필터링한다() throws Exception

@Test
void 관리자는_판매자_ID로_주문을_필터링한다() throws Exception

@Test
void 관리자는_주문_상태로_주문을_필터링한다() throws Exception

@Test
void 관리자는_상품_ID로_주문을_필터링한다() throws Exception

@Test
void 관리자는_정산_존재_여부가_포함된_주문_상세를_조회한다() throws Exception

@Test
void 없는_주문_상세는_찾을_수_없다() throws Exception

@Test
void 일반_회원은_관리자_주문_목록에_접근할_수_없다() throws Exception

@Test
void 인증되지_않은_사용자는_관리자_주문_목록에_접근할_수_없다() throws Exception
```

Use these assertions in the first list test:

```java
mockMvc.perform(get("/api/admin/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].orderId").value(second.orderId()))
        .andExpect(jsonPath("$.data.content[0].productId").value(second.productId()))
        .andExpect(jsonPath("$.data.content[0].productTitle").value("Admin Order Product second"))
        .andExpect(jsonPath("$.data.content[0].productPrice").value(40000))
        .andExpect(jsonPath("$.data.content[0].buyerId").value(second.buyerId()))
        .andExpect(jsonPath("$.data.content[0].buyerNickname").value("buyer-second"))
        .andExpect(jsonPath("$.data.content[0].sellerId").value(second.sellerId()))
        .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller-second"))
        .andExpect(jsonPath("$.data.content[0].status").value("CREATED"))
        .andExpect(jsonPath("$.data.content[0].productStatus").value("RESERVED"))
        .andExpect(jsonPath("$.data.totalElements").value(2));
```

Use this detail assertion for settlement context:

```java
mockMvc.perform(get("/api/admin/orders/{orderId}", order.orderId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orderId").value(order.orderId()))
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.productStatus").value("SOLD_OUT"))
        .andExpect(jsonPath("$.data.orderedAt").isNotEmpty())
        .andExpect(jsonPath("$.data.canceledAt").isEmpty())
        .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
        .andExpect(jsonPath("$.data.settlementExists").value(true));
```

Fixture rule: create orders through `Order.create(buyer, product)`. For a confirmed order, call `markPaid()`, `startShipping()`, `completeDelivery()`, and `confirm()`, then persist `Settlement.create(order)` when the test needs `settlementExists=true`.

- [ ] **Step 2: Run the order API test and verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.order.admin.AdminOrderOperationsApiTest
```

Expected: FAIL with compilation errors or 404 responses because the admin order API does not exist.

- [ ] **Step 3: Add admin order DTOs**

Create `backend/src/main/java/com/sweet/market/order/admin/AdminOrderSearchRequest.java`:

```java
package com.sweet.market.order.admin;

import com.sweet.market.order.domain.OrderStatus;

public record AdminOrderSearchRequest(
        Long buyerId,
        Long sellerId,
        OrderStatus status,
        Long productId
) {
}
```

Create `backend/src/main/java/com/sweet/market/order/admin/AdminOrderSummaryResponse.java`:

```java
package com.sweet.market.order.admin;

import java.time.LocalDateTime;

import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.product.domain.ProductStatus;

public record AdminOrderSummaryResponse(
        Long orderId,
        Long productId,
        String productTitle,
        long productPrice,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        String status,
        String productStatus,
        LocalDateTime orderedAt
) {

    public AdminOrderSummaryResponse(
            Long orderId,
            Long productId,
            String productTitle,
            long productPrice,
            Long buyerId,
            String buyerNickname,
            Long sellerId,
            String sellerNickname,
            OrderStatus status,
            ProductStatus productStatus,
            LocalDateTime orderedAt
    ) {
        this(
                orderId,
                productId,
                productTitle,
                productPrice,
                buyerId,
                buyerNickname,
                sellerId,
                sellerNickname,
                status.name(),
                productStatus.name(),
                orderedAt
        );
    }
}
```

Create `backend/src/main/java/com/sweet/market/order/admin/AdminOrderDetailResponse.java`:

```java
package com.sweet.market.order.admin;

import java.time.LocalDateTime;

import com.sweet.market.order.domain.Order;

public record AdminOrderDetailResponse(
        Long orderId,
        Long productId,
        String productTitle,
        long productPrice,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        String status,
        String productStatus,
        LocalDateTime orderedAt,
        LocalDateTime canceledAt,
        LocalDateTime confirmedAt,
        boolean settlementExists
) {

    public static AdminOrderDetailResponse from(Order order, boolean settlementExists) {
        return new AdminOrderDetailResponse(
                order.getId(),
                order.getProduct().getId(),
                order.getProduct().getTitle(),
                order.getProduct().getPrice(),
                order.getBuyer().getId(),
                order.getBuyer().getNickname(),
                order.getProduct().getSeller().getId(),
                order.getProduct().getSeller().getNickname(),
                order.getStatus().name(),
                order.getProduct().getStatus().name(),
                order.getOrderedAt(),
                order.getCanceledAt(),
                order.getConfirmedAt(),
                settlementExists
        );
    }
}
```

- [ ] **Step 4: Add admin order repository queries**

Modify `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java` by adding imports:

```java
import com.sweet.market.order.admin.AdminOrderSummaryResponse;
import com.sweet.market.order.domain.OrderStatus;
```

Add these methods inside `OrderRepository`:

```java
    @Query(value = """
            select new com.sweet.market.order.admin.AdminOrderSummaryResponse(
                o.id,
                p.id,
                p.title,
                p.price,
                buyer.id,
                buyer.nickname,
                seller.id,
                seller.nickname,
                o.status,
                p.status,
                o.orderedAt
            )
            from Order o
            join o.buyer buyer
            join o.product p
            join p.seller seller
            where (:buyerId is null or buyer.id = :buyerId)
              and (:sellerId is null or seller.id = :sellerId)
              and (:status is null or o.status = :status)
              and (:productId is null or p.id = :productId)
            order by o.id desc
            """,
            countQuery = """
            select count(o)
            from Order o
            join o.buyer buyer
            join o.product p
            join p.seller seller
            where (:buyerId is null or buyer.id = :buyerId)
              and (:sellerId is null or seller.id = :sellerId)
              and (:status is null or o.status = :status)
              and (:productId is null or p.id = :productId)
            """)
    Page<AdminOrderSummaryResponse> searchAdminOrders(
            @Param("buyerId") Long buyerId,
            @Param("sellerId") Long sellerId,
            @Param("status") OrderStatus status,
            @Param("productId") Long productId,
            Pageable pageable
    );

    long countByBuyerId(Long buyerId);
```

- [ ] **Step 5: Add admin order query service and controller**

Create `backend/src/main/java/com/sweet/market/order/admin/AdminOrderQueryService.java`:

```java
package com.sweet.market.order.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.settlement.repository.SettlementRepository;

@Service
public class AdminOrderQueryService {

    private final OrderRepository orderRepository;
    private final SettlementRepository settlementRepository;

    public AdminOrderQueryService(OrderRepository orderRepository, SettlementRepository settlementRepository) {
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminOrderSummaryResponse> search(AdminOrderSearchRequest request, Pageable pageable) {
        return orderRepository.searchAdminOrders(
                request.buyerId(),
                request.sellerId(),
                request.status(),
                request.productId(),
                pageable
        );
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse findDetail(Long orderId) {
        Order order = orderRepository.findWithBuyerAndProductById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        boolean settlementExists = settlementRepository.existsByOrderId(orderId);
        return AdminOrderDetailResponse.from(order, settlementExists);
    }
}
```

Create `backend/src/main/java/com/sweet/market/order/admin/AdminOrderController.java`:

```java
package com.sweet.market.order.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderQueryService adminOrderQueryService;

    public AdminOrderController(AdminOrderQueryService adminOrderQueryService) {
        this.adminOrderQueryService = adminOrderQueryService;
    }

    @GetMapping
    public ApiResponse<Page<AdminOrderSummaryResponse>> search(
            @ModelAttribute AdminOrderSearchRequest request,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(adminOrderQueryService.search(request, pageable));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<AdminOrderDetailResponse> detail(@PathVariable Long orderId) {
        return ApiResponse.ok(adminOrderQueryService.findDetail(orderId));
    }
}
```

- [ ] **Step 6: Run the order API test and commit**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.order.admin.AdminOrderOperationsApiTest
```

Expected: PASS.

Commit:

```powershell
git add backend/src/test/java/com/sweet/market/order/admin/AdminOrderOperationsApiTest.java backend/src/main/java/com/sweet/market/order/admin/AdminOrderController.java backend/src/main/java/com/sweet/market/order/admin/AdminOrderSearchRequest.java backend/src/main/java/com/sweet/market/order/admin/AdminOrderSummaryResponse.java backend/src/main/java/com/sweet/market/order/admin/AdminOrderDetailResponse.java backend/src/main/java/com/sweet/market/order/admin/AdminOrderQueryService.java backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java
git commit -m "feat: add admin order operations api"
```

---

### Task 3: Backend Admin Member Operations

**Files:**
- Create: `backend/src/test/java/com/sweet/market/member/admin/AdminMemberOperationsApiTest.java`
- Create: `backend/src/main/java/com/sweet/market/member/admin/AdminMemberController.java`
- Create: `backend/src/main/java/com/sweet/market/member/admin/AdminMemberSearchRequest.java`
- Create: `backend/src/main/java/com/sweet/market/member/admin/AdminMemberSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/member/admin/AdminMemberDetailResponse.java`
- Create: `backend/src/main/java/com/sweet/market/member/admin/AdminMemberQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/member/repository/MemberRepository.java`

- [ ] **Step 1: Write the failing admin member API test**

Create `backend/src/test/java/com/sweet/market/member/admin/AdminMemberOperationsApiTest.java`. Use the login helper shape from `AdminSettlementApiTest`, and include these test methods:

```java
@Test
void 관리자는_회원_목록을_필터_없이_조회한다() throws Exception

@Test
void 관리자는_이메일_일부로_회원을_필터링한다() throws Exception

@Test
void 관리자는_닉네임_일부로_회원을_필터링한다() throws Exception

@Test
void 관리자는_역할로_회원을_필터링한다() throws Exception

@Test
void 관리자는_상품수와_주문수가_포함된_회원_상세를_조회한다() throws Exception

@Test
void 없는_회원_상세는_찾을_수_없다() throws Exception

@Test
void 일반_회원은_관리자_회원_목록에_접근할_수_없다() throws Exception

@Test
void 인증되지_않은_사용자는_관리자_회원_목록에_접근할_수_없다() throws Exception
```

Use this assertion in the detail test:

```java
mockMvc.perform(get("/api/admin/members/{memberId}", member.memberId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memberId").value(member.memberId()))
        .andExpect(jsonPath("$.data.email").value("target-member@example.com"))
        .andExpect(jsonPath("$.data.nickname").value("target-member"))
        .andExpect(jsonPath("$.data.role").value("MEMBER"))
        .andExpect(jsonPath("$.data.productCount").value(2))
        .andExpect(jsonPath("$.data.orderCount").value(1));
```

Fixture rule: create two products owned by the target member for `productCount=2`, and create one order where the target member is the buyer for `orderCount=1`.

- [ ] **Step 2: Run the member API test and verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.member.admin.AdminMemberOperationsApiTest
```

Expected: FAIL with compilation errors or 404 responses because the admin member API does not exist.

- [ ] **Step 3: Add admin member DTOs**

Create `backend/src/main/java/com/sweet/market/member/admin/AdminMemberSearchRequest.java`:

```java
package com.sweet.market.member.admin;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.domain.MemberRole;

public record AdminMemberSearchRequest(
        String email,
        String nickname,
        MemberRole role
) {

    public String normalizedEmail() {
        if (email == null || email.isBlank()) {
            return null;
        }
        return Member.normalizeEmail(email);
    }

    public String normalizedNickname() {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        return nickname.trim();
    }
}
```

Create `backend/src/main/java/com/sweet/market/member/admin/AdminMemberSummaryResponse.java`:

```java
package com.sweet.market.member.admin;

import com.sweet.market.member.domain.MemberRole;

public record AdminMemberSummaryResponse(
        Long memberId,
        String email,
        String nickname,
        String role
) {

    public AdminMemberSummaryResponse(Long memberId, String email, String nickname, MemberRole role) {
        this(memberId, email, nickname, role.name());
    }
}
```

Create `backend/src/main/java/com/sweet/market/member/admin/AdminMemberDetailResponse.java`:

```java
package com.sweet.market.member.admin;

import com.sweet.market.member.domain.Member;

public record AdminMemberDetailResponse(
        Long memberId,
        String email,
        String nickname,
        String role,
        long productCount,
        long orderCount
) {

    public static AdminMemberDetailResponse from(Member member, long productCount, long orderCount) {
        return new AdminMemberDetailResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole().name(),
                productCount,
                orderCount
        );
    }
}
```

- [ ] **Step 4: Add admin member repository query**

Modify `backend/src/main/java/com/sweet/market/member/repository/MemberRepository.java` by adding imports:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.member.admin.AdminMemberSummaryResponse;
import com.sweet.market.member.domain.MemberRole;
```

Add this method inside `MemberRepository`:

```java
    @Query(value = """
            select new com.sweet.market.member.admin.AdminMemberSummaryResponse(
                m.id,
                m.email,
                m.nickname,
                m.role
            )
            from Member m
            where (:email is null or lower(m.email) like lower(concat('%', :email, '%')))
              and (:nickname is null or lower(m.nickname) like lower(concat('%', :nickname, '%')))
              and (:role is null or m.role = :role)
            order by m.id desc
            """,
            countQuery = """
            select count(m)
            from Member m
            where (:email is null or lower(m.email) like lower(concat('%', :email, '%')))
              and (:nickname is null or lower(m.nickname) like lower(concat('%', :nickname, '%')))
              and (:role is null or m.role = :role)
            """)
    Page<AdminMemberSummaryResponse> searchAdminMembers(
            @Param("email") String email,
            @Param("nickname") String nickname,
            @Param("role") MemberRole role,
            Pageable pageable
    );
```

- [ ] **Step 5: Add admin member query service and controller**

Create `backend/src/main/java/com/sweet/market/member/admin/AdminMemberQueryService.java`:

```java
package com.sweet.market.member.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class AdminMemberQueryService {

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public AdminMemberQueryService(
            MemberRepository memberRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository
    ) {
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminMemberSummaryResponse> search(AdminMemberSearchRequest request, Pageable pageable) {
        return memberRepository.searchAdminMembers(
                request.normalizedEmail(),
                request.normalizedNickname(),
                request.role(),
                pageable
        );
    }

    @Transactional(readOnly = true)
    public AdminMemberDetailResponse findDetail(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        long productCount = productRepository.countBySellerId(memberId);
        long orderCount = orderRepository.countByBuyerId(memberId);
        return AdminMemberDetailResponse.from(member, productCount, orderCount);
    }
}
```

Create `backend/src/main/java/com/sweet/market/member/admin/AdminMemberController.java`:

```java
package com.sweet.market.member.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final AdminMemberQueryService adminMemberQueryService;

    public AdminMemberController(AdminMemberQueryService adminMemberQueryService) {
        this.adminMemberQueryService = adminMemberQueryService;
    }

    @GetMapping
    public ApiResponse<Page<AdminMemberSummaryResponse>> search(
            @ModelAttribute AdminMemberSearchRequest request,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(adminMemberQueryService.search(request, pageable));
    }

    @GetMapping("/{memberId}")
    public ApiResponse<AdminMemberDetailResponse> detail(@PathVariable Long memberId) {
        return ApiResponse.ok(adminMemberQueryService.findDetail(memberId));
    }
}
```

- [ ] **Step 6: Run the member API test and commit**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.member.admin.AdminMemberOperationsApiTest
```

Expected: PASS.

Commit:

```powershell
git add backend/src/test/java/com/sweet/market/member/admin/AdminMemberOperationsApiTest.java backend/src/main/java/com/sweet/market/member/admin/AdminMemberController.java backend/src/main/java/com/sweet/market/member/admin/AdminMemberSearchRequest.java backend/src/main/java/com/sweet/market/member/admin/AdminMemberSummaryResponse.java backend/src/main/java/com/sweet/market/member/admin/AdminMemberDetailResponse.java backend/src/main/java/com/sweet/market/member/admin/AdminMemberQueryService.java backend/src/main/java/com/sweet/market/member/repository/MemberRepository.java
git commit -m "feat: add admin member operations api"
```

---

### Task 4: Web Admin Operations API Client

**Files:**
- Create: `web/src/features/admin/adminOperationsApi.ts`

- [ ] **Step 1: Add the typed admin operations API client**

Create `web/src/features/admin/adminOperationsApi.ts`:

```ts
import { api } from '../../shared/api/http';

export type ProductStatus = 'ON_SALE' | 'RESERVED' | 'SOLD_OUT' | 'HIDDEN';
export type OrderStatus = 'CREATED' | 'PAID' | 'SHIPPING' | 'DELIVERED' | 'CONFIRMED' | 'CANCELED';
export type MemberRole = 'MEMBER' | 'ADMIN';

export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
};

export type AdminProductSummary = {
  productId: number;
  sellerId: number;
  sellerNickname: string;
  title: string;
  price: number;
  status: ProductStatus;
  thumbnailUrl: string | null;
};

export type AdminProductDetail = AdminProductSummary & {
  description: string;
  imageUrls: string[];
};

export type AdminProductSearchInput = {
  sellerId?: number;
  status?: ProductStatus | '';
  keyword?: string;
  page: number;
  size: number;
};

export type AdminOrderSummary = {
  orderId: number;
  productId: number;
  productTitle: string;
  productPrice: number;
  buyerId: number;
  buyerNickname: string;
  sellerId: number;
  sellerNickname: string;
  status: OrderStatus;
  productStatus: ProductStatus;
  orderedAt: string;
};

export type AdminOrderDetail = AdminOrderSummary & {
  canceledAt: string | null;
  confirmedAt: string | null;
  settlementExists: boolean;
};

export type AdminOrderSearchInput = {
  buyerId?: number;
  sellerId?: number;
  status?: OrderStatus | '';
  productId?: number;
  page: number;
  size: number;
};

export type AdminMemberSummary = {
  memberId: number;
  email: string;
  nickname: string;
  role: MemberRole;
};

export type AdminMemberDetail = AdminMemberSummary & {
  productCount: number;
  orderCount: number;
};

export type AdminMemberSearchInput = {
  email?: string;
  nickname?: string;
  role?: MemberRole | '';
  page: number;
  size: number;
};

function appendOptionalParam(searchParams: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && value !== '') {
    searchParams.set(key, String(value));
  }
}

export function getAdminProducts(input: AdminProductSearchInput) {
  const searchParams = new URLSearchParams();
  appendOptionalParam(searchParams, 'sellerId', input.sellerId);
  appendOptionalParam(searchParams, 'status', input.status);
  appendOptionalParam(searchParams, 'keyword', input.keyword?.trim() || undefined);
  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));
  return api<PageResponse<AdminProductSummary>>(`/api/admin/products?${searchParams.toString()}`);
}

export function getAdminProductDetail(productId: number) {
  return api<AdminProductDetail>(`/api/admin/products/${productId}`);
}

export function hideAdminProduct(productId: number) {
  return api<AdminProductDetail>(`/api/admin/products/${productId}/hide`, {
    method: 'POST',
  });
}

export function getAdminOrders(input: AdminOrderSearchInput) {
  const searchParams = new URLSearchParams();
  appendOptionalParam(searchParams, 'buyerId', input.buyerId);
  appendOptionalParam(searchParams, 'sellerId', input.sellerId);
  appendOptionalParam(searchParams, 'status', input.status);
  appendOptionalParam(searchParams, 'productId', input.productId);
  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));
  return api<PageResponse<AdminOrderSummary>>(`/api/admin/orders?${searchParams.toString()}`);
}

export function getAdminOrderDetail(orderId: number) {
  return api<AdminOrderDetail>(`/api/admin/orders/${orderId}`);
}

export function getAdminMembers(input: AdminMemberSearchInput) {
  const searchParams = new URLSearchParams();
  appendOptionalParam(searchParams, 'email', input.email?.trim() || undefined);
  appendOptionalParam(searchParams, 'nickname', input.nickname?.trim() || undefined);
  appendOptionalParam(searchParams, 'role', input.role);
  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));
  return api<PageResponse<AdminMemberSummary>>(`/api/admin/members?${searchParams.toString()}`);
}

export function getAdminMemberDetail(memberId: number) {
  return api<AdminMemberDetail>(`/api/admin/members/${memberId}`);
}
```

- [ ] **Step 2: Run web build and commit**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

Commit:

```powershell
git add web/src/features/admin/adminOperationsApi.ts
git commit -m "feat: add admin operations api client"
```

---

### Task 5: Web Admin Operations Page

**Files:**
- Create: `web/src/pages/AdminOperationsPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/shared/layout/Shell.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Add route and navigation**

Modify `web/src/app/router.tsx`:

```tsx
import { AdminOperationsPage } from '../pages/AdminOperationsPage';
```

Add this route before `admin/batches/settlements`:

```tsx
        <Route
          path="admin/operations"
          element={
            <RequireAdmin>
              <AdminOperationsPage />
            </RequireAdmin>
          }
        />
```

Modify `web/src/shared/layout/Shell.tsx`:

```tsx
{member.role === 'ADMIN' ? <NavLink to="/admin/operations">관리자</NavLink> : null}
```

- [ ] **Step 2: Create the admin operations page**

Create `web/src/pages/AdminOperationsPage.tsx` with these sections:

```tsx
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import {
  getAdminMemberDetail,
  getAdminMembers,
  getAdminOrderDetail,
  getAdminOrders,
  getAdminProductDetail,
  getAdminProducts,
  hideAdminProduct,
  type AdminMemberDetail,
  type AdminMemberSearchInput,
  type AdminOrderDetail,
  type AdminOrderSearchInput,
  type AdminProductDetail,
  type AdminProductSearchInput,
  type MemberRole,
  type OrderStatus,
  type ProductStatus,
} from '../features/admin/adminOperationsApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const PAGE_SIZE = 10;

type ProductSearchForm = {
  sellerId: string;
  status: ProductStatus | '';
  keyword: string;
};

type OrderSearchForm = {
  buyerId: string;
  sellerId: string;
  status: OrderStatus | '';
  productId: string;
};

type MemberSearchForm = {
  email: string;
  nickname: string;
  role: MemberRole | '';
};

export function AdminOperationsPage() {
  const queryClient = useQueryClient();
  const [productInput, setProductInput] = useState<AdminProductSearchInput>({ page: 0, size: PAGE_SIZE });
  const [orderInput, setOrderInput] = useState<AdminOrderSearchInput>({ page: 0, size: PAGE_SIZE });
  const [memberInput, setMemberInput] = useState<AdminMemberSearchInput>({ page: 0, size: PAGE_SIZE });
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null);
  const [selectedMemberId, setSelectedMemberId] = useState<number | null>(null);
  const [hideError, setHideError] = useState<string | null>(null);

  const productForm = useForm<ProductSearchForm>({ defaultValues: { sellerId: '', status: '', keyword: '' } });
  const orderForm = useForm<OrderSearchForm>({
    defaultValues: { buyerId: '', sellerId: '', status: '', productId: '' },
  });
  const memberForm = useForm<MemberSearchForm>({ defaultValues: { email: '', nickname: '', role: '' } });

  const productQuery = useQuery({
    queryKey: ['admin-operations', 'products', productInput],
    queryFn: () => getAdminProducts(productInput),
  });
  const productDetailQuery = useQuery({
    queryKey: ['admin-operations', 'products', 'detail', selectedProductId],
    queryFn: () => getAdminProductDetail(selectedProductId ?? 0),
    enabled: selectedProductId !== null,
  });
  const orderQuery = useQuery({
    queryKey: ['admin-operations', 'orders', orderInput],
    queryFn: () => getAdminOrders(orderInput),
  });
  const orderDetailQuery = useQuery({
    queryKey: ['admin-operations', 'orders', 'detail', selectedOrderId],
    queryFn: () => getAdminOrderDetail(selectedOrderId ?? 0),
    enabled: selectedOrderId !== null,
  });
  const memberQuery = useQuery({
    queryKey: ['admin-operations', 'members', memberInput],
    queryFn: () => getAdminMembers(memberInput),
  });
  const memberDetailQuery = useQuery({
    queryKey: ['admin-operations', 'members', 'detail', selectedMemberId],
    queryFn: () => getAdminMemberDetail(selectedMemberId ?? 0),
    enabled: selectedMemberId !== null,
  });

  const hideMutation = useMutation({
    mutationFn: hideAdminProduct,
    onSuccess: async (product) => {
      setHideError(null);
      setSelectedProductId(product.productId);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin-operations', 'products'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
      ]);
    },
    onError: (error) => {
      setHideError(toErrorMessage(error, '상품을 숨기지 못했습니다.'));
    },
  });

  const onProductSearch = productForm.handleSubmit((values) => {
    setSelectedProductId(null);
    setHideError(null);
    setProductInput({
      sellerId: toOptionalNumber(values.sellerId),
      status: values.status,
      keyword: values.keyword.trim() || undefined,
      page: 0,
      size: PAGE_SIZE,
    });
  });

  const onOrderSearch = orderForm.handleSubmit((values) => {
    setSelectedOrderId(null);
    setOrderInput({
      buyerId: toOptionalNumber(values.buyerId),
      sellerId: toOptionalNumber(values.sellerId),
      status: values.status,
      productId: toOptionalNumber(values.productId),
      page: 0,
      size: PAGE_SIZE,
    });
  });

  const onMemberSearch = memberForm.handleSubmit((values) => {
    setSelectedMemberId(null);
    setMemberInput({
      email: values.email.trim() || undefined,
      nickname: values.nickname.trim() || undefined,
      role: values.role,
      page: 0,
      size: PAGE_SIZE,
    });
  });

  return (
    <section className="admin-operations-page">
      <div className="list-page-header">
        <div>
          <h1>관리자 운영</h1>
          <p>상품, 주문, 회원 상태를 조회하고 필요한 상품만 숨김 처리합니다.</p>
        </div>
        <Link className="text-button" to="/admin/batches/settlements">
          정산 배치
        </Link>
      </div>

      <section className="admin-tool-panel" aria-labelledby="admin-products-title">
        <h2 id="admin-products-title">상품 운영</h2>
        <form className="admin-search-form" onSubmit={onProductSearch}>
          <label>
            판매자 ID
            <input type="number" min="1" step="1" {...productForm.register('sellerId')} />
          </label>
          <label>
            상태
            <select {...productForm.register('status')}>
              <option value="">전체</option>
              <option value="ON_SALE">판매중</option>
              <option value="RESERVED">예약중</option>
              <option value="SOLD_OUT">판매완료</option>
              <option value="HIDDEN">숨김</option>
            </select>
          </label>
          <label>
            키워드
            <input {...productForm.register('keyword')} />
          </label>
          <button type="submit" className="text-button">검색</button>
        </form>
        <AdminProductList
          query={productQuery}
          selectedProductId={selectedProductId}
          onSelect={setSelectedProductId}
          onMovePage={(page) => setProductInput((current) => ({ ...current, page }))}
        />
        <AdminProductDetailPanel
          detail={productDetailQuery.data ?? null}
          isLoading={productDetailQuery.isLoading}
          hasError={Boolean(productDetailQuery.error)}
          hideError={hideError}
          isHiding={hideMutation.isPending}
          onHide={(productId) => hideMutation.mutate(productId)}
        />
      </section>

      <section className="admin-tool-panel" aria-labelledby="admin-orders-title">
        <h2 id="admin-orders-title">주문 조회</h2>
        <form className="admin-search-form" onSubmit={onOrderSearch}>
          <label>
            구매자 ID
            <input type="number" min="1" step="1" {...orderForm.register('buyerId')} />
          </label>
          <label>
            판매자 ID
            <input type="number" min="1" step="1" {...orderForm.register('sellerId')} />
          </label>
          <label>
            상품 ID
            <input type="number" min="1" step="1" {...orderForm.register('productId')} />
          </label>
          <label>
            상태
            <select {...orderForm.register('status')}>
              <option value="">전체</option>
              <option value="CREATED">주문 생성</option>
              <option value="PAID">결제 완료</option>
              <option value="SHIPPING">배송 중</option>
              <option value="DELIVERED">배송 완료</option>
              <option value="CONFIRMED">구매 확정</option>
              <option value="CANCELED">취소</option>
            </select>
          </label>
          <button type="submit" className="text-button">검색</button>
        </form>
        <AdminOrderList
          query={orderQuery}
          selectedOrderId={selectedOrderId}
          onSelect={setSelectedOrderId}
          onMovePage={(page) => setOrderInput((current) => ({ ...current, page }))}
        />
        <AdminOrderDetailPanel
          detail={orderDetailQuery.data ?? null}
          isLoading={orderDetailQuery.isLoading}
          hasError={Boolean(orderDetailQuery.error)}
        />
      </section>

      <section className="admin-tool-panel" aria-labelledby="admin-members-title">
        <h2 id="admin-members-title">회원 조회</h2>
        <form className="admin-search-form" onSubmit={onMemberSearch}>
          <label>
            이메일
            <input {...memberForm.register('email')} />
          </label>
          <label>
            닉네임
            <input {...memberForm.register('nickname')} />
          </label>
          <label>
            역할
            <select {...memberForm.register('role')}>
              <option value="">전체</option>
              <option value="MEMBER">회원</option>
              <option value="ADMIN">관리자</option>
            </select>
          </label>
          <button type="submit" className="text-button">검색</button>
        </form>
        <AdminMemberList
          query={memberQuery}
          selectedMemberId={selectedMemberId}
          onSelect={setSelectedMemberId}
          onMovePage={(page) => setMemberInput((current) => ({ ...current, page }))}
        />
        <AdminMemberDetailPanel
          detail={memberDetailQuery.data ?? null}
          isLoading={memberDetailQuery.isLoading}
          hasError={Boolean(memberDetailQuery.error)}
        />
      </section>
    </section>
  );
}
```

In the same file, add these focused helper component declarations with explicit props. Each component body renders the matching loading, error, empty, row, pagination, and detail states already wired in the JSX above:

```tsx
type ProductListProps = {
  query: {
    data?: { content: import('../features/admin/adminOperationsApi').AdminProductSummary[]; number: number; totalPages: number };
    isLoading: boolean;
    error: unknown;
  };
  selectedProductId: number | null;
  onSelect: (productId: number) => void;
  onMovePage: (page: number) => void;
};

type ProductDetailProps = {
  detail: AdminProductDetail | null;
  isLoading: boolean;
  hasError: boolean;
  hideError: string | null;
  isHiding: boolean;
  onHide: (productId: number) => void;
};

type OrderListProps = {
  query: {
    data?: { content: import('../features/admin/adminOperationsApi').AdminOrderSummary[]; number: number; totalPages: number };
    isLoading: boolean;
    error: unknown;
  };
  selectedOrderId: number | null;
  onSelect: (orderId: number) => void;
  onMovePage: (page: number) => void;
};

type OrderDetailProps = {
  detail: AdminOrderDetail | null;
  isLoading: boolean;
  hasError: boolean;
};

type MemberListProps = {
  query: {
    data?: { content: import('../features/admin/adminOperationsApi').AdminMemberSummary[]; number: number; totalPages: number };
    isLoading: boolean;
    error: unknown;
  };
  selectedMemberId: number | null;
  onSelect: (memberId: number) => void;
  onMovePage: (page: number) => void;
};

type MemberDetailProps = {
  detail: AdminMemberDetail | null;
  isLoading: boolean;
  hasError: boolean;
};

function AdminProductList({ query, selectedProductId, onSelect, onMovePage }: ProductListProps) {
  if (query.isLoading) {
    return <p className="status-text">상품 목록을 불러오고 있습니다.</p>;
  }
  if (query.error) {
    return <ErrorState message="상품 목록을 불러오지 못했습니다." />;
  }
  const products = query.data?.content ?? [];
  if (products.length === 0) {
    return <EmptyState title="상품이 없습니다" description="조건에 맞는 상품이 없습니다." />;
  }
  return (
    <>
      <div className="admin-operations-table" aria-label="관리자 상품 검색 결과">
        <div className="admin-operations-table-head admin-product-grid">
          <span>상품</span>
          <span>판매자</span>
          <span>제목</span>
          <span>가격</span>
          <span>상태</span>
        </div>
        {products.map((product) => (
          <button
            type="button"
            className={`admin-operations-row admin-product-grid ${
              selectedProductId === product.productId ? 'admin-operations-row-selected' : ''
            }`}
            key={product.productId}
            onClick={() => onSelect(product.productId)}
          >
            <span>#{product.productId}</span>
            <span>#{product.sellerId} {product.sellerNickname}</span>
            <span>{product.title}</span>
            <span>{currencyFormatter.format(product.price)}원</span>
            <span><StatusBadge status={product.status} /></span>
          </button>
        ))}
      </div>
      <Pagination page={query.data?.number ?? 0} totalPages={query.data?.totalPages ?? 1} onMovePage={onMovePage} />
    </>
  );
}
```

Implement `AdminProductDetailPanel` with loading, error, "상품을 선택하면 상세 정보가 표시됩니다." empty text, product fields, image count, and a hide button disabled while `isHiding` is true. Implement `AdminOrderList` with order id, product title, buyer, seller, order status, and ordered date rows. Implement `AdminOrderDetailPanel` with loading, error, "주문을 선택하면 상세 정보가 표시됩니다." empty text, lifecycle dates, product status, and `formatBoolean(detail.settlementExists)`. Implement `AdminMemberList` with member id, email, nickname, and role rows. Implement `AdminMemberDetailPanel` with loading, error, "회원을 선택하면 상세 정보가 표시됩니다." empty text, role, product count, and order count. Use `StatusBadge`, `EmptyState`, and `ErrorState`, and reuse formatting helpers:

```ts
const currencyFormatter = new Intl.NumberFormat('ko-KR');
const dateFormatter = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' });

function toOptionalNumber(value: string) {
  const trimmedValue = value.trim();
  if (trimmedValue === '') {
    return undefined;
  }
  const parsedValue = Number(trimmedValue);
  return Number.isInteger(parsedValue) && parsedValue > 0 ? parsedValue : undefined;
}

function formatDate(value: string | null) {
  if (!value) {
    return '-';
  }
  return dateFormatter.format(new Date(value));
}

function formatBoolean(value: boolean) {
  return value ? '있음' : '없음';
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;
  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
```

- [ ] **Step 3: Add admin operations CSS**

Append to `web/src/shared/styles.css` near existing admin styles:

```css
.admin-operations-page {
  display: grid;
  gap: 24px;
}

.admin-operations-table {
  display: grid;
  gap: 8px;
  overflow-x: auto;
}

.admin-operations-row,
.admin-operations-table-head {
  display: grid;
  gap: 10px;
  align-items: center;
  min-width: 820px;
}

.admin-operations-table-head {
  color: #637282;
  font-size: 13px;
  font-weight: 900;
}

.admin-operations-row {
  width: 100%;
  border: 1px solid #dfe6ee;
  border-radius: 8px;
  padding: 12px;
  background: #ffffff;
  color: #172026;
  cursor: pointer;
  font-size: 14px;
  text-align: left;
}

.admin-operations-row:hover,
.admin-operations-row-selected {
  border-color: #0f7b78;
  background: #f0fbfa;
}

.admin-operations-row span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.admin-product-grid {
  grid-template-columns: 80px minmax(140px, 1fr) minmax(180px, 1.5fr) 100px 100px;
}

.admin-order-grid {
  grid-template-columns: 80px minmax(160px, 1.4fr) minmax(140px, 1fr) minmax(140px, 1fr) 110px 150px;
}

.admin-member-grid {
  grid-template-columns: 80px minmax(220px, 1.5fr) minmax(140px, 1fr) 100px;
}

.admin-detail-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
```

- [ ] **Step 4: Run web build and commit**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

Commit:

```powershell
git add web/src/pages/AdminOperationsPage.tsx web/src/app/router.tsx web/src/shared/layout/Shell.tsx web/src/shared/styles.css
git commit -m "feat: add admin operations page"
```

---

### Task 6: Full Verification

**Files:**
- No planned code files. Only edit milestone files when a verification command exposes a failing assertion, TypeScript error, Java compilation error, or runtime behavior mismatch.

- [ ] **Step 1: Run the full backend test suite**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: PASS.

- [ ] **Step 2: Run the full web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 3: Check whitespace and worktree state**

Run:

```powershell
git diff --check
git status --short --branch --untracked-files=all
```

Expected: `git diff --check` has no output. `git status` shows only intentional milestone files if verification fixes were made, plus pre-existing unrelated local files such as `backend/src/main/resources/application.yaml` and the untracked handoff document if they still exist.

- [ ] **Step 4: Commit verification fixes after making a concrete fix**

After editing milestone files to fix a failing verification command, run:

```powershell
git add backend/src/test/java/com/sweet/market/product/admin/AdminProductOperationsApiTest.java backend/src/main/java/com/sweet/market/product/admin backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java backend/src/test/java/com/sweet/market/order/admin/AdminOrderOperationsApiTest.java backend/src/main/java/com/sweet/market/order/admin backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java backend/src/test/java/com/sweet/market/member/admin/AdminMemberOperationsApiTest.java backend/src/main/java/com/sweet/market/member/admin backend/src/main/java/com/sweet/market/member/repository/MemberRepository.java web/src/features/admin/adminOperationsApi.ts web/src/pages/AdminOperationsPage.tsx web/src/app/router.tsx web/src/shared/layout/Shell.tsx web/src/shared/styles.css
git commit -m "fix: stabilize admin operations"
```

Expected: commit succeeds.

---

## Self-Review

- Spec coverage: product search/detail/hide, order search/detail with settlement existence, member search/detail counts, `/admin/operations`, route separation, security, and verification are covered.
- Placeholder scan: no `TBD`, `TODO`, incomplete requirement markers, or deferred implementation notes are included.
- Type consistency: backend DTO names match frontend API type names; route paths match the approved spec; product/order/member status strings match existing enums.
- Scope check: admin role management, member suspension, order correction, full moderation workflow, and settlement detail duplication are excluded.
