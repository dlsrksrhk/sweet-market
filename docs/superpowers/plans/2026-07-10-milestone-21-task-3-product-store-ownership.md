# Milestone 21 Task 3 Product Store Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 상품의 유일한 상업적 소유자를 스토어로 전환하고 주문의 판매자 회원 이력을 보존한다.

**Architecture:** 기존 DB에서는 Flyway가 V1에서 백필한 `products.store_id`를 검증한 뒤 V3가 legacy `products.seller_id`를 제거한다. 신규 DB에서는 Hibernate `update`가 `Product.store`와 `Order.seller` 매핑을 생성한다. 명령은 `StoreAccessService`로 인가하고, 상품은 현재 스토어를, 주문 이후 판매자 흐름은 `Order.seller`를 기준으로 조회한다.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Hibernate, Flyway, PostgreSQL/Testcontainers, JUnit 5, MockMvc.

## Global Constraints

- 구현 전용 worktree는 실행 시 `superpowers:using-git-worktrees` 지침으로 만든다.
- JUnit `@Test` 메서드명은 한국어_밑줄_형식으로 작성한다.
- 상품 생성 요청에는 active store id를 명시하고, 상품은 생성 후 스토어를 바꾸지 않는다.
- owner와 manager는 활성 스토어에서 카탈로그 명령을 수행할 수 있다.
- pending, rejected, suspended 사업자 스토어 상품은 일반 목록에서 제외하고 구매 명령은 거절한다.
- 주문 판매자 이력은 `Order.seller` 회원 관계만 보존하고 닉네임은 스냅샷하지 않는다.
- 웹의 스토어 선택 UI는 이 계획 범위 밖이며 M21 Task 5에서 구현한다.
- 백엔드 Gradle 실행은 JDK 21과 `JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`을 사용한다.

---

## Target File Map

- Create `backend/src/main/resources/db/migration/V3__complete_product_store_ownership.sql` — legacy product seller FK/column을 검증 후 제거한다.
- Modify `backend/src/main/java/com/sweet/market/product/domain/Product.java` — 필수 `Store` 소유권과 구매 가능 상태를 제공한다.
- Modify `backend/src/main/java/com/sweet/market/order/domain/Order.java` — 생성 당시 store owner를 `seller`로 기록한다.
- Modify `backend/src/main/java/com/sweet/market/product/{application,query,repository,api}/**` — store 인가, 공개 조회, DTO를 전환한다.
- Modify `backend/src/main/java/com/sweet/market/{cart,wishlist,order,refund,settlement,review,seller}/**` — `Product.seller`를 store 또는 order seller 기준으로 전환한다.
- Modify `backend/src/test/java/com/sweet/market/store/migration/{StoreMigrationTest,StoreFreshDatabaseStartupTest}.java` — legacy/fresh PostgreSQL 결과를 검증한다.
- Modify `backend/src/test/java/com/sweet/market/{product,order,cart,wishlist,refund,settlement,seller}/**` — 스토어 fixture, 인가, 구매 가능성, 이력 회귀를 검증한다.

### Task 1: Lock Down The Legacy And Fresh Database Contract

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__complete_product_store_ownership.sql`
- Modify: `backend/src/main/java/com/sweet/market/product/domain/Product.java`
- Modify: `backend/src/main/java/com/sweet/market/order/domain/Order.java`
- Test: `backend/src/test/java/com/sweet/market/store/migration/StoreMigrationTest.java`
- Test: `backend/src/test/java/com/sweet/market/store/migration/StoreFreshDatabaseStartupTest.java`

**Interfaces:**
- Produces: `Product.getStore(): Store`, `Product.create(Store, String, String, long): Product`, `Order.getSeller(): Member`.
- Produces: database `products.store_id` FK/index and `orders.seller_id` FK/index on both legacy and fresh PostgreSQL schemas.

- [ ] **Step 1: Add failing legacy migration assertions**

Extend `기존_판매자별_상점으로_상품_주문을_이관하고_사업자_상점은_소유자당_하나만_생성된다` so its legacy `products` fixture includes `status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE'` and it asserts the completed state, not the intermediate V1 state:

```java
assertThat(queryLong(connection, "SELECT COUNT(*) FROM products WHERE store_id IS NULL")).isZero();
assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns "
        + "WHERE table_name = 'products' AND column_name = 'seller_id'")).isZero();
assertThat(queryLong(connection, "SELECT COUNT(*) FROM pg_indexes "
        + "WHERE tablename = 'products' AND indexname = 'idx_products_store_status_id'")).isEqualTo(1);
```

- [ ] **Step 2: Run the migration test to verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.store.migration.StoreMigrationTest'
```

Expected: FAIL because Flyway version 2 leaves `products.seller_id` in the legacy schema.

- [ ] **Step 3: Write V3 as a guarded, fail-fast migration**

Create a migration that only acts when the legacy `products` table exists. Before dropping `seller_id`, reject null or orphaned `store_id` values, add the store/status/id index, then drop the legacy column. Use explicit catalog checks so a fresh database, where Hibernate creates `products` after Flyway, is not affected.

```sql
DO $$
BEGIN
    IF to_regclass('public.products') IS NULL THEN
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM products WHERE store_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot complete product store ownership: products.store_id contains NULL values';
    END IF;
    IF EXISTS (
        SELECT 1 FROM products p
        LEFT JOIN stores s ON s.id = p.store_id
        WHERE s.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot complete product store ownership: products.store_id contains orphaned values';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_products_store_status_id ON products (store_id, status, id);
ALTER TABLE products DROP COLUMN IF EXISTS seller_id;
```

Keep `orders.seller_id` intact; it is the historical seller relation, not legacy product ownership.

- [ ] **Step 4: Convert the two JPA relations**

Replace the `Product.seller` mapping with the following required store mapping and make `Order.seller` a required relation with its index:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "store_id", nullable = false)
private Store store;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "seller_id", nullable = false)
private Member seller;
```

Set the table indexes with `@Table(indexes = @Index(name = "idx_products_store_status_id", columnList = "store_id, status, id"))` on `Product` and `@Index(name = "idx_orders_seller_id", columnList = "seller_id")` on `Order`. Change `Product.create` to accept a `Store`; remove `isOwnedBy` rather than retaining a mutable seller ownership API. In `Order.create`, assign `product.getStore().getOwnerMember()` to `seller` before reserving the product.

- [ ] **Step 5: Add failing fresh-schema assertions, then make them pass**

Update `빈_PostgreSQL에서도_Flyway와_JPA_업데이트로_애플리케이션이_시작된다` to expect Flyway version `3` and assert the four final constraints through `information_schema` and `pg_indexes`:

```java
assertThat(columnExists("products", "store_id")).isTrue();
assertThat(columnExists("orders", "seller_id")).isTrue();
assertThat(foreignKeyExists("products", "store_id", "stores")).isTrue();
assertThat(foreignKeyExists("orders", "seller_id", "members")).isTrue();
assertThat(indexExists("idx_products_store_status_id")).isTrue();
assertThat(indexExists("idx_orders_seller_id")).isTrue();
```

Run the two migration tests again. Expected: PASS.

- [ ] **Step 6: Commit the schema boundary**

```powershell
git add backend/src/main/resources/db/migration/V3__complete_product_store_ownership.sql backend/src/main/java/com/sweet/market/product/domain/Product.java backend/src/main/java/com/sweet/market/order/domain/Order.java backend/src/test/java/com/sweet/market/store/migration/StoreMigrationTest.java backend/src/test/java/com/sweet/market/store/migration/StoreFreshDatabaseStartupTest.java
git commit -m "feat: complete product store ownership schema"
```

### Task 2: Move Product Commands To Store Membership Authorization

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductCreateRequest.java`
- Modify: `backend/src/main/java/com/sweet/market/product/application/ProductService.java`
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/{ProductResponse,ProductSummaryResponse}.java`
- Test: `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/product/ProductSellerApiTest.java`

**Interfaces:**
- Consumes: `StoreAccessService.requireCatalogOperator(Long memberId, Long storeId): Store`.
- Produces: `ProductCreateRequest(Long storeId, String title, String description, long price, List<ProductCreateImageRequest> images)`.
- Produces: `ProductRepository.findWithStoreAndImagesById(Long): Optional<Product>`.

- [ ] **Step 1: Add command tests that expose seller-id ownership assumptions**

Add API tests using signup-created personal stores. Obtain the personal store id from `GET /api/stores/me`, include it in creation JSON, and cover:

```java
void 상점_운영자는_선택한_활성_상점에_상품을_등록한다() throws Exception
void 다른_상점_운영자는_상품을_수정할_수_없다() throws Exception
void 비활성_사업자_상점에는_상품을_등록할_수_없다() throws Exception
void 상품_수정은_기존_상점의_운영_권한을_검사한다() throws Exception
```

Expect `STORE_ACCESS_DENIED` for an outsider/inactive store and retain `PRODUCT_ACCESS_DENIED` for another member's temporary upload ownership failure.

- [ ] **Step 2: Run focused product tests to verify the new request contract fails**

Run:

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.product.ProductApiTest' --tests 'com.sweet.market.product.ProductSellerApiTest'
```

Expected: FAIL because `storeId` does not yet exist and command authorization still compares `Product.seller`.

- [ ] **Step 3: Implement create/update/hide authorization**

Add the required field to the request:

```java
public record ProductCreateRequest(
        @NotNull Long storeId,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 2000) String description,
        @Positive long price,
        @NotNull @Size(max = 10) List<@Valid ProductCreateImageRequest> images
) {}
```

Inject `StoreAccessService` into `ProductService`. In `create`, call `requireCatalogOperator(sellerId, request.storeId())`, pass its result to `Product.create`, and keep image upload ownership checks bound to the authenticated member id. In update/hide, load `findWithStoreAndImagesById`, then call `requireCatalogOperator(memberId, product.getStore().getId())`. Rename seller-oriented repository methods and entity graphs to `store`/`store.ownerMember` names.

- [ ] **Step 4: Extend product response constructors without breaking legacy fields**

Add `storeId`, `storeName`, and `storeType` immediately after the legacy seller fields. Map seller values from `product.getStore().getOwnerMember()` and store values from `product.getStore()`:

```java
product.getStore().getId(),
product.getStore().getPublicName(),
product.getStore().getType().name()
```

Apply the same field order and mapping to `ProductSummaryResponse` and update JPQL constructor projections to join `p.store store` and `store.ownerMember owner`.

- [ ] **Step 5: Run focused product tests to verify they pass**

Run the Step 2 command. Expected: PASS, including the four Korean-named store ownership tests.

- [ ] **Step 6: Commit product command conversion**

```powershell
git add backend/src/main/java/com/sweet/market/product backend/src/test/java/com/sweet/market/product
git commit -m "feat: authorize products through stores"
```

### Task 3: Make Public Product Reads Store-Aware And Purchase-Safe

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/product/domain/Product.java`
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/cart/{application/CartService.java,repository/CartItemRepository.java}`
- Modify: `backend/src/main/java/com/sweet/market/wishlist/{application/WishlistService.java,repository/WishlistItemRepository.java}`
- Modify: `backend/src/main/java/com/sweet/market/order/application/OrderService.java`
- Test: `backend/src/test/java/com/sweet/market/{product/ProductApiTest,cart/CartApiTest,wishlist/WishlistApiTest,order/OrderApiTest}.java`

**Interfaces:**
- Produces: `Product.isPurchasable(): boolean`, true only for `ON_SALE` products in an `ACTIVE` store.
- Produces: public product response `purchasable: boolean` for direct reads.

- [ ] **Step 1: Add availability regression tests**

Add tests with an approved business store that is later suspended:

```java
void 비활성_사업자_상점_상품은_공개_목록에서_제외된다() throws Exception
void 비활성_사업자_상점_상품의_직접_조회는_구매_불가를_반환한다() throws Exception
void 비활성_사업자_상점_상품은_장바구니에_담을_수_없다() throws Exception
void 비활성_사업자_상점_상품은_주문할_수_없다() throws Exception
```

The direct response must contain `"purchasable": false`; list responses must contain no suspended-business product id.

- [ ] **Step 2: Run the four focused tests to verify current behavior fails**

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.product.ProductApiTest' --tests 'com.sweet.market.cart.CartApiTest' --tests 'com.sweet.market.wishlist.WishlistApiTest' --tests 'com.sweet.market.order.OrderApiTest'
```

Expected: FAIL because public queries and cart/order checks inspect only product status.

- [ ] **Step 3: Implement one availability predicate and reuse it**

Implement the domain predicate:

```java
public boolean isPurchasable() {
    return status == ProductStatus.ON_SALE && store.getStatus() == StoreStatus.ACTIVE;
}
```

Use it in `CartService.validateCartable`, `CartService.isCheckoutNotAllowed`, `WishlistService`, and `OrderService` before creating an order. Load the store association in each command query. Map `purchasable` in `ProductResponse` and retain the existing `PRODUCT_NOT_ON_SALE`, `CART_PRODUCT_NOT_ON_SALE`, and `WISHLIST_PRODUCT_NOT_ON_SALE` contracts for rejection.

- [ ] **Step 4: Filter public queries without leaking private store data**

Change public JPQL to join `p.store store` and require:

```sql
and (store.type = com.sweet.market.store.domain.StoreType.PERSONAL
     or store.status = com.sweet.market.store.domain.StoreStatus.ACTIVE)
```

Keep direct lookup broad enough to return an existing non-hidden product from an inactive business store, but map it as not purchasable. Replace all `product.seller` entity graphs in cart/wishlist/order commands with `product.store` and `product.store.ownerMember`.

- [ ] **Step 5: Run the Step 2 suite to verify it passes**

Expected: PASS; list filtering, direct-read state, cart, checkout, and direct order all agree on availability.

- [ ] **Step 6: Commit public availability behavior**

```powershell
git add backend/src/main/java/com/sweet/market/product backend/src/main/java/com/sweet/market/cart backend/src/main/java/com/sweet/market/wishlist backend/src/main/java/com/sweet/market/order backend/src/test/java/com/sweet/market/product backend/src/test/java/com/sweet/market/cart backend/src/test/java/com/sweet/market/wishlist backend/src/test/java/com/sweet/market/order
git commit -m "feat: enforce store product availability"
```

### Task 4: Preserve Historical Seller Identity On Orders And Downstream Aggregates

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/order/{application/OrderService.java,repository/OrderRepository.java,api/{OrderResponse,OrderSummaryResponse}.java,admin/{AdminOrderDetailResponse,AdminOrderQueryService}.java}`
- Modify: `backend/src/main/java/com/sweet/market/{settlement/domain/Settlement.java,settlement/application/SettlementService.java,refund/domain/RefundRequest.java,refund/repository/RefundRequestRepository.java,refund/api/RefundRequestResponse.java,review/domain/Review.java}`
- Modify: `backend/src/main/java/com/sweet/market/seller/report/SellerReportQueryService.java`
- Test: `backend/src/test/java/com/sweet/market/{order/OrderApiTest,order/OrderQueryApiTest,refund/RefundRequestApiTest,settlement/SettlementApiTest,seller/report/SellerReportApiTest}.java`

**Interfaces:**
- Consumes: `Order.getSeller(): Member` populated by `Order.create`.
- Produces: order/refund/admin order seller fields from `Order.seller`, settlement and review seller snapshots from `Order.seller`.

- [ ] **Step 1: Add historical seller tests**

Add tests proving that a completed order keeps the seller member identity after store-based product conversion and that seller-side settlement/refund authorization uses the order snapshot:

```java
void 주문은_생성_시점의_상점_소유자를_판매자로_보존한다() throws Exception
void 판매자_환불_권한은_주문_판매자_스냅샷을_사용한다() throws Exception
void 정산은_주문_판매자_스냅샷을_사용한다() throws Exception
void 판매_리포트는_주문_판매자_스냅샷을_집계한다() throws Exception
```

- [ ] **Step 2: Run downstream tests to verify `Product.seller` references fail**

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.order.*' --tests 'com.sweet.market.refund.*' --tests 'com.sweet.market.settlement.*' --tests 'com.sweet.market.seller.report.*'
```

Expected: compilation or assertion failures where queries still traverse `order.product.seller`.

- [ ] **Step 3: Convert snapshot creation and all historical reads**

Use these replacements consistently:

```java
// Order.create
return new Order(buyer, product, product.getStore().getOwnerMember(), OrderStatus.CREATED, LocalDateTime.now());

// Settlement.create
order.getSeller()

// RefundRequest.isSellerOwnedBy
return order.getSeller().getId().equals(sellerId);

// Review constructor
this.seller = order.getSeller();
```

Update `OrderRepository` entity graphs to include `seller` rather than `product.seller`; change seller report and administrator order JPQL filters from `p.seller.id` to `o.seller.id`. Map `OrderResponse`, `OrderSummaryResponse`, `AdminOrderDetailResponse`, and `RefundRequestResponse` seller fields from `order.getSeller()`.

- [ ] **Step 4: Run downstream tests to verify they pass**

Run the Step 2 command. Expected: PASS, including seller snapshot assertions.

- [ ] **Step 5: Commit historical seller conversion**

```powershell
git add backend/src/main/java/com/sweet/market/order backend/src/main/java/com/sweet/market/settlement backend/src/main/java/com/sweet/market/refund backend/src/main/java/com/sweet/market/review backend/src/main/java/com/sweet/market/seller backend/src/test/java/com/sweet/market/order backend/src/test/java/com/sweet/market/refund backend/src/test/java/com/sweet/market/settlement backend/src/test/java/com/sweet/market/seller
git commit -m "feat: preserve order seller history"
```

### Task 5: Finish Remaining Product-Seller Reference Removal And Query Verification

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/product/{repository/ProductRepository.java,admin/{AdminProductQueryService,AdminProductDetailResponse,AdminProductSummaryResponse}.java}`
- Modify: `backend/src/main/java/com/sweet/market/{cart/repository/CartItemRepository.java,wishlist/repository/WishlistItemRepository.java,payment/repository/PaymentRepository.java,delivery/repository/DeliveryRepository.java,settlement/repository/SettlementRepository.java}`
- Modify: `backend/src/test/java/com/sweet/market/jpalab/{ProductQueryOptimizationTest,OrderQueryOptimizationTest,SettlementQueryOptimizationTest}.java`
- Test: `backend/src/test/java/com/sweet/market/product/{admin/AdminProductDetailResponseTest,admin/AdminProductOperationsApiTest,api/ProductSummaryResponseTest}.java`

**Interfaces:**
- Produces: no main-source reference to `Product.getSeller()`, `p.seller`, `product.seller`, or `findWithSeller`.
- Produces: bounded product/order query paths that join only store and immutable seller relations needed by the response.

- [ ] **Step 1: Add response and query-count regression tests**

Add Korean-named tests that assert product summary/detail/admin responses expose matching store and seller identities. Adapt the JPA lab tests so a page accesses `product.getStore().getPublicName()` and `product.getStore().getOwnerMember().getNickname()` without additional per-row selects.

```java
void 상품_응답은_상점과_판매자_식별자를_일관되게_반환한다()
void 상품_목록은_상점_정보를_N플러스일_없이_조회한다()
```

- [ ] **Step 2: Run product and JPA query tests to verify references fail**

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.product.*' --tests 'com.sweet.market.jpalab.ProductQueryOptimizationTest' --tests 'com.sweet.market.jpalab.OrderQueryOptimizationTest' --tests 'com.sweet.market.jpalab.SettlementQueryOptimizationTest'
```

Expected: FAIL until all projections, entity graphs, and test fixtures use store ownership.

- [ ] **Step 3: Complete the reference audit**

Replace each remaining product seller traversal with exactly one of these forms:

```java
product.getStore().getOwnerMember() // current product seller display
order.getSeller()                   // historical seller display, authority, or report
join p.store store                  // product query projection/filter
join o.seller seller                // order-history query projection/filter
```

For administrator product filters that keep a `sellerId` request parameter, join `p.store.ownerMember owner` and filter `owner.id`. Rename repository methods and `@EntityGraph` paths to make their store-based load shape explicit. Do not fetch `store.memberships` in any paginated query.

- [ ] **Step 4: Run the Step 2 suite to verify it passes**

Expected: PASS and query-count assertions remain bounded.

- [ ] **Step 5: Commit query and compatibility cleanup**

```powershell
git add backend/src/main/java/com/sweet/market/product backend/src/main/java/com/sweet/market/cart backend/src/main/java/com/sweet/market/wishlist backend/src/main/java/com/sweet/market/payment backend/src/main/java/com/sweet/market/delivery backend/src/main/java/com/sweet/market/settlement backend/src/test/java/com/sweet/market/product backend/src/test/java/com/sweet/market/jpalab
git commit -m "refactor: remove product seller references"
```

### Task 6: Run The Milestone Regression Gate And Record The Handoff

**Files:**
- Modify: `docs/superpowers/handoffs/2026-07-10-milestone-21-store-foundation-task-3-handoff.md`
- Test: all backend tests and the web production build.

**Interfaces:**
- Produces: a verified Task 3 handoff listing the final migration version, API contract changes, compatibility rules, and commands run.

- [ ] **Step 1: Run the complete backend suite**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: PASS.

- [ ] **Step 2: Run the web contract build**

Update web API contract types only if the build identifies newly required product/order response fields; do not add store selection UI. Then run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 3: Run repository hygiene checks**

```powershell
cd ..
git diff --check
rg -n "getSeller\(|\.seller\b|findWithSeller" backend/src/main/java/com/sweet/market/product
```

Expected: `git diff --check` has no output and the `rg` command finds no product-owner seller references. Historical `Order.seller` and response field names are allowed outside the product package.

- [ ] **Step 4: Update and commit the handoff**

Record V3 behavior, fresh/legacy database checks, new `storeId` request field, response fields, the order seller snapshot policy, completed commands, and the remaining Task 4/Task 5 boundaries.

```powershell
git add docs/superpowers/handoffs/2026-07-10-milestone-21-store-foundation-task-3-handoff.md
git commit -m "docs: hand off product store ownership"
```

## Plan Self-Review

- Spec coverage: Tasks 1-2 implement the single store ownership model and command authorization; Task 3 implements buyer visibility and stale-command protection; Task 4 preserves order history and downstream seller behavior; Task 5 covers remaining read-model references and N+1 protection; Task 6 verifies and records the result.
- Placeholder scan: all tasks name concrete files, test methods, commands, expected results, and implementation interfaces.
- Type consistency: product ownership uses `Product.getStore()`, historical seller logic uses `Order.getSeller()`, and creation calls `StoreAccessService.requireCatalogOperator(memberId, storeId)` throughout.
