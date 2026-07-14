# Milestone 25 Store Promotions And Price Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let owners of active business stores run selected-product and store-wide automatic promotions while every buyer and order surface uses the server-calculated effective price.

**Architecture:** A `PromotionCampaign` aggregate and optional `PromotionTarget` rows own lifecycle and eligibility. `PromotionPricingService` returns a shared `PromotionPrice` for entity-based reads and order creation; the catalog's native projection reproduces the same deterministic SQL selection so filtering, sorting, and keyset pagination use effective price without N+1 queries. Orders persist price snapshots and downstream projections read the snapshot rather than current product price.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, JDBC `NamedParameterJdbcTemplate`, PostgreSQL/Flyway, JUnit 5/Testcontainers test setup, React, TypeScript, TanStack Query, Vite.

## Global Constraints

- Create promotions only for `ACTIVE` `BUSINESS` stores and authorize every mutation with the existing store `OWNER` membership check.
- KST (`Asia/Seoul`) is the UI and API time zone; persist promotion instants in UTC and inject `Clock` into price/lifecycle services.
- Support `SELECTED_PRODUCTS` and `STORE_WIDE`; select one promotion by lowest effective price, then higher priority, then lower campaign id.
- Never stack promotions, add coupon behavior, alter product list price, or add personal-store/platform promotions.
- The catalog price filters, price sorts, and keyset cursors use `effectivePrice`, not `Product.price`.
- New JUnit `@Test` methods use Korean names with underscores. Run backend Gradle work with JDK 21 and `JWT_SECRET` set.
- Do not stage, overwrite, reset, or discard the existing local `backend/src/main/resources/application.yaml` modification.

---

### Task 1: Promotion schema, aggregate, and deterministic lifecycle

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__add_store_promotions_and_order_price_snapshots.sql`
- Create: `backend/src/main/java/com/sweet/market/promotion/domain/PromotionCampaign.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/domain/PromotionTarget.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/domain/PromotionScope.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/domain/PromotionDiscountType.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/domain/PromotionLifecycleStatus.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/domain/PromotionEffectiveStatus.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/domain/PromotionDomainError.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/repository/PromotionCampaignRepository.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/repository/PromotionTargetRepository.java`
- Test: `backend/src/test/java/com/sweet/market/promotion/domain/PromotionCampaignTest.java`
- Test: `backend/src/test/java/com/sweet/market/store/migration/PromotionMigrationTest.java`

**Interfaces:**
- Produces: `PromotionCampaign.create(Store, PromotionScope, PromotionDiscountType, long, int, String, String, Instant, Instant, List<Product>)`.
- Produces: `PromotionCampaign.effectiveStatus(Instant): PromotionEffectiveStatus`, `schedule(Instant)`, `pause(Instant)`, `resume(Instant)`, `end()`, and `update(..., Instant now)`.

- [ ] **Step 1: Write failing lifecycle and migration tests.**

```java
@Test
void 예약_프로모션은_시작시각부터_활성으로_판단한다() {
    PromotionCampaign campaign = 캠페인(Instant.parse("2026-07-14T00:00:00Z"), Instant.parse("2026-07-15T00:00:00Z"));

    assertThat(campaign.effectiveStatus(Instant.parse("2026-07-13T23:59:59Z"))).isEqualTo(SCHEDULED);
    assertThat(campaign.effectiveStatus(Instant.parse("2026-07-14T00:00:00Z"))).isEqualTo(ACTIVE);
    assertThat(campaign.effectiveStatus(Instant.parse("2026-07-15T00:00:00Z"))).isEqualTo(ENDED);
}

@Test
void 기존_주문은_상품가격으로_가격스냅샷을_채운다() {
    jdbcTemplate.queryForObject("select list_price from orders where id = :id", parameters, Long.class);
    assertThat(listPrice).isEqualTo(existingProductPrice);
}
```

- [ ] **Step 2: Run the focused tests and confirm they fail because promotion types, tables, and order columns do not exist.**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.promotion.domain.PromotionCampaignTest' --tests 'com.sweet.market.store.migration.PromotionMigrationTest'
```

Expected: compilation or migration failure naming the missing promotion classes/columns.

- [ ] **Step 3: Add V8 and the small domain types.**

Create `promotion_campaigns` with store FK, scope, discount type/value, priority, title, nullable label, UTC `start_at`/`end_at`, lifecycle status, and audit timestamps. Create `promotion_targets` with campaign/product FKs and `unique (promotion_campaign_id, product_id)`. Add indexes matching `(store_id, lifecycle_status, start_at, end_at)` and `(product_id, promotion_campaign_id)`. Add `orders.list_price`, nullable `orders.promotion_campaign_id`, `orders.promotion_discount_amount`, and `orders.final_price`; backfill all current rows from `products.price` with zero discount/final price equal to list price before marking monetary columns not null.

Implement the aggregate around these exact state rules:

```java
public PromotionEffectiveStatus effectiveStatus(Instant now) {
    if (lifecycleStatus == PAUSED) return PAUSED;
    if (lifecycleStatus == ENDED || !now.isBefore(endAt)) return ENDED;
    return now.isBefore(startAt) ? SCHEDULED : ACTIVE;
}

public void update(..., Instant now) {
    if (effectiveStatus(now) == ACTIVE || lifecycleStatus == PAUSED || lifecycleStatus == ENDED) {
        throw new DomainException(PromotionDomainError.UPDATE_NOT_ALLOWED);
    }
    // validate startAt < endAt, discountValue >= 0, then replace scalar fields/targets
}
```

Use `@ManyToOne(fetch = LAZY)` for store and target product, a private `List<PromotionTarget>`, and aggregate methods that replace selected targets only when the scope is `SELECTED_PRODUCTS`.

- [ ] **Step 4: Run the focused domain/migration tests.**

Run the command from Step 2.

Expected: `BUILD SUCCESSFUL`; lifecycle boundary and V8 backfill assertions pass.

- [ ] **Step 5: Commit the schema and aggregate foundation.**

```powershell
git add backend/src/main/resources/db/migration/V8__add_store_promotions_and_order_price_snapshots.sql backend/src/main/java/com/sweet/market/promotion backend/src/test/java/com/sweet/market/promotion/domain/PromotionCampaignTest.java backend/src/test/java/com/sweet/market/store/migration/PromotionMigrationTest.java
git commit -m "feat: add store promotion domain foundation"
```

### Task 2: Business-owner campaign management API

**Files:**
- Create: `backend/src/main/java/com/sweet/market/promotion/application/PromotionCampaignService.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/api/PromotionCampaignController.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/api/PromotionCampaignCreateRequest.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/api/PromotionCampaignUpdateRequest.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/api/PromotionCampaignResponse.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/api/PromotionCampaignSearchRequest.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/api/PromotionTargetProductResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/store/application/StoreAccessService.java`
- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Test: `backend/src/test/java/com/sweet/market/promotion/PromotionCampaignApiTest.java`

**Interfaces:**
- Consumes: `StoreAccessService.requireOwner(Long memberId, Long storeId): Store`.
- Produces: `PromotionCampaignService.create(Long, Long, PromotionCampaignCreateRequest): PromotionCampaignResponse` and equivalent `findPage`, `find`, `update`, `schedule`, `pause`, `resume`, `end` methods.
- Produces REST paths under `/api/stores/{storeId}/promotions` with the request/response records from this task.

- [ ] **Step 1: Write API tests before adding the controller.**

Cover the successful selected-product and store-wide create paths plus these failures: personal store owner, business manager, unrelated member, inactive business store, another store's target product, non-purchasable target, invalid time window, and invalid state transition. Use Korean names, for example:

```java
@Test
void 사업자_상점_소유자는_선택상품_프로모션을_예약한다() throws Exception { ... }

@Test
void 매니저는_프로모션을_생성할_수_없다() throws Exception { ... }
```

- [ ] **Step 2: Run the API test class and confirm endpoint compilation fails.**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.promotion.PromotionCampaignApiTest'
```

Expected: FAIL because the promotion API/service does not exist.

- [ ] **Step 3: Add strict owner/business authorization and DTO validation.**

Add `StoreAccessService.requireActiveBusinessOwner(memberId, storeId)` that composes `requireOwner`, then rejects `StoreType.PERSONAL` with `STORE_INVALID_TYPE` and non-active stores with `STORE_ACCESS_DENIED`. Keep manager behavior unchanged for catalog operations.

Use records that accept KST `LocalDateTime` values and a target list only when scope is selected:

```java
public record PromotionCampaignCreateRequest(
    @NotNull PromotionScope scope,
    @NotNull PromotionDiscountType discountType,
    @PositiveOrZero long discountValue,
    int priority,
    @NotBlank @Size(max = 100) String title,
    @Size(max = 200) String label,
    @NotNull LocalDateTime startsAt,
    @NotNull LocalDateTime endsAt,
    List<@Positive Long> productIds
) {}
```

Convert with `startsAt.atZone(ZoneId.of("Asia/Seoul")).toInstant()`. Require a non-empty product ID set for `SELECTED_PRODUCTS` and an empty/null set for `STORE_WIDE`.

- [ ] **Step 4: Implement lifecycle endpoints and paged responses.**

Expose the paths specified in the approved design. Have `GET` return `Page<PromotionCampaignResponse>` using bounded page/size request validation, and return derived status from `Clock.instant()`. `PATCH` delegates to `campaign.update`; state commands call aggregate methods and return the updated response. Add promotion-specific `ErrorCode` values only for lifecycle conflict responses that the UI needs to distinguish; use existing validation/access/store codes otherwise.

- [ ] **Step 5: Run API tests and add a focused regression for resume-after-expiry.**

Run the command from Step 2.

Expected: `BUILD SUCCESSFUL`; every request returns the expected `ApiResponse` and HTTP status.

- [ ] **Step 6: Commit owner operations.**

```powershell
git add backend/src/main/java/com/sweet/market/promotion backend/src/main/java/com/sweet/market/store/application/StoreAccessService.java backend/src/main/java/com/sweet/market/common/error/ErrorCode.java backend/src/test/java/com/sweet/market/promotion/PromotionCampaignApiTest.java
git commit -m "feat: manage business store promotions"
```

### Task 3: Shared effective-price calculation and order snapshots

**Files:**
- Create: `backend/src/main/java/com/sweet/market/promotion/application/PromotionPrice.java`
- Create: `backend/src/main/java/com/sweet/market/promotion/application/PromotionPricingService.java`
- Modify: `backend/src/main/java/com/sweet/market/order/domain/Order.java`
- Modify: `backend/src/main/java/com/sweet/market/order/application/OrderService.java`
- Modify: `backend/src/main/java/com/sweet/market/cart/application/CartService.java`
- Modify: `backend/src/main/java/com/sweet/market/payment/application/PaymentApprovalTransactionService.java`
- Modify: `backend/src/main/java/com/sweet/market/order/api/OrderResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/order/api/OrderSummaryResponse.java`
- Test: `backend/src/test/java/com/sweet/market/promotion/application/PromotionPricingServiceTest.java`
- Test: `backend/src/test/java/com/sweet/market/order/OrderApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/cart/CartCheckoutApiTest.java`

**Interfaces:**
- Produces: `record PromotionPrice(long listPrice, Long promotionId, String promotionTitle, long promotionDiscountAmount, long effectivePrice)`.
- Produces: `PromotionPricingService.quote(Product product): PromotionPrice` and `quoteAll(Collection<Long> productIds): Map<Long, PromotionPrice>`.
- Consumes: `PromotionCampaignRepository` candidate queries and injected `Clock`.
- Changes: `Order.create(Member buyer, Product product, PromotionPrice price): Order`.

- [ ] **Step 1: Write failing price-policy tests.**

Test fixed and percentage rounding, zero-floor clamping, selected versus store-wide candidates, lowest effective price, priority/id ties, no active promotion, paused/expired campaign exclusion, and a promotion expiring between cart read and `OrderService.create`.

```java
@Test
void 같은_할인가면_우선순위가_높고_ID가_작은_프로모션을_선택한다() {
    PromotionPrice price = pricingService.quote(product);

    assertThat(price.promotionId()).isEqualTo(highPriorityLowIdCampaign.getId());
    assertThat(price.effectivePrice()).isEqualTo(8_000L);
}
```

- [ ] **Step 2: Run the service tests and confirm they fail.**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.promotion.application.PromotionPricingServiceTest'
```

Expected: FAIL because `PromotionPrice` and `PromotionPricingService` are absent.

- [ ] **Step 3: Implement the shared calculation, then pass it through both order creation paths.**

Use a repository query limited to the product/store candidate set and calculate amounts in Java:

```java
private long discount(PromotionCampaign campaign, long listPrice) {
    return switch (campaign.getDiscountType()) {
        case FIXED_AMOUNT -> Math.min(campaign.getDiscountValue(), listPrice);
        case PERCENTAGE -> Math.min((listPrice * campaign.getDiscountValue()) / 100, listPrice);
    };
}
```

Select an internal `PricedCandidate(PromotionCampaign campaign, PromotionPrice price)` with `Comparator.comparingLong(candidate -> candidate.price().effectivePrice()).thenComparing(Comparator.comparingInt(candidate -> candidate.campaign().getPriority()).reversed()).thenComparing(candidate -> candidate.campaign().getId())`; keep campaign priority internal to the selection helper instead of exposing it in the buyer response.

Inject the pricing service into `OrderService` and `CartService`. Call it after product availability is verified and immediately before `Order.create`. Make `PaymentApprovalTransactionService` use `order.getFinalPrice()` for gateway approval data/response amounts rather than `order.getProduct().getPrice()`.

- [ ] **Step 4: Extend order responses and downstream money projections.**

Replace response fields derived from `order.product.price` with order snapshot fields. Preserve compatibility by retaining `productPrice` only if existing web consumers require it, but map it to `finalPrice` and add explicit list/promotion fields. Update `OrderRepository`, payment, settlement, refund, admin, and seller-report projection constructors that currently select `p.price` for an order monetary amount to select `o.finalPrice` and `o.listPrice` instead.

- [ ] **Step 5: Run focused order/cart/payment tests.**

Run:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.promotion.application.PromotionPricingServiceTest' --tests 'com.sweet.market.order.OrderApiTest' --tests 'com.sweet.market.cart.CartCheckoutApiTest' --tests 'com.sweet.market.payment.PaymentApiTest'
```

Expected: `BUILD SUCCESSFUL`; direct and cart checkout orders persist the expected price snapshots.

- [ ] **Step 6: Commit shared pricing and snapshots.**

```powershell
git add backend/src/main/java/com/sweet/market/promotion/application backend/src/main/java/com/sweet/market/order backend/src/main/java/com/sweet/market/cart/application/CartService.java backend/src/main/java/com/sweet/market/payment backend/src/main/java/com/sweet/market/settlement backend/src/main/java/com/sweet/market/refund backend/src/test/java/com/sweet/market/promotion/application backend/src/test/java/com/sweet/market/order/OrderApiTest.java backend/src/test/java/com/sweet/market/cart/CartCheckoutApiTest.java
git commit -m "feat: calculate and snapshot promotion prices"
```

### Task 4: Buyer detail, cart, and legacy card price read models

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/product/query/ProductQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductSummaryResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/cart/repository/CartItemRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/cart/api/CartItemResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/store/storefront/StorefrontQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/store/storefront/StorefrontProductResponse.java`
- Test: `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/cart/CartApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/store/StorefrontApiTest.java`

**Interfaces:**
- Consumes: `PromotionPricingService.quote(Product)` and `quoteAll(Collection<Product>)`.
- Produces the common buyer fields `listPrice`, nullable `promotionId`/`promotionTitle`, `promotionDiscountAmount`, and `effectivePrice` on product detail, summary, cart, and storefront records.

- [ ] **Step 1: Write failing buyer-read tests.**

Assert that a buyer-visible detail, cart row, storefront card, and legacy public product summary expose the same price values for an active promotion; assert the cart returns a fresh price after the campaign is paused or expires.

- [ ] **Step 2: Run the three focused API suites and confirm response-field assertions fail.**

Run:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.product.ProductApiTest' --tests 'com.sweet.market.cart.CartApiTest' --tests 'com.sweet.market.store.StorefrontApiTest'
```

Expected: FAIL because buyer response records do not contain the shared price fields.

- [ ] **Step 3: Enrich read models without per-row promotion queries.**

For product detail, call `quote(product)` after the existing availability check. For page/list results, collect only the current page product IDs and call `quoteAll(productIds)` once; do not invoke `quote` in a `map` that performs a repository query per item. Replace the JPQL `CartItemResponse` constructor projection with a cart query/service mapping that can receive the bounded price map, preserving its current availability and image query behavior.

- [ ] **Step 4: Run focused buyer-read suites.**

Run the command from Step 2.

Expected: `BUILD SUCCESSFUL`; no promotion is returned for inactive/expired campaigns and all current buyer reads agree.

- [ ] **Step 5: Commit buyer read models.**

```powershell
git add backend/src/main/java/com/sweet/market/product backend/src/main/java/com/sweet/market/cart backend/src/main/java/com/sweet/market/store/storefront backend/src/test/java/com/sweet/market/product/ProductApiTest.java backend/src/test/java/com/sweet/market/cart/CartApiTest.java backend/src/test/java/com/sweet/market/store/StorefrontApiTest.java
git commit -m "feat: expose promotion prices to buyer reads"
```

### Task 5: Effective-price catalog SQL, cursor, and budget regression coverage

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/catalog/query/CatalogSearchRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/catalog/query/CatalogProductRow.java`
- Modify: `backend/src/main/java/com/sweet/market/catalog/query/CatalogCursor.java`
- Modify: `backend/src/main/java/com/sweet/market/catalog/query/CatalogCursorCodec.java`
- Modify: `backend/src/main/java/com/sweet/market/catalog/query/CatalogSearchQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/catalog/api/CatalogProductCardResponse.java`
- Modify: `backend/src/test/java/com/sweet/market/catalog/CatalogApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/catalog/CatalogQueryOptimizationTest.java`
- Modify: `backend/src/test/java/com/sweet/market/catalog/query/CatalogCursorCodecTest.java`
- Modify: `backend/src/test/java/com/sweet/market/catalog/query/CatalogSearchRepositoryTest.java`

**Interfaces:**
- Changes `CatalogProductRow` and `CatalogProductCardResponse` from `price` alone to the common price representation.
- Changes price-sort cursor payload to carry `effectivePrice` plus product ID; `NEWEST` remains ID-only.
- Produces one native card projection with an eligible campaign lateral/derived selection, no per-card Java repository calls.

- [ ] **Step 1: Write failing catalog tests for effective price and cursor boundaries.**

Create products whose list-price order differs from effective-price order. Test `PRICE_ASC`, `PRICE_DESC`, min/max filters, equal effective price tie IDs, next-page continuation, selected-product and store-wide eligibility, and an active promotion that must beat an equal-price candidate by priority/id.

- [ ] **Step 2: Run catalog API/repository tests and confirm the current list-price behavior fails.**

Run:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.catalog.CatalogApiTest' --tests 'com.sweet.market.catalog.query.CatalogSearchRepositoryTest' --tests 'com.sweet.market.catalog.query.CatalogCursorCodecTest'
```

Expected: FAIL on effective-price order/filter/cursor assertions.

- [ ] **Step 3: Extend the native SQL with one effective campaign candidate.**

Join a `LEFT JOIN LATERAL` candidate that scopes to `p.store_id`, includes only active business-store campaigns, accepts store-wide or targeted rows, calculates the discount with SQL `CASE`, orders by effective price ascending, priority descending, and campaign ID ascending, and limits to one. Use `coalesce` to derive `effective_price`/discount for no-campaign rows. Apply `minPrice`, `maxPrice`, price seek predicates, and `ORDER BY` to that derived effective price; retain `p.id` as every sort's final tie breaker.

Update cursor signing fields and fingerprint only as needed for the effective price seek value. Keep malformed/tampered/mismatched/expired behavior unchanged.

- [ ] **Step 4: Extend query-budget assertions before accepting the SQL.**

Keep anonymous catalog at exactly one JDBC projection and authenticated catalog at the existing bounded JDBC + wishlist/cart query budget. Add SQL assertions that the page query does not contain a second per-card promotion select or collection fetch. Do not weaken existing no-`COUNT(` and no-`inventory_adjustments` assertions.

- [ ] **Step 5: Run all focused catalog tests.**

Run:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.catalog.*' --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`; effective-price paging is deterministic and query budgets remain bounded.

- [ ] **Step 6: Commit catalog pricing.**

```powershell
git add backend/src/main/java/com/sweet/market/catalog backend/src/test/java/com/sweet/market/catalog
git commit -m "feat: sort catalog by effective promotion price"
```

### Task 6: Promotion workspace and buyer price presentation

**Files:**
- Create: `web/src/features/promotions/promotionApi.ts`
- Create: `web/src/pages/MyStorePromotionsPage.tsx`
- Create: `web/src/pages/StorePromotionDetailPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/pages/MyStorePage.tsx`
- Modify: `web/src/features/catalog/catalogApi.ts`
- Modify: `web/src/features/catalog/CatalogProductCard.tsx`
- Modify: `web/src/features/products/productApi.ts`
- Modify: `web/src/features/products/ProductCard.tsx`
- Modify: `web/src/pages/ProductDetailPage.tsx`
- Modify: `web/src/features/cart/cartApi.ts`
- Modify: `web/src/pages/MyCartPage.tsx`
- Modify: `web/src/shared/styles.css`

**Interfaces:**
- Produces `PromotionCampaign`, `PromotionCampaignInput`, `PromotionSearchInput`, request functions, and TanStack query keys in `promotionApi.ts`.
- Consumes the common buyer price fields from Tasks 3-5.
- Adds authenticated routes `/me/store/promotions` and `/me/store/promotions/:storeId/:promotionId`.

- [ ] **Step 1: Define client contracts and write the price formatter component helper first.**

In `promotionApi.ts`, model status, scope, discount type, KST ISO date-time fields, target product IDs, and paged responses. Add a small reusable `PriceDisplay` component or helper in the product feature that receives:

```ts
type PromotionPrice = {
  listPrice: number;
  promotionId: number | null;
  promotionTitle: string | null;
  promotionDiscountAmount: number;
  effectivePrice: number;
};
```

It renders only the effective price for a null promotion; otherwise it renders a struck-through list price, effective price, and optional campaign title.

- [ ] **Step 2: Implement the owner workspace.**

`MyStorePromotionsPage` first loads `getOperableStores`, then allows only active BUSINESS stores where `role === 'OWNER'` to manage campaigns. It provides list filters, create navigation, state chips, and pause/resume/end mutation buttons that invalidate the campaign list/detail, store catalog, buyer catalog, product, and cart query keys. For personal stores/managers, show a clear access-state explanation rather than a non-functional form.

`StorePromotionDetailPage` creates/edits draft or future scheduled campaigns. Scope switching clears selected product IDs when `STORE_WIDE`; selected scope uses the existing owner catalog API to choose products. Render KST date-time-local controls and server field errors. Hide edit controls once the effective status is `ACTIVE`; show only permitted lifecycle controls.

- [ ] **Step 3: Wire buyer prices through all existing cards and pages.**

Replace `price` rendering in catalog cards, legacy product cards, product detail, and cart cards with the shared price display. Change cart `selectedTotal` to sum `item.effectivePrice`. Keep the checkout disclaimer visible: `주문 시점의 현재 프로모션 가격으로 최종 금액이 확정됩니다.` Reserve a fixed price block height in CSS to avoid card-grid layout shifts.

- [ ] **Step 4: Add routes and discoverability.**

Add both routes under `RequireAuth`. Add an owner-only `프로모션 관리` link in `MyStorePage`'s existing `.store-owner-links` navigation. Keep the My Store route and existing catalog controls intact.

- [ ] **Step 5: Run TypeScript/Vite verification.**

Run:

```powershell
cd web
npm run build
```

Expected: exit code 0; TypeScript accepts all expanded response types and Vite builds the new routes.

- [ ] **Step 6: Commit web surfaces.**

```powershell
git add web/src/features/promotions web/src/features/catalog web/src/features/products web/src/features/cart web/src/pages/MyStorePromotionsPage.tsx web/src/pages/StorePromotionDetailPage.tsx web/src/pages/MyStorePage.tsx web/src/pages/ProductDetailPage.tsx web/src/pages/MyCartPage.tsx web/src/app/router.tsx web/src/shared/styles.css
git commit -m "feat: add promotion workspace and price displays"
```

### Task 7: Compatibility sweep, query-plan evidence, and release verification

**Files:**
- Modify: `backend/src/test/java/com/sweet/market/payment/PaymentApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/settlement/SettlementApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/jpalab/QueryOptimizationTestSupport.java` only if needed to expose a promotion SQL assertion helper
- Create: `docs/superpowers/handoffs/2026-07-14-milestone-25-store-promotions-and-price-policy-handoff.md`

**Interfaces:**
- Consumes final order snapshot fields and the catalog query budget from prior tasks.
- Produces delivery evidence for promotion lifecycle, effective pricing, downstream snapshot compatibility, backend tests, web build, and representative `EXPLAIN (ANALYZE, BUFFERS)` output.

- [ ] **Step 1: Add regression tests for historical money.**

Create an order under an active promotion, then pause/end/edit the campaign. Assert payment, settlement, refund, buyer order, and seller/admin report reads retain the snapshot `finalPrice`, list price, campaign ID/title snapshot contract chosen by the response, and discount amount. Assert no current `Product.price` or current campaign state changes the historical amount.

- [ ] **Step 2: Run compatibility-focused backend tests.**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='4'
.\gradlew.bat test --tests 'com.sweet.market.promotion.*' --tests 'com.sweet.market.catalog.*' --tests 'com.sweet.market.product.ProductApiTest' --tests 'com.sweet.market.cart.*' --tests 'com.sweet.market.order.*' --tests 'com.sweet.market.payment.*' --tests 'com.sweet.market.settlement.*' --tests 'com.sweet.market.refund.*' --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` with zero failures, errors, and skipped tests.

- [ ] **Step 3: Capture representative promotion query-plan evidence.**

Use the M24 PostgreSQL harness pattern with active store-wide and selected-product campaigns. Run `EXPLAIN (ANALYZE, BUFFERS)` for first/deep effective-price catalog pages and document: the active-campaign/target indexes selected, no `OFFSET`, one candidate promotion per card, and the measured planning/execution times. Add an index only when the observed predicate/order needs it; do not add speculative indexes.

- [ ] **Step 4: Run full release verification.**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='4'
.\gradlew.bat test --rerun-tasks

cd ..\web
npm run build

cd ..
git diff --check
```

Expected: backend `BUILD SUCCESSFUL`, web exit code 0, and no `git diff --check` diagnostics.

- [ ] **Step 5: Write the handoff and commit final verification artifacts.**

Record exact commands, test counts, selected query-plan/index evidence, API/UI behavior, and deferred coupon/stacking work in the handoff.

```powershell
git add backend/src/test/java/com/sweet/market/payment/PaymentApiTest.java backend/src/test/java/com/sweet/market/settlement/SettlementApiTest.java backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java backend/src/test/java/com/sweet/market/jpalab/QueryOptimizationTestSupport.java docs/superpowers/handoffs/2026-07-14-milestone-25-store-promotions-and-price-policy-handoff.md
git commit -m "test: verify promotion pricing compatibility"
```

## Plan Self-Review

- **Spec coverage:** Tasks 1-2 cover business-only owner operations, both target scopes, KST lifecycle, and edits; Tasks 3-5 cover one-promotion selection, snapshots, effective-price catalog behavior, and query budgets; Task 6 covers the dedicated owner route and buyer UI; Task 7 covers downstream monetary compatibility and delivery evidence.
- **Placeholder scan:** The plan contains no unresolved markers or unspecified validation/testing steps. Every task has exact paths, named interfaces, failing tests, commands, expected outcomes, and a commit boundary.
- **Type consistency:** `PromotionPrice` is the single shared buyer/order price representation. `effectivePrice` is used by catalog SQL, cursor, frontend totals, and response records; `listPrice`, `promotionDiscountAmount`, and nullable campaign fields are the persisted/readable companion values.
