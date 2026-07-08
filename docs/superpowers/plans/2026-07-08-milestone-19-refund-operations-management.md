# Milestone 19 Refund Operations Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build dedicated seller and admin refund operation screens backed by paginated refund request APIs.

**Architecture:** Keep the Milestone 18 refund domain and mutation endpoints intact. Convert only the seller/admin list reads to Spring `Page` responses, add `buyerNickname` to the refund response DTO, then add focused React pages that share the same operation workflow while calling role-specific API functions.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Spring Security, JUnit 5, MockMvc, React, TypeScript, TanStack Query, Vite.

---

## Spec And Safety Notes

Read before implementation:

- `docs/superpowers/specs/2026-07-08-milestone-19-refund-operations-management-design.md`
- `docs/superpowers/specs/2026-07-07-milestone-18-cancellation-and-refund-flow-design.md`
- `AGENTS.md`

Work in the isolated worktree:

```powershell
cd C:\dev\jpa-study\.worktrees\milestone-19-refund-operations-management
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

Backend files to modify:

- `backend/src/main/java/com/sweet/market/refund/api/RefundRequestResponse.java`: add `buyerNickname` to API responses.
- `backend/src/main/java/com/sweet/market/refund/repository/RefundRequestRepository.java`: convert seller/admin list queries to `Page` with `Pageable`.
- `backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java`: return mapped `Page<RefundRequestResponse>` for seller/admin list reads.
- `backend/src/main/java/com/sweet/market/refund/api/SellerRefundRequestController.java`: accept `Pageable`, return `ApiResponse<Page<RefundRequestResponse>>`.
- `backend/src/main/java/com/sweet/market/refund/api/AdminRefundRequestController.java`: accept `Pageable`, return `ApiResponse<Page<RefundRequestResponse>>`.
- `backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java`: update list assertions and add pagination/filtering/response-field tests.

Web files to create:

- `web/src/features/refunds/refundApi.ts`: refund request types and seller/admin API functions.
- `web/src/pages/MyRefundRequestsPage.tsx`: seller refund operation page.
- `web/src/pages/AdminRefundRequestsPage.tsx`: admin refund operation page.

Web files to modify:

- `web/src/app/router.tsx`: add `/me/refunds` and `/admin/refunds` routes.
- `web/src/shared/layout/Shell.tsx`: add navigation links for seller refund operations and admin refund operations.
- `web/src/shared/ui/ResourceStates.tsx`: add `REQUESTED` and `REJECTED` labels to shared `StatusBadge`.
- `web/src/shared/styles.css`: add refund operation grid, filter, action, and rejection form styles.

Documentation files to create:

- `docs/superpowers/handoffs/2026-07-08-milestone-19-refund-operations-management-handoff.md`: final handoff after implementation and verification.

## Task 1: Backend Contract Tests For Paginated Refund Lists

**Files:**

- Modify: `backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java`

- [ ] **Step 1: Add buyer nickname expectation to the create response test**

In `구매자는_배송완료_주문에_환불을_요청할_수_있다`, add this assertion after the existing `buyerId` assertion:

```java
.andExpect(jsonPath("$.data.buyerNickname").value("buyer"))
```

- [ ] **Step 2: Replace the seller list test with a paginated response assertion**

Replace the full `판매자는_자신의_상품_환불_요청_목록만_조회할_수_있다` test with:

```java
@Test
void 판매자는_자신의_상품_환불_요청_목록만_페이지로_조회할_수_있다() throws Exception {
    String sellerToken = signupAndLogin("seller-list@example.com", "password123", "seller");
    String otherSellerToken = signupAndLogin("other-seller-list@example.com", "password123", "otherSeller");
    String buyerToken = signupAndLogin("buyer-list@example.com", "password123", "buyer");
    String otherBuyerToken = signupAndLogin("other-buyer-list@example.com", "password123", "otherBuyer");
    Long productId = createProduct(sellerToken, "MacBook Pro");
    Long otherProductId = createProduct(otherSellerToken, "iPad Pro");
    Long orderId = createDeliveredOrder(buyerToken, productId);
    Long otherOrderId = createDeliveredOrder(otherBuyerToken, otherProductId);
    Long refundRequestId = createRefundRequest(buyerToken, orderId);
    createRefundRequest(otherBuyerToken, otherOrderId);

    mockMvc.perform(get("/api/seller/refund-requests")
                    .param("status", "REQUESTED")
                    .param("page", "0")
                    .param("size", "10")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(refundRequestId))
            .andExpect(jsonPath("$.data.content[0].orderId").value(orderId))
            .andExpect(jsonPath("$.data.content[0].productId").value(productId))
            .andExpect(jsonPath("$.data.content[0].productTitle").value("MacBook Pro"))
            .andExpect(jsonPath("$.data.content[0].buyerId").isNumber())
            .andExpect(jsonPath("$.data.content[0].buyerNickname").value("buyer"))
            .andExpect(jsonPath("$.data.content[0].status").value("REQUESTED"))
            .andExpect(jsonPath("$.data.content[0].reason").value("상품 상태가 설명과 달라 환불을 요청합니다."))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.number").value(0))
            .andExpect(jsonPath("$.data.size").value(10));
}
```

- [ ] **Step 3: Replace the admin list test with a paginated response assertion**

Replace the full `관리자는_모든_환불_요청_목록을_조회할_수_있다` test with:

```java
@Test
void 관리자는_모든_환불_요청_목록을_페이지로_조회할_수_있다() throws Exception {
    String adminToken = createAdminToken("admin-list@example.com");
    String sellerToken = signupAndLogin("seller-admin-list@example.com", "password123", "seller");
    String otherSellerToken = signupAndLogin("other-seller-admin-list@example.com", "password123", "otherSeller");
    String buyerToken = signupAndLogin("buyer-admin-list@example.com", "password123", "buyer");
    String otherBuyerToken = signupAndLogin("other-buyer-admin-list@example.com", "password123", "otherBuyer");
    Long productId = createProduct(sellerToken, "MacBook Pro");
    Long otherProductId = createProduct(otherSellerToken, "iPad Pro");
    Long orderId = createDeliveredOrder(buyerToken, productId);
    Long otherOrderId = createDeliveredOrder(otherBuyerToken, otherProductId);
    Long refundRequestId = createRefundRequest(buyerToken, orderId);
    Long otherRefundRequestId = createRefundRequest(otherBuyerToken, otherOrderId);

    mockMvc.perform(get("/api/admin/refund-requests")
                    .param("page", "0")
                    .param("size", "10")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.content[0].id").value(otherRefundRequestId))
            .andExpect(jsonPath("$.data.content[0].orderId").value(otherOrderId))
            .andExpect(jsonPath("$.data.content[0].productTitle").value("iPad Pro"))
            .andExpect(jsonPath("$.data.content[0].buyerNickname").value("otherBuyer"))
            .andExpect(jsonPath("$.data.content[1].id").value(refundRequestId))
            .andExpect(jsonPath("$.data.content[1].orderId").value(orderId))
            .andExpect(jsonPath("$.data.content[1].productTitle").value("MacBook Pro"))
            .andExpect(jsonPath("$.data.content[1].buyerNickname").value("buyer"))
            .andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.number").value(0))
            .andExpect(jsonPath("$.data.size").value(10));
}
```

- [ ] **Step 4: Add status filter coverage for seller lists**

Add this test near the seller list test:

```java
@Test
void 판매자는_환불_요청_상태로_목록을_필터링할_수_있다() throws Exception {
    String sellerToken = signupAndLogin("seller-filter@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-filter-requested@example.com", "password123", "buyerRequested");
    String approvedBuyerToken = signupAndLogin("buyer-filter-approved@example.com", "password123", "buyerApproved");
    String rejectedBuyerToken = signupAndLogin("buyer-filter-rejected@example.com", "password123", "buyerRejected");
    Long requestedProductId = createProduct(sellerToken, "Requested Product");
    Long approvedProductId = createProduct(sellerToken, "Approved Product");
    Long rejectedProductId = createProduct(sellerToken, "Rejected Product");
    Long requestedOrderId = createDeliveredOrder(buyerToken, requestedProductId);
    Long approvedOrderId = createDeliveredOrder(approvedBuyerToken, approvedProductId);
    Long rejectedOrderId = createDeliveredOrder(rejectedBuyerToken, rejectedProductId);
    Long requestedRefundRequestId = createRefundRequest(buyerToken, requestedOrderId);
    Long approvedRefundRequestId = createRefundRequest(approvedBuyerToken, approvedOrderId);
    Long rejectedRefundRequestId = createRefundRequest(rejectedBuyerToken, rejectedOrderId);

    approveRefundRequest(sellerToken, approvedRefundRequestId);
    rejectRefundRequest(sellerToken, rejectedRefundRequestId);

    mockMvc.perform(get("/api/seller/refund-requests")
                    .param("status", "REQUESTED")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(requestedRefundRequestId))
            .andExpect(jsonPath("$.data.content[0].status").value("REQUESTED"));

    mockMvc.perform(get("/api/seller/refund-requests")
                    .param("status", "APPROVED")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(approvedRefundRequestId))
            .andExpect(jsonPath("$.data.content[0].status").value("APPROVED"));

    mockMvc.perform(get("/api/seller/refund-requests")
                    .param("status", "REJECTED")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(rejectedRefundRequestId))
            .andExpect(jsonPath("$.data.content[0].status").value("REJECTED"));
}
```

- [ ] **Step 5: Add all-status pagination and ordering coverage for admin lists**

Add this test near the admin list test:

```java
@Test
void 관리자는_상태_없이_전체_환불_요청을_최신순으로_페이지_조회한다() throws Exception {
    String adminToken = createAdminToken("admin-page-order@example.com");
    String sellerToken = signupAndLogin("seller-page-order@example.com", "password123", "seller");
    String firstBuyerToken = signupAndLogin("buyer-page-order-1@example.com", "password123", "firstBuyer");
    String secondBuyerToken = signupAndLogin("buyer-page-order-2@example.com", "password123", "secondBuyer");
    String thirdBuyerToken = signupAndLogin("buyer-page-order-3@example.com", "password123", "thirdBuyer");
    Long firstProductId = createProduct(sellerToken, "First Product");
    Long secondProductId = createProduct(sellerToken, "Second Product");
    Long thirdProductId = createProduct(sellerToken, "Third Product");
    Long firstOrderId = createDeliveredOrder(firstBuyerToken, firstProductId);
    Long secondOrderId = createDeliveredOrder(secondBuyerToken, secondProductId);
    Long thirdOrderId = createDeliveredOrder(thirdBuyerToken, thirdProductId);
    Long firstRefundRequestId = createRefundRequest(firstBuyerToken, firstOrderId);
    Long secondRefundRequestId = createRefundRequest(secondBuyerToken, secondOrderId);
    Long thirdRefundRequestId = createRefundRequest(thirdBuyerToken, thirdOrderId);

    approveRefundRequest(sellerToken, secondRefundRequestId);
    rejectRefundRequest(sellerToken, thirdRefundRequestId);

    mockMvc.perform(get("/api/admin/refund-requests")
                    .param("page", "0")
                    .param("size", "2")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.content[0].id").value(thirdRefundRequestId))
            .andExpect(jsonPath("$.data.content[1].id").value(secondRefundRequestId))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.number").value(0))
            .andExpect(jsonPath("$.data.size").value(2));

    mockMvc.perform(get("/api/admin/refund-requests")
                    .param("page", "1")
                    .param("size", "2")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(firstRefundRequestId))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.number").value(1))
            .andExpect(jsonPath("$.data.size").value(2));
}
```

- [ ] **Step 6: Add helper methods for approve and reject in the test class**

Add these helpers near the existing `createRefundRequest` helper:

```java
private void approveRefundRequest(String accessToken, Long refundRequestId) throws Exception {
    mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/approve", refundRequestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk());
}

private void rejectRefundRequest(String accessToken, Long refundRequestId) throws Exception {
    mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/reject", refundRequestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "rejectReason": "상품 설명과 다른 부분을 확인할 수 없습니다."
                            }
                            """))
            .andExpect(status().isOk());
}
```

- [ ] **Step 7: Run the refund API tests and verify failure**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "*RefundRequestApiTest"
```

Expected: FAIL because the backend still returns list-shaped refund request data and `RefundRequestResponse` does not expose `buyerNickname`.

- [ ] **Step 8: Commit the failing contract tests**

```powershell
git add backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java
git commit -m "test: define paged refund request operations"
```

## Task 2: Backend Paged Refund List Implementation

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/refund/api/RefundRequestResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/refund/repository/RefundRequestRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java`
- Modify: `backend/src/main/java/com/sweet/market/refund/api/SellerRefundRequestController.java`
- Modify: `backend/src/main/java/com/sweet/market/refund/api/AdminRefundRequestController.java`

- [ ] **Step 1: Add buyer nickname to the refund response**

Change `RefundRequestResponse` to include `buyerNickname` immediately after `buyerId`:

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

- [ ] **Step 2: Convert repository list queries to Page**

In `RefundRequestRepository`, add these imports:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
```

Replace the seller/admin list methods with:

```java
@EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "buyer", "handledBy"})
@Query(
        value = """
                select r
                from RefundRequest r
                join r.order o
                join o.product p
                where p.seller.id = :sellerId
                  and (:status is null or r.status = :status)
                order by r.requestedAt desc, r.id desc
                """,
        countQuery = """
                select count(r)
                from RefundRequest r
                join r.order o
                join o.product p
                where p.seller.id = :sellerId
                  and (:status is null or r.status = :status)
                """
)
Page<RefundRequest> findSellerRequests(
        @Param("sellerId") Long sellerId,
        @Param("status") RefundRequestStatus status,
        Pageable pageable
);

@EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "buyer", "handledBy"})
@Query(
        value = """
                select r
                from RefundRequest r
                where (:status is null or r.status = :status)
                order by r.requestedAt desc, r.id desc
                """,
        countQuery = """
                select count(r)
                from RefundRequest r
                where (:status is null or r.status = :status)
                """
)
Page<RefundRequest> findAdminRequests(
        @Param("status") RefundRequestStatus status,
        Pageable pageable
);
```

- [ ] **Step 3: Convert service list methods to Page**

In `RefundRequestService`, replace the `java.util.List` import with:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
```

Replace the two list methods with:

```java
@Transactional(readOnly = true)
public Page<RefundRequestResponse> findSellerRequests(Long sellerId, RefundRequestStatus status, Pageable pageable) {
    return refundRequestRepository.findSellerRequests(sellerId, status, pageable)
            .map(RefundRequestResponse::from);
}

@Transactional(readOnly = true)
public Page<RefundRequestResponse> findAdminRequests(RefundRequestStatus status, Pageable pageable) {
    return refundRequestRepository.findAdminRequests(status, pageable)
            .map(RefundRequestResponse::from);
}
```

- [ ] **Step 4: Update the seller controller list method**

In `SellerRefundRequestController`, remove the `java.util.List` import and add:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
```

Replace the list method with:

```java
@GetMapping
public ApiResponse<Page<RefundRequestResponse>> list(
        Authentication authentication,
        @RequestParam(required = false) RefundRequestStatus status,
        @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
) {
    AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
    return ApiResponse.ok(refundRequestService.findSellerRequests(member.id(), status, pageable));
}
```

- [ ] **Step 5: Update the admin controller list method**

In `AdminRefundRequestController`, remove the `java.util.List` import and add:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
```

Replace the list method with:

```java
@GetMapping
public ApiResponse<Page<RefundRequestResponse>> list(
        @RequestParam(required = false) RefundRequestStatus status,
        @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
) {
    return ApiResponse.ok(refundRequestService.findAdminRequests(status, pageable));
}
```

- [ ] **Step 6: Run refund API tests and verify pass**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "*RefundRequestApiTest"
```

Expected: PASS.

- [ ] **Step 7: Commit backend implementation**

```powershell
git add backend/src/main/java/com/sweet/market/refund/api/RefundRequestResponse.java backend/src/main/java/com/sweet/market/refund/repository/RefundRequestRepository.java backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java backend/src/main/java/com/sweet/market/refund/api/SellerRefundRequestController.java backend/src/main/java/com/sweet/market/refund/api/AdminRefundRequestController.java
git commit -m "feat: page refund operation requests"
```

## Task 3: Web Refund API Module

**Files:**

- Create: `web/src/features/refunds/refundApi.ts`

- [ ] **Step 1: Create the refund API module**

Create `web/src/features/refunds/refundApi.ts`:

```ts
import { api } from '../../shared/api/http';
import { type PageResponse } from '../admin/adminOperationsApi';

export type RefundRequestStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED';
export type RefundRequestStatusFilter = RefundRequestStatus | 'ALL';

export type RefundRequest = {
  id: number;
  orderId: number;
  productId: number;
  productTitle: string;
  buyerId: number;
  buyerNickname: string;
  reason: string;
  status: RefundRequestStatus;
  requestedAt: string;
  handledById: number | null;
  handledAt: string | null;
  rejectReason: string | null;
};

export type RefundRequestPage = PageResponse<RefundRequest>;

export type RefundRequestSearchInput = {
  status: RefundRequestStatusFilter;
  page: number;
  size: number;
};

function buildRefundRequestSearchParams(input: RefundRequestSearchInput) {
  const searchParams = new URLSearchParams();

  if (input.status !== 'ALL') {
    searchParams.set('status', input.status);
  }

  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));

  return searchParams.toString();
}

export function getSellerRefundRequests(input: RefundRequestSearchInput) {
  return api<RefundRequestPage>(`/api/seller/refund-requests?${buildRefundRequestSearchParams(input)}`);
}

export function getAdminRefundRequests(input: RefundRequestSearchInput) {
  return api<RefundRequestPage>(`/api/admin/refund-requests?${buildRefundRequestSearchParams(input)}`);
}

export function approveSellerRefundRequest(refundRequestId: number) {
  return api<RefundRequest>(`/api/seller/refund-requests/${refundRequestId}/approve`, {
    method: 'POST',
  });
}

export function rejectSellerRefundRequest(refundRequestId: number, rejectReason: string) {
  return api<RefundRequest>(`/api/seller/refund-requests/${refundRequestId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ rejectReason }),
  });
}

export function approveAdminRefundRequest(refundRequestId: number) {
  return api<RefundRequest>(`/api/admin/refund-requests/${refundRequestId}/approve`, {
    method: 'POST',
  });
}

export function rejectAdminRefundRequest(refundRequestId: number, rejectReason: string) {
  return api<RefundRequest>(`/api/admin/refund-requests/${refundRequestId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ rejectReason }),
  });
}
```

- [ ] **Step 2: Run TypeScript build and verify expected routing failures**

Run:

```powershell
cd web
npm run build
```

Expected: PASS because the new module is not imported yet.

- [ ] **Step 3: Commit web API module**

```powershell
git add web/src/features/refunds/refundApi.ts
git commit -m "feat: add refund operations web API"
```

## Task 4: Seller Refund Operation Page

**Files:**

- Create: `web/src/pages/MyRefundRequestsPage.tsx`

- [ ] **Step 1: Create seller refund operation page**

Create `web/src/pages/MyRefundRequestsPage.tsx`:

```tsx
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import {
  approveSellerRefundRequest,
  getSellerRefundRequests,
  rejectSellerRefundRequest,
  type RefundRequest,
  type RefundRequestPage,
  type RefundRequestSearchInput,
  type RefundRequestStatusFilter,
} from '../features/refunds/refundApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const REFUND_REQUEST_PAGE_SIZE = 10;

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

type RejectMutationInput = {
  refundRequestId: number;
  rejectReason: string;
};

export function MyRefundRequestsPage() {
  const queryClient = useQueryClient();
  const [searchInput, setSearchInput] = useState<RefundRequestSearchInput>({
    status: 'REQUESTED',
    page: 0,
    size: REFUND_REQUEST_PAGE_SIZE,
  });
  const [rejectingRefundRequestId, setRejectingRefundRequestId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const refundListQuery = useQuery({
    queryKey: ['refund-operations', 'seller', searchInput],
    queryFn: () => getSellerRefundRequests(searchInput),
  });

  const approveMutation = useMutation({
    mutationFn: approveSellerRefundRequest,
    onSuccess: () => invalidateRefundOperationQueries(queryClient),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ refundRequestId, rejectReason }: RejectMutationInput) =>
      rejectSellerRefundRequest(refundRequestId, rejectReason),
    onSuccess: () => {
      resetRejectForm();
      return invalidateRefundOperationQueries(queryClient);
    },
  });

  const actionError = approveMutation.error ?? rejectMutation.error;
  const refundRequests = refundListQuery.data?.content ?? [];

  function changeStatus(status: RefundRequestStatusFilter) {
    setRejectingRefundRequestId(null);
    setRejectReason('');
    setSearchInput((current) => ({ ...current, status, page: 0 }));
  }

  function approveRefund(refundRequest: RefundRequest) {
    if (!window.confirm(`환불 요청 #${refundRequest.id}을 승인할까요?`)) {
      return;
    }

    approveMutation.mutate(refundRequest.id);
  }

  function startReject(refundRequestId: number) {
    rejectMutation.reset();
    setRejectingRefundRequestId(refundRequestId);
    setRejectReason('');
  }

  function resetRejectForm() {
    setRejectingRefundRequestId(null);
    setRejectReason('');
    rejectMutation.reset();
  }

  function submitReject(event: FormEvent<HTMLFormElement>, refundRequestId: number) {
    event.preventDefault();

    if (rejectMutation.isPending) {
      return;
    }

    rejectMutation.mutate({ refundRequestId, rejectReason: rejectReason.trim() });
  }

  return (
    <section className="refund-operations-page">
      <div className="list-page-header">
        <h1>환불 요청 관리</h1>
        <p>내 상품에 접수된 환불 요청을 검토하고 승인 또는 거절합니다.</p>
      </div>

      <RefundStatusFilter selectedStatus={searchInput.status} onChange={changeStatus} />

      {actionError ? <p className="error-text">{toErrorMessage(actionError, '환불 처리 요청을 완료하지 못했습니다.')}</p> : null}
      {refundListQuery.isLoading ? <p className="status-text">환불 요청 목록을 불러오고 있습니다.</p> : null}
      {refundListQuery.error ? <ErrorState message="환불 요청 목록을 불러오지 못했습니다." /> : null}
      {!refundListQuery.isLoading && !refundListQuery.error && refundRequests.length === 0 ? (
        <EmptyState title="환불 요청이 없습니다" description="선택한 상태에 해당하는 환불 요청이 없습니다." />
      ) : null}

      {refundRequests.length > 0 ? (
        <>
          <div className="refund-operations-table" aria-label="판매자 환불 요청 목록">
            <div className="refund-operations-table-head refund-operations-grid" role="row">
              <span>요청</span>
              <span>상품</span>
              <span>구매자</span>
              <span>상태</span>
              <span>요청일</span>
              <span>처리</span>
            </div>
            {refundRequests.map((refundRequest) => (
              <RefundOperationRow
                key={refundRequest.id}
                approvePending={approveMutation.isPending && approveMutation.variables === refundRequest.id}
                rejectPending={
                  rejectMutation.isPending && rejectMutation.variables?.refundRequestId === refundRequest.id
                }
                rejecting={rejectingRefundRequestId === refundRequest.id}
                refundRequest={refundRequest}
                rejectReason={rejectReason}
                onApprove={approveRefund}
                onCancelReject={resetRejectForm}
                onChangeRejectReason={setRejectReason}
                onStartReject={startReject}
                onSubmitReject={submitReject}
              />
            ))}
          </div>
          {refundListQuery.data ? (
            <RefundPagination
              data={refundListQuery.data}
              isFetching={refundListQuery.isFetching}
              onMovePage={(page) => setSearchInput((current) => ({ ...current, page }))}
            />
          ) : null}
        </>
      ) : null}
    </section>
  );
}

type RefundStatusFilterProps = {
  selectedStatus: RefundRequestStatusFilter;
  onChange: (status: RefundRequestStatusFilter) => void;
};

function RefundStatusFilter({ selectedStatus, onChange }: RefundStatusFilterProps) {
  const options: Array<{ value: RefundRequestStatusFilter; label: string }> = [
    { value: 'REQUESTED', label: '요청됨' },
    { value: 'APPROVED', label: '승인됨' },
    { value: 'REJECTED', label: '거절됨' },
    { value: 'ALL', label: '전체' },
  ];

  return (
    <div className="refund-status-filter" aria-label="환불 요청 상태 필터">
      {options.map((option) => (
        <button
          type="button"
          className={`text-button${selectedStatus === option.value ? ' selected-filter-button' : ''}`}
          aria-pressed={selectedStatus === option.value}
          key={option.value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

type RefundOperationRowProps = {
  approvePending: boolean;
  rejectPending: boolean;
  rejecting: boolean;
  refundRequest: RefundRequest;
  rejectReason: string;
  onApprove: (refundRequest: RefundRequest) => void;
  onCancelReject: () => void;
  onChangeRejectReason: (value: string) => void;
  onStartReject: (refundRequestId: number) => void;
  onSubmitReject: (event: FormEvent<HTMLFormElement>, refundRequestId: number) => void;
};

function RefundOperationRow({
  approvePending,
  rejectPending,
  rejecting,
  refundRequest,
  rejectReason,
  onApprove,
  onCancelReject,
  onChangeRejectReason,
  onStartReject,
  onSubmitReject,
}: RefundOperationRowProps) {
  return (
    <article className="refund-operations-row">
      <div className="refund-operations-grid">
        <span>#{refundRequest.id}</span>
        <span>
          #{refundRequest.productId} {refundRequest.productTitle}
        </span>
        <span>
          #{refundRequest.buyerId} {refundRequest.buyerNickname}
        </span>
        <span>
          <StatusBadge status={refundRequest.status} />
        </span>
        <span>{formatDate(refundRequest.requestedAt)}</span>
        <span className="refund-row-actions">
          {refundRequest.status === 'REQUESTED' ? (
            <>
              <button type="button" className="text-button" disabled={approvePending || rejectPending} onClick={() => onApprove(refundRequest)}>
                {approvePending ? '승인 중' : '승인'}
              </button>
              <button type="button" className="text-button danger-button" disabled={approvePending || rejectPending} onClick={() => onStartReject(refundRequest.id)}>
                거절
              </button>
            </>
          ) : (
            <span className="muted-text">처리 완료</span>
          )}
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
          <dd>{refundRequest.handledById ? `#${refundRequest.handledById}` : '-'}</dd>
        </div>
        <div>
          <dt>처리일</dt>
          <dd>{formatDate(refundRequest.handledAt)}</dd>
        </div>
        <div>
          <dt>거절 사유</dt>
          <dd>{refundRequest.rejectReason ?? '-'}</dd>
        </div>
      </dl>
      {rejecting ? (
        <form className="refund-reject-form" onSubmit={(event) => onSubmitReject(event, refundRequest.id)}>
          <label>
            거절 사유
            <textarea
              value={rejectReason}
              minLength={5}
              maxLength={500}
              required
              rows={3}
              disabled={rejectPending}
              onChange={(event) => onChangeRejectReason(event.target.value)}
            />
          </label>
          <div className="refund-reject-actions">
            <button type="submit" className="text-button danger-button" disabled={rejectPending}>
              {rejectPending ? '거절 중' : '거절 확정'}
            </button>
            <button type="button" className="text-button secondary-button" disabled={rejectPending} onClick={onCancelReject}>
              취소
            </button>
          </div>
        </form>
      ) : null}
    </article>
  );
}

type RefundPaginationProps = {
  data: RefundRequestPage;
  isFetching: boolean;
  onMovePage: (page: number) => void;
};

function RefundPagination({ data, isFetching, onMovePage }: RefundPaginationProps) {
  const currentPage = data.number;
  const totalPages = data.totalPages;
  const hasPreviousPage = currentPage > 0;
  const hasNextPage = totalPages > currentPage + 1;

  return (
    <div className="admin-pagination">
      <button type="button" className="text-button" disabled={!hasPreviousPage || isFetching} onClick={() => onMovePage(currentPage - 1)}>
        이전
      </button>
      <span>
        {currentPage + 1} / {Math.max(totalPages, 1)}
      </span>
      <button type="button" className="text-button" disabled={!hasNextPage || isFetching} onClick={() => onMovePage(currentPage + 1)}>
        다음
      </button>
    </div>
  );
}

async function invalidateRefundOperationQueries(queryClient: ReturnType<typeof useQueryClient>) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['refund-operations'] }),
    queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
    queryClient.invalidateQueries({ queryKey: ['admin-operations', 'orders'] }),
  ]);
}

function formatDate(value: string | null) {
  if (!value) {
    return '-';
  }

  return dateFormatter.format(new Date(value));
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
```

- [ ] **Step 2: Run web build and verify route import failure is absent**

Run:

```powershell
cd web
npm run build
```

Expected: PASS because the page is not imported by the router yet.

- [ ] **Step 3: Commit seller page**

```powershell
git add web/src/pages/MyRefundRequestsPage.tsx
git commit -m "feat: add seller refund operations page"
```

## Task 5: Admin Refund Operation Page

**Files:**

- Create: `web/src/pages/AdminRefundRequestsPage.tsx`

- [ ] **Step 1: Create the admin refund operation page**

Create `web/src/pages/AdminRefundRequestsPage.tsx`:

```tsx
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import {
  approveAdminRefundRequest,
  getAdminRefundRequests,
  rejectAdminRefundRequest,
  type RefundRequest,
  type RefundRequestPage,
  type RefundRequestSearchInput,
  type RefundRequestStatusFilter,
} from '../features/refunds/refundApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const REFUND_REQUEST_PAGE_SIZE = 10;

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

type RejectMutationInput = {
  refundRequestId: number;
  rejectReason: string;
};

export function AdminRefundRequestsPage() {
  const queryClient = useQueryClient();
  const [searchInput, setSearchInput] = useState<RefundRequestSearchInput>({
    status: 'REQUESTED',
    page: 0,
    size: REFUND_REQUEST_PAGE_SIZE,
  });
  const [rejectingRefundRequestId, setRejectingRefundRequestId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const refundListQuery = useQuery({
    queryKey: ['refund-operations', 'admin', searchInput],
    queryFn: () => getAdminRefundRequests(searchInput),
  });

  const approveMutation = useMutation({
    mutationFn: approveAdminRefundRequest,
    onSuccess: () => invalidateRefundOperationQueries(queryClient),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ refundRequestId, rejectReason }: RejectMutationInput) =>
      rejectAdminRefundRequest(refundRequestId, rejectReason),
    onSuccess: () => {
      resetRejectForm();
      return invalidateRefundOperationQueries(queryClient);
    },
  });

  const actionError = approveMutation.error ?? rejectMutation.error;
  const refundRequests = refundListQuery.data?.content ?? [];

  function changeStatus(status: RefundRequestStatusFilter) {
    setRejectingRefundRequestId(null);
    setRejectReason('');
    setSearchInput((current) => ({ ...current, status, page: 0 }));
  }

  function approveRefund(refundRequest: RefundRequest) {
    if (!window.confirm(`환불 요청 #${refundRequest.id}을 승인할까요?`)) {
      return;
    }

    approveMutation.mutate(refundRequest.id);
  }

  function startReject(refundRequestId: number) {
    rejectMutation.reset();
    setRejectingRefundRequestId(refundRequestId);
    setRejectReason('');
  }

  function resetRejectForm() {
    setRejectingRefundRequestId(null);
    setRejectReason('');
    rejectMutation.reset();
  }

  function submitReject(event: FormEvent<HTMLFormElement>, refundRequestId: number) {
    event.preventDefault();

    if (rejectMutation.isPending) {
      return;
    }

    rejectMutation.mutate({ refundRequestId, rejectReason: rejectReason.trim() });
  }

  return (
    <section className="refund-operations-page">
      <div className="list-page-header">
        <h1>관리자 환불 관리</h1>
        <p>전체 판매자의 환불 요청을 검토하고 승인 또는 거절합니다.</p>
      </div>

      <RefundStatusFilter selectedStatus={searchInput.status} onChange={changeStatus} />

      {actionError ? <p className="error-text">{toErrorMessage(actionError, '환불 처리 요청을 완료하지 못했습니다.')}</p> : null}
      {refundListQuery.isLoading ? <p className="status-text">환불 요청 목록을 불러오고 있습니다.</p> : null}
      {refundListQuery.error ? <ErrorState message="환불 요청 목록을 불러오지 못했습니다." /> : null}
      {!refundListQuery.isLoading && !refundListQuery.error && refundRequests.length === 0 ? (
        <EmptyState title="환불 요청이 없습니다" description="선택한 상태에 해당하는 환불 요청이 없습니다." />
      ) : null}

      {refundRequests.length > 0 ? (
        <>
          <div className="refund-operations-table" aria-label="관리자 환불 요청 목록">
            <div className="refund-operations-table-head refund-operations-grid" role="row">
              <span>요청</span>
              <span>상품</span>
              <span>구매자</span>
              <span>상태</span>
              <span>요청일</span>
              <span>처리</span>
            </div>
            {refundRequests.map((refundRequest) => (
              <RefundOperationRow
                key={refundRequest.id}
                approvePending={approveMutation.isPending && approveMutation.variables === refundRequest.id}
                rejectPending={
                  rejectMutation.isPending && rejectMutation.variables?.refundRequestId === refundRequest.id
                }
                rejecting={rejectingRefundRequestId === refundRequest.id}
                refundRequest={refundRequest}
                rejectReason={rejectReason}
                onApprove={approveRefund}
                onCancelReject={resetRejectForm}
                onChangeRejectReason={setRejectReason}
                onStartReject={startReject}
                onSubmitReject={submitReject}
              />
            ))}
          </div>
          {refundListQuery.data ? (
            <RefundPagination
              data={refundListQuery.data}
              isFetching={refundListQuery.isFetching}
              onMovePage={(page) => setSearchInput((current) => ({ ...current, page }))}
            />
          ) : null}
        </>
      ) : null}
    </section>
  );
}

type RefundStatusFilterProps = {
  selectedStatus: RefundRequestStatusFilter;
  onChange: (status: RefundRequestStatusFilter) => void;
};

function RefundStatusFilter({ selectedStatus, onChange }: RefundStatusFilterProps) {
  const options: Array<{ value: RefundRequestStatusFilter; label: string }> = [
    { value: 'REQUESTED', label: '요청됨' },
    { value: 'APPROVED', label: '승인됨' },
    { value: 'REJECTED', label: '거절됨' },
    { value: 'ALL', label: '전체' },
  ];

  return (
    <div className="refund-status-filter" aria-label="환불 요청 상태 필터">
      {options.map((option) => (
        <button
          type="button"
          className={`text-button${selectedStatus === option.value ? ' selected-filter-button' : ''}`}
          aria-pressed={selectedStatus === option.value}
          key={option.value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

type RefundOperationRowProps = {
  approvePending: boolean;
  rejectPending: boolean;
  rejecting: boolean;
  refundRequest: RefundRequest;
  rejectReason: string;
  onApprove: (refundRequest: RefundRequest) => void;
  onCancelReject: () => void;
  onChangeRejectReason: (value: string) => void;
  onStartReject: (refundRequestId: number) => void;
  onSubmitReject: (event: FormEvent<HTMLFormElement>, refundRequestId: number) => void;
};

function RefundOperationRow({
  approvePending,
  rejectPending,
  rejecting,
  refundRequest,
  rejectReason,
  onApprove,
  onCancelReject,
  onChangeRejectReason,
  onStartReject,
  onSubmitReject,
}: RefundOperationRowProps) {
  return (
    <article className="refund-operations-row">
      <div className="refund-operations-grid">
        <span>#{refundRequest.id}</span>
        <span>
          #{refundRequest.productId} {refundRequest.productTitle}
        </span>
        <span>
          #{refundRequest.buyerId} {refundRequest.buyerNickname}
        </span>
        <span>
          <StatusBadge status={refundRequest.status} />
        </span>
        <span>{formatDate(refundRequest.requestedAt)}</span>
        <span className="refund-row-actions">
          {refundRequest.status === 'REQUESTED' ? (
            <>
              <button type="button" className="text-button" disabled={approvePending || rejectPending} onClick={() => onApprove(refundRequest)}>
                {approvePending ? '승인 중' : '승인'}
              </button>
              <button type="button" className="text-button danger-button" disabled={approvePending || rejectPending} onClick={() => onStartReject(refundRequest.id)}>
                거절
              </button>
            </>
          ) : (
            <span className="muted-text">처리 완료</span>
          )}
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
          <dd>{refundRequest.handledById ? `#${refundRequest.handledById}` : '-'}</dd>
        </div>
        <div>
          <dt>처리일</dt>
          <dd>{formatDate(refundRequest.handledAt)}</dd>
        </div>
        <div>
          <dt>거절 사유</dt>
          <dd>{refundRequest.rejectReason ?? '-'}</dd>
        </div>
      </dl>
      {rejecting ? (
        <form className="refund-reject-form" onSubmit={(event) => onSubmitReject(event, refundRequest.id)}>
          <label>
            거절 사유
            <textarea
              value={rejectReason}
              minLength={5}
              maxLength={500}
              required
              rows={3}
              disabled={rejectPending}
              onChange={(event) => onChangeRejectReason(event.target.value)}
            />
          </label>
          <div className="refund-reject-actions">
            <button type="submit" className="text-button danger-button" disabled={rejectPending}>
              {rejectPending ? '거절 중' : '거절 확정'}
            </button>
            <button type="button" className="text-button secondary-button" disabled={rejectPending} onClick={onCancelReject}>
              취소
            </button>
          </div>
        </form>
      ) : null}
    </article>
  );
}

type RefundPaginationProps = {
  data: RefundRequestPage;
  isFetching: boolean;
  onMovePage: (page: number) => void;
};

function RefundPagination({ data, isFetching, onMovePage }: RefundPaginationProps) {
  const currentPage = data.number;
  const totalPages = data.totalPages;
  const hasPreviousPage = currentPage > 0;
  const hasNextPage = totalPages > currentPage + 1;

  return (
    <div className="admin-pagination">
      <button type="button" className="text-button" disabled={!hasPreviousPage || isFetching} onClick={() => onMovePage(currentPage - 1)}>
        이전
      </button>
      <span>
        {currentPage + 1} / {Math.max(totalPages, 1)}
      </span>
      <button type="button" className="text-button" disabled={!hasNextPage || isFetching} onClick={() => onMovePage(currentPage + 1)}>
        다음
      </button>
    </div>
  );
}

async function invalidateRefundOperationQueries(queryClient: ReturnType<typeof useQueryClient>) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['refund-operations'] }),
    queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
    queryClient.invalidateQueries({ queryKey: ['admin-operations', 'orders'] }),
  ]);
}

function formatDate(value: string | null) {
  if (!value) {
    return '-';
  }

  return dateFormatter.format(new Date(value));
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
```

- [ ] **Step 2: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS because the admin page is not imported by the router yet.

- [ ] **Step 3: Commit admin page**

```powershell
git add web/src/pages/AdminRefundRequestsPage.tsx
git commit -m "feat: add admin refund operations page"
```

## Task 6: Routes, Navigation, Status Labels, And Styles

**Files:**

- Modify: `web/src/app/router.tsx`
- Modify: `web/src/shared/layout/Shell.tsx`
- Modify: `web/src/shared/ui/ResourceStates.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Add refund page imports and routes**

In `web/src/app/router.tsx`, add imports:

```tsx
import { AdminRefundRequestsPage } from '../pages/AdminRefundRequestsPage';
import { MyRefundRequestsPage } from '../pages/MyRefundRequestsPage';
```

Add the seller route after `me/reports`:

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

Add the admin route after `admin/operations`:

```tsx
<Route
  path="admin/refunds"
  element={
    <RequireAdmin>
      <AdminRefundRequestsPage />
    </RequireAdmin>
  }
/>
```

- [ ] **Step 2: Add navigation links**

In `web/src/shared/layout/Shell.tsx`, add the seller link after 리포트:

```tsx
<NavLink to="/me/refunds">환불 관리</NavLink>
```

Change the admin navigation block to:

```tsx
{member.role === 'ADMIN' ? (
  <>
    <NavLink to="/admin/operations">관리자</NavLink>
    <NavLink to="/admin/refunds">관리자 환불</NavLink>
  </>
) : null}
```

- [ ] **Step 3: Add shared refund status labels**

In `web/src/shared/ui/ResourceStates.tsx`, add cases to `formatStatus`:

```tsx
case 'REQUESTED':
  return '요청';
case 'REJECTED':
  return '거절';
```

`APPROVED` already maps to `승인`.

- [ ] **Step 4: Add refund operation styles**

Append these styles to `web/src/shared/styles.css` near the admin operation styles:

```css
.refund-operations-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.refund-status-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.selected-filter-button {
  border-color: #1d4ed8;
  color: #1d4ed8;
  background: #eff6ff;
}

.refund-operations-table {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.refund-operations-table-head,
.refund-operations-row {
  border-bottom: 1px solid #e5e7eb;
}

.refund-operations-table-head {
  background: #f8fafc;
  color: #475569;
  font-size: 0.85rem;
  font-weight: 700;
}

.refund-operations-row:last-child {
  border-bottom: 0;
}

.refund-operations-grid {
  display: grid;
  grid-template-columns: minmax(70px, 0.7fr) minmax(180px, 1.5fr) minmax(130px, 1fr) minmax(90px, 0.8fr) minmax(140px, 1fr) minmax(150px, 1fr);
  gap: 0.75rem;
  align-items: center;
  padding: 0.85rem 1rem;
}

.refund-operations-grid span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.refund-row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.refund-operation-detail {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.75rem;
  margin: 0;
  padding: 0 1rem 1rem;
  color: #334155;
}

.refund-operation-detail div {
  min-width: 0;
}

.refund-operation-detail dt {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 700;
}

.refund-operation-detail dd {
  margin: 0.2rem 0 0;
  overflow-wrap: anywhere;
}

.refund-reject-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 0 1rem 1rem;
  padding: 0.85rem;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fef2f2;
}

.refund-reject-form label {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  color: #7f1d1d;
  font-weight: 700;
}

.refund-reject-form textarea {
  width: 100%;
  resize: vertical;
}

.refund-reject-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

@media (max-width: 960px) {
  .refund-operations-table-head {
    display: none;
  }

  .refund-operations-grid,
  .refund-operation-detail {
    grid-template-columns: 1fr;
  }

  .refund-operations-grid {
    gap: 0.45rem;
  }
}
```

- [ ] **Step 5: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit route, navigation, labels, and styles**

```powershell
git add web/src/app/router.tsx web/src/shared/layout/Shell.tsx web/src/shared/ui/ResourceStates.tsx web/src/shared/styles.css
git commit -m "feat: route refund operations pages"
```

## Task 7: Full Verification And Handoff

**Files:**

- Create: `docs/superpowers/handoffs/2026-07-08-milestone-19-refund-operations-management-handoff.md`

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

Create `docs/superpowers/handoffs/2026-07-08-milestone-19-refund-operations-management-handoff.md`:

```markdown
# Milestone 19 Refund Operations Management Handoff

## Completed

- Converted seller/admin refund request list APIs to paginated responses.
- Kept seller/admin refund API paths stable.
- Added optional refund status filtering for requested, approved, rejected, and all views.
- Added buyer nickname to refund request responses.
- Added seller refund management page at `/me/refunds`.
- Added admin refund management page at `/admin/refunds`.
- Added inline approve and reject actions with rejection reason input.
- Refetched refund and order query data after approve/reject mutations.
- Added backend tests for pagination, filtering, ordering, response fields, seller ownership, and admin access.

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

- Work was done in `C:\dev\jpa-study\.worktrees\milestone-19-refund-operations-management`.
- The main checkout's local-only `backend/src/main/resources/application.yaml` change was not touched.

## Follow-Up Candidates

- Buyer refund history page.
- Refund detail pages.
- Return shipping workflow.
- Partial refunds.
- Real payment gateway refund integration.
- Refund evidence upload.
- Dispute mediation.
```

- [ ] **Step 5: Commit handoff**

```powershell
git add docs/superpowers/handoffs/2026-07-08-milestone-19-refund-operations-management-handoff.md
git commit -m "docs: add milestone 19 handoff"
```

- [ ] **Step 6: Review final diff**

Run:

```powershell
git status --short --branch --untracked-files=all
git log --oneline --decorate -n 12
```

Expected: branch is clean and the latest commits are the Milestone 19 implementation commits.

## Self-Review Checklist

- Spec coverage: Tasks cover paginated list APIs, status filters, buyer nickname, seller/admin pages, list actions, mutation invalidation, authorization boundaries, backend tests, web build, and handoff.
- Scope control: Buyer refund history, detail pages, return shipping, partial refunds, gateway integration, relisting, evidence upload, and dispute mediation stay outside the plan.
- Type consistency: `RefundRequestStatusFilter` uses `REQUESTED`, `APPROVED`, `REJECTED`, and frontend-only `ALL`; backend uses nullable `RefundRequestStatus`.
- API consistency: Existing approve/reject paths remain unchanged.
- Local safety: No step stages or edits `backend/src/main/resources/application.yaml` in the main checkout.
