# Milestone 20 Buyer Refund History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a buyer refund history page backed by a buyer-scoped paginated refund request API, while moving seller refund operations to a sales-scoped route.

**Architecture:** Reuse the existing refund package and shared `RefundRequestResponse`, extending it with seller context. Add one buyer-scoped read endpoint, keep seller/admin mutation behavior unchanged, and add a focused React page that consumes the same refund API module used by seller/admin refund screens.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Spring Security, JUnit 5, MockMvc, React, TypeScript, TanStack Query, Vite.

---

## Spec And Safety Notes

Read before implementation:

- `docs/superpowers/specs/2026-07-09-milestone-20-buyer-refund-history-design.md`
- `docs/superpowers/specs/2026-07-08-milestone-19-refund-operations-management-design.md`
- `docs/superpowers/specs/2026-07-07-milestone-18-cancellation-and-refund-flow-design.md`
- `AGENTS.md`

Use an isolated worktree before implementation work:

```powershell
cd C:\dev\jpa-study
git worktree add .worktrees\milestone-20-buyer-refund-history -b codex/milestone-20-buyer-refund-history main
cd .worktrees\milestone-20-buyer-refund-history
```

Do not stage, overwrite, reset, or discard `backend/src/main/resources/application.yaml` in the main checkout.

All new JUnit `@Test` method names must be Korean with underscores.

Backend commands should set `JWT_SECRET`:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
```

If `C:\java\jdk-21` is unavailable in this environment, remove `JAVA_HOME` and let Gradle toolchain resolution provide JDK 21:

```powershell
Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
```

## File Structure

Backend files to create:

- `backend/src/main/java/com/sweet/market/refund/api/BuyerRefundRequestController.java`: authenticated buyer refund list endpoint.

Backend files to modify:

- `backend/src/main/java/com/sweet/market/refund/api/RefundRequestResponse.java`: add `sellerId` and `sellerNickname`.
- `backend/src/main/java/com/sweet/market/refund/repository/RefundRequestRepository.java`: add buyer-scoped paginated query.
- `backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java`: expose buyer-scoped read service method.
- `backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java`: add buyer list contract tests and seller response assertions.

Web files to create:

- `web/src/pages/MyRefundHistoryPage.tsx`: buyer refund history page.

Web files to move:

- `web/src/pages/MyRefundRequestsPage.tsx` -> `web/src/pages/SellerRefundRequestsPage.tsx`: clarify that this page is seller operations.

Web files to modify:

- `web/src/features/refunds/refundApi.ts`: add seller fields and buyer API function.
- `web/src/app/router.tsx`: add buyer route and move seller route.
- `web/src/shared/layout/Shell.tsx`: split buyer and seller navigation labels.

Documentation files to create:

- `docs/superpowers/handoffs/2026-07-09-milestone-20-buyer-refund-history-handoff.md`: final implementation handoff.

## Task 1: Backend Contract Tests For Buyer Refund History

**Files:**

- Modify: `backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java`

- [ ] **Step 1: Add seller fields to the refund creation response test**

In `구매자는_배송완료_주문에_환불을_요청할_수_있다`, add these assertions after the existing `buyerNickname` assertion:

```java
.andExpect(jsonPath("$.data.sellerId").isNumber())
.andExpect(jsonPath("$.data.sellerNickname").value("seller"))
```

- [ ] **Step 2: Add seller field expectations to the seller list test**

In `판매자는_자신의_상품_환불_요청_목록만_페이지로_조회할_수_있다`, add these assertions after the existing `buyerNickname` assertion:

```java
.andExpect(jsonPath("$.data.content[0].sellerId").isNumber())
.andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"))
```

- [ ] **Step 3: Add seller field expectations to the admin list test**

In `관리자는_모든_환불_요청_목록을_페이지로_조회할_수_있다`, add these assertions near the existing product and buyer nickname assertions:

```java
.andExpect(jsonPath("$.data.content[0].sellerNickname").value("otherSeller"))
.andExpect(jsonPath("$.data.content[1].sellerNickname").value("seller"))
```

- [ ] **Step 4: Add buyer-scoped list test**

Add this test near the seller/admin list tests:

```java
@Test
void 구매자는_자신의_환불_요청_목록만_페이지로_조회할_수_있다() throws Exception {
    String sellerToken = signupAndLogin("seller-buyer-list@example.com", "password123", "seller");
    String otherSellerToken = signupAndLogin("other-seller-buyer-list@example.com", "password123", "otherSeller");
    String buyerToken = signupAndLogin("buyer-refund-list@example.com", "password123", "buyer");
    String otherBuyerToken = signupAndLogin("other-buyer-refund-list@example.com", "password123", "otherBuyer");
    Long productId = createProduct(sellerToken, "MacBook Pro");
    Long otherProductId = createProduct(otherSellerToken, "iPad Pro");
    Long orderId = createDeliveredOrder(buyerToken, productId);
    Long otherOrderId = createDeliveredOrder(otherBuyerToken, otherProductId);
    Long refundRequestId = createRefundRequest(buyerToken, orderId);
    createRefundRequest(otherBuyerToken, otherOrderId);

    mockMvc.perform(get("/api/refund-requests/me")
                    .param("page", "0")
                    .param("size", "10")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(refundRequestId))
            .andExpect(jsonPath("$.data.content[0].orderId").value(orderId))
            .andExpect(jsonPath("$.data.content[0].productId").value(productId))
            .andExpect(jsonPath("$.data.content[0].productTitle").value("MacBook Pro"))
            .andExpect(jsonPath("$.data.content[0].buyerId").isNumber())
            .andExpect(jsonPath("$.data.content[0].buyerNickname").value("buyer"))
            .andExpect(jsonPath("$.data.content[0].sellerId").isNumber())
            .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"))
            .andExpect(jsonPath("$.data.content[0].status").value("REQUESTED"))
            .andExpect(jsonPath("$.data.content[0].reason").value("상품 상태가 설명과 달라 환불을 요청합니다."))
            .andExpect(jsonPath("$.data.content[0].requestedAt").exists())
            .andExpect(jsonPath("$.data.content[0].handledById").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].handledAt").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].rejectReason").doesNotExist())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.number").value(0))
            .andExpect(jsonPath("$.data.size").value(10));
}
```

- [ ] **Step 5: Add buyer status filtering test**

Add this test near the buyer list test:

```java
@Test
void 구매자는_환불_요청_상태로_내역을_필터링할_수_있다() throws Exception {
    String sellerToken = signupAndLogin("seller-buyer-filter@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-refund-filter@example.com", "password123", "buyer");
    Long requestedProductId = createProduct(sellerToken, "Requested Product");
    Long approvedProductId = createProduct(sellerToken, "Approved Product");
    Long rejectedProductId = createProduct(sellerToken, "Rejected Product");
    Long requestedOrderId = createDeliveredOrder(buyerToken, requestedProductId);
    Long approvedOrderId = createDeliveredOrder(buyerToken, approvedProductId);
    Long rejectedOrderId = createDeliveredOrder(buyerToken, rejectedProductId);
    Long requestedRefundRequestId = createRefundRequest(buyerToken, requestedOrderId);
    Long approvedRefundRequestId = createRefundRequest(buyerToken, approvedOrderId);
    Long rejectedRefundRequestId = createRefundRequest(buyerToken, rejectedOrderId);

    approveRefundRequest(sellerToken, approvedRefundRequestId);
    rejectRefundRequest(sellerToken, rejectedRefundRequestId);

    mockMvc.perform(get("/api/refund-requests/me")
                    .param("status", "REQUESTED")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(requestedRefundRequestId))
            .andExpect(jsonPath("$.data.content[0].status").value("REQUESTED"));

    mockMvc.perform(get("/api/refund-requests/me")
                    .param("status", "APPROVED")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(approvedRefundRequestId))
            .andExpect(jsonPath("$.data.content[0].status").value("APPROVED"))
            .andExpect(jsonPath("$.data.content[0].handledById").isNumber())
            .andExpect(jsonPath("$.data.content[0].handledAt").exists());

    mockMvc.perform(get("/api/refund-requests/me")
                    .param("status", "REJECTED")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(rejectedRefundRequestId))
            .andExpect(jsonPath("$.data.content[0].status").value("REJECTED"))
            .andExpect(jsonPath("$.data.content[0].handledById").isNumber())
            .andExpect(jsonPath("$.data.content[0].handledAt").exists())
            .andExpect(jsonPath("$.data.content[0].rejectReason").value("상품 설명과 다른 부분을 확인할 수 없습니다."));
}
```

- [ ] **Step 6: Add buyer all-status pagination and ordering test**

Add this test near the buyer filtering test:

```java
@Test
void 구매자는_상태_없이_전체_환불_내역을_최신순으로_페이지_조회한다() throws Exception {
    String sellerToken = signupAndLogin("seller-buyer-page-order@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-refund-page-order@example.com", "password123", "buyer");
    Long firstProductId = createProduct(sellerToken, "First Product");
    Long secondProductId = createProduct(sellerToken, "Second Product");
    Long thirdProductId = createProduct(sellerToken, "Third Product");
    Long firstOrderId = createDeliveredOrder(buyerToken, firstProductId);
    Long secondOrderId = createDeliveredOrder(buyerToken, secondProductId);
    Long thirdOrderId = createDeliveredOrder(buyerToken, thirdProductId);
    Long firstRefundRequestId = createRefundRequest(buyerToken, firstOrderId);
    Long secondRefundRequestId = createRefundRequest(buyerToken, secondOrderId);
    Long thirdRefundRequestId = createRefundRequest(buyerToken, thirdOrderId);

    approveRefundRequest(sellerToken, secondRefundRequestId);
    rejectRefundRequest(sellerToken, thirdRefundRequestId);

    mockMvc.perform(get("/api/refund-requests/me")
                    .param("page", "0")
                    .param("size", "2")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.content[0].id").value(thirdRefundRequestId))
            .andExpect(jsonPath("$.data.content[1].id").value(secondRefundRequestId))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.number").value(0))
            .andExpect(jsonPath("$.data.size").value(2));

    mockMvc.perform(get("/api/refund-requests/me")
                    .param("page", "1")
                    .param("size", "2")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(firstRefundRequestId))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.number").value(1))
            .andExpect(jsonPath("$.data.size").value(2));
}
```

- [ ] **Step 7: Add authentication test for buyer refund history**

Add this test near the buyer list tests:

```java
@Test
void 환불_내역_조회는_인증_토큰이_필요하다() throws Exception {
    mockMvc.perform(get("/api/refund-requests/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
}
```

- [ ] **Step 8: Run refund API tests and verify failure**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "*RefundRequestApiTest"
```

Expected: FAIL because `sellerId`, `sellerNickname`, and `GET /api/refund-requests/me` do not exist yet.

- [ ] **Step 9: Commit failing contract tests**

```powershell
git add backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java
git commit -m "test: define buyer refund history contract"
```

## Task 2: Backend Buyer Refund History API

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/refund/api/RefundRequestResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/refund/repository/RefundRequestRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java`
- Create: `backend/src/main/java/com/sweet/market/refund/api/BuyerRefundRequestController.java`

- [ ] **Step 1: Add seller fields to `RefundRequestResponse`**

Replace the record with this version:

```java
package com.sweet.market.refund.api;

import java.time.LocalDateTime;

import com.sweet.market.refund.domain.RefundRequest;

public record RefundRequestResponse(
        Long id,
        Long orderId,
        Long productId,
        String productTitle,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        String reason,
        String status,
        LocalDateTime requestedAt,
        Long handledById,
        LocalDateTime handledAt,
        String rejectReason
) {

    public static RefundRequestResponse from(RefundRequest refundRequest) {
        return new RefundRequestResponse(
                refundRequest.getId(),
                refundRequest.getOrder().getId(),
                refundRequest.getOrder().getProduct().getId(),
                refundRequest.getOrder().getProduct().getTitle(),
                refundRequest.getBuyer().getId(),
                refundRequest.getBuyer().getNickname(),
                refundRequest.getOrder().getProduct().getSeller().getId(),
                refundRequest.getOrder().getProduct().getSeller().getNickname(),
                refundRequest.getReason(),
                refundRequest.getStatus().name(),
                refundRequest.getRequestedAt(),
                refundRequest.getHandledBy() == null ? null : refundRequest.getHandledBy().getId(),
                refundRequest.getHandledAt(),
                refundRequest.getRejectReason()
        );
    }
}
```

- [ ] **Step 2: Add buyer query to `RefundRequestRepository`**

Add this method after `findWithOrderByOrderId` and before `findSellerRequests`:

```java
@EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "buyer", "handledBy"})
@Query(
        value = """
                select r
                from RefundRequest r
                where r.buyer.id = :buyerId
                  and (:status is null or r.status = :status)
                order by r.requestedAt desc, r.id desc
                """,
        countQuery = """
                select count(r)
                from RefundRequest r
                where r.buyer.id = :buyerId
                  and (:status is null or r.status = :status)
                """
)
Page<RefundRequest> findBuyerRequests(
        @Param("buyerId") Long buyerId,
        @Param("status") RefundRequestStatus status,
        Pageable pageable
);
```

- [ ] **Step 3: Add buyer service method**

In `RefundRequestService`, add this method before `findSellerRequests`:

```java
@Transactional(readOnly = true)
public Page<RefundRequestResponse> findBuyerRequests(Long buyerId, RefundRequestStatus status, Pageable pageable) {
    return refundRequestRepository.findBuyerRequests(buyerId, status, pageable)
            .map(RefundRequestResponse::from);
}
```

- [ ] **Step 4: Add buyer controller**

Create `backend/src/main/java/com/sweet/market/refund/api/BuyerRefundRequestController.java`:

```java
package com.sweet.market.refund.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.refund.application.RefundRequestService;
import com.sweet.market.refund.domain.RefundRequestStatus;

@RestController
@RequestMapping("/api/refund-requests")
public class BuyerRefundRequestController {

    private final RefundRequestService refundRequestService;

    public BuyerRefundRequestController(RefundRequestService refundRequestService) {
        this.refundRequestService = refundRequestService;
    }

    @GetMapping("/me")
    public ApiResponse<Page<RefundRequestResponse>> listMine(
            Authentication authentication,
            @RequestParam(required = false) RefundRequestStatus status,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.findBuyerRequests(member.id(), status, pageable));
    }
}
```

- [ ] **Step 5: Run refund API tests**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "*RefundRequestApiTest"
```

Expected: PASS.

- [ ] **Step 6: Commit backend API implementation**

```powershell
git add backend/src/main/java/com/sweet/market/refund/api/RefundRequestResponse.java backend/src/main/java/com/sweet/market/refund/repository/RefundRequestRepository.java backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java backend/src/main/java/com/sweet/market/refund/api/BuyerRefundRequestController.java
git commit -m "feat: add buyer refund history api"
```

## Task 3: Web Refund API And Seller Route Move

**Files:**

- Modify: `web/src/features/refunds/refundApi.ts`
- Move: `web/src/pages/MyRefundRequestsPage.tsx` -> `web/src/pages/SellerRefundRequestsPage.tsx`
- Modify: `web/src/pages/SellerRefundRequestsPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/shared/layout/Shell.tsx`

- [ ] **Step 1: Extend refund API types and add buyer API function**

In `web/src/features/refunds/refundApi.ts`, add seller fields after `buyerNickname`:

```ts
  sellerId: number;
  sellerNickname: string;
```

Then add this function after `getAdminRefundRequests`:

```ts
export function getMyRefundRequests(input: RefundRequestSearchInput) {
  return api<RefundRequestPage>(`/api/refund-requests/me?${buildRefundRequestSearchParams(input)}`);
}
```

- [ ] **Step 2: Move and rename the seller page file**

Run:

```powershell
git mv web/src/pages/MyRefundRequestsPage.tsx web/src/pages/SellerRefundRequestsPage.tsx
```

In `web/src/pages/SellerRefundRequestsPage.tsx`, change:

```ts
export function MyRefundRequestsPage() {
```

to:

```ts
export function SellerRefundRequestsPage() {
```

Also change the page title and subtitle from:

```tsx
<h1>환불 요청 관리</h1>
<p>판매 상품의 환불 요청을 검토하고 승인 또는 거절 처리합니다.</p>
```

to:

```tsx
<h1>판매 환불 관리</h1>
<p>내 판매 상품의 환불 요청을 검토하고 승인 또는 거절 처리합니다.</p>
```

- [ ] **Step 3: Update router imports and seller route**

In `web/src/app/router.tsx`, replace:

```ts
import { MyRefundRequestsPage } from '../pages/MyRefundRequestsPage';
```

with:

```ts
import { SellerRefundRequestsPage } from '../pages/SellerRefundRequestsPage';
```

Replace the seller route:

```tsx
<Route
  path="me/refunds"
  element={
    <RequireAuth>
      <MyRefundRequestsPage />
    </RequireAuth>
  }
/>
```

with:

```tsx
<Route
  path="me/sales/refunds"
  element={
    <RequireAuth>
      <SellerRefundRequestsPage />
    </RequireAuth>
  }
/>
```

- [ ] **Step 4: Update shell navigation labels and links**

In `web/src/shared/layout/Shell.tsx`, replace:

```tsx
<NavLink to="/me/refunds">환불 관리</NavLink>
```

with:

```tsx
<NavLink to="/me/sales/refunds">판매 환불 관리</NavLink>
```

- [ ] **Step 5: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit web API and seller route move**

```powershell
git add web/src/features/refunds/refundApi.ts web/src/pages/SellerRefundRequestsPage.tsx web/src/app/router.tsx web/src/shared/layout/Shell.tsx
git add -u web/src/pages/MyRefundRequestsPage.tsx
git commit -m "feat: move seller refund operations route"
```

## Task 4: Buyer Refund History Page

**Files:**

- Create: `web/src/pages/MyRefundHistoryPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/shared/layout/Shell.tsx`

- [ ] **Step 1: Create buyer refund history page**

Create `web/src/pages/MyRefundHistoryPage.tsx`:

```tsx
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  getMyRefundRequests,
  type RefundRequest,
  type RefundRequestPage,
  type RefundRequestSearchInput,
  type RefundRequestStatusFilter,
} from '../features/refunds/refundApi';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const REFUND_REQUEST_PAGE_SIZE = 10;

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

const statusFilters: RefundRequestStatusFilter[] = ['ALL', 'REQUESTED', 'APPROVED', 'REJECTED'];

export function MyRefundHistoryPage() {
  const [searchInput, setSearchInput] = useState<RefundRequestSearchInput>({
    status: 'ALL',
    page: 0,
    size: REFUND_REQUEST_PAGE_SIZE,
  });

  const refundRequestsQuery = useQuery({
    queryKey: ['refund-history', 'buyer', searchInput],
    queryFn: () => getMyRefundRequests(searchInput),
  });

  const refundRequests = refundRequestsQuery.data?.content ?? [];
  const shouldShowPagination = Boolean(refundRequestsQuery.data && refundRequestsQuery.data.totalElements > 0);

  function changeStatusFilter(status: RefundRequestStatusFilter) {
    setSearchInput({
      status,
      page: 0,
      size: REFUND_REQUEST_PAGE_SIZE,
    });
  }

  function movePage(page: number) {
    setSearchInput((current) => ({ ...current, page }));
  }

  return (
    <section className="refund-operations-page">
      <div className="list-page-header">
        <h1>환불 내역</h1>
        <p>내가 요청한 환불의 접수 상태와 처리 결과를 확인합니다.</p>
      </div>

      <div className="admin-panel-heading-row">
        <div>
          <h2>내 환불 요청</h2>
          <p className="status-text">전체 내역을 기본으로 보고 상태별로 좁혀볼 수 있습니다.</p>
        </div>
        <span className="admin-execution-meta">페이지당 {REFUND_REQUEST_PAGE_SIZE}건</span>
      </div>

      {renderStatusFilter(searchInput.status, changeStatusFilter)}

      {refundRequestsQuery.isLoading ? <p className="status-text">환불 내역을 불러오고 있습니다.</p> : null}
      {refundRequestsQuery.error ? <ErrorState message="환불 내역을 불러오지 못했습니다." /> : null}
      {!refundRequestsQuery.isLoading && !refundRequestsQuery.error && refundRequests.length === 0 ? (
        <EmptyState title="환불 내역이 없습니다" description="배송 완료 주문에서 환불을 요청하면 이곳에서 처리 상태를 확인할 수 있습니다." />
      ) : null}

      {refundRequests.length > 0 ? (
        <div className="refund-operations-table" aria-label="내 환불 요청 목록">
          <div className="refund-operations-table-head refund-operations-grid" role="row">
            <span>환불 요청</span>
            <span>상품</span>
            <span>판매자</span>
            <span>상태</span>
            <span>요청일</span>
            <span>이동</span>
          </div>
          {refundRequests.map((refundRequest) => renderRefundHistoryRow(refundRequest))}
        </div>
      ) : null}

      {shouldShowPagination && refundRequestsQuery.data ? (
        renderPagination(refundRequestsQuery.data, refundRequestsQuery.isFetching, movePage)
      ) : null}
    </section>
  );
}

function renderStatusFilter(
  selectedStatus: RefundRequestStatusFilter,
  onChangeStatus: (status: RefundRequestStatusFilter) => void,
) {
  return (
    <div className="admin-search-form" aria-label="환불 내역 상태 필터">
      <label>
        상태
        <select value={selectedStatus} onChange={(event) => onChangeStatus(event.target.value as RefundRequestStatusFilter)}>
          {statusFilters.map((status) => (
            <option value={status} key={status}>
              {formatStatusFilter(status)}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}

function renderRefundHistoryRow(refundRequest: RefundRequest) {
  return (
    <article className="refund-operations-row" key={refundRequest.id}>
      <div className="refund-operations-grid">
        <span>#{refundRequest.id}</span>
        <span>
          #{refundRequest.productId} {refundRequest.productTitle}
        </span>
        <span>
          #{refundRequest.sellerId} {refundRequest.sellerNickname}
        </span>
        <span>
          <StatusBadge status={refundRequest.status} />
        </span>
        <span>{formatDate(refundRequest.requestedAt)}</span>
        <span>
          <Link className="text-button secondary-button" to={`/products/${refundRequest.productId}`}>
            상품
          </Link>
          <Link className="text-button secondary-button" to="/me/orders">
            주문
          </Link>
        </span>
      </div>
      <dl className="refund-operation-detail">
        <div>
          <dt>주문 번호</dt>
          <dd>#{refundRequest.orderId}</dd>
        </div>
        <div>
          <dt>환불 사유</dt>
          <dd>{refundRequest.reason}</dd>
        </div>
        <div>
          <dt>처리자</dt>
          <dd>{formatNullableNumber(refundRequest.handledById)}</dd>
        </div>
        <div>
          <dt>처리일시</dt>
          <dd>{formatDate(refundRequest.handledAt)}</dd>
        </div>
        <div>
          <dt>거절 사유</dt>
          <dd>{refundRequest.rejectReason ?? '-'}</dd>
        </div>
      </dl>
    </article>
  );
}

function renderPagination(data: RefundRequestPage, isFetching: boolean, onMovePage: (page: number) => void) {
  const currentPage = data.number;
  const totalPages = data.totalPages;
  const displayTotalPages = Math.max(totalPages, 1);
  const displayPage = Math.min(currentPage + 1, displayTotalPages);
  const hasPreviousPage = currentPage > 0;
  const hasNextPage = totalPages > currentPage + 1;

  return (
    <div className="admin-pagination">
      <button
        type="button"
        className="text-button"
        disabled={!hasPreviousPage || isFetching}
        onClick={() => onMovePage(currentPage - 1)}
      >
        이전
      </button>
      <span>
        {displayPage} / {displayTotalPages}
      </span>
      <button
        type="button"
        className="text-button"
        disabled={!hasNextPage || isFetching}
        onClick={() => onMovePage(currentPage + 1)}
      >
        다음
      </button>
    </div>
  );
}

function formatStatusFilter(status: RefundRequestStatusFilter) {
  switch (status) {
    case 'ALL':
      return '전체';
    case 'REQUESTED':
      return '요청';
    case 'APPROVED':
      return '승인';
    case 'REJECTED':
      return '거절';
  }
}

function formatNullableNumber(value: number | null) {
  return value === null ? '-' : `#${value}`;
}

function formatDate(value: string | null | undefined) {
  if (!value) {
    return '-';
  }

  return dateFormatter.format(new Date(value));
}
```

- [ ] **Step 2: Add buyer route to router**

In `web/src/app/router.tsx`, add this import:

```ts
import { MyRefundHistoryPage } from '../pages/MyRefundHistoryPage';
```

Add this route after the `/me/orders` route:

```tsx
<Route
  path="me/refunds"
  element={
    <RequireAuth>
      <MyRefundHistoryPage />
    </RequireAuth>
  }
/>
```

- [ ] **Step 3: Add buyer refund history navigation**

In `web/src/shared/layout/Shell.tsx`, add the buyer link after `내 주문` and before `내 판매`:

```tsx
<NavLink to="/me/refunds">환불 내역</NavLink>
```

The signed-in navigation order should include both buyer and seller refund surfaces:

```tsx
<NavLink to="/me/orders">내 주문</NavLink>
<NavLink to="/me/refunds">환불 내역</NavLink>
<NavLink to="/me/sales">내 판매</NavLink>
<NavLink to="/me/settlements">정산</NavLink>
<NavLink to="/me/reports">리포트</NavLink>
<NavLink to="/me/sales/refunds">판매 환불 관리</NavLink>
```

- [ ] **Step 4: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 5: Commit buyer refund history page**

```powershell
git add web/src/pages/MyRefundHistoryPage.tsx web/src/app/router.tsx web/src/shared/layout/Shell.tsx
git commit -m "feat: add buyer refund history page"
```

## Task 5: Verification And Handoff

**Files:**

- Create: `docs/superpowers/handoffs/2026-07-09-milestone-20-buyer-refund-history-handoff.md`

- [ ] **Step 1: Run backend full test suite**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```

Expected: PASS.

- [ ] **Step 2: Run web production build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 3: Run repository hygiene check**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 4: Write handoff**

Create `docs/superpowers/handoffs/2026-07-09-milestone-20-buyer-refund-history-handoff.md`:

```markdown
# Milestone 20 Buyer Refund History Handoff

## Completed

- Added a buyer-scoped paginated refund request list API at `GET /api/refund-requests/me`.
- Returned only refund requests created by the authenticated buyer.
- Added optional status filtering for requested, approved, rejected, and all-status buyer views.
- Added `sellerId` and `sellerNickname` to refund request responses.
- Added buyer refund history page at `/me/refunds`.
- Moved seller refund operations from `/me/refunds` to `/me/sales/refunds`.
- Kept admin refund operations at `/admin/refunds`.
- Preserved My Orders refund request and refund status behavior.
- Added product and order navigation from buyer refund history rows.
- Added backend tests for buyer scoping, pagination, filtering, ordering, authentication, and response fields.

## Verification

- Backend full suite passed:
  - `cd backend`
  - `$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`
  - `.\gradlew.bat --no-daemon test`
- Web build passed:
  - `cd web`
  - `npm run build`
- Repo hygiene passed:
  - `git diff --check`

## Local Notes

- Work was done in `C:\dev\jpa-study\.worktrees\milestone-20-buyer-refund-history`.
- Branch: `codex/milestone-20-buyer-refund-history`.
- The main checkout's local-only `backend/src/main/resources/application.yaml` change was not touched.

## Follow-Up Candidates

- Dedicated refund detail pages.
- Buyer refund cancellation.
- Buyer refund edit or reopen flow.
- Return shipping workflow.
- Refund evidence upload.
- Refund-related review rules.
```

- [ ] **Step 5: Commit handoff**

```powershell
git add docs/superpowers/handoffs/2026-07-09-milestone-20-buyer-refund-history-handoff.md
git commit -m "docs: add milestone 20 handoff"
```

- [ ] **Step 6: Review final state**

Run:

```powershell
git status --short --branch --untracked-files=all
git log --oneline --decorate -n 12
```

Expected: the implementation worktree is clean and the latest commits are the Milestone 20 implementation commits.

## Self-Review Checklist

- Spec coverage: Tasks cover buyer-scoped API, all-status default, optional status filters, pagination, newest-first ordering, seller response fields, `/me/refunds` buyer route, `/me/sales/refunds` seller route, My Orders preservation, links to product and orders, backend tests, web build, and handoff.
- Scope control: Detail pages, refund cancellation, refund editing, return shipping, partial refund, payment gateway integration, evidence upload, dispute mediation, product relisting, and review policy changes stay outside the plan.
- Type consistency: Backend uses nullable `RefundRequestStatus`; frontend uses `RefundRequestStatusFilter` with frontend-only `ALL`; `sellerId` and `sellerNickname` are added to both backend response and frontend `RefundRequest`.
- Route consistency: Buyer history owns `/me/refunds`; seller operations move to `/me/sales/refunds`; admin remains `/admin/refunds`.
- Local safety: No step stages or edits `backend/src/main/resources/application.yaml` in the main checkout.
