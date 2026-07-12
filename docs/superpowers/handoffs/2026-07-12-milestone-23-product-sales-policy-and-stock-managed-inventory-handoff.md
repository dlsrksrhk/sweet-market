# Milestone 23 product sales policy and stock-managed inventory handoff

Milestone 23 adds an immutable product sales policy and a stock-managed inventory aggregate while preserving the single-item marketplace lifecycle. The final implementation baseline before this handoff is `ca521dfe031e9db70ffec065b30fa454d88271d5` (`feat: add stock inventory web operations`). The Task 7 regression and this handoff are committed together as `docs: hand off milestone 23 inventory`; the commit hash is intentionally reported outside this file because a commit cannot contain its own stable hash.

## Delivered boundary

- Existing and personal-store products use `SINGLE_ITEM`; active business-store operators may create `STOCK_MANAGED` products with an initial total and low-stock threshold. The policy is immutable after creation.
- `Inventory` owns total and reserved quantities. Available quantity is derived as total minus reserved, and total cannot fall below reservations.
- Initialization, manual adjustment, reservation, release, and shipment commitment append `InventoryAdjustment` records. The product and inventory mappings do not expose an eager adjustment collection.
- `V5__add_product_sales_policy_and_inventory.sql` backfills existing products to `SINGLE_ITEM`, adds quantity constraints, and indexes history by `(product_id, occurred_at DESC, id DESC)`.
- Buyer product, storefront, cart, and wishlist projections expose only `IN_STOCK`, `LOW_STOCK`, or `SOLD_OUT`, with a quantity only for low stock. Operator projections additionally expose total, reserved, available, and threshold values.
- The web console supports business-store policy selection, operator adjustment, paginated history, inactive-store read-only behavior, and the required query invalidations.

## Authorization and immutable-history evidence

- Inventory writes use `requireCatalogOperator`: an active OWNER or MANAGER may adjust stock, an outsider receives `STORE_ACCESS_DENIED`, and a suspended store cannot mutate inventory.
- History reads use `requireOperator`: OWNER and MANAGER may read history, including for a suspended store, while an outsider is denied.
- `StoreOperationsApiTest` covers owner/manager adjustment and history, outsider denial, suspended-store read-only behavior, validation limits, deterministic pagination, optimistic-lock conflict mapping, and rollback of inventory plus audit changes.
- The final regression creates a real initialization adjustment and verifies authenticated `DELETE /api/store-operations/{storeId}/products/{productId}/inventory/history/{id}` returns 404. Controller inspection shows only the inventory PATCH and history GET mappings; no history update or delete command is implemented.
- Pre-final-review full-run XML: `StoreOperationsApiTest` ran 27 tests with 0 failures, 0 errors, and 0 skipped. The four inventory suites ran 12 tests with 0 failures, 0 errors, and 0 skipped. The final-review focused run below supersedes the store-operation count with 28 tests.

## Order lifecycle evidence

- Direct order and cart checkout reserve one unit for `STOCK_MANAGED`; cart addition only revalidates availability.
- Created-order cancellation and approved-payment cancellation release the reservation exactly once. A failed stock-managed payment approval now rolls back the approval attempt, then cancels the still-created order and releases its reservation in a separate `REQUIRES_NEW` transaction; retries do not append another release.
- Shipment start commits the sale by decrementing total and reserved together.
- A later refund does not restore shipped inventory automatically.
- Pre-final-review full-run XML included `OrderApiTest` 11, `CartApiTest` 17, `CartCheckoutApiTest` 9, `PaymentApiTest` 9, and `RefundRequestApiTest` 30 tests, all with 0 failures, 0 errors, and 0 skipped. The final-review focused run below supersedes the payment count with 10 tests.

## Query-shape evidence

`StorefrontQueryOptimizationTest` ran both budget tests in the final full suite with 0 failures:

- Public store header plus the first 12-product page: at most 3 prepared statements and 0 collection fetches.
- Operable-store list plus summary plus the first 12-product operator page: at most 6 prepared statements and 0 collection fetches.

Both projections assert their M23 availability/inventory fields while retaining the M22 budgets. Repository and response inspection confirms that buyer/operator catalog projections join the lightweight inventory values and do not load `inventory_adjustments`.

## Final verification (2026-07-12)

Focused immutable-history regression:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.store.StoreOperationsApiTest.재고_이력은_수정과_삭제_엔드포인트를_제공하지_않는다' --rerun-tasks
```

Result: exit 0, `BUILD SUCCESSFUL in 28s`, 1 test, 0 failures.

The first complete run reached 483 tests but PostgreSQL returned `FATAL: sorry, too many clients already` while two cached Spring contexts started. The only affected classes, `ProductImageUploadApiTest` and `AdminSettlementBatchApiTest`, then passed together on an unchanged isolated rerun: 10 tests, 0 failures, exit 0, `BUILD SUCCESSFUL in 31s`. For the definitive complete run, the process-local Hikari maximum was bounded to 4 so 18 cached contexts remained below the Testcontainers PostgreSQL connection limit:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='4'
.\gradlew.bat test --rerun-tasks
```

Result: exit 0, `BUILD SUCCESSFUL in 2m 39s`; 483 tests in 69 suites, 0 failures, 0 errors, 0 skipped.

Web production build:

```powershell
cd web
npm run build
```

Result: exit 0; both TypeScript checks passed, Vite 6.4.3 transformed 138 modules and built in 1.43s.

`git diff --check` returned exit 0 with no diagnostics. The root `package-lock.json` remained untracked and was not staged.

## Known verification limitation

Task 6 could not run authenticated OWNER/MANAGER/PERSONAL browser flows or a live 390px overflow measurement because no in-app browser runtime was available. The production web build and static responsive/privacy checks passed; this handoff does not claim interactive browser evidence.

## Final-review corrections (2026-07-12)

Implementation commit: `0adc7d35e5f4e2a39e6eccac107d4da287cf60e7` (`fix: close inventory final review gaps`).

- Failed stock-managed payment approval is coordinated outside the failed transaction. The isolated approval attempt rolls back first; a `REQUIRES_NEW` cleanup then locks the still-created order, cancels it, restores availability, and appends one release audit. A repeated request remains `PAYMENT_APPROVE_NOT_ALLOWED` without another release. Single-item and already-paid behavior is unchanged.
- Wishlist add checks the buyer-safe availability projection and rejects zero stock with `WISHLIST_PRODUCT_NOT_ON_SALE`. Wishlist list uses a lightweight inventory join, computes stock-managed `ON_SALE`/`SOLD_OUT`, exposes low-stock quantity only, and the web renders `BuyerAvailabilityBadge`.
- Authorized inventory history exposes nullable `orderId`; API regression covers reservation, release, and shipment commitment, and the operator UI displays `주문 #N` for those entries.

The final-review backend verification intentionally ran relevant suites, not the full suite:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='4'
.\gradlew.bat test --tests 'com.sweet.market.payment.PaymentApiTest' --tests 'com.sweet.market.wishlist.WishlistApiTest' --tests 'com.sweet.market.store.StoreOperationsApiTest' --tests 'com.sweet.market.inventory.*' --tests 'com.sweet.market.order.OrderApiTest' --rerun-tasks
```

Result: exit 0, `BUILD SUCCESSFUL in 51s`; 79 tests in 8 suites, 0 failures, 0 errors, 0 skipped. Suite counts: Payment 10, Wishlist 18, StoreOperations 28, inventory 12, Order 11.

`cd web; npm run build` returned exit 0; TypeScript checks passed, Vite transformed 138 modules and built in 1.65s. `git diff --check` returned exit 0 with no diagnostics. The root `package-lock.json` remained untracked and unstaged.

## M24 boundary

M24 catalog discovery consumes public product/store/availability projections. It must filter hidden or unavailable products from those projections and must not join, fetch, or otherwise load inventory adjustment history as part of search, filtering, sorting, or keyset pagination. Operational quantities and responsible-operator audit data remain outside buyer discovery responses.
