# Milestone 21 Store Foundation And Business Seller Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 상품의 상업적 소유 주체를 스토어로 전환하고 개인·사업자 스토어 운영과 심사 흐름을 제공합니다.

**Architecture:** `Store`는 불변 소유자와 공개 프로필·상태를 가지며, `StoreMembership`가 명령 인가를 담당합니다. `Product`는 하나의 `Store`만 참조하고, `Order`는 생성 당시의 판매자 회원을 스냅샷해 정산·환불·리포트 호환성을 지킵니다.

**Tech Stack:** Java 21, Spring Boot, JPA, PostgreSQL/Flyway, Testcontainers, React, TypeScript, TanStack Query.

---

## Target File Map

- Create `backend/src/main/java/com/sweet/market/store/domain/{Store,StoreType,StoreStatus,StoreMembership,StoreMemberRole}.java` — 스토어 상태와 멤버십 모델입니다.
- Create `backend/src/main/java/com/sweet/market/store/{repository,application,api,admin}/` — 인가, 심사, 공개 조회, 관리자 API입니다.
- Create `backend/src/main/resources/db/migration/V1__add_store_foundation.sql` — 스토어·멤버십 테이블, 기존 데이터 백필, 인덱스입니다.
- Modify `backend/src/main/java/com/sweet/market/{auth,product,order,cart,wishlist,refund,settlement}/` — 스토어 소유와 판매자 이력 호환성입니다.
- Create `web/src/features/stores/storeApi.ts`, `web/src/pages/{MyStorePage,StoreProfilePage,AdminBusinessStoresPage}.tsx` — 스토어 웹 흐름입니다.
- Modify `web/src/{app/router.tsx,shared/layout/Shell.tsx,features/products/productApi.ts,pages/HomePage.tsx,pages/ProductDetailPage.tsx,pages/ProductFormPage.tsx,shared/styles.css}` — 라우팅과 상품 화면입니다.

### Task 1: Database Migration And Store Domain

**Files:** `backend/build.gradle`, `backend/src/main/resources/application.yaml`, `backend/src/main/resources/db/migration/V1__add_store_foundation.sql`, `backend/src/main/java/com/sweet/market/store/**`, `backend/src/test/java/com/sweet/market/store/domain/{StoreTest,StoreMembershipTest}.java`

- [ ] Write Korean-named failing tests for personal-store creation, business application, approval, rejection, resubmission, suspension, reactivation, and illegal transitions.
- [ ] Run: `cd backend; .\gradlew.bat test --tests "com.sweet.market.store.domain.*"` — expected failure because store types are absent.
- [ ] Add Flyway PostgreSQL support. Configure baseline version `0`; disable Flyway in Testcontainers tests that use Hibernate `create`.
- [ ] Implement `Store` with immutable `owner`, `PERSONAL`/`BUSINESS`, public name/introduction, legal data, rejection reason, timestamps, and explicit state-transition methods. Implement active `OWNER`/`MANAGER` memberships.
- [ ] Write migration SQL to create store tables, one personal store plus owner membership per existing ordinary member, backfill `products.store_id`, backfill `orders.seller_id`, and create unique/index constraints. Guard legacy `ALTER TABLE` statements with `to_regclass` so a fresh database boots safely.
- [ ] Re-run the focused tests — expected PASS — then commit `feat: add store domain foundation`.

### Task 2: Personal Store Provisioning And Governance APIs

**Files:** `backend/src/main/java/com/sweet/market/store/application/{StoreProvisioningService,StoreAccessService,StoreGovernanceService,StoreQueryService}.java`, `backend/src/main/java/com/sweet/market/store/api/**`, `backend/src/main/java/com/sweet/market/store/admin/**`, `backend/src/main/java/com/sweet/market/auth/application/AuthService.java`, `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`, `backend/src/test/java/com/sweet/market/store/{StoreApiTest,admin/AdminBusinessStoreApiTest}.java`

- [ ] Write failing API tests: signup creates exactly one personal store; duplicate business application is rejected; rejection needs a reason; resubmission returns to pending; outsiders cannot mutate; managers may operate catalog commands but cannot edit legal data; public reads omit registration and review fields.
- [ ] Run: `cd backend; .\gradlew.bat test --tests "com.sweet.market.store.*"` — expected route failures.
- [ ] Implement provisioning in the signup transaction. Reuse an existing personal store if present, otherwise create the personal store and active owner membership.
- [ ] Implement `requireCatalogOperator` for active owner/manager catalog commands and `requireOwner` for profile and legal-data commands.
- [ ] Implement `GET /api/stores/me`, `PATCH /api/stores/{storeId}/profile`, `POST/PATCH /api/stores/business-applications`, `GET /api/stores/{storeId}`, and administrator list/detail/approve/reject/suspend/reactivate endpoints under `/api/admin/business-stores`.
- [ ] Run the two API tests — expected PASS — then commit `feat: add business store governance`.

### Task 3: Product Ownership And Historical Seller Compatibility

**Files:** `backend/src/main/java/com/sweet/market/product/{domain/Product.java,application/ProductService.java,query/ProductQueryService.java,repository/ProductRepository.java,api/{ProductController,ProductCreateRequest,ProductResponse,ProductSummaryResponse}.java}`, `backend/src/main/java/com/sweet/market/order/{domain/Order.java,application/OrderService.java,repository/OrderRepository.java,api/{OrderResponse,OrderSummaryResponse}.java}`, `backend/src/main/java/com/sweet/market/{cart,wishlist,refund,settlement,seller}/**/*.java`, `backend/src/test/java/com/sweet/market/product/{ProductApiTest,ProductSellerApiTest}.java`, `backend/src/test/java/com/sweet/market/{order/OrderApiTest,cart/CartApiTest,wishlist/WishlistApiTest,refund/RefundRequestApiTest,settlement/SettlementApiTest}.java`.

- [ ] Update product/order/cart/wishlist/refund/settlement tests with a personal-store fixture and add Korean-named tests proving an outsider cannot operate another store's product.
- [ ] Run focused regression tests for product, order, cart, wishlist, refund, and settlement; expected failures reference removed seller ownership.
- [ ] Replace `Product.seller` with required lazy `Product.store`; change creation to require `storeId`; authorize create/update/hide through `StoreAccessService`; forbid inactive business-store publication.
- [ ] Add required `Order.seller` snapshot populated from `product.getStore().getOwner()`. Change settlement, seller refund access, seller reports, and order query paths to use this snapshot where they represent historical seller data.
- [ ] Add `storeId`, `storeName`, and `storeType` to product and order DTOs. Keep `sellerId`/`sellerNickname` from the immutable store owner or order snapshot, never from an unrelated operator.
- [ ] Make public product lists exclude inactive business stores. Direct reads must expose a non-purchasable unavailable state; cart and order creation must revalidate it.
- [ ] Run: `cd backend; .\gradlew.bat test --tests "com.sweet.market.product.*" --tests "com.sweet.market.order.*" --tests "com.sweet.market.cart.*" --tests "com.sweet.market.wishlist.*" --tests "com.sweet.market.refund.*" --tests "com.sweet.market.settlement.*"` — expected PASS — then commit `feat: migrate products to store ownership`.

### Task 4: Query Shape And Public Store Profile

**Files:** `backend/src/main/java/com/sweet/market/store/api/PublicStoreResponse.java`, `backend/src/main/java/com/sweet/market/store/application/StoreQueryService.java`, `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`, `backend/src/test/java/com/sweet/market/{store/StoreApiTest,jpalab/ProductQueryOptimizationTest,product/api/ProductSummaryResponseTest}.java`

- [ ] Write failing tests for public-store privacy, active-store filtering, legacy seller/store consistency, and no per-row store lookup on a product page.
- [ ] Implement a projection or bounded join selecting only store id, public name, type, and status required by cards/details; do not fetch `Store.memberships` in catalog queries.
- [ ] Inspect SQL/query count for a full product page and add only indexes supporting membership authorization, owner/type uniqueness, store/status product lookup, and public store lookup.
- [ ] Run focused tests and commit `perf: bound store-aware product queries`.

### Task 5: Web Contracts, Routes, And Store Screens

**Files:** `web/src/features/stores/storeApi.ts`, `web/src/pages/{MyStorePage,StoreProfilePage,AdminBusinessStoresPage}.tsx`, `web/src/app/router.tsx`, `web/src/shared/layout/Shell.tsx`, `web/src/features/products/productApi.ts`, `web/src/pages/{HomePage,ProductDetailPage,ProductFormPage}.tsx`, `web/src/shared/styles.css`

- [ ] Add TypeScript contracts for public store, My Store, business application, business-store review, and the new product store fields.
- [ ] Add `/me/store`, `/stores/:storeId`, and `/admin/business-stores` routes using the existing auth/admin guards; add My Store navigation.
- [ ] Implement My Store public-profile editing, business application/rejection/resubmission, legal-data re-verification messaging, and explicit loading/error/pending states.
- [ ] Implement the public profile with name, verification/type signal, and introduction only. Implement the administrator list/detail with status filters, mandatory rejection-reason input, and mutation invalidation.
- [ ] Update product cards/details to show and link store identity. Add the active-store selector in create mode; preserve a product's store during edit mode; determine edit controls from My Store operable store ids rather than seller id equality.
- [ ] Run: `cd web; npm run build` — expected PASS — then commit `feat: add store governance screens`.

### Task 6: Full Verification And Handoff

**Files:** Create `docs/superpowers/handoffs/2026-07-10-milestone-21-store-foundation-and-business-seller-governance-handoff.md`

- [ ] Run backend verification with JDK 21 and `JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`: `cd backend; .\gradlew.bat test`.
- [ ] Run `cd web; npm run build`, then from the repository root run `git diff --check`.
- [ ] Manually verify: signup personal store; business apply/reject/resubmit/approve; active business-store product creation; buyer privacy; suspended-store purchase rejection; historical seller data in orders/refunds/settlements.
- [ ] Record migration behavior, routes, compatibility policy, commands/results, and M22 prerequisites in the handoff; commit `docs: add milestone 21 handoff`.
