# Milestone 9 Automatic Purchase Confirmation Design

## Goal

Milestone 9 adds a learning-friendly automatic purchase confirmation flow to Sweet Market.

The feature should confirm delivered orders after a practical waiting period, make the behavior visible in the admin web demo, and keep the implementation focused on JPA transactions, dirty checking, idempotent state transitions, and scheduler basics.

## Context

The current order lifecycle is:

```text
CREATED -> PAID -> SHIPPING -> DELIVERED -> CONFIRMED
```

Manual purchase confirmation already exists at:

```text
POST /api/orders/{orderId}/confirm
```

The manual path loads the order, checks buyer ownership, calls `Order.confirm()`, marks the product `SOLD_OUT`, and sets `confirmedAt`.

Delivery completion stores the completion time on `Delivery.completedAt` and moves the related order to `DELIVERED`. Settlement creation already runs separately through the settlement batch, which selects `CONFIRMED` orders by `confirmed_at`.

## Decisions

- Automatic confirmation threshold is 7 days after delivery completion.
- Scheduler execution is enabled by default only for `local` and `dev` profiles.
- A manual admin trigger and web button are included for demos and verification.
- Automatic confirmation does not create settlements directly.
- Existing manual buyer confirmation remains unchanged.
- The user-local `backend/src/main/resources/application.yaml` changes noted in the handoff should not be overwritten unless the implementation explicitly needs a safe additive setting.

## Non-Goals

- Quartz, distributed locks, or external scheduler infrastructure.
- Production-grade job monitoring.
- Business-day or holiday calculations.
- Automatic settlement creation inside the confirmation flow.
- Large frontend redesign.

## Backend Design

Add a small automatic confirmation application service, for example under `com.sweet.market.order.application`.

The service exposes a method shaped around this use case:

```text
confirmDeliveredOrders(now, limit)
```

The service calculates:

```text
deliveredBefore = now.minusDays(7)
```

It then finds orders whose delivery is `DELIVERED`, whose order status is `DELIVERED`, and whose `Delivery.completedAt` is older than `deliveredBefore`. The repository query should fetch the order and product data needed by `Order.confirm()` so that product status changes are handled through the existing domain method and JPA dirty checking.

Each eligible order is confirmed by calling `Order.confirm()`. Since the query only selects `DELIVERED` orders, re-running the operation skips already confirmed orders naturally. If an order changes state before processing, the service should skip it instead of failing the whole run.

The result DTO should stay small:

```text
confirmedCount
deliveredBefore
thresholdDays
executedAt
```

## Scheduler Design

Add Spring scheduling support and a scheduler component that calls the automatic confirmation service.

The scheduler should be active by default for `local` and `dev` profiles only. It should also be controlled by a property so tests and unexpected environments can disable it explicitly.

Recommended property shape:

```yaml
market:
  order:
    auto-confirm:
      enabled: true
      threshold-days: 7
      limit: 100
      fixed-delay: 1h
```

Implementation may adjust exact property names to match existing Spring Boot conventions, but the settings must remain simple and discoverable.

## Admin API Design

Add an admin-only manual trigger endpoint:

```text
POST /api/admin/orders/auto-confirm
```

The endpoint calls the same service as the scheduler and returns the result DTO.

Security should rely on the existing `/api/admin/**` rule, so non-admin users and anonymous users remain blocked by the current security configuration.

## Web Design

Add a compact admin panel for automatic purchase confirmation. The existing admin settlement batch page can either gain a small adjacent section or the app can add a separate admin operations page if that better fits the current routes.

The first implementation should prefer the smallest clear surface:

- Button: `자동 구매 확정 실행`
- Result fields: confirmed count, threshold date/time, threshold days, executed date/time
- Error state using the existing API error pattern

The web demo should make it possible to run automatic confirmation, then inspect order or settlement flows using existing pages.

## Data Flow

```text
Scheduler or admin trigger
-> automatic confirmation service
-> query delivered deliveries older than 7 days
-> load related orders/products
-> call Order.confirm()
-> transaction commits order CONFIRMED, product SOLD_OUT, confirmedAt set
-> existing settlement batch can later settle confirmed orders
```

## Error Handling

- No eligible orders is a successful run with `confirmedCount = 0`.
- Ineligible state changes discovered during processing are skipped.
- Unexpected persistence or infrastructure errors should fail the run and be visible to logs/API callers.
- Manual admin trigger failures should use the existing API error response style.

## Testing Plan

Backend tests should cover:

- Delivered order older than 7 days is automatically confirmed.
- Delivered order newer than 7 days remains delivered.
- Already confirmed orders are skipped.
- Canceled, paid, or shipping orders are skipped.
- Running the service twice does not duplicate side effects.
- Product status changes to `SOLD_OUT` through `Order.confirm()`.
- Admin trigger requires admin access through existing security rules.

New JUnit `@Test` method names must be Korean_with_underscores.

Frontend verification should cover:

- `npm run build` succeeds.
- Admin can run the manual trigger and see the result fields.

Full backend verification should use:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

## Acceptance Criteria

- Delivered orders older than 7 days are automatically confirmed.
- Recent delivered orders are not confirmed.
- Confirmed, canceled, and otherwise ineligible orders are skipped safely.
- Re-running the scheduler or manual trigger is idempotent.
- Existing manual buyer confirmation still works.
- Scheduler is enabled by default only in `local` and `dev`.
- Admin can trigger automatic confirmation from the web demo.
- Automatic confirmation prepares orders for the existing settlement batch without creating settlements directly.
