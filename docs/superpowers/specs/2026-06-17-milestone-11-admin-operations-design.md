# Milestone 11 Admin Operations Design

## Goal

Milestone 11 adds a practical admin operations console for inspecting core Sweet Market state without direct database access.

The feature should let admins search and inspect products, orders, and members, and perform one safe write operation: hiding a product. The milestone should deepen JPA learning around admin read models, filtering, projections, fetch joins, pagination, authorization boundaries, and domain-invariant-preserving write actions.

## Context

Milestones 9 and 10 made automatic purchase confirmation and settlement operations visible from the admin surface. The current admin route, `/admin/batches/settlements`, is now a compact settlement and batch operations console.

Milestone 11 should broaden the admin surface without folding unrelated responsibilities into the settlement page. The next useful slice is a read-heavy operations console across products, orders, and members, with product hiding as the only write action.

Existing foundations to reuse:

- `/api/admin/**` is already restricted to admins by Spring Security.
- The web app already has `RequireAdmin`.
- Milestone 10 established `settlement/admin` as a domain-local admin package pattern.
- Existing API responses use `ApiResponse<T>`.
- Existing list endpoints return Spring `Page` data.
- `Product.hide()` already implements the domain rule for hiding products.

## Decisions

- Add a new admin operations page at `/admin/operations`.
- Keep `/admin/batches/settlements` as the settlement and batch operations page.
- Make the top navigation's admin link point to `/admin/operations`.
- Keep Milestone 11 read-heavy.
- Include product, order, and member search plus detail.
- Include exactly one write action: admin product hide.
- Reuse the existing `Product.hide()` domain rule instead of creating admin-only product state rules.
- Keep member operations read-only.
- Keep order operations read-only.
- Use projection-oriented list queries to avoid obvious N+1 behavior.
- Keep settlement detail out of the order admin API, but expose whether a settlement exists for an order.

## Non-Goals

- Admin role management UI.
- Member suspension or moderation policy depth.
- Order state correction from the admin console.
- Full moderation workflow.
- Audit compliance or legal logs.
- Customer support messaging.
- A new admin landing page.
- A redesign of the settlement operations page.

## Backend API Design

Add three admin-only API groups.

### Admin Products

```text
GET  /api/admin/products
GET  /api/admin/products/{productId}
POST /api/admin/products/{productId}/hide
```

Search filters:

```text
sellerId optional
status optional
keyword optional
page default 0
size default 20
```

`keyword` searches product titles with partial matching. Description search is intentionally excluded in this milestone to keep query behavior simple and predictable.

Product list rows include:

```text
productId
sellerId
sellerNickname
title
price
status
thumbnailUrl optional
```

Product detail includes the list fields plus:

```text
description
imageUrls
```

Product hide returns the updated product detail. It calls `Product.hide()`.

Domain behavior:

- `ON_SALE` can become `HIDDEN`.
- `SOLD_OUT` can become `HIDDEN`.
- `HIDDEN` remains `HIDDEN`, making repeated hide requests idempotent at the state level.
- `RESERVED` fails with `PRODUCT_CHANGE_NOT_ALLOWED`, preserving the existing trading invariant.

### Admin Orders

```text
GET /api/admin/orders
GET /api/admin/orders/{orderId}
```

Search filters:

```text
buyerId optional
sellerId optional
status optional
productId optional
page default 0
size default 20
```

Order list rows include:

```text
orderId
productId
productTitle
productPrice
buyerId
buyerNickname
sellerId
sellerNickname
status
productStatus
orderedAt
```

Order detail includes the list fields plus:

```text
canceledAt
confirmedAt
settlementExists
```

The order admin API should not duplicate the full settlement detail API. Admins can use the existing settlement operations page when they need settlement-specific information.

### Admin Members

```text
GET /api/admin/members
GET /api/admin/members/{memberId}
```

Search filters:

```text
email optional
nickname optional
role optional
page default 0
size default 20
```

Email and nickname use partial matching. Email input should be normalized consistently with existing member email normalization before filtering.

Member list rows include:

```text
memberId
email
nickname
role
```

Member detail includes the list fields plus:

```text
productCount
orderCount
```

The API must not expose password hashes, credentials, tokens, or private security internals.

## Backend Components

Use domain-local admin packages:

```text
com.sweet.market.product.admin
com.sweet.market.order.admin
com.sweet.market.member.admin
```

Recommended product components:

- `AdminProductController`
- `AdminProductSearchRequest`
- `AdminProductSummaryResponse`
- `AdminProductDetailResponse`
- `AdminProductQueryService`
- `AdminProductService`

Recommended order components:

- `AdminOrderController`
- `AdminOrderSearchRequest`
- `AdminOrderSummaryResponse`
- `AdminOrderDetailResponse`
- `AdminOrderQueryService`

Recommended member components:

- `AdminMemberController`
- `AdminMemberSearchRequest`
- `AdminMemberSummaryResponse`
- `AdminMemberDetailResponse`
- `AdminMemberQueryService`

Repositories can use explicit projection queries or custom query methods. The important implementation constraint is that list queries should not load a large graph and then map it row by row.

## Web Design

Add `AdminOperationsPage` at:

```text
/admin/operations
```

The page is a dense operations console, not a landing page. It should reuse the existing admin visual language from the settlement operations screen:

- compact search forms
- table-like result lists
- detail panels
- clear loading, empty, and error states
- status badges for domain states

The page contains three sections:

```text
상품 운영
주문 조회
회원 조회
```

Product operations:

- Search by seller id, status, and keyword.
- Show product results immediately on page load using default pagination.
- Selecting a product shows detail.
- Product detail includes a hide button unless the selected product is already hidden.
- After hide succeeds, invalidate product list and product detail queries.

Order operations:

- Search by buyer id, seller id, status, and product id.
- Show order results immediately on page load using default pagination.
- Selecting an order shows lifecycle detail, including `settlementExists`.

Member operations:

- Search by partial email, partial nickname, and role.
- Show member results immediately on page load using default pagination.
- Selecting a member shows basic profile plus product and order counts.

Navigation:

- Change the existing admin nav link to `/admin/operations`.
- Add a clear link from the admin operations page to `/admin/batches/settlements`.
- Keep the settlement page route unchanged.

## Error Handling

- Non-admin access to new APIs is blocked by the existing `/api/admin/**` rule.
- Anonymous users receive the existing authentication failure response.
- Missing product detail returns `PRODUCT_NOT_FOUND`.
- Missing order detail returns `ORDER_NOT_FOUND`.
- Missing member detail returns `MEMBER_NOT_FOUND`.
- Reserved product hide returns `PRODUCT_CHANGE_NOT_ALLOWED`.
- Empty search results return an empty page.
- Invalid enum or malformed numeric filters use the existing validation and binding error style.
- Product hide should not catch and swallow unexpected persistence failures.

## Testing Plan

Backend tests should cover:

- Admin can search products with no filters.
- Admin can filter products by seller id.
- Admin can filter products by status.
- Admin can filter products by keyword.
- Admin can view product detail.
- Admin can hide a product.
- Repeated product hide leaves the product hidden.
- Reserved product hide fails with `PRODUCT_CHANGE_NOT_ALLOWED`.
- Admin can search orders with no filters.
- Admin can filter orders by buyer id.
- Admin can filter orders by seller id.
- Admin can filter orders by status.
- Admin can filter orders by product id.
- Admin can view order detail with lifecycle and settlement existence context.
- Admin can search members with no filters.
- Admin can filter members by partial email.
- Admin can filter members by partial nickname.
- Admin can filter members by role.
- Admin can view member detail with product and order counts.
- Non-admin users cannot access product, order, or member admin APIs.
- Anonymous users cannot access product, order, or member admin APIs.

New JUnit `@Test` method names must use Korean_with_underscores.

Frontend verification should cover:

- `npm run build` passes.
- `/admin/operations` is protected by `RequireAdmin`.
- Admin API client types match backend response shapes.
- Search form values convert optional numbers and enum filters correctly.
- Product hide invalidates product search and detail queries.
- Existing `/admin/batches/settlements` still builds and remains routable.

Full verification commands:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

```powershell
cd web
npm run build
```

```powershell
git diff --check
git status --short --branch --untracked-files=all
```

Do not stage or overwrite `backend/src/main/resources/application.yaml`; it has an existing local-only development change.

## Acceptance Criteria

- Admin can search products, orders, and members with practical filters.
- Admin can inspect product, order, and member detail.
- Admin can hide products through the admin API and web console.
- Product hide follows existing domain rules, including reserved product protection.
- Non-admin and anonymous users cannot access the new admin APIs.
- Product, order, and member list endpoints avoid obvious N+1 behavior.
- The admin operations page demonstrates the lookup flows without crowding the settlement operations page.
- Existing buyer, seller, settlement, and admin settlement flows still work.
- Backend tests pass with JDK 21 and `JWT_SECRET`.
- Web build passes.

## Self-Review

- The scope is one milestone: admin product, order, and member operations with one product write action.
- The design keeps admin role management, member suspension, order correction, and audit workflow out of scope.
- Product hide reuses existing domain behavior instead of introducing admin-only invariants.
- The web route separation keeps settlement operations from becoming a general-purpose admin page.
- The order detail exposes settlement existence without duplicating settlement operations.
