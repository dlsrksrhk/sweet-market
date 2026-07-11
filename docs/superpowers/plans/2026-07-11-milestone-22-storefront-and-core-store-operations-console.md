# Milestone 22 Storefront And Core Store Operations Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 구매자가 활성 스토어의 공개 카탈로그를 탐색하고, OWNER와 MANAGER가 권한에 맞는 스토어 운영 콘솔에서 상품 가시성과 멤버십을 안전하게 관리하게 한다.

**Architecture:** 공개 읽기는 `store.storefront`의 전용 projection/service/controller로, 운영 읽기·명령은 `store.operations`로 분리한다. 모든 운영 쓰기는 `StoreAccessService`와 활성 `StoreMembership`을 통과하고, 웹은 공개 storefront 캐시와 operator 캐시를 별도 키로 관리한다.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, PostgreSQL 17/Testcontainers, JUnit 5, MockMvc, Hibernate statistics, React 19, TypeScript 5.8, React Router 7, TanStack Query 5, React Hook Form, Vite 6.

## Global Constraints

- JUnit `@Test` 메서드명은 한국어_밑줄_형식으로 작성한다.
- Gradle은 JDK 21과 `JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`으로 실행한다.
- `Store`, `StoreMembership`, `StoreAccessService`, 필수 `Product.store` 관계를 재사용하고 새로운 판매자 프로필이나 member/product 소유권을 추가하지 않는다.
- 공개 projection은 법적 사업자 정보, 관리자 심사 정보, 멤버십, staff identifier를 노출하지 않는다.
- 상품의 임시 `sellerId`와 `sellerNickname` 호환 필드는 유지한다.
- 주문·환불·정산·리포트의 역사적 판매자 의미는 계속 `Order.seller` 스냅샷을 사용한다.
- 공개 상품 페이지는 offset `Page`를 사용하고 `id`를 최종 정렬 키로 포함한다.
- paginated query에서 image/review to-many fetch join을 사용하지 않는다.
- 공개 전체 페이지는 서비스 계층 최대 3개, 운영 콘솔 초기 읽기는 최대 6개 SQL statement로 제한한다.
- M22에서는 manager 추가/초대/재활성화, owner 이전, 재고, 프로모션, rich search, keyset pagination을 구현하지 않는다.
- M22에서는 상품 timestamp 컬럼을 추가하지 않으며 최신순/오래된순은 상품 ID로 정의한다.
- 웹 테스트 프레임워크를 새로 도입하지 않는다. TypeScript production build와 수동 브라우저 흐름으로 검증한다.
- 기존 상품 이미지, 찜, 장바구니, 주문, 리뷰, 환불, 정산, 판매자 리포트 동작을 보존한다.

---

## Target File Map

### Backend storefront

- Create `backend/src/main/java/com/sweet/market/store/storefront/StorefrontController.java` — public header and public product page endpoints.
- Create `backend/src/main/java/com/sweet/market/store/storefront/StorefrontQueryService.java` — state-safe public reads and page construction.
- Create `backend/src/main/java/com/sweet/market/store/storefront/StorefrontResponse.java` — public header/rating/count contract.
- Create `backend/src/main/java/com/sweet/market/store/storefront/StorefrontProductResponse.java` — dedicated buyer card projection.
- Create `backend/src/main/java/com/sweet/market/store/storefront/StorefrontProductSort.java` — `NEWEST`, `PRICE_ASC`, `PRICE_DESC`.
- Modify `backend/src/main/java/com/sweet/market/store/{api/StoreController.java,application/StoreQueryService.java,repository/StoreRepository.java}` — move the public route out while retaining owner-private reads.
- Delete `backend/src/main/java/com/sweet/market/store/api/PublicStoreResponse.java` after all callers move.
- Modify `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java` — storefront page and store-scoped product loading queries.
- Modify `backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java` — explicitly permit `GET /api/stores/{id}/products`.

### Backend operations

- Create `backend/src/main/java/com/sweet/market/store/operations/{StoreOperationsController,StoreCatalogQueryService,StoreCatalogCommandService,StoreMembershipQueryService,StoreMembershipCommandService}.java`.
- Create `backend/src/main/java/com/sweet/market/store/operations/{OperableStoreResponse,StoreCatalogSummaryResponse,StoreCatalogProductResponse,StoreCatalogSearchRequest,StoreCatalogSort,StoreProductIdsRequest,StoreMembershipResponse}.java`.
- Modify `backend/src/main/java/com/sweet/market/store/application/StoreAccessService.java` — one-query active operator resolution without requiring active store for reads.
- Modify `backend/src/main/java/com/sweet/market/store/repository/StoreMembershipRepository.java` — store/operator projection, active membership list, manager removal lookup.
- Modify `backend/src/main/java/com/sweet/market/product/domain/Product.java` — explicit `HIDDEN -> ON_SALE` transition and price index declaration.
- Modify `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java` — owner-membership protection conflict.

### Migration and backend tests

- Create `backend/src/main/resources/db/migration/V4__add_storefront_price_index.sql`.
- Create `backend/src/test/java/com/sweet/market/store/StorefrontApiTest.java`.
- Create `backend/src/test/java/com/sweet/market/store/StoreOperationsApiTest.java`.
- Create `backend/src/test/java/com/sweet/market/jpalab/StorefrontQueryOptimizationTest.java`.
- Modify `backend/src/test/java/com/sweet/market/{store/StoreApiTest.java,product/domain/ProductTest.java}`.
- Modify `backend/src/test/java/com/sweet/market/store/migration/{StoreMigrationTest,StoreFreshDatabaseStartupTest,StoreSpringBootFlywayTest}.java`.

### Web

- Modify `web/src/features/stores/storeApi.ts` — expanded public header/product contracts and keys.
- Create `web/src/features/stores/storeOperationsApi.ts` — operator types, keys, reads, batch commands, membership removal.
- Create `web/src/features/products/ProductCard.tsx` — reusable buyer product card.
- Create `web/src/features/stores/{StoreCatalogPanel,StoreProfilePanel,StoreMembershipPanel}.tsx` — focused console panels.
- Modify `web/src/pages/{HomePage,StoreProfilePage,MyStorePage,ProductFormPage}.tsx`.
- Modify `web/src/features/auth/AuthProvider.tsx` — remove operator-private cache on auth changes.
- Modify `web/src/shared/{styles.css,ui/ResourceStates.tsx}` — status labels and responsive storefront/console layouts.

### Documentation

- Modify `docs/milestone-21-store-foundation-implementation-notes.md` only if M22 retains a new hybrid-DDL caveat; otherwise create `docs/milestone-22-storefront-implementation-notes.md`.
- Create the final M22 handoff under `docs/superpowers/handoffs/` after verification.

---

### Task 1: Add Visibility Transition And Price-Sort Index Foundation

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/product/domain/Product.java`
- Create: `backend/src/main/resources/db/migration/V4__add_storefront_price_index.sql`
- Test: `backend/src/test/java/com/sweet/market/product/domain/ProductTest.java`
- Test: `backend/src/test/java/com/sweet/market/store/migration/StoreMigrationTest.java`
- Test: `backend/src/test/java/com/sweet/market/store/migration/StoreFreshDatabaseStartupTest.java`
- Test: `backend/src/test/java/com/sweet/market/store/migration/StoreSpringBootFlywayTest.java`

**Interfaces:**
- Produces: `Product.show(): void`, valid only from `HIDDEN`.
- Produces: `idx_products_store_status_price_id` on `(store_id, status, price, id)` in legacy-upgrade and fresh-startup paths.
- Produces: Flyway current version `4`.

- [ ] **Step 1: Write failing product transition tests**

Add these cases to `ProductTest`:

```java
@Test
void 숨김_상품은_다시_판매_중으로_노출할_수_있다() {
    Member seller = Member.create("show@example.com", "encoded-password", "seller");
    Product product = Product.create(seller, "상품", "설명", 10_000L);
    product.hide();

    product.show();

    assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
}

@Test
void 숨김이_아닌_상품은_재노출할_수_없다() {
    Member seller = Member.create("show-conflict@example.com", "encoded-password", "seller");
    Product product = Product.create(seller, "상품", "설명", 10_000L);

    assertThatThrownBy(product::show)
            .isInstanceOf(IllegalStateException.class);
}
```

- [ ] **Step 2: Extend migration assertions and verify RED**

Change Flyway-version assertions from `3` to `4` and assert `idx_products_store_status_price_id` exists in both migration tests. Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.product.domain.ProductTest' --tests 'com.sweet.market.store.migration.*'
```

Expected: FAIL because `show()` and Flyway V4/index do not exist.

- [ ] **Step 3: Implement the domain transition and both DDL paths**

Add:

```java
public void show() {
    if (status != ProductStatus.HIDDEN) {
        throw new IllegalStateException("Product is not hidden: " + status);
    }
    status = ProductStatus.ON_SALE;
}
```

Change `@Table` to include both indexes:

```java
@Table(name = "products", indexes = {
        @Index(name = "idx_products_store_status_id", columnList = "store_id, status, id"),
        @Index(name = "idx_products_store_status_price_id", columnList = "store_id, status, price, id")
})
```

Create V4 as a transactional conditional migration:

```sql
DO $$
BEGIN
    IF to_regclass('public.products') IS NOT NULL
       AND EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'store_id'
       ) THEN
        CREATE INDEX IF NOT EXISTS idx_products_store_status_price_id
            ON products (store_id, status, price, id);
    END IF;
END $$;
```

- [ ] **Step 4: Re-run focused tests and commit**

Run the Step 2 command; expected PASS. Commit only these files:

```powershell
git add backend/src/main/java/com/sweet/market/product/domain/Product.java backend/src/main/resources/db/migration/V4__add_storefront_price_index.sql backend/src/test/java/com/sweet/market/product/domain/ProductTest.java backend/src/test/java/com/sweet/market/store/migration
git commit -m "feat: add storefront visibility foundation"
```

### Task 2: Implement The Public Storefront Header

**Files:**
- Create: `backend/src/main/java/com/sweet/market/store/storefront/StorefrontResponse.java`
- Create: `backend/src/main/java/com/sweet/market/store/storefront/StorefrontQueryService.java`
- Create: `backend/src/main/java/com/sweet/market/store/storefront/StorefrontController.java`
- Modify: `backend/src/main/java/com/sweet/market/store/repository/StoreRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/store/api/StoreController.java`
- Modify: `backend/src/main/java/com/sweet/market/store/application/StoreQueryService.java`
- Delete: `backend/src/main/java/com/sweet/market/store/api/PublicStoreResponse.java`
- Create: `backend/src/test/java/com/sweet/market/store/StorefrontApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/store/StoreApiTest.java`

**Interfaces:**
- Produces: `StorefrontQueryService.findStorefront(long storeId): StorefrontResponse`.
- Produces: `GET /api/stores/{storeId}` with `operatingStatus`, one-decimal `averageRating`, `reviewCount`, and `publicProductCount`.
- Replaces: the M21 four-field `PublicStoreResponse` without changing the route.

- [ ] **Step 1: Write failing public-state and privacy tests**

Create `StorefrontApiTest` on `IntegrationTestSupport` with Korean-named tests that assert:

```java
mockMvc.perform(get("/api/stores/{storeId}", activeStoreId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.operatingStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data.averageRating").value(4.5))
        .andExpect(jsonPath("$.data.reviewCount").value(2))
        .andExpect(jsonPath("$.data.publicProductCount").value(3))
        .andExpect(jsonPath("$.data.legalBusinessName").doesNotExist())
        .andExpect(jsonPath("$.data.businessRegistrationId").doesNotExist())
        .andExpect(jsonPath("$.data.rejectionReason").doesNotExist())
        .andExpect(jsonPath("$.data.ownerMemberId").doesNotExist())
        .andExpect(jsonPath("$.data.memberships").doesNotExist());
```

Add separate tests for zero reviews (`averageRating: null`), suspended public identity with zeroed aggregates, and pending/rejected 404. Replace the old `비활성_사업자_상점은_공개_프로필로_조회할_수_없다()` expectation in `StoreApiTest` with the new suspended contract or remove it after equivalent coverage exists.

- [ ] **Step 2: Run the storefront test and verify RED**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.store.StorefrontApiTest' --tests 'com.sweet.market.store.StoreApiTest'
```

Expected: FAIL because the M21 response has no status/rating/count and suspended stores return 404.

- [ ] **Step 3: Add the exact header projection**

Define:

```java
public record StorefrontResponse(
        Long storeId,
        StoreType type,
        String publicName,
        String introduction,
        StoreStatus operatingStatus,
        Double averageRating,
        long reviewCount,
        long publicProductCount
) {
    public StorefrontResponse {
        if (averageRating != null) {
            averageRating = BigDecimal.valueOf(averageRating)
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();
        }
    }
}
```

Add one `StoreRepository` constructor projection. It accepts `ACTIVE` and `SUSPENDED`; for `SUSPENDED` it returns `null`, `0`, and `0`. For active stores it aggregates reviews by `review.product.store.id` and counts products whose status is `ON_SALE`, `RESERVED`, or `SOLD_OUT`.

- [ ] **Step 4: Move the public route to the storefront boundary**

Implement `StorefrontQueryService.findStorefront()` with `STORE_NOT_FOUND`, create `StorefrontController` for `GET /api/stores/{storeId}`, and remove the public method/dependency from `StoreController` and `StoreQueryService`. Delete `PublicStoreResponse` after its tests/imports move.

- [ ] **Step 5: Re-run and commit**

Run Step 2; expected PASS. Then:

```powershell
git add backend/src/main/java/com/sweet/market/store backend/src/test/java/com/sweet/market/store
git commit -m "feat: add public storefront header"
```

### Task 3: Implement Public Storefront Catalog Filtering And Sorting

**Files:**
- Create: `backend/src/main/java/com/sweet/market/store/storefront/StorefrontProductResponse.java`
- Create: `backend/src/main/java/com/sweet/market/store/storefront/StorefrontProductSort.java`
- Modify: `backend/src/main/java/com/sweet/market/store/storefront/{StorefrontController,StorefrontQueryService}.java`
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/sweet/market/store/StorefrontApiTest.java`

**Interfaces:**
- Produces: `StorefrontQueryService.findProducts(storeId, status, sort, page, size, viewerId): Page<StorefrontProductResponse>`.
- Produces: `GET /api/stores/{storeId}/products?status=ON_SALE&sort=NEWEST&page=0&size=12`.
- Produces: `StorefrontProductSort { NEWEST, PRICE_ASC, PRICE_DESC }`.

- [ ] **Step 1: Write failing catalog contract tests**

Add tests for default `ON_SALE`, explicit `RESERVED`/`SOLD_OUT`, `PRICE_ASC`, `PRICE_DESC`, equal-price ID tie-breaking, maximum size validation, suspended empty page, pending/rejected 404, seller compatibility consistency, representative image, wishlist flag, and cart flag. The central deterministic assertion is:

```java
mockMvc.perform(get("/api/stores/{storeId}/products", storeId)
                .queryParam("status", "ON_SALE")
                .queryParam("sort", "PRICE_ASC")
                .queryParam("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].price").value(10_000))
        .andExpect(jsonPath("$.data.content[0].id").value(higherEqualPriceId))
        .andExpect(jsonPath("$.data.content[1].id").value(lowerEqualPriceId));
```

- [ ] **Step 2: Verify RED including the security boundary**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.store.StorefrontApiTest'
```

Expected: FAIL/401 because the nested public path and catalog implementation do not exist.

- [ ] **Step 3: Implement the dedicated projection and repository query**

Define the response with exact fields:

```java
public record StorefrontProductResponse(
        Long id, Long storeId, String storeName, StoreType storeType,
        Long sellerId, String sellerNickname, String title, long price,
        ProductStatus status, String thumbnailUrl, long wishlistCount,
        boolean wishlisted, boolean carted
) {}
```

Add a `ProductRepository` constructor query filtered by store ID and one allowed public status. Copy the existing representative-image priority exactly, keep wishlist/cart correlated scalar subqueries in the content query, and use a separate count query. Map the enum to a `PageRequest` whose `Sort` is exactly `id DESC`, `price ASC/id DESC`, or `price DESC/id DESC`; reject public `HIDDEN` before repository access.

- [ ] **Step 4: Add controller validation and explicit public security matcher**

Use `@RequestParam(defaultValue = "ON_SALE")`, `@Min(0) page`, `@Min(1) @Max(40) size`, and optional authentication extraction. Add:

```java
.requestMatchers(HttpMethod.GET, "/api/stores/*", "/api/stores/*/products").permitAll()
```

Keep `/api/stores/me` before the public matcher.

- [ ] **Step 5: Re-run and commit**

Run Step 2; expected PASS. Then:

```powershell
git add backend/src/main/java/com/sweet/market/store/storefront backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java backend/src/test/java/com/sweet/market/store/StorefrontApiTest.java
git commit -m "feat: add paged storefront catalog"
```

### Task 4: Add The Operator Read Authorization Primitive And Catalog Reads

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/store/application/StoreAccessService.java`
- Modify: `backend/src/main/java/com/sweet/market/store/repository/StoreMembershipRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Create: `backend/src/main/java/com/sweet/market/store/operations/OperableStoreResponse.java`
- Create: `backend/src/main/java/com/sweet/market/store/operations/StoreCatalogSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/store/operations/StoreCatalogProductResponse.java`
- Create: `backend/src/main/java/com/sweet/market/store/operations/StoreCatalogSearchRequest.java`
- Create: `backend/src/main/java/com/sweet/market/store/operations/StoreCatalogSort.java`
- Create: `backend/src/main/java/com/sweet/market/store/operations/StoreCatalogQueryService.java`
- Create: `backend/src/main/java/com/sweet/market/store/operations/StoreOperationsController.java`
- Create: `backend/src/test/java/com/sweet/market/store/StoreOperationsApiTest.java`

**Interfaces:**
- Produces: `StoreAccessService.requireOperator(memberId, storeId): Store` for active memberships regardless of store status.
- Preserves: `requireCatalogOperator(memberId, storeId): Store`, additionally requiring `StoreStatus.ACTIVE`.
- Produces: operator list, summary, and paged catalog endpoints from the approved spec.

- [ ] **Step 1: Write failing OWNER/MANAGER/OUTSIDER and inactive-read tests**

Create active owner and manager memberships plus an outsider. Assert both roles see the store in `GET /api/store-operations`, both can read summary/catalog, outsiders get `STORE_ACCESS_DENIED`, and a suspended store remains readable with `catalogWritable: false`.

```java
mockMvc.perform(get("/api/store-operations/{storeId}/summary", storeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(managerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.onSaleCount").value(1))
        .andExpect(jsonPath("$.data.hiddenCount").value(1))
        .andExpect(jsonPath("$.data.catalogWritable").value(true));
```

Add catalog tests for all-status filtering, trimmed keyword, newest/oldest ID order, page metadata, representative image, default size 20, and size 101 validation.

- [ ] **Step 2: Run the focused API test and verify RED**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.store.StoreOperationsApiTest'
```

Expected: FAIL because `/api/store-operations` does not exist.

- [ ] **Step 3: Implement one-query operator resolution**

Annotate the existing active membership lookup with `@EntityGraph(attributePaths = "store")`. Implement:

```java
@Transactional(readOnly = true)
public Store requireOperator(Long memberId, Long storeId) {
    StoreMembership membership = storeMembershipRepository
            .findByStoreIdAndMemberIdAndActiveTrue(storeId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.STORE_ACCESS_DENIED));
    if (membership.getRole() != StoreMemberRole.OWNER
            && membership.getRole() != StoreMemberRole.MANAGER) {
        throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
    }
    return membership.getStore();
}

public Store requireCatalogOperator(Long memberId, Long storeId) {
    Store store = requireOperator(memberId, storeId);
    if (store.getStatus() != StoreStatus.ACTIVE) {
        throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
    }
    return store;
}
```

- [ ] **Step 4: Implement projection queries and controller validation**

Add one membership projection query ordered by personal/business then store ID, one product status aggregate query, and one paged operator product projection filtered by store/status/trimmed keyword. `StoreCatalogQueryService` calls `requireOperator()` once for summary and catalog endpoints. `StoreCatalogSearchRequest` accepts optional status/keyword, `NEWEST|OLDEST`, page default 0, size default 20/max 100.

- [ ] **Step 5: Re-run product authorization regression and commit**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.store.StoreOperationsApiTest' --tests 'com.sweet.market.product.ProductApiTest' --tests 'com.sweet.market.store.StoreApiTest'
```

Expected: PASS, including existing product create/update/hide authorization. Commit:

```powershell
git add backend/src/main/java/com/sweet/market/store/application/StoreAccessService.java backend/src/main/java/com/sweet/market/store/repository/StoreMembershipRepository.java backend/src/main/java/com/sweet/market/store/operations backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java backend/src/test/java/com/sweet/market/store/StoreOperationsApiTest.java
git commit -m "feat: add store operator catalog reads"
```

### Task 5: Add Atomic Hide And Show Commands

**Files:**
- Create: `backend/src/main/java/com/sweet/market/store/operations/StoreProductIdsRequest.java`
- Create: `backend/src/main/java/com/sweet/market/store/operations/StoreCatalogCommandService.java`
- Modify: `backend/src/main/java/com/sweet/market/store/operations/StoreOperationsController.java`
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Test: `backend/src/test/java/com/sweet/market/store/StoreOperationsApiTest.java`

**Interfaces:**
- Produces: `hide(memberId, storeId, productIds): void` and `show(memberId, storeId, productIds): void`.
- Produces: POST hide/show endpoints accepting `{ "productIds": [1, 2] }`.
- Guarantees: all-or-nothing validation and mutation for 1-50 unique positive IDs.

- [ ] **Step 1: Write failing individual, batch, and rollback tests**

Cover OWNER and MANAGER success, outsider and inactive-store denial, duplicate/empty/51 IDs, cross-store ID, missing ID, hide from `RESERVED`/`SOLD_OUT`/`HIDDEN`, show from non-`HIDDEN`, and rollback when one member of a valid-looking batch conflicts.

```java
mockMvc.perform(post("/api/store-operations/{storeId}/products/hide", storeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[%d,%d]}".formatted(firstId, reservedId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRODUCT_CHANGE_NOT_ALLOWED"));

assertThat(productRepository.findById(firstId).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.ON_SALE);
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.store.StoreOperationsApiTest'
```

Expected: FAIL with missing endpoints/service.

- [ ] **Step 3: Implement request validation and transactional commands**

Define the request:

```java
public record StoreProductIdsRequest(
        @NotEmpty @Size(max = 50) List<@NotNull @Positive Long> productIds
) {}
```

In the service, reject duplicates with `VALIDATION_ERROR`, authorize through `requireCatalogOperator`, load using `findAllByStoreIdAndIdIn(storeId, ids)`, compare loaded count, validate every source state before mutating any entity, then call `hide()` or `show()`. Map invalid source states to `PRODUCT_CHANGE_NOT_ALLOWED` and count mismatch to `PRODUCT_NOT_FOUND`.

- [ ] **Step 4: Add endpoints and verify GREEN**

Add POST `/products/hide` and `/products/show` under the operations controller. Return `ApiResponse<Void>` with a null data field using the project's existing success convention. Run Step 2; expected PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/store/operations backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java backend/src/test/java/com/sweet/market/store/StoreOperationsApiTest.java
git commit -m "feat: add atomic store catalog actions"
```

### Task 6: Add Owner-Managed Membership Listing And Manager Removal

**Files:**
- Create: `backend/src/main/java/com/sweet/market/store/operations/StoreMembershipResponse.java`
- Create: `backend/src/main/java/com/sweet/market/store/operations/StoreMembershipQueryService.java`
- Create: `backend/src/main/java/com/sweet/market/store/operations/StoreMembershipCommandService.java`
- Modify: `backend/src/main/java/com/sweet/market/store/operations/StoreOperationsController.java`
- Modify: `backend/src/main/java/com/sweet/market/store/repository/StoreMembershipRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Test: `backend/src/test/java/com/sweet/market/store/StoreOperationsApiTest.java`

**Interfaces:**
- Produces: `findActive(memberId, storeId): List<StoreMembershipResponse>`.
- Produces: `removeManager(memberId, storeId, membershipId): void`.
- Produces: `STORE_OWNER_MEMBERSHIP_PROTECTED` HTTP 409.

- [ ] **Step 1: Write failing membership tests**

Assert owner sees active owner/manager rows with `membershipId`, `memberId`, nickname, role, and `joinedAt`; inactive rows are absent; manager/outsider get `STORE_OWNER_REQUIRED`; owner can remove manager; removed manager disappears from `/api/store-operations` and receives `STORE_ACCESS_DENIED`; owner removal returns the new conflict code.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.store.StoreOperationsApiTest'
```

- [ ] **Step 3: Implement owner-only query and command**

Add repository methods that project active memberships ordered by `OWNER` before `MANAGER`, then membership ID, and load an active target by store/membership ID. The command sequence is:

```java
storeAccessService.requireOwner(memberId, storeId);
StoreMembership target = repository.findByIdAndStoreIdAndActiveTrue(membershipId, storeId)
        .orElseThrow(() -> new BusinessException(ErrorCode.STORE_ACCESS_DENIED));
if (target.getRole() == StoreMemberRole.OWNER) {
    throw new BusinessException(ErrorCode.STORE_OWNER_MEMBERSHIP_PROTECTED);
}
target.deactivate();
```

Add `STORE_OWNER_MEMBERSHIP_PROTECTED(HttpStatus.CONFLICT, "상점 소유자 멤버십은 제거할 수 없습니다.")`.

- [ ] **Step 4: Add GET/DELETE routes, re-run, and commit**

Run Step 2; expected PASS. Then:

```powershell
git add backend/src/main/java/com/sweet/market/store/operations backend/src/main/java/com/sweet/market/store/repository/StoreMembershipRepository.java backend/src/main/java/com/sweet/market/common/error/ErrorCode.java backend/src/test/java/com/sweet/market/store/StoreOperationsApiTest.java
git commit -m "feat: add store manager removal"
```

### Task 7: Lock Query Budgets And Complete Backend Regression

**Files:**
- Create: `backend/src/test/java/com/sweet/market/jpalab/StorefrontQueryOptimizationTest.java`
- Modify only if a failure proves necessary: M22 storefront/operations repositories and services.

**Interfaces:**
- Verifies: storefront header + first product page uses at most 3 statements.
- Verifies: operable stores + summary + first operator product page uses at most 6 statements.
- Verifies: no collection fetch and no per-row membership/store/image/review query.

- [ ] **Step 1: Write failing statistics tests with a full page fixture**

Extend `QueryOptimizationTestSupport` and create at least 20 products with representative/fallback images, reviews, wishlist/cart rows, and one active operator membership. After `flushAndClear()` and `resetStatistics()` call the same service methods used by the endpoints:

```java
storefrontQueryService.findStorefront(storeId);
storefrontQueryService.findProducts(
        storeId, ProductStatus.ON_SALE, StorefrontProductSort.NEWEST, 0, 12, viewerId);
assertThat(queryCount()).isLessThanOrEqualTo(3L);
assertThat(collectionFetchCount()).isZero();
```

Reset statistics, then call operable list, summary, and catalog and assert `queryCount() <= 6`.

- [ ] **Step 2: Run optimization tests and verify the measured result**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.jpalab.StorefrontQueryOptimizationTest' --rerun-tasks
```

Expected: PASS at the approved budgets. If RED, inspect Hibernate SQL and change only the query causing the excess; do not relax the thresholds.

- [ ] **Step 3: Run the focused backend M22 suite**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.store.StorefrontApiTest' --tests 'com.sweet.market.store.StoreOperationsApiTest' --tests 'com.sweet.market.store.StoreApiTest' --tests 'com.sweet.market.product.ProductApiTest' --tests 'com.sweet.market.jpalab.StorefrontQueryOptimizationTest' --tests 'com.sweet.market.store.migration.*'
```

Expected: PASS with no skipped test.

- [ ] **Step 4: Commit the performance gate**

```powershell
git add backend/src/test/java/com/sweet/market/jpalab/StorefrontQueryOptimizationTest.java backend/src/main/java/com/sweet/market/store backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java
git commit -m "test: lock store page query budgets"
```

### Task 8: Add Web Contracts And A Reusable Buyer Product Card

**Files:**
- Modify: `web/src/features/stores/storeApi.ts`
- Create: `web/src/features/stores/storeOperationsApi.ts`
- Create: `web/src/features/products/ProductCard.tsx`
- Modify: `web/src/pages/HomePage.tsx`
- Modify: `web/src/features/auth/AuthProvider.tsx`

**Interfaces:**
- Produces: public store/header/catalog types and functions.
- Produces: `storeOperationQueryKeys`, operator read functions, hide/show commands, membership removal.
- Produces: `<ProductCard product={product} />` shared by home and storefront.

- [ ] **Step 1: Define exact API-shaped TypeScript contracts**

Extend `PublicStore` with:

```ts
operatingStatus: 'ACTIVE' | 'SUSPENDED';
averageRating: number | null;
reviewCount: number;
publicProductCount: number;
```

Add `StorefrontProductSort`, `StorefrontProductSearchInput`, and `getStorefrontProducts`. Create operations types for `OperableStore`, `StoreCatalogSummary`, `StoreCatalogProduct`, `StoreCatalogSearchInput`, `StoreMembership`, and all approved request functions.

- [ ] **Step 2: Implement stable hierarchical query keys**

Use keys rooted at `['stores']` for public data and `['store-operations']` for private data. Include store ID, status, keyword, sort, page, and size in list keys. Add `['store-operations']` to `authenticatedPrivateQueryKeys` so logout/login cannot display a prior operator's catalog.

- [ ] **Step 3: Extract the buyer card without changing behavior**

Move `ProductThumb`, store link, badges, wishlist, cart, and currency formatting from `HomePage` into `ProductCard.tsx`. Keep the prop compatible with `ProductSummary` and `StorefrontProduct` through a shared structural `BuyerProductCard` type. `HomePage` maps to `<ProductCard>` and retains its existing empty/loading/error behavior.

- [ ] **Step 4: Build and commit**

```powershell
cd web
npm run build
```

Expected: PASS. Commit:

```powershell
git add web/src/features/stores web/src/features/products/ProductCard.tsx web/src/pages/HomePage.tsx web/src/features/auth/AuthProvider.tsx
git commit -m "feat: add store operations web contracts"
```

### Task 9: Build The Buyer Storefront Page

**Files:**
- Modify: `web/src/pages/StoreProfilePage.tsx`
- Modify: `web/src/shared/styles.css`
- Modify: `web/src/shared/ui/ResourceStates.tsx`

**Interfaces:**
- Consumes: public header and product API/query keys from Task 8.
- Produces: active storefront filters/sorts/pagination and suspended direct state at `/stores/:storeId`.

- [ ] **Step 1: Replace the minimal profile with URL-backed catalog state**

Use `useSearchParams()` for `status`, `sort`, and `page`. Normalize invalid client values to `ON_SALE`, `NEWEST`, and `0`; the server remains authoritative. Reset page to zero when status or sort changes. Query the header and catalog separately so their loading/error/empty states remain independent.

- [ ] **Step 2: Render the approved active and suspended structures**

For active stores render identity, type badge, introduction, one-decimal rating or `아직 리뷰 없음`, review/product counts, three status filters, three sort options, 12-card grid, and previous/next/page controls. For suspended stores render only public identity and `현재 운영이 중지된 상점입니다`; do not invoke/render the catalog query when suspended.

- [ ] **Step 3: Add responsive and accessible styles**

Add scoped storefront classes, visible labels for controls, `aria-pressed` on status choices, an `aria-label` on the catalog grid, and mobile rules that retain price/status/store identity. Reuse `ProductCard`; do not fork card markup.

- [ ] **Step 4: Build, manually inspect source states, and commit**

```powershell
npm run build
```

Expected: PASS. Verify invalid store ID, header error, catalog error, empty status, suspended state, and a two-page result by source-level state tracing. Commit:

```powershell
git add web/src/pages/StoreProfilePage.tsx web/src/shared/styles.css web/src/shared/ui/ResourceStates.tsx
git commit -m "feat: build buyer storefront"
```

### Task 10: Build The Store Catalog Operations Workspace

**Files:**
- Create: `web/src/features/stores/StoreCatalogPanel.tsx`
- Create: `web/src/features/stores/StoreProfilePanel.tsx`
- Create: `web/src/features/stores/StoreMembershipPanel.tsx`
- Modify: `web/src/pages/MyStorePage.tsx`
- Modify: `web/src/pages/ProductFormPage.tsx`
- Modify: `web/src/shared/styles.css`

**Interfaces:**
- Consumes: all Task 8 operation APIs.
- Produces: OWNER/MANAGER store selector, summary strip, catalog/profile/operator tabs, atomic individual/batch actions.
- Produces: manager-compatible product create/edit store access.

- [ ] **Step 1: Turn `MyStorePage` into a small workspace orchestrator**

Query operable stores, keep selected store by ID with safe fallback, show role/status/public preview, fetch the selected summary, and render `catalog`, `profile`, and `memberships` tabs. OWNER sees all tabs; MANAGER sees only catalog. If the selected store changes while an owner-only tab is active, reset to catalog.

Below the workspace header, render direct OWNER links to `/me/sales`, `/me/sales/refunds`, `/me/settlements`, and `/me/reports`. Keep these as navigation only; do not duplicate their queries or commands in the console. Hide the seller-identity-based links for MANAGER because those existing routes still use the historical member seller boundary rather than store-operator authorization.

- [ ] **Step 2: Move existing owner profile behavior unchanged**

Extract the current profile form and lifecycle guidance into `StoreProfilePanel`. It continues to consume owner-private `/api/stores/me` data and existing profile/application APIs. Match by selected store ID; never pass private store data into manager state.

- [ ] **Step 3: Implement the catalog panel state and commands**

`StoreCatalogPanel` owns status, trimmed keyword, sort, page, selection, and confirm dialogs. Disable all command controls when `catalogWritable` is false. Hide accepts only selected `ON_SALE` IDs; show accepts only selected `HIDDEN` IDs. Mixed selections show no batch mutation button. After success invalidate the selected summary, all selected-store operator product keys, and all selected-store public product keys, then clear selection.

For rows:

- `ON_SALE`: edit and hide.
- `HIDDEN`: show; no edit link because the existing public product-detail loader excludes hidden products.
- `RESERVED`: read-only status.
- `SOLD_OUT`: edit link only, preserving existing product-update behavior.

- [ ] **Step 4: Implement owner membership management**

`StoreMembershipPanel` queries only when the OWNER tab is active, lists nickname/role/joined date, protects the OWNER row, and asks for confirmation before deleting a MANAGER membership. On success invalidate membership and operable-store keys.

- [ ] **Step 5: Make product forms use operable stores**

Replace `getMyStores()`/`ownedStoreIds` in `ProductFormPage` with `getOperableStores()`/`operableStoreIds`; keep only active stores selectable for create. Accept `?storeId=` as the console create default when it identifies an active operable store. Keep edit store identity read-only and preserve the existing server authorization. Update the denial copy from `소유한 상점` to `운영 권한이 있는 상점`.

- [ ] **Step 6: Add dense desktop and scan-friendly mobile styles**

Create scoped summary-strip, tab, toolbar, selectable row, batch-action, and membership-list styles. Desktop uses a header-aligned table grid. Under the existing mobile breakpoint, hide the decorative table header and make each row a vertical card while retaining checkbox, status, price, and primary action.

- [ ] **Step 7: Build and commit**

```powershell
npm run build
```

Expected: PASS with no TypeScript errors. Commit:

```powershell
git add web/src/features/stores web/src/pages/MyStorePage.tsx web/src/pages/ProductFormPage.tsx web/src/shared/styles.css
git commit -m "feat: build store operations console"
```

### Task 11: Run Full Verification And Manual HTTP/Browser Gates

**Files:**
- Modify only files proven necessary by failures.
- Create: `docs/milestone-22-storefront-implementation-notes.md` if the hybrid index behavior needs durable explanation.
- Create: `docs/superpowers/handoffs/2026-07-11-milestone-22-storefront-and-core-store-operations-console-handoff.md` after all checks pass.

**Interfaces:**
- Produces: executable evidence for backend, web, migration, HTTP authorization, and responsive operator flow.
- Produces: M23 prerequisites without changing historical seller rules.

- [ ] **Step 1: Run focused backend verification from a clean task execution**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.store.StorefrontApiTest' --tests 'com.sweet.market.store.StoreOperationsApiTest' --tests 'com.sweet.market.jpalab.StorefrontQueryOptimizationTest' --tests 'com.sweet.market.store.migration.*' --rerun-tasks
```

Expected: BUILD SUCCESSFUL, zero failed/skipped M22 tests.

- [ ] **Step 2: Run the full backend suite**

```powershell
.\gradlew.bat test --rerun-tasks
```

Expected: BUILD SUCCESSFUL. Record test and suite counts from JUnit XML for the handoff.

- [ ] **Step 3: Run web and diff gates**

```powershell
cd ..\web
npm run build
cd ..
git diff --check
```

Expected: TypeScript checks and Vite build PASS; no whitespace errors.

- [ ] **Step 4: Execute an isolated PostgreSQL HTTP flow**

Start a uniquely named disposable PostgreSQL 17 container and backend on a non-default port. Through HTTP, create owner, manager fixture member, outsider, and buyer; insert only the manager membership in the disposable database because M22 intentionally has no manager-add endpoint. Verify:

1. active public header/rating/count and default/filtered/sorted catalog;
2. suspended header with no catalog exposure;
3. owner and manager operator lists/summary/catalog;
4. manager hide/show success and outsider denial;
5. mixed invalid batch rollback;
6. owner membership list and manager removal;
7. removed manager denial;
8. hidden or inactive-store product order returns `PRODUCT_NOT_ON_SALE`.

Stop and remove only the disposable application/database resources.

- [ ] **Step 5: Execute the manual browser walkthrough**

Run `npm run dev` against the disposable backend and inspect desktop and narrow mobile widths. Cover anonymous active/suspended storefront, pagination/filter/sort, owner store switching, all tabs, individual/batch actions, manager catalog-only view, inactive read-only state, and product form preselection. Confirm no private legal/review/membership data appears on buyer pages and no row overflows horizontally on mobile.

- [ ] **Step 6: Write implementation notes and final handoff**

Document the V4 conditional migration plus matching JPA index, exact test/build outputs, query budgets, HTTP flow, manual browser evidence, compatibility fields, known limitations, and M23 prerequisites. Do not claim browser evidence unless Step 5 actually ran.

- [ ] **Step 7: Commit verification documentation**

```powershell
git add docs/milestone-22-storefront-implementation-notes.md docs/superpowers/handoffs/2026-07-11-milestone-22-storefront-and-core-store-operations-console-handoff.md
git commit -m "docs: hand off milestone 22 storefront console"
```

If no standalone implementation note was necessary, omit that path from `git add` rather than creating an empty document.
