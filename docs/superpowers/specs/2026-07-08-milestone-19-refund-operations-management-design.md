# Milestone 19 Refund Operations Management Design

## Goal

Milestone 19 turns the refund handling APIs from Milestone 18 into dedicated seller and admin operation workflows.

The product goal is that sellers and admins can discover, inspect, approve, and reject refund requests from focused operation screens instead of relying on raw API calls.

The learning goal is to practice paginated operation queries, role-specific frontend workflows, mutation invalidation across related resources, and seller/admin authorization boundaries.

## Context

Milestone 18 added delivered-order refund requests, seller/admin approval and rejection, refund-aware order and payment states, buyer refund request UI, and basic seller/admin refund request list APIs.

Current refund operation APIs already exist:

```text
GET /api/seller/refund-requests?status=REQUESTED
POST /api/seller/refund-requests/{refundRequestId}/approve
POST /api/seller/refund-requests/{refundRequestId}/reject

GET /api/admin/refund-requests?status=REQUESTED
POST /api/admin/refund-requests/{refundRequestId}/approve
POST /api/admin/refund-requests/{refundRequestId}/reject
```

The current list responses are unpaged `List<RefundRequestResponse>` values. They are useful for API discovery but are not sufficient for operational screens that need pagination, filtering, and repeatable refresh after mutations.

## Scope

In scope:

- Convert seller and admin refund request list APIs from unpaged lists to paginated responses.
- Keep the existing seller and admin refund API paths.
- Support optional status filtering for `REQUESTED`, `APPROVED`, and `REJECTED`.
- Default the frontend filter to `REQUESTED`.
- Keep backend status filtering optional so omitting `status` returns all refund requests in scope.
- Sort refund requests by `requestedAt desc, id desc`.
- Add `buyerNickname` to refund request list responses.
- Add a seller refund management page at `/me/refunds`.
- Add an admin refund management page at `/admin/refunds`.
- Show enough context for an operator to decide: refund request id, order id, product title, buyer id, buyer nickname, reason, status, requested at, handled by, handled at, and reject reason.
- Let sellers and admins approve or reject `REQUESTED` refunds from the list.
- Confirm approval with a browser confirmation prompt.
- Require a 5 to 500 character rejection reason before rejection.
- Refetch or invalidate relevant refund and order queries after approval or rejection.
- Keep seller authorization restricted to refund requests for products owned by the current seller.
- Keep admin authorization unrestricted across sellers.
- Add backend tests for pagination, filtering, ordering, response fields, seller ownership, and admin access.
- Run backend test, web build, and repository hygiene verification.

Out of scope:

- Buyer refund history page separate from My Orders.
- Refund detail pages.
- Return shipping workflow.
- Partial refunds.
- Real payment gateway refund integration.
- Buyer refund cancellation.
- Product relisting after refund.
- Refund-related review rules.
- Evidence upload.
- Full dispute mediation.
- Automatic page correction when the last item on a page disappears after mutation.

## Backend API Design

The existing list routes stay in place and gain pagination parameters:

```text
GET /api/seller/refund-requests?status=REQUESTED&page=0&size=10
GET /api/admin/refund-requests?status=REQUESTED&page=0&size=10
```

`status` is optional:

- `status=REQUESTED` returns pending refund requests.
- `status=APPROVED` returns approved refund requests.
- `status=REJECTED` returns rejected refund requests.
- Omitting `status` returns all refund requests visible to the caller.

The response should use the same Spring `Page` shape already used by existing order, cart, wishlist, product, settlement, and admin operation APIs.

The controller methods should accept `Pageable` with a default page size. The effective query ordering should remain explicit in the repository query:

```text
requestedAt desc, id desc
```

This keeps the API stable even when callers omit sort parameters.

## Backend Data And Query Design

`RefundRequestResponse` should add `buyerNickname`:

```text
id
orderId
productId
productTitle
buyerId
buyerNickname
reason
status
requestedAt
handledById
handledAt
rejectReason
```

The repository list methods should move from `List<RefundRequest>` to `Page<RefundRequest>`:

```text
Page<RefundRequest> findSellerRequests(Long sellerId, RefundRequestStatus status, Pageable pageable)
Page<RefundRequest> findAdminRequests(RefundRequestStatus status, Pageable pageable)
```

Seller queries must keep the product owner condition:

```text
product.seller.id = current seller id
```

Admin queries must not apply seller ownership restrictions.

Both query variants should load the associations required by `RefundRequestResponse` without N+1 behavior:

- `order`
- `order.product`
- `order.product.seller`
- `buyer`
- `handledBy`

If `@EntityGraph` cannot be safely combined with a pageable custom query in the current code style, the implementation plan should choose the smallest proven project-local pattern that avoids N+1 reads.

## Backend Mutation Design

The existing approve and reject endpoints remain unchanged:

```text
POST /api/seller/refund-requests/{refundRequestId}/approve
POST /api/seller/refund-requests/{refundRequestId}/reject
POST /api/admin/refund-requests/{refundRequestId}/approve
POST /api/admin/refund-requests/{refundRequestId}/reject
```

Approve behavior remains:

1. Load the refund request with its order, product, seller, payment, and handler context.
2. Reject missing requests.
3. For seller handling, reject requests for products not owned by the seller.
4. Reject requests that are not in `REQUESTED`.
5. Mark the refund request as `APPROVED`.
6. Mark the order as `REFUNDED`.
7. Mark the payment as `REFUNDED`.
8. Return the updated refund request response.

Reject behavior remains:

1. Validate `rejectReason`.
2. Load the refund request with its order, product, seller, and handler context.
3. Reject missing requests.
4. For seller handling, reject requests for products not owned by the seller.
5. Reject requests that are not in `REQUESTED`.
6. Mark the refund request as `REJECTED`.
7. Return the order to `DELIVERED`.
8. Return the updated refund request response.

Milestone 19 should not introduce new refund states.

## Web API Design

Add a focused refund feature API module:

```text
web/src/features/refunds/refundApi.ts
```

It should define:

- `RefundRequestStatus`
- `RefundRequest`
- `RefundRequestPage`
- `getSellerRefundRequests({ status, page, size })`
- `getAdminRefundRequests({ status, page, size })`
- `approveSellerRefundRequest(refundRequestId)`
- `rejectSellerRefundRequest(refundRequestId, rejectReason)`
- `approveAdminRefundRequest(refundRequestId)`
- `rejectAdminRefundRequest(refundRequestId, rejectReason)`

The frontend status filter should support:

```text
REQUESTED
APPROVED
REJECTED
ALL
```

`ALL` is a frontend-only value that omits the `status` query parameter.

## Web Page Design

Add two pages:

```text
web/src/pages/MyRefundRequestsPage.tsx
web/src/pages/AdminRefundRequestsPage.tsx
```

Add routes:

```text
/me/refunds
/admin/refunds
```

Access rules:

- `/me/refunds` uses `RequireAuth`.
- `/admin/refunds` uses `RequireAdmin`.

Both pages should use the same workflow:

- Show status filter controls near the top.
- Default to `REQUESTED`.
- Show paginated refund request records.
- Show empty, loading, and error states using existing shared UI patterns where possible.
- Show approve and reject actions only for `REQUESTED` refund requests.
- Use a browser confirmation prompt for approval.
- Open an inline rejection form for rejection.
- Disable action buttons while the matching mutation is pending.
- Keep the selected filter and page after a successful mutation.

Each record should display:

- Refund request id.
- Order id.
- Product title.
- Buyer id and buyer nickname.
- Refund reason.
- Status label.
- Requested at.
- Handler id when present.
- Handled at when present.
- Reject reason when present.

The seller and admin pages may share small local helpers for labels and date formatting if that matches the current web code style. A larger abstraction should be added only if it clearly reduces duplication without making the pages harder to read.

## Mutation Invalidation

After approval or rejection succeeds, the frontend should invalidate or refetch:

- The active refund request list query for the current role, status, and page.
- Buyer/order related queries that can display refund state, especially `orders/me`.
- Admin order queries if their query keys are easy to target from the current API modules.

The implementation should prefer the existing TanStack Query invalidation style already used in the web app. It should not add a new query key framework unless the current key usage makes targeted invalidation impractical.

## Error Handling

Backend error handling should reuse the existing Milestone 18 errors and validation paths:

- Missing refund request remains the existing not-found error.
- Seller handling of another seller's refund request remains access denied.
- Handling a non-`REQUESTED` refund request remains a conflict.
- Reject reason validation uses the existing validation error path.
- Invalid enum values for `status` use the existing request binding error flow.

Frontend error handling should show mutation and query errors in the page using existing error display patterns. A failed mutation should not clear the selected filter, current page, or rejection form text.

## Testing

Backend tests should extend the existing refund API coverage.

Required API coverage:

- Seller refund list returns a paginated response.
- Seller refund list includes only refund requests for the seller's own products.
- Admin refund list includes refund requests across sellers.
- `REQUESTED` filter returns only requested refunds.
- `APPROVED` filter returns only approved refunds.
- `REJECTED` filter returns only rejected refunds.
- Omitting `status` returns all visible refund requests.
- Page size and total counts are correct.
- Sorting is `requestedAt desc, id desc`.
- Response includes `buyerNickname`.
- Seller cannot approve or reject another seller's refund request.
- Admin can approve and reject refund requests across sellers.

All new JUnit `@Test` method names must be Korean with underscores.

Web verification:

- `npm run build` passes.

Full verification:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```

```powershell
cd web
npm run build
```

```powershell
git diff --check
```

## Implementation Notes

Use an isolated worktree for implementation work:

```text
C:\dev\jpa-study\.worktrees\milestone-19-refund-operations-management
```

Recommended branch:

```text
codex/milestone-19-refund-operations-management
```

Do not stage, overwrite, reset, or discard the existing local-only `backend/src/main/resources/application.yaml` change in the main checkout.

Keep roadmap, design spec, implementation plan, and handoff documents separate.

## Self-Review

- Scope is focused on seller/admin refund operation workflows.
- The design keeps Milestone 18 refund domain transitions unchanged.
- The backend API keeps existing paths and expands only the list response shape.
- The frontend adds dedicated operation pages instead of overloading existing admin order pages.
- Buyer-specific refund history, detail pages, return shipping, partial refunds, payment gateway integration, relisting, and dispute features are explicitly out of scope.
- The design identifies required tests for pagination, filtering, ordering, response fields, ownership, and admin access.
