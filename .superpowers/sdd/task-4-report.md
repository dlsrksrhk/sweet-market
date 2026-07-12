# Task 4 Report

## Result

- `Order.create` validates visibility for both sales policies and reserves `ProductStatus` only for `SINGLE_ITEM`.
- Direct orders and cart checkout reserve one stock-managed unit after the order is saved, in the same transaction.
- Created-order cancellation and approved-payment cancellation release one reservation exactly once.
- Delivery start commits one reservation by decrementing total and reserved quantity in the same transaction.
- Cart add and checkout revalidate inventory availability without reserving at add time, including duplicate re-adds.
- Shipment is irreversible for inventory: later refund approval does not release or restore stock.
- Manual inventory adjustment behavior was not changed.

## TDD Evidence

- Initial lifecycle tests failed because stock-managed products entered `RESERVED` and inventory quantities did not change.
- Duplicate cart re-add test failed with `200 OK` after another order exhausted stock, then passed after validation moved before the idempotent early return.
- Focused lifecycle and refund regression tests cover reservation, exact-once cancellation release, shipment commitment, and refund non-restock.

## Review

- Independent review found no Critical issues.
- Two Important findings (duplicate cart re-add validation and stock-managed refund coverage) were fixed before commit.

## Verification

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --rerun-tasks --tests 'com.sweet.market.order.*' --tests 'com.sweet.market.cart.*' --tests 'com.sweet.market.payment.*' --tests 'com.sweet.market.refund.*' --tests 'com.sweet.market.inventory.*' --tests 'com.sweet.market.store.StoreOperationsApiTest'
```

Result: `BUILD SUCCESSFUL`; 178 tests, 0 failures, 0 errors, 0 skipped.

`git diff --check` passed. Root `package-lock.json` remains untracked and is excluded from the commit.
