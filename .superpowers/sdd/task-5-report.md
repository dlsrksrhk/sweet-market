# Task 5 Report

## Scope

- Added buyer-facing availability with `IN_STOCK`, `LOW_STOCK`, and `SOLD_OUT` states.
- Exposed a quantity only for low-stock buyer responses; total and reserved quantities remain absent.
- Added inventory left joins to public product, storefront, cart, and operator catalog projections.
- Computed stock-managed catalog state from available quantity while preserving single-item `RESERVED` and excluding `HIDDEN` from buyer catalogs.
- Added operator-only policy, total, reserved, available, and low-stock-threshold fields.
- Kept inventory adjustment history outside all read projections.

## TDD Evidence

- RED: the focused suite failed compilation because availability and operator inventory projection accessors did not exist.
- GREEN: focused storefront, cart, product, store-operation, and query-optimization tests passed after the minimal projections were added.
- Query optimization assertions continue to enforce the M22 query budgets and zero collection fetches.

## Verification

- `gradlew test --tests StorefrontApiTest --tests StoreOperationsApiTest --tests CartApiTest --tests ProductApiTest --tests StorefrontQueryOptimizationTest`: passed.
- A full backend run completed 69 test-class reports but two contexts initially failed because PostgreSQL rejected connections with `too many clients already`.
- The two affected classes (`SettlementBatchJobTest`, `StoreOperationsApiTest`) passed together on an immediate isolated rerun.
- `package-lock.json` was not staged.
