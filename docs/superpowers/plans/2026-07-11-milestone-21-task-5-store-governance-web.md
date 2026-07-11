# Milestone 21 Task 5 Store Governance Web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 스토어 소유자·구매자·관리자가 스토어 프로필, 사업자 신청·심사, 상품의 스토어 소유권을 웹에서 안전하게 사용할 수 있게 한다.

**Architecture:** `features/stores/storeApi.ts`가 스토어 계약과 요청 함수의 단일 경계가 되고, My Store·사업자 신청·공개 프로필·관리자 심사 페이지가 이를 공유한다. 상품 UI는 `storeId`를 생성 계약과 수정 권한 기준으로 사용하며, 관리자 상태 필터만 정확한 페이지네이션을 위해 백엔드에 추가한다.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, JUnit 5, MockMvc, React 19, TypeScript 5.8, React Router 7, TanStack Query 5, React Hook Form, Vite 6.

## Global Constraints

- JUnit `@Test` 메서드명은 한국어_밑줄_형식으로 작성한다.
- `/me/store`는 개인·사업자 스토어를 한 화면에서 전환한다.
- 사업자 최초 신청과 반려 후 재신청은 `/me/store/business-application`에서 처리한다.
- Task 5 웹 권한은 `/api/stores/me`가 반환하는 owner 스토어만 사용하며 manager 웹 진입은 추가하지 않는다.
- 공개 스토어 화면은 공개 이름·유형/승인 신호·소개만 표시하고 상품 목록과 민감정보를 표시하지 않는다.
- 상품 생성은 active 스토어 카드에서 `storeId`를 선택하고, 수정은 기존 스토어를 변경하지 않는다.
- 관리자 상태 필터는 서버에서 처리하며 현재 페이지의 클라이언트 필터링으로 대체하지 않는다.
- Task 5는 manager 관리, storefront catalog, dashboard, bulk operation, inventory, promotion UI를 구현하지 않는다.
- Gradle은 JDK 21과 `JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`으로 실행한다.

---

## Target File Map

- Create `web/src/features/stores/storeApi.ts` — store types, query keys, owner/public/admin API functions.
- Create `web/src/pages/{MyStorePage,BusinessStoreApplicationPage,StoreProfilePage,AdminBusinessStoresPage}.tsx` — role-specific store flows.
- Modify `web/src/app/router.tsx`, `web/src/shared/layout/Shell.tsx` — guarded routes and navigation.
- Modify `web/src/features/products/productApi.ts`, `web/src/pages/{HomePage,ProductDetailPage,ProductFormPage}.tsx` — store identity, create selection, owner permission.
- Modify `web/src/shared/ui/ResourceStates.tsx`, `web/src/shared/styles.css` — store status labels and responsive layouts.
- Modify `backend/src/main/java/com/sweet/market/store/{admin,repository}/**` — optional server-side status filter and admin timestamps.
- Modify `backend/src/test/java/com/sweet/market/store/StoreApiTest.java` — status filtering and pagination contract.

### Task 1: Add Server-Side Administrator Store Status Filtering

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/store/admin/AdminBusinessStoreController.java`
- Modify: `backend/src/main/java/com/sweet/market/store/admin/AdminBusinessStoreQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/store/admin/AdminBusinessStoreResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/store/repository/StoreRepository.java`
- Test: `backend/src/test/java/com/sweet/market/store/StoreApiTest.java`

**Interfaces:**
- Produces: `search(StoreStatus status, Pageable pageable): Page<AdminBusinessStoreResponse>`.
- Produces: `GET /api/admin/business-stores?status=PENDING&page=0&size=20`.
- Produces: admin response `createdAt` and `updatedAt` ISO date-time fields.

- [ ] **Step 1: Write the failing filtered-page API test**

Add `관리자는_사업자_상점을_상태별로_페이지_조회한다()` that creates pending and active business stores, requests `status=PENDING&size=1`, and asserts one pending row, page metadata, `createdAt`, and `updatedAt`.

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.store.StoreApiTest'
```

Expected: FAIL because the controller ignores `status` and the response has no timestamps.

- [ ] **Step 3: Implement the exact backend contract**

Use a nullable request parameter and repository branches:

```java
public Page<AdminBusinessStoreResponse> search(StoreStatus status, Pageable pageable) {
    Page<Store> stores = status == null
            ? storeRepository.findAllByType(StoreType.BUSINESS, pageable)
            : storeRepository.findAllByTypeAndStatus(StoreType.BUSINESS, status, pageable);
    return stores.map(AdminBusinessStoreResponse::from);
}
```

Add `Page<Store> findAllByTypeAndStatus(StoreType type, StoreStatus status, Pageable pageable)` and map `store.getCreatedAt()`/`store.getUpdatedAt()` in the admin record. Pass `@RequestParam(required = false) StoreStatus status` from controller to service.

- [ ] **Step 4: Re-run and commit**

Run the Step 2 command; expected PASS. Then:

```powershell
git add backend/src/main/java/com/sweet/market/store backend/src/test/java/com/sweet/market/store/StoreApiTest.java
git commit -m "feat: filter business stores by status"
```

### Task 2: Create The Shared Store API Module

**Files:**
- Create: `web/src/features/stores/storeApi.ts`
- Modify: `web/src/features/products/productApi.ts`

**Interfaces:**
- Produces: `StoreType`, `StoreStatus`, `PrivateStore`, `PublicStore`, `AdminBusinessStore`, `BusinessApplicationInput`, `StoreProfileInput`.
- Produces: owner/public/admin request functions and `storeQueryKeys`.

- [ ] **Step 1: Define the backend-shaped contracts**

Implement unions and records with exact backend names:

```ts
export type StoreType = 'PERSONAL' | 'BUSINESS';
export type StoreStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'SUSPENDED';
export type PrivateStore = {
  storeId: number; type: StoreType; publicName: string; introduction: string;
  status: StoreStatus; legalBusinessName: string | null;
  businessRegistrationId: string | null; rejectionReason: string | null;
};
export type PublicStore = Pick<PrivateStore, 'storeId' | 'type' | 'publicName' | 'introduction'>;
```

Add admin owner/timestamp fields and input types matching `publicName`, `introduction`, `legalBusinessName`, and `businessRegistrationId`.

- [ ] **Step 2: Add request functions and stable query keys**

Implement `getMyStores`, `getPublicStore`, `applyBusinessStore`, `resubmitBusinessStore`, `updateStoreProfile`, `getAdminBusinessStores`, `getAdminBusinessStore`, `approveBusinessStore`, `rejectBusinessStore`, `suspendBusinessStore`, and `reactivateBusinessStore`. Encode `status`, `page`, and `size` with `URLSearchParams`.

- [ ] **Step 3: Align product response contracts**

Add `storeId`, `storeName`, `storeType` to `ProductSummary` and `purchasable` to `Product`. Keep seller compatibility fields. Defer the required `ProductCreateInput.storeId` change to Task 5 so every committed task remains buildable.

- [ ] **Step 4: Type-check and commit**

```powershell
cd web
npm run build
```

Expected: PASS. Commit:

```powershell
git add web/src/features/stores/storeApi.ts web/src/features/products/productApi.ts
git commit -m "feat: add store web contracts"
```

### Task 3: Implement My Store And Business Application

**Files:**
- Create: `web/src/pages/MyStorePage.tsx`
- Create: `web/src/pages/BusinessStoreApplicationPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/shared/layout/Shell.tsx`

**Interfaces:**
- Consumes: `getMyStores`, `updateStoreProfile`, `applyBusinessStore`, `resubmitBusinessStore`.
- Produces: authenticated `/me/store` and `/me/store/business-application` routes.

- [ ] **Step 1: Implement My Store state selection**

Query `storeQueryKeys.me()`, default to the personal store, and render an accessible button switcher when a business store exists. Keep selection by `storeId`; if refreshed data removes it, fall back to the personal store.

- [ ] **Step 2: Implement profile editing and lifecycle guidance**

Use React Hook Form for `publicName` and `introduction`, call `updateStoreProfile`, invalidate My Store and public profile, and render exact PENDING/REJECTED/ACTIVE/SUSPENDED guidance. Show rejection reason only in this owner view.

- [ ] **Step 3: Implement the separate application route**

No business store calls POST; rejected business store prefills private fields and calls PATCH. Pending/active/suspended states render read-only guidance and a back link. Validate limits 100/2000/120/40 to match backend records.

- [ ] **Step 4: Add routes/navigation and verify**

Wrap both routes in `RequireAuth`, add `내 상점` navigation, run `npm run build`, and manually verify the four lifecycle branches with typed fixture reasoning. Commit:

```powershell
git add web/src/pages/MyStorePage.tsx web/src/pages/BusinessStoreApplicationPage.tsx web/src/app/router.tsx web/src/shared/layout/Shell.tsx
git commit -m "feat: add my store workflows"
```

### Task 4: Add Public Store Identity To Buyer Product Surfaces

**Files:**
- Create: `web/src/pages/StoreProfilePage.tsx`
- Modify: `web/src/pages/HomePage.tsx`
- Modify: `web/src/pages/ProductDetailPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/shared/ui/ResourceStates.tsx`

**Interfaces:**
- Consumes: `getPublicStore(storeId)` and product store fields.
- Produces: public `/stores/:storeId` and clickable store identity.

- [ ] **Step 1: Implement the minimal public profile**

Parse a positive store id, query the public endpoint, and render only public name, introduction, and a PERSONAL/BUSINESS label. Use `ErrorState` for invalid ids and API failures.

- [ ] **Step 2: Link product cards and detail**

Replace seller-name-only presentation with a link to `/stores/${product.storeId}` and a compact store type badge. Preserve wishlist/cart props that still use seller compatibility ids.

- [ ] **Step 3: Extend status labels and verify privacy**

Add readable labels for PERSONAL, BUSINESS, PENDING, ACTIVE, and SUSPENDED without adding legal fields to the public page. Run `npm run build`; expected PASS. Commit:

```powershell
git add web/src/pages/StoreProfilePage.tsx web/src/pages/HomePage.tsx web/src/pages/ProductDetailPage.tsx web/src/app/router.tsx web/src/shared/ui/ResourceStates.tsx
git commit -m "feat: show public store identity"
```

### Task 5: Make Product Forms Store-Owned

**Files:**
- Modify: `web/src/pages/ProductFormPage.tsx`
- Modify: `web/src/features/products/productApi.ts`

**Interfaces:**
- Consumes: `getMyStores()` and product `storeId`.
- Produces: create payload with `storeId`; read-only store in edit mode; owner-store edit authorization.

- [ ] **Step 1: Load owner stores and derive operable ids**

Add a My Store query for authenticated users. Derive `activeStores = stores.filter(store => store.status === 'ACTIVE')` and `ownedStoreIds = new Set(stores.map(store => store.storeId))`.

- [ ] **Step 2: Replace legacy edit authorization**

Replace `member?.id !== product.sellerId` with `!ownedStoreIds.has(product.storeId)`. Delay the access verdict until both product and My Store queries finish; show an error if either fails.

- [ ] **Step 3: Render store cards and submit storeId**

In create mode render every owned store as a radio-style card. Disable non-active stores with status guidance. Default exactly one active store; require explicit selection when multiple exist. Add required `storeId: number` to `ProductCreateInput` and submit `storeId: selectedStoreId` only for create mode. Edit mode displays `product.storeName`/`storeType` read-only.

- [ ] **Step 4: Build and commit**

Run `npm run build`; expected PASS with no TypeScript errors. Commit:

```powershell
git add web/src/pages/ProductFormPage.tsx web/src/features/products/productApi.ts
git commit -m "feat: select product store ownership"
```

### Task 6: Implement Administrator Business Store Review

**Files:**
- Create: `web/src/pages/AdminBusinessStoresPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/shared/layout/Shell.tsx`

**Interfaces:**
- Consumes: server-filtered admin list/detail and governance mutations.
- Produces: guarded `/admin/business-stores` list-plus-detail workflow.

- [ ] **Step 1: Implement filter, pagination, and selection**

Keep optional `StoreStatus` filter and zero-based page in component state. Changing status resets page and selected id. Query list with filter/page/size 20 and detail only when an id is selected.

- [ ] **Step 2: Implement legal detail and valid actions**

Render owner id, private legal fields, public fields, status, rejection reason, created/updated timestamps. Show approve/reject only for PENDING, suspend only for ACTIVE, reactivate only for SUSPENDED, and no command for REJECTED.

- [ ] **Step 3: Implement rejection validation and invalidation**

Reject requires a trimmed nonblank reason. After a successful mutation, invalidate admin list/detail, `storeQueryKeys.me()`, and `storeQueryKeys.public(storeId)`; keep the selected detail visible.

- [ ] **Step 4: Guard route, add navigation, build, and commit**

Wrap the route with `RequireAdmin`, add `사업자 상점 심사` navigation, run `npm run build`, expected PASS, then commit:

```powershell
git add web/src/pages/AdminBusinessStoresPage.tsx web/src/app/router.tsx web/src/shared/layout/Shell.tsx
git commit -m "feat: add business store review screen"
```

### Task 7: Add Responsive Styling And Run The Task 5 Gate

**Files:**
- Modify: `web/src/shared/styles.css`
- Test: backend store suite and web production build.

- [ ] **Step 1: Add scoped responsive styles**

Add store switcher/cards, owner status panel, application form, public profile, admin list/detail, product store cards, disabled-state, and mobile stacked layouts. Reuse existing color variables, `page-panel`, `status-badge`, `text-button`, and resource-state patterns.

- [ ] **Step 2: Run backend and web verification**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.store.StoreApiTest'
cd ..\web
npm run build
cd ..
git diff --check
```

Expected: all commands succeed and `git diff --check` has no output.

- [ ] **Step 3: Manually verify route/state contracts**

Verify authenticated route guards, personal/business switching, first application, rejected resubmission, public privacy, admin status filtering/actions, product create store selection, edit store lock, and mobile admin stacking. Record results in the Task 5 implementation report or Task 6 handoff.

- [ ] **Step 4: Commit styling**

```powershell
git add web/src/shared/styles.css
git commit -m "style: add store governance layouts"
```

## Plan Self-Review

- Spec coverage: Task 1 adds accurate server filtering; Tasks 2-3 provide contracts and owner workflows; Task 4 covers public identity; Task 5 enforces store-owned product UI; Task 6 covers admin review; Task 7 covers responsive styling and the full gate.
- Scope: no manager membership UI, storefront catalog, dashboard, inventory, promotion, or bulk catalog action appears in the plan.
- Type consistency: `storeId`, `storeName`, `storeType`, `purchasable`, `StoreStatus`, and query-key names are consistent across tasks.
- Verification: backend behavior has MockMvc coverage; the repository has no frontend test runner, so TypeScript/Vite build and manual route-state checks are the web verification boundary.
