# Milestone 20 Buyer Refund History Design

## Goal

Milestone 20 adds a buyer-facing refund history screen.

The product goal is that buyers can track refund requests from a dedicated refund history screen and inspect the request, handling result, and related order context without digging through My Orders cards.

The learning goal is to practice buyer-scoped paginated read APIs, route-level frontend state, and consistency between buyer, seller, and admin refund views.

## Context

Milestone 18 added refund request creation from delivered orders, refund approval and rejection, refund-aware order/payment states, and buyer refund status display in My Orders.

Milestone 19 added seller and admin refund operation APIs and pages:

```text
GET /api/seller/refund-requests?status=REQUESTED&page=0&size=10
GET /api/admin/refund-requests?status=REQUESTED&page=0&size=10

POST /api/seller/refund-requests/{refundRequestId}/approve
POST /api/seller/refund-requests/{refundRequestId}/reject
POST /api/admin/refund-requests/{refundRequestId}/approve
POST /api/admin/refund-requests/{refundRequestId}/reject
```

The current seller page uses:

```text
/me/refunds
```

That route is better suited to buyer expectations. Milestone 20 moves seller refund operations to a sales-scoped route and uses `/me/refunds` for buyer refund history.

## Scope

In scope:

- Add a buyer-scoped refund request list API.
- Return only refund requests created by the authenticated buyer.
- Use paginated newest-first responses.
- Support optional status filtering for `REQUESTED`, `APPROVED`, and `REJECTED`.
- Use all statuses as the buyer page default.
- Add `sellerId` and `sellerNickname` to refund request responses.
- Add a buyer refund history page at `/me/refunds`.
- Move seller refund operations from `/me/refunds` to `/me/sales/refunds`.
- Keep admin refund operations at `/admin/refunds`.
- Preserve the existing My Orders refund request entry point and refund status display.
- Show buyer-facing context: refund request id, order id, product title, seller nickname, reason, status, requested at, handled at, and reject reason.
- Link refund rows to the related product detail page.
- Link refund rows to the existing My Orders page for order context.
- Add backend API tests for buyer scoping, pagination, status filtering, ordering, and response fields.
- Run backend tests, web build, and repository hygiene verification.

Out of scope:

- A separate refund detail page.
- Buyer refund cancellation.
- Buyer refund editing or reopening.
- Return shipping workflow.
- Partial refund.
- Real payment gateway refund integration.
- Evidence upload.
- Full dispute mediation.
- Product relisting after refund.
- Refund-related review rules.

## Route Design

Buyer refund history uses:

```text
/me/refunds
```

Seller refund operations move to:

```text
/me/sales/refunds
```

Admin refund operations remain:

```text
/admin/refunds
```

Navigation labels should make the distinction clear:

- Buyer page: `환불 내역`
- Seller page: `판매 환불 관리`
- Admin page: `관리자 환불`

The route move keeps `/me/refunds` aligned with the signed-in buyer's own refund history. It also places seller refund operations under the existing sales mental model next to sales, settlements, and reports.

## Backend API Design

Add a buyer read endpoint:

```text
GET /api/refund-requests/me?status=REQUESTED&page=0&size=10
GET /api/refund-requests/me?page=0&size=10
```

Authentication is required. The controller must use the authenticated member id as the buyer id and must not accept a buyer id parameter.

`status` is optional:

- `status=REQUESTED` returns pending refund requests.
- `status=APPROVED` returns approved refund requests.
- `status=REJECTED` returns rejected refund requests.
- Omitting `status` returns all refund requests created by the authenticated buyer.

The response should use the same Spring `Page` shape already used by seller and admin refund request APIs.

The effective ordering should be explicit:

```text
requestedAt desc, id desc
```

This keeps the read model stable even when callers omit sort parameters.

## Backend Data And Query Design

Extend `RefundRequestResponse` with seller information:

```text
id
orderId
productId
productTitle
buyerId
buyerNickname
sellerId
sellerNickname
reason
status
requestedAt
handledById
handledAt
rejectReason
```

The same response type should remain shared by buyer, seller, and admin refund request reads. Seller/admin pages can ignore seller fields if they do not need them immediately.

Add a repository query:

```text
Page<RefundRequest> findBuyerRequests(Long buyerId, RefundRequestStatus status, Pageable pageable)
```

The query condition is:

```text
r.buyer.id = :buyerId
```

The query should load the associations required by `RefundRequestResponse` without N+1 reads:

- `order`
- `order.product`
- `order.product.seller`
- `buyer`
- `handledBy`

Add a service method:

```text
Page<RefundRequestResponse> findBuyerRequests(Long buyerId, RefundRequestStatus status, Pageable pageable)
```

It maps the repository page with `RefundRequestResponse::from`.

## Web API Design

Extend the existing refund API module:

```text
web/src/features/refunds/refundApi.ts
```

Add:

```text
getMyRefundRequests({ status, page, size })
```

The existing frontend filter type can stay shared:

```text
REQUESTED
APPROVED
REJECTED
ALL
```

`ALL` remains frontend-only and omits the `status` query parameter.

Add `sellerId` and `sellerNickname` to the shared `RefundRequest` type.

## Web Page Design

Add a buyer refund history page:

```text
web/src/pages/MyRefundHistoryPage.tsx
```

The page should use `RequireAuth` through the router.

The buyer page should show:

- Status filter controls for all, requested, approved, and rejected.
- Default filter `ALL`.
- Paginated refund request records.
- Loading, empty, and error states using existing shared UI patterns where possible.
- Refund request id.
- Order id.
- Product title.
- Seller id and seller nickname.
- Refund reason.
- Status label.
- Requested at.
- Handled at.
- Reject reason when present.
- Link to `/products/{productId}`.
- Link to `/me/orders`.

The buyer page must not show approve or reject actions.

The existing seller operation page can keep its current behavior, but its route should change to `/me/sales/refunds`. If the implementation cost is low, rename the page file/component from `MyRefundRequestsPage` to `SellerRefundRequestsPage` so the code matches the route role.

## Error Handling

Backend error handling should reuse existing platform behavior:

- Missing authentication uses the existing authentication error flow.
- Invalid `status` enum values use the existing request binding error flow.
- Invalid pagination parameters use the existing pageable binding behavior.

The buyer list endpoint does not expose individual refund request lookup, so it does not need a refund-request-not-found path.

Frontend error handling should show query errors in the page without clearing the selected filter or current page.

## Testing

Backend tests should extend refund API coverage.

Required API coverage:

- A buyer can list their own refund requests as a paginated response.
- A buyer cannot see another buyer's refund requests.
- `REQUESTED` filter returns only requested refunds.
- `APPROVED` filter returns only approved refunds.
- `REJECTED` filter returns only rejected refunds.
- Omitting `status` returns all refunds for the buyer.
- Page size, total elements, total pages, and page number are correct.
- Sorting is `requestedAt desc, id desc`.
- Response includes `sellerId` and `sellerNickname`.
- Response still includes `buyerId`, `buyerNickname`, handling fields, and reject reason.

All new JUnit `@Test` method names must be Korean with underscores.

Web verification:

```powershell
cd web
npm run build
```

Backend verification:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```

If `C:\java\jdk-21` is unavailable, remove `JAVA_HOME` and let Gradle toolchain resolution provide JDK 21.

Repository hygiene:

```powershell
git diff --check
```

## Implementation Notes

Use an isolated worktree before implementation work:

```text
C:\dev\jpa-study\.worktrees\milestone-20-buyer-refund-history
```

Recommended branch:

```text
codex/milestone-20-buyer-refund-history
```

Do not stage, overwrite, reset, or discard the existing local-only `backend/src/main/resources/application.yaml` change in the main checkout.

Keep roadmap, design spec, implementation plan, and handoff documents separate.

## Self-Review

- Scope is focused on buyer refund history and route clarification.
- The design does not introduce new refund states or mutation behavior.
- The buyer API is scoped by the authenticated member id and does not accept arbitrary buyer ids.
- The route design gives `/me/refunds` to buyer history and moves seller operations to `/me/sales/refunds`.
- The response extension adds seller context needed by the buyer page while keeping the existing shared refund response.
- Separate refund detail pages, refund cancellation/editing, return shipping, partial refund, PG integration, evidence upload, dispute mediation, relisting, and review policy changes are explicitly out of scope.
