# Milestone 6 Query Optimization Design

## Goal

Milestone 6 adds practical read APIs and JPA query optimization exercises.

The user-facing goal is that a member can inspect their order history and order detail through APIs. The learning goal is to observe N+1 query behavior in product, order, and settlement reads, then fix the real query paths using fetch joins, entity graphs, or DTO projections.

## Scope

In scope:

- `GET /api/orders/me`
- `GET /api/orders/{orderId}`
- Order read DTOs for list and detail responses
- `OrderQueryService`
- Product list query optimization review and improvement
- Order list/detail query optimization
- Settlement list query optimization review and improvement
- JPA lab tests that reproduce N+1 behavior
- JPA lab tests that prove optimized read paths load required data predictably

Out of scope:

- New write behavior
- Admin order lookup
- Seller order management
- Search filters beyond basic pageable order list
- Cursor pagination
- Full SQL count assertions for every API
- Batch settlement or scheduled jobs
- Frontend UI

## Current Context

The backend already has separate packages for write and read behavior in some domains.

Existing read-side pieces:

- `ProductQueryService` supports public product list and detail reads.
- `SettlementQueryService` supports seller settlement list reads.
- `OrderRepository.findWithBuyerAndProductById` loads buyer, product, seller, and product images for write response flows.

Missing read-side pieces:

- `OrderQueryService`
- order list API
- order detail API
- order list/detail response DTOs specialized for read use
- explicit JPA lab coverage for N+1 and optimized reads

## Recommended Approach

Use a hybrid design:

1. Add real order read APIs.
2. Optimize existing product and settlement read paths where needed.
3. Add JPA lab tests that intentionally demonstrate N+1 and then exercise optimized query methods.

This keeps Milestone 6 grounded in working API value while preserving the project’s JPA-study purpose.

## API Design

### Order List

Endpoint:

```text
GET /api/orders/me
```

Authentication:

- Required.
- Returns only orders where the authenticated member is the buyer.

Response:

```json
{
  "data": {
    "content": [
      {
        "id": 1,
        "productId": 10,
        "productTitle": "MacBook Pro",
        "productPrice": 2000000,
        "sellerId": 2,
        "sellerNickname": "seller",
        "status": "CONFIRMED",
        "productStatus": "SOLD_OUT",
        "orderedAt": "2026-06-09T15:00:00"
      }
    ],
    "pageable": {},
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "size": 20,
    "number": 0,
    "sort": {},
    "numberOfElements": 1,
    "first": true,
    "empty": false
  }
}
```

Use Spring `Page` response shape, matching existing product list behavior.

Default sorting:

- `id DESC`

### Order Detail

Endpoint:

```text
GET /api/orders/{orderId}
```

Authentication:

- Required.
- Buyer only.

Response:

Use the existing `OrderResponse` shape. It already includes buyer, seller, product, status, product status, and timestamps.

Error behavior:

- Missing order: `ORDER_NOT_FOUND`
- Non-buyer access: `ORDER_ACCESS_DENIED`
- Missing/invalid JWT: existing auth errors

## Query Design

### Product List

Existing public product list returns summaries for `ON_SALE` products.

Milestone 6 should inspect whether the current mapping touches seller lazily per row. If it does, optimize the query method used by `ProductQueryService.findOnSaleProducts`.

Preferred implementation:

- Add a repository method that loads the seller for listed products.
- Keep pagination support.
- Avoid collection fetch join for paged product lists because product images are not needed for summary responses.

Recommended method shape:

```java
@EntityGraph(attributePaths = "seller")
Page<Product> findByStatusOrderByIdDesc(ProductStatus status, Pageable pageable);
```

If Spring Data cannot combine the existing derived method and entity graph cleanly, introduce a separate named method with `@Query`.

### Order List

Order list needs buyer, product, and seller data. Product images are not needed.

Preferred implementation:

- `OrderRepository.findByBuyerIdOrderByIdDesc(Long buyerId, Pageable pageable)`
- Apply `@EntityGraph(attributePaths = {"product", "product.seller"})`

Reason:

- Keeps pagination simple.
- Avoids collection fetch join.
- Avoids N+1 when mapping order summaries.

### Order Detail

Order detail can use an entity graph because it is a single row.

Preferred implementation:

- Reuse or add a method that loads buyer, product, seller, and product images.
- Check ownership in `OrderQueryService`.

### Settlement List

Settlement list currently returns seller settlements. It should include order and product data without per-row lazy loads.

Preferred implementation:

- Keep `SettlementRepository.findBySellerIdOrderByIdDesc`.
- Ensure it has `@EntityGraph(attributePaths = {"order", "order.product", "seller"})`.
- Product seller is not needed when settlement seller is already loaded.

## Components

### `OrderQueryService`

Responsibilities:

- List authenticated buyer’s orders.
- Read authenticated buyer’s order detail.
- Enforce buyer-only read access.
- Return API DTOs, not entities.

### `OrderSummaryResponse`

Fields:

- `id`
- `productId`
- `productTitle`
- `productPrice`
- `sellerId`
- `sellerNickname`
- `status`
- `productStatus`
- `orderedAt`

### `OrderController`

Add:

- `GET /api/orders/me`
- `GET /api/orders/{orderId}`

Keep write endpoints in the same controller for now. Splitting `OrderQueryController` is not necessary yet because the controller is still readable and the package already groups order API concerns.

### JPA Lab Tests

Add tests under `backend/src/test/java/com/sweet/market/jpalab`.

Recommended test classes:

- `ProductQueryOptimizationTest`
- `OrderQueryOptimizationTest`
- `SettlementQueryOptimizationTest`

Each class should include:

- one test that demonstrates the lazy-loading pattern that causes N+1
- one test that uses the optimized repository/query method and maps DTOs without triggering per-row select queries

The tests should not assert exact SQL strings. They should use Hibernate `Statistics` from `EntityManagerFactory.unwrap(SessionFactory.class)` and assert broad query-count differences.

## Error Handling

No new error codes are expected.

Use existing codes:

- `ORDER_NOT_FOUND`
- `ORDER_ACCESS_DENIED`
- `AUTHENTICATION_FAILED`

If a query-specific error appears during implementation, prefer reusing existing domain errors before adding a new code.

## Testing Strategy

API tests:

- buyer can list only their orders
- buyer can view their order detail
- other member cannot view order detail
- unauthenticated order list/detail requests fail

Repository/query tests:

- optimized product list can map seller nickname without row-by-row seller selects
- optimized order list can map seller/product fields without row-by-row selects
- optimized settlement list can map order/product fields without row-by-row selects

Naming rule:

- All JUnit `@Test` method names must be Korean with underscores.
- Helper methods may remain English camelCase.

## Risks And Decisions

Pagination with fetch joins:

- Avoid collection fetch joins in paged list endpoints.
- Single-valued associations via entity graph are acceptable for paged reads.

DTO projection vs entity graph:

- Use entity graph first because it matches existing style and keeps the implementation small.
- Use DTO projection only if entity graph causes awkward pagination or excess loading.

SQL count assertions:

- Exact counts can be brittle across Hibernate versions.
- Prefer assertions that compare naive and optimized paths or verify that optimized paths stay under a small threshold.

Controller split:

- Keep order read/write endpoints in `OrderController`.
- Revisit split only if the controller becomes noisy after Milestone 6.

## Acceptance Criteria

- `GET /api/orders/me` returns a paged list for the authenticated buyer.
- `GET /api/orders/{orderId}` returns detail for the authenticated buyer.
- Other users cannot read a buyer’s order detail.
- Product, order, and settlement optimized read paths are covered by tests.
- N+1 behavior is intentionally demonstrated in JPA lab tests.
- Full backend tests pass.
- Test naming rule remains satisfied.
