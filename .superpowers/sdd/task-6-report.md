# Task 6 Report: Purchase, order, payment, and inventory outcomes

## Status

Implemented M31 Task 6 with database-outbox outcome recording at existing durable boundaries and idempotent projection upserts.

- Purchase success/failure, order status, payment failure, and inventory outcome payloads are normalized.
- Store-hourly applied, realized, canceled, and refunded amounts remain separate columns and are never netted against earlier buckets.
- Promotion and coupon campaign rows use the immutable order discount snapshots; stacked orders update both campaign rows while the store order total remains store-only.
- Inventory pressure accepts state only when `incomingVersion > storedVersion`; reservation-failure counters are independent of state version.
- Low stock is exactly `STOCK_MANAGED && availableQuantity != null && availableQuantity <= 5`; `SINGLE_ITEM` is excluded.
- The 90-day maintenance refresh uses `now - 90 days` inclusively and deletes only strictly older hourly buckets.
- Existing M29 conditional stock reservation, deterministic cart locking, durable purchase idempotency, and once-only stock/coupon compensation paths were preserved.
- Payment-failure events are emitted after successful REQUIRES_NEW compensation through best-effort after-commit recording, so event failure cannot change the command result.

## TDD evidence

### Baseline

Command:

```powershell
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.operations.*' --tests 'com.sweet.market.purchase.*' --tests 'com.sweet.market.inventory.*' --tests 'com.sweet.market.refund.RefundRequestApiTest' --tests 'com.sweet.market.coupon.CouponRedemptionConcurrencyTest'
```

Result: `BUILD SUCCESSFUL` in 1m05s before Task 6 changes.

### RED 1: required types did not exist

After adding all nine required Korean-named tests, command:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.purchase.PurchaseOutcomeProjectionTest' --tests 'com.sweet.market.operations.inventory.InventoryPressureProjectionTest' --tests 'com.sweet.market.purchase.*' --tests 'com.sweet.market.inventory.*'
```

Result: `BUILD FAILED` in 5s during `compileTestJava`, for the expected missing Task 6 types:

- `PurchaseOutcomeEventFactory`
- `InventoryOutcomeEventFactory`
- `InventoryPressureMaintenanceService`

### GREEN 1 / RED 2: projections implemented, durable boundaries still absent

After implementing payloads, factories, handlers, and maintenance, command:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.purchase.PurchaseOutcomeProjectionTest' --tests 'com.sweet.market.operations.inventory.InventoryPressureProjectionTest'
```

Result: 9 tests executed, 7 passed, 2 failed in 32s for the intended remaining source-boundary behavior:

- concurrent reservation winners produced `0` purchase-success events instead of `3`;
- completed compensation produced `0` `PAYMENT_FAILED` events instead of `1`.

### GREEN 2: source boundaries implemented

The same two-class command then returned `BUILD SUCCESSFUL` in 34s with all 9 tests passing.

The required tests cover:

1. `주문_생성은_적용_할인액과_주문수를_한번_집계한다`
2. `구매확정은_실현_할인액을_확정시각_버킷에_집계한다`
3. `취소와_환불은_각각_별도_할인액으로_집계한다`
4. `품절_경쟁_패배는_SOLD_OUT으로_집계하고_재고를_음수로_만들지_않는다`
5. `결제실패_보상은_재고와_쿠폰을_한번만_복구한다`
6. `STOCK_MANAGED_수량_5이하는_저재고로_표시한다`
7. `SINGLE_ITEM은_저재고에서_제외하고_품절전환은_기록한다`
8. `낮은_version의_재고_event는_최신_projection을_덮어쓰지_않는다`
9. `_90일밖_재고실패는_recent_count에서_제외한다`

## Verification

### Required focused Task 6 and M29 regression suite

Command:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.purchase.*' --tests 'com.sweet.market.operations.inventory.*' --tests 'com.sweet.market.purchase.*' --tests 'com.sweet.market.inventory.*' --tests 'com.sweet.market.refund.RefundRequestApiTest' --tests 'com.sweet.market.coupon.CouponRedemptionConcurrencyTest'
```

Results:

- initial complete Task 6 implementation: `BUILD SUCCESSFUL` in 58s;
- fresh final run after version-boundary review: `BUILD SUCCESSFUL` in 57s (58.1s wall time).

### Full-backend regression diagnosis

Initial full command:

```powershell
.\gradlew.bat test
```

Result: 769 tests executed, 766 passed, 3 failed in 4m51s. All failures were in `OperationalProjectionCoordinatorTest` because its full Spring context discovered the two new production purchase handlers, while the test's synthetic `{}` events and receipt counts were designed only for its two controlled handlers.

The coordinator test was isolated with named `@MockitoBean` replacements for the two production handlers and committed separately:

```text
efe6484 test: isolate projection coordinator handlers
```

Focused regression-support verification:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.projection.OperationalProjectionCoordinatorTest'
```

Result: `BUILD SUCCESSFUL` in 29s.

### Fresh full-backend verification

Command:

```powershell
.\gradlew.bat test
```

Result: `BUILD SUCCESSFUL` in 4m46s (287.5s wall time); all 769 tests passed.

## Review notes

- `git diff --check` reported no whitespace errors; only the repository's existing LF-to-CRLF checkout warnings were printed.
- Pre-existing modifications to `task-2-report.md`, `task-3-report.md`, and `task-4-report.md` were deliberately excluded from both commits.
- No Kafka, broker, OLAP, authorization-by-projection, or remote calls inside commerce locks were introduced.

## Release-blocking review follow-up

The review findings were reproduced with source-boundary regression tests and fixed without changing the M29 reservation or money-separation rules.

- Added a production hourly maintenance caller using the `Asia/Seoul` scheduling zone while retaining `refresh(Instant)` as the deterministic maintenance seam.
- Operator adjustments now record `INVENTORY_OUTCOME` action `ADJUST` in the inventory/audit transaction. Source tests cover projection changes from 0 to 10 and 6 to 5, plus complete rollback when outbox recording fails.
- Compensation records `ORDER_STATUS_CHANGED/CANCELED` inside its existing `REQUIRES_NEW` transaction. `PAYMENT_FAILED` remains post-commit best effort; cancellation recorder failure rolls back order, coupon, inventory, audit, and outbox state.
- The V15 schema now includes campaign `purchase_failure_count` in prerequisite commit `df5efe5`. Failed purchases retain promotion and coupon campaign IDs across reservation rollback and increment each campaign row with the commerce store and exact failure reason.
- `last_sold_out_at` is updated only for `SOLD_OUT` actions, with `GREATEST` independently of current-state version gating. Later zero-quantity shipment observations do not overwrite it, while a late lower-version transition can still contribute its timestamp.
- Source coverage confirms an idempotent replay emits no second purchase outcome.

### Follow-up RED evidence

- `OperationsDashboardMigrationTest` failed at the new campaign-column assertion before V15 was amended.
- The inventory covering command failed at test compilation because the production maintenance scheduler did not exist.
- After the scheduler test compiled, the operator-adjustment projection test failed because no `ADJUST` outcome existed.
- The compensation rollback test failed because cancellation recording still occurred only after commit.
- The campaign source test initially observed null campaign IDs on the rolled-back `SOLD_OUT` failure.

### Follow-up verification

Fresh focused command:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.purchase.*' --tests 'com.sweet.market.operations.inventory.*' --tests 'com.sweet.market.purchase.*' --tests 'com.sweet.market.inventory.*' --tests 'com.sweet.market.refund.RefundRequestApiTest' --tests 'com.sweet.market.coupon.CouponRedemptionConcurrencyTest'
```

Result: `BUILD SUCCESSFUL` in 58s.

Fresh full-backend command:

```powershell
.\gradlew.bat test
```

Result: final fresh run `BUILD SUCCESSFUL` in 4m44s; 777 tests, 0 failures, 0 errors.
