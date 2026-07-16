# Milestone 29 Handoff — Concurrent Purchase and Inventory Reservation

## Delivered Boundary

M29 makes direct purchase and cart checkout idempotent and contention-safe. Migration `V13__add_purchase_requests.sql` persists purchase claims, request fingerprints, execution leases, replay payloads, and expiry data.

- `POST /api/orders` and `POST /api/me/cart/checkout` require a non-blank `Idempotency-Key` header.
- The same buyer/key/fingerprint replays the stored HTTP response; the same key with a different request is rejected; an active matching request returns `409 ORDER_REQUEST_IN_PROGRESS`.
- Single-item reservation is a conditional `ON_SALE -> RESERVED` update. Stock-managed reservation is a conditional available-quantity update. Do not replace either with a read-then-write check.
- Cart checkout locks store then product rows in ascending ID order and returns an item-specific `SOLD_OUT` or `UNAVAILABLE` reason while retaining all cart rows on a failed checkout.
- Coupon reservation is created only after product reservation and is released on a failed/cancelled pre-shipping order. Inventory release occurs exactly once through the existing idempotent release path.

## Locking And Inventory Evidence

`docs/superpowers/handoffs/2026-07-16-milestone-29-locking-comparison.md` records the conditional-update versus pessimistic-lock comparison. M29 automated coverage includes direct replay, in-progress conflict, N-stock/N-winner contention, cart lock ordering, coupon preservation for losing requests, and one-time release on cancellation/payment failure.

The store catalog operator projection exposes `totalQuantity`, `reservedQuantity`, and `availableQuantity`; buyer-facing availability remains derived from available quantity.

## Verification

On 2026-07-16 in the M29 worktree:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --rerun-tasks
```

The final run completed 703 tests with one known non-M29 residual failure: `StorefrontQueryOptimizationTest.운영_상점_목록과_요약과_첫_상품_페이지는_여섯_쿼리_이내로_조회한다` expects `onSaleCount=20` but observes `22`. The storefront count query was not changed by M29. All M29-related regressions found in the initial run (missing idempotency headers and fixtures assuming implicit product reservation) were corrected without weakening the production rules.

`docker compose up -d` started PostgreSQL and Redis. Local API QA logged in as `buyer1@example.com`, created a direct order for product `370` with `Idempotency-Key: manual-m29-direct-replay-001`, and replayed it: both responses returned order `251` with `CREATED` status. The remaining contention, cart, coupon, release, and catalog checks are covered by the automated M29 concurrency and API tests; repeat them manually when browser/API exploratory QA is required.

## Keep For M30

Never weaken conditional database reservations, durable idempotency replay, the fingerprint conflict rule, or cart’s deterministic lock order. New purchase flows must use the same claim/reserve/complete protocol, and any compensation must remain idempotent.
