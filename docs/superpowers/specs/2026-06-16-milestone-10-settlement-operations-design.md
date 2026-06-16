# Milestone 10 Settlement Operations Design

## Goal

Milestone 10 turns the existing settlement batch into a clearer admin operations tool.

The feature should help admins search settlements, inspect settlement context, and safely retry settlement creation for one confirmed order at a time. It should deepen JPA learning around admin filter queries, DTO projections, pagination, uniqueness guarantees, and transaction-safe retry behavior without turning the project into a full accounting system.

## Context

Milestone 9 added automatic purchase confirmation. That creates more realistic `CONFIRMED` orders that can later be settled by the existing settlement batch.

The current settlement model already has useful foundations:

- `Settlement.create(order)` creates a completed settlement only for `CONFIRMED` orders.
- `settlements.order_id` is unique through the one-to-one mapping.
- `SettlementRepository.insertIfAbsent(...)` uses PostgreSQL `on conflict (order_id) do nothing`.
- The settlement batch already supports a `forcedOrderId` job parameter internally.
- The admin batch page can run settlement batch jobs and inspect recent batch executions.

The missing part is operational clarity. Admins can run a batch, but they cannot yet search settlement records by practical filters, inspect a settlement in context, or safely retry one order from the web/API surface.

## Decisions

- Focus Milestone 10 on admin operations tooling.
- Add admin settlement search, settlement detail, and one-order retry.
- Keep the current settlement status model centered on `COMPLETED` records.
- Do not introduce a full `READY -> COMPLETED -> FAILED` settlement lifecycle in this milestone.
- Explain skipped, blocked, and failed retry outcomes through admin retry responses and batch execution history.
- Allow one-order retry only when the order is `CONFIRMED` and no settlement exists yet.
- Preserve duplicate settlement prevention as a hard invariant.
- Keep the seller settlement page working and simple.

## Non-Goals

- Real bank transfer integration.
- Accounting ledger, reversal, or adjustment records.
- Retrying already completed settlements by overwriting or duplicating them.
- External reconciliation file import.
- Production audit compliance.
- Admin member search by email or nickname; that belongs better in Milestone 11 admin operations.
- Full redesign of the admin area.

## Admin User Flow

The admin settlement area becomes a compact operations console.

An admin searches settlement records using practical filters:

```text
orderId
sellerId
status
settledFrom
settledTo
page
size
```

The search result shows a scannable list with settlement id, order id, seller, product, amount, status, and settled time. Selecting a settlement opens a detail panel with order status, confirmed time, seller summary, buyer summary, product summary, amount, and settlement metadata.

The same page also exposes one-order retry. The admin enters an order id, or uses the order id from a selected settlement context when applicable. The server checks the order and returns a clear operational result:

```text
CREATED
ALREADY_SETTLED
ORDER_NOT_CONFIRMED
ORDER_NOT_FOUND
BATCH_FAILED
```

Only `CREATED` means a new settlement was produced. The other outcomes are successful operational responses except `BATCH_FAILED`, which means the launch or execution failed unexpectedly.

## Backend API Design

Add admin-only APIs under:

```text
/api/admin/settlements
```

### Search Settlements

```text
GET /api/admin/settlements
```

Query parameters:

```text
orderId optional
sellerId optional
status optional
settledFrom optional
settledTo optional
page default 0
size default 20, max 100
```

Response should be a page-shaped DTO with list content and pagination metadata. Each row contains only the data needed for an admin operations list:

```text
settlementId
orderId
sellerId
sellerNickname
productId
productTitle
amount
status
settledAt
```

### Settlement Detail

```text
GET /api/admin/settlements/{settlementId}
```

Response includes the search row fields plus operational context:

```text
orderStatus
confirmedAt
buyerId
buyerNickname
sellerId
sellerNickname
productId
productTitle
amount
status
settledAt
```

This endpoint should avoid sensitive payment details and should not expose private credentials or tokens.

### Retry One Order

```text
POST /api/admin/settlements/retry
```

Request:

```json
{
  "orderId": 123
}
```

The service validates:

1. If the order does not exist, return `ORDER_NOT_FOUND`.
2. If a settlement already exists for the order, return `ALREADY_SETTLED`.
3. If the order status is not `CONFIRMED`, return `ORDER_NOT_CONFIRMED`.
4. Otherwise launch the existing `settlementJob` with `forcedOrderId`.

The response includes:

```text
resultCode
orderId
settlementId optional
jobExecutionId optional
message
```

If the batch path creates a settlement, the service should look up the settlement by order id after execution and include `settlementId` when available.

## Backend Components

Add a small admin query boundary rather than mixing admin read models into the seller-facing settlement API.

Recommended components:

- `AdminSettlementController`
- `AdminSettlementSearchRequest`
- `AdminSettlementSummaryResponse`
- `AdminSettlementDetailResponse`
- `AdminSettlementRetryRequest`
- `AdminSettlementRetryResponse`
- `AdminSettlementRetryResultCode`
- `AdminSettlementQueryService`
- `AdminSettlementRetryService`

The query service can use repository DTO projections or a custom query repository. The implementation should prefer projection-based reads for the search list so the API does not load large object graphs just to render rows.

The detail query can use a fetch join or projection. The important part is to avoid obvious N+1 behavior while keeping the implementation readable for a JPA learning project.

## Retry Design

Retry should remain conservative and idempotent.

The retry service should expose the existing `forcedOrderId` batch path through the admin API instead of directly inserting a settlement. This keeps manual retry behavior aligned with normal batch behavior.

The batch launch should include a unique parameter such as `requestedAt` so repeated retry attempts can create separate job executions while still relying on the settlement uniqueness constraint to prevent duplicate records.

Duplicate prevention remains layered:

- Service pre-check with `existsByOrderId`.
- Batch reader excludes already settled orders for normal runs.
- Batch writer uses `insertIfAbsent`.
- Database uniqueness on `order_id` remains the final guard.

## Web Design

Extend the existing admin settlement batch page into a settlement operations console at the current route:

```text
/admin/batches/settlements
```

The page can use tabs or clear full-width sections to keep the dense operations surface readable:

```text
Automatic purchase confirmation
Settlement batch run
Settlement search
One-order retry
Execution history
Execution detail
```

The settlement search area should use compact controls and a scannable table or table-like list. It should not become a marketing-style page. Admin workflows benefit from dense, predictable layouts.

The one-order retry area should accept an order id, show pending/error/success states, and render result codes in Korean:

```text
CREATED -> 정산이 생성되었습니다.
ALREADY_SETTLED -> 이미 정산된 주문입니다.
ORDER_NOT_CONFIRMED -> 구매 확정 상태가 아니라 정산할 수 없습니다.
ORDER_NOT_FOUND -> 주문을 찾을 수 없습니다.
BATCH_FAILED -> 정산 배치 실행에 실패했습니다.
```

After a `CREATED` result, the web app should invalidate settlement search, batch execution history, and seller settlement queries where appropriate.

## Error Handling

- Empty search results are normal and should return an empty page.
- Invalid filter values should return the existing validation error response style.
- Missing settlement detail should return a clear not-found error.
- Retry blocked by business state should return HTTP 200 with an operational result code, not a server error.
- Unexpected job launch or persistence failures should use the existing API error response style. `BATCH_FAILED` is reserved for a launched retry job that returns a failed batch execution in a controlled response.
- Non-admin and anonymous access should be blocked by existing admin security rules.

## Testing Plan

Backend tests should cover admin API behavior:

- Admin can search settlements with no filters.
- Admin can filter settlements by order id.
- Admin can filter settlements by seller id.
- Admin can filter settlements by settlement status.
- Admin can filter settlements by settled date range.
- Admin can page settlement search results.
- Admin can view settlement detail with order, seller, buyer, and product context.
- Missing settlement detail returns a not-found response.
- A confirmed unsettled order can be retried and creates one settlement.
- An already settled order returns `ALREADY_SETTLED`.
- A non-confirmed order returns `ORDER_NOT_CONFIRMED`.
- A missing order returns `ORDER_NOT_FOUND`.
- Repeating retry for the same order does not create duplicate settlements.
- Non-admin and anonymous users cannot access admin settlement APIs.

New JUnit `@Test` method names must be Korean_with_underscores.

Frontend verification should cover:

- `npm run build` succeeds.
- Admin settlement search form compiles and renders typed API data.
- Retry result codes render understandable Korean messages.
- Existing admin batch run and execution history UI still compiles.

Full backend verification should use:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Full web verification should use:

```powershell
cd web
npm run build
```

## Acceptance Criteria

- Admin can search settlement records by order id, seller id, status, settled date range, and pagination.
- Admin can inspect a settlement detail with enough order, seller, buyer, and product context to understand why it exists.
- Admin can retry settlement for one confirmed unsettled order.
- Retry returns explicit result codes for created, already settled, not confirmed, and not found cases.
- Duplicate settlement creation remains impossible.
- Existing seller settlement view still works.
- Existing settlement batch execution and history behavior still works.
- Backend tests pass with JDK 21 and `JWT_SECRET`.
- Web build passes.
