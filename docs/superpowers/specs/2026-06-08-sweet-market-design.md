# Sweet Market Design

## Purpose

Sweet Market is a practical second-hand commerce backend for studying Spring Boot, JPA, and PostgreSQL through a real transaction flow. The project should behave like a real API server first, then use that real behavior as the context for deeper JPA experiments.

The first major flow is:

```text
signup/login -> product listing -> order -> payment approval -> delivery -> purchase confirmation -> seller settlement
```

The long-term learning goal is to extend this into large batch-style work such as daily settlements, auto-confirmation, stale product cleanup, and seller reports.

## Current Project State

- Root directory: `C:\dev\jpa-study`
- Backend directory: `backend`
- Framework: Spring Boot
- Build: Gradle
- Java: toolchain configured for Java 21
- Database: PostgreSQL through Docker Compose
- Host database port: `15432`, mapped to container port `5432`
- Existing local PostgreSQL on host port `5432` is intentionally avoided

## Architecture Approach

Use a thin full-flow first, then deepen each area.

The first implementation should connect the entire commerce flow with simple but real APIs. After that, each milestone adds stronger domain rules, JPA experiments, query optimization, locking, and eventually batch processing.

Use domain-based packages:

```text
com.sweet.market
  auth
  member
  product
  order
  payment
  delivery
  settlement
  common
```

Each business domain should follow this shape where useful:

```text
api
application
domain
repository
query
```

Write operations should be domain-model oriented:

```text
Controller -> Application Service -> Entity/Repository
```

Read operations should be optimized separately:

```text
Controller -> Query Service -> Query Repository -> Response DTO
```

This keeps the project practical while making JPA tradeoffs visible. Write-side code can focus on aggregate behavior, state changes, transactions, and dirty checking. Read-side code can focus on N+1, fetch joins, DTO projection, pagination, and batch size.

## Authentication

Use practical JWT-based authentication from the beginning.

Initial auth scope:

- Member signup
- Login
- Password hashing
- JWT access token issuing
- Security filter that resolves the current authenticated member
- Authenticated APIs for product creation, order creation, payment, delivery, confirmation, and settlement

Spring Security is part of the real backend surface, not a later mock.

## Core Domain Model

Initial entities:

- `Member`
- `Product`
- `ProductImage`
- `Order`
- `Payment`
- `Delivery`
- `Settlement`

Initial relationships:

```text
Member 1 - N Product
Member 1 - N Order
Product 1 - N ProductImage
Product 1 - 1 Order
Order 1 - 1 Payment
Order 1 - 1 Delivery
Order 1 - 1 Settlement
```

Use lazy loading by default for entity associations unless there is a strong reason otherwise.

Initial statuses:

```text
ProductStatus
- ON_SALE
- RESERVED
- SOLD_OUT
- HIDDEN

OrderStatus
- CREATED
- PAID
- SHIPPING
- DELIVERED
- CONFIRMED
- CANCELED

PaymentStatus
- READY
- APPROVED
- CANCELED
- FAILED

DeliveryStatus
- READY
- SHIPPING
- DELIVERED

SettlementStatus
- READY
- COMPLETED
- FAILED
```

Treat `Order` as the center of the transaction flow:

- Creating an order reserves the product.
- Approving payment moves the order to paid.
- Starting and completing delivery move the order through shipping states.
- Confirming purchase marks the order confirmed and the product sold out.
- Settlement is created from confirmed orders.

## External Integration Boundaries

Payment and delivery should use interfaces with fake adapters first.

Examples:

```text
PaymentGateway
DeliveryClient
```

The first implementation should not call real external payment or shipping APIs. Fake adapters allow the code to model real integration boundaries, failure cases, and transaction decisions without spending early effort on provider-specific setup. Real providers can replace the fake adapters later.

## Initial API Flow

Authentication:

```text
POST /api/auth/signup
POST /api/auth/login
```

Products:

```text
POST   /api/products
GET    /api/products
GET    /api/products/{productId}
PATCH  /api/products/{productId}
DELETE /api/products/{productId}
```

Orders:

```text
POST /api/orders
GET  /api/orders/me
GET  /api/orders/{orderId}
POST /api/orders/{orderId}/cancel
POST /api/orders/{orderId}/confirm
```

Payments:

```text
POST /api/payments/{orderId}/approve
POST /api/payments/{orderId}/cancel
```

Deliveries:

```text
POST /api/deliveries/{orderId}/start
POST /api/deliveries/{orderId}/complete
```

Settlements:

```text
POST /api/settlements/orders/{orderId}
GET  /api/settlements/me
```

## Service Responsibilities

`AuthService`

- Signup
- Password hashing
- Login
- JWT issuing

`ProductService`

- Product creation
- Product modification
- Product deletion or hiding
- Product image add/remove
- Seller authorization checks

`OrderService`

- Order creation
- Order cancellation
- Purchase confirmation
- Main transaction boundary for order state changes

`PaymentService`

- Payment approval
- Payment cancellation
- Payment gateway interaction through `PaymentGateway`
- Order status transition after payment result

`DeliveryService`

- Delivery start
- Delivery completion
- Delivery client interaction through `DeliveryClient`
- Order status transition after delivery result

`SettlementService`

- Single-order settlement creation
- Duplicate settlement prevention
- Seller settlement lookup
- Future expansion into batch settlement

Query services:

- `ProductQueryService`
- `OrderQueryService`
- `SettlementQueryService`

Query services should return DTO responses and should not expose entities directly.

## Error Handling

Use a common error response shape and a global exception handler.

Initial error categories:

- Validation error
- Authentication failure
- Authorization failure
- Entity not found
- Invalid state transition
- Duplicate business action
- External adapter failure

Domain state errors should be explicit. For example, confirming an unpaid order or settling an unconfirmed order should fail with an invalid state transition error.

## Testing Strategy

Use three kinds of tests.

Functional tests:

- Auth signup/login
- JWT-protected API access
- Product registration and lookup
- Order creation and cancellation
- Payment approval/cancellation
- Delivery start/completion
- Purchase confirmation
- Settlement creation and duplicate prevention

Domain tests:

- Product status transitions
- Order status transitions
- Seller/buyer authorization rules
- Settlement eligibility rules

JPA lab tests:

```text
jpalab
  PersistenceContextTest
  DirtyCheckingTest
  FlushTest
  LazyLoadingProxyTest
  NPlusOneTest
  FetchJoinTest
  DtoProjectionTest
  CascadeOrphanRemovalTest
  OptimisticLockTest
  BulkUpdateTest
  BatchSettlementTest
```

The `jpalab` package is intentionally educational. These tests should expose and explain JPA behavior instead of only checking business features.

Key JPA experiments:

- Same entity identity inside one persistence context
- Dirty checking on product/order status changes
- Flush timing and when SQL is sent
- Lazy loading and proxy behavior
- Lazy loading outside transaction boundaries
- N+1 reproduction on order/product/member lists
- Fetch join and DTO projection comparison
- Cascade and orphan removal with product images
- Optimistic locking for concurrent orders on the same product
- Bulk update persistence context mismatch
- Large settlement processing with `flush()` and `clear()`

## Milestones

### Milestone 1: Foundation

- Common response and error structure
- Spring Security and JWT
- Signup/login
- Test fixture structure
- Base API integration test setup

Goal: authenticated API calls work.

### Milestone 2: Product Domain

- Product create/update/delete or hide
- Product image add/remove
- Product list/detail lookup
- Product image cascade and orphan removal experiment

Goal: sellers can list products and buyers can browse them.

### Milestone 3: Order Domain

- Order creation
- Order cancellation
- Product status transition from `ON_SALE` to `RESERVED`
- Product status restoration on cancellation
- Dirty checking experiment
- Optimistic locking experiment for concurrent orders

Goal: transaction reservation flow works.

### Milestone 4: Payment And Delivery

- Fake `PaymentGateway`
- Payment approval/cancellation
- Fake `DeliveryClient`
- Delivery start/completion
- Order state transition tests

Goal: external integration boundaries exist and the transaction flow reaches delivery completion.

### Milestone 5: Confirmation And Settlement

- Purchase confirmation
- Product status transition to `SOLD_OUT`
- Single settlement creation
- Duplicate settlement prevention
- Seller settlement lookup

Goal: transaction completion and seller settlement work.

### Milestone 6: Query Optimization

- Product list optimization
- Order list optimization
- Settlement list optimization
- N+1 reproduction
- Fetch join solution
- DTO projection solution
- Pagination limitations exploration

Goal: practical JPA read performance problems are visible and solvable.

### Milestone 7: Batch Expansion

- Auto-settlement for confirmed orders
- Large settlement generation
- Idempotency and retry design
- `flush()` and `clear()` experiment
- Bulk update behavior experiment
- Later Spring Batch adoption review

Goal: large batch-style work becomes a first-class learning area.

## Acceptance Criteria For The Design

- The first backend version can complete the full transaction flow through APIs.
- JWT authentication is used from the start.
- Payment and delivery use fake adapters behind interfaces.
- Write models and read queries are separated enough to study both domain modeling and query optimization.
- JPA lab tests are maintained as learning documentation.
- The design can expand into batch settlement work without changing the core transaction model.
