# Milestone 21 Task 3 Product Store Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 상품의 유일한 상업적 소유자를 스토어로 전환하고 주문의 판매자 회원 이력을 보존한다.

**Architecture:** `Product.seller`와 DB의 `products.seller_id`는 다른 상품·주문·환불·정산 코드와 강하게 결합되어 있으므로, 모두를 `Product.store` 또는 `Order.seller`로 바꾸는 원자적 컴파일 단위에서 제거한다. 기존 DB는 V1 백필을 검증한 V3로 legacy 컬럼을 제거하고, 신규 DB는 Hibernate `update`가 새 매핑을 생성한다.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Hibernate, Flyway, PostgreSQL/Testcontainers, JUnit 5, MockMvc.

## Global Constraints

- JUnit `@Test` 메서드명은 한국어_밑줄_형식으로 작성한다.
- 상품은 생성 시 active `storeId`를 받고, 생성 뒤 스토어를 변경하지 않는다.
- owner와 manager는 active store의 카탈로그 명령을 수행할 수 있다.
- pending, rejected, suspended 사업자 스토어 상품은 일반 목록에서 제외하고 장바구니·주문을 거절한다.
- 주문 판매자 이력은 `Order.seller` 회원 관계만 보존하고 닉네임은 현재 회원 프로필에서 표시한다.
- 웹의 스토어 선택 UI는 M21 Task 5 범위이며 구현하지 않는다.
- Gradle은 JDK 21과 `JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`으로 실행한다.

---

## Why The First Task Is Atomic

`Product.seller`는 JPA 연관관계뿐 아니라 JPQL, entity graph, product DTO, cart/wishlist, order, refund, settlement, review, delivery/payment repository, admin query, seller report, 그리고 테스트 fixture에 사용된다. 따라서 `Product.seller`나 `products.seller_id`를 먼저 제거하고 뒤 작업에서 참조를 바꾸는 순서는 전체 프로젝트를 컴파일할 수 없다. 이 계획은 임시 이중 소유권을 만들지 않고, 첫 task에서 모든 참조를 일괄 전환한다.

## Target File Map

- Create `backend/src/main/resources/db/migration/V3__complete_product_store_ownership.sql` — legacy `store_id`를 검증하고 `products.seller_id`를 제거한다.
- Modify `backend/src/main/java/com/sweet/market/product/{domain,application,query,repository,api,admin}/**` — 스토어 소유권, 명령 인가, DTO, 공개 쿼리를 전환한다.
- Modify `backend/src/main/java/com/sweet/market/order/{domain,application,query,repository,api,admin}/**` — 주문 판매자 스냅샷과 이력 조회를 전환한다.
- Modify `backend/src/main/java/com/sweet/market/{cart,wishlist,refund,settlement,review,seller,delivery,payment}/**` — 현재 상품 판매자 또는 주문 이력 판매자 기준으로 참조를 바꾼다.
- Modify `backend/src/test/java/com/sweet/market/{store/migration,product,order,cart,wishlist,refund,settlement,seller,jpalab}/**` — migration, authorization, availability, compatibility, query-count 회귀를 검증한다.

### Task 1: Atomic Product Ownership Cutover

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__complete_product_store_ownership.sql`
- Modify: `backend/src/main/java/com/sweet/market/product/{domain/Product.java,application/ProductService.java,query/ProductQueryService.java,repository/ProductRepository.java,api/{ProductCreateRequest,ProductResponse,ProductSummaryResponse}.java,admin/**}`
- Modify: `backend/src/main/java/com/sweet/market/order/{domain/Order.java,application/OrderService.java,query/OrderQueryService.java,repository/OrderRepository.java,api/{OrderResponse,OrderSummaryResponse}.java,admin/**}`
- Modify: `backend/src/main/java/com/sweet/market/{cart,wishlist,refund,settlement,review,seller,delivery,payment}/**/*.java`
- Test: `backend/src/test/java/com/sweet/market/store/migration/{StoreMigrationTest,StoreFreshDatabaseStartupTest}.java`
- Test: `backend/src/test/java/com/sweet/market/{product,order,cart,wishlist,refund,settlement,seller,jpalab}/**/*Test.java`

**Interfaces:**
- Produces: `Product.getStore(): Store`, `Product.create(Store, String, String, long): Product`, and `Product.isPurchasable(): boolean`.
- Produces: `Order.getSeller(): Member` populated from `product.getStore().getOwnerMember()` at creation.
- Produces: `ProductCreateRequest(Long storeId, String title, String description, long price, List<ProductCreateImageRequest> images)`.
- Produces: `ProductResponse` and `ProductSummaryResponse` fields `storeId`, `storeName`, `storeType`; direct product response additionally has `purchasable`.

- [ ] **Step 1: Write all failing contract tests before changing the mapping**

Add focused Korean-named tests that describe the finished observable behavior:

```java
void 상점_운영자는_선택한_활성_상점에_상품을_등록한다() throws Exception
void 다른_상점_운영자는_상품을_수정할_수_없다() throws Exception
void 비활성_사업자_상점에는_상품을_등록할_수_없다() throws Exception
void 비활성_사업자_상점_상품은_공개_목록에서_제외된다() throws Exception
void 비활성_사업자_상점_상품의_직접_조회는_구매_불가를_반환한다() throws Exception
void 비활성_사업자_상점_상품은_장바구니에_담거나_주문할_수_없다() throws Exception
void 주문은_생성_시점의_상점_소유자를_판매자로_보존한다() throws Exception
void 판매자_환불과_정산은_주문_판매자_스냅샷을_사용한다() throws Exception
```

Extend the legacy migration fixture so `products` includes `status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE'`. Assert `products.seller_id` is absent after migration, and assert `products.store_id`, `orders.seller_id`, their FKs, `idx_products_store_status_id`, and `idx_orders_seller_id` exist on a fresh PostgreSQL boot.

- [ ] **Step 2: Run the full affected suite to capture RED failures**

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.store.migration.*' --tests 'com.sweet.market.product.*' --tests 'com.sweet.market.order.*' --tests 'com.sweet.market.cart.*' --tests 'com.sweet.market.wishlist.*' --tests 'com.sweet.market.refund.*' --tests 'com.sweet.market.settlement.*' --tests 'com.sweet.market.seller.report.*' --tests 'com.sweet.market.jpalab.ProductQueryOptimizationTest' --tests 'com.sweet.market.jpalab.OrderQueryOptimizationTest' --tests 'com.sweet.market.jpalab.SettlementQueryOptimizationTest'
```

Expected: FAIL because the request lacks `storeId`, product ownership is member-based, inactive store availability is not checked, and order seller snapshots do not exist.

- [ ] **Step 3: Replace the product ownership relation and create the schema migration**

Implement the required JPA mappings and domain factories:

```java
// Product
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "store_id", nullable = false)
private Store store;

public static Product create(Store store, String title, String description, long price) {
    return new Product(store, title, description, price, ProductStatus.ON_SALE);
}

public boolean isPurchasable() {
    return status == ProductStatus.ON_SALE && store.getStatus() == StoreStatus.ACTIVE;
}

// Order
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "seller_id", nullable = false)
private Member seller;
```

Create V3 with guarded legacy-table checks. It must raise an exception for null or orphaned `products.store_id`, create `idx_products_store_status_id`, and then run `ALTER TABLE products DROP COLUMN IF EXISTS seller_id`. The migration must return without change when `products` does not yet exist on a fresh database. Add the JPA table indexes so Hibernate creates the fresh-schema equivalents.

- [ ] **Step 4: Convert all command, read, and history references in the same change**

Use only these ownership forms after the cutover:

```java
Store store = storeAccessService.requireCatalogOperator(memberId, request.storeId());
product.getStore().getOwnerMember(); // current product seller display
order.getSeller();                   // historical seller display, authority, settlement, refund, review, report
```

Apply them across repositories, entity graphs, JPQL, DTO constructors, controllers, domain methods, and tests. Rename `findWithSeller...` repository methods to store-oriented names. Change public product JPQL to join `p.store store` and exclude a business store unless its status is `ACTIVE`. Direct reads retain an existing non-hidden product but map `purchasable` to false when its store is inactive. Cart, checkout, wishlist, and direct order creation call `product.isPurchasable()` inside their transactions.

`ProductCreateRequest` must include `@NotNull Long storeId`; `ProductService` must use `StoreAccessService.requireCatalogOperator(memberId, storeId)` for creation and the existing product store id for update/hide. Keep temporary-upload authorization tied to the authenticated member id.

Order creation sets `seller` from `product.getStore().getOwnerMember()`. Settlement creation, seller refund authority, review construction, seller reports, admin order filters, and all historical response seller fields use `Order.seller`, not the current product store owner.

- [ ] **Step 5: Run the complete affected suite and fix every compilation or behavioral failure**

Run the Step 2 command until it passes. Then run:

```powershell
.\gradlew.bat test
```

Expected: PASS. The full suite proves no remaining product seller relation is required by a compiled source or existing test.

- [ ] **Step 6: Run source and query-shape checks**

```powershell
cd ..
rg -n "getSeller\(|\.seller\b|findWithSeller" backend/src/main/java/com/sweet/market/product
git diff --check
```

Expected: the `rg` command finds no product-owner seller reference; `git diff --check` has no output. Update the JPA optimization tests so accessing store public name and store owner nickname does not cause a per-row query or fetch `store.memberships`.

- [ ] **Step 7: Commit the atomic cutover**

```powershell
git add backend/src/main/java backend/src/main/resources/db/migration/V3__complete_product_store_ownership.sql backend/src/test/java
git commit -m "feat: migrate products to store ownership"
```

### Task 2: Audit Response Contracts And M21 Boundaries

**Files:**
- Modify: `backend/src/test/java/com/sweet/market/product/{ProductApiTest,ProductSellerApiTest}.java`
- Modify: `backend/src/test/java/com/sweet/market/order/{OrderApiTest,OrderQueryApiTest}.java`
- Modify: `backend/src/test/java/com/sweet/market/{refund/RefundRequestApiTest,settlement/SettlementApiTest,seller/report/SellerReportApiTest}.java`
- Modify: `docs/superpowers/handoffs/2026-07-10-milestone-21-store-foundation-task-3-handoff.md`

**Interfaces:**
- Verifies product/order responses have compatible legacy seller fields and consistent store fields.
- Documents that UI store selection remains Task 5 work.

- [ ] **Step 1: Add response consistency regression tests**

Add tests asserting product detail, product summary, order detail, and order summary expose matching `storeId`, `storeName`, `storeType`, `sellerId`, and `sellerNickname` according to the current-product versus historical-order rule. Use Korean method names such as:

```java
void 상품_응답은_상점과_판매자_식별자를_일관되게_반환한다() throws Exception
void 주문_응답은_주문_판매자와_상점_정보를_일관되게_반환한다() throws Exception
```

- [ ] **Step 2: Run the response regression suite**

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.product.*' --tests 'com.sweet.market.order.*' --tests 'com.sweet.market.refund.*' --tests 'com.sweet.market.settlement.*' --tests 'com.sweet.market.seller.report.*'
```

Expected: PASS.

- [ ] **Step 3: Record the completed boundary and commit**

Update the Task 3 handoff with V3 migration semantics, fresh/legacy database checks, `storeId` request contract, response fields, order seller snapshot rule, test commands/results, and the explicit Task 5 UI boundary.

```powershell
git add backend/src/test/java/com/sweet/market docs/superpowers/handoffs/2026-07-10-milestone-21-store-foundation-task-3-handoff.md
git commit -m "docs: hand off product store ownership"
```

### Task 3: Final Verification

**Files:**
- Test: backend full suite and web production build.

- [ ] **Step 1: Verify backend and web builds**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
cd ..\web
npm run build
cd ..
git diff --check
```

Expected: every command succeeds with no diff whitespace error. If the web build identifies response type changes, update only its existing API contract types; do not add Task 5 store-selection UI.

- [ ] **Step 2: Commit any contract-only web adjustment**

```powershell
git add web/src
git commit -m "fix: align store ownership API contracts"
```

Run this commit only when Task 3 required a web contract type adjustment. Otherwise do not create an empty commit.

## Plan Self-Review

- Spec coverage: Task 1 atomically removes member-owned products and validates migration, authorization, availability, historical seller behavior, queries, and regression coverage. Task 2 verifies DTO compatibility and writes the handoff. Task 3 runs the final backend/web gate.
- Atomicity: no task removes `Product.seller` before every compiled production and test reference is converted; no temporary dual product ownership model is introduced.
- Type consistency: current product seller display comes from `Product.store.ownerMember`, while order history always comes from `Order.seller`.
