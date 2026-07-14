# Milestone 25 promotion and price policy handoff

## Delivered compatibility coverage

- Added regressions proving that an order created under an active store-wide promotion keeps its `listPrice`, `promotionCampaignId`, `promotionDiscountAmount`, and `finalPrice` after the product's current price changes and the campaign ends.
- Payment approval verifies the gateway receives the persisted `finalPrice` (`1,500,000`), not the changed current product price (`300,000`).
- Settlement creation and the seller settlement list retain the persisted `finalPrice` (`1,500,000`).
- The refund-request flow retains the buyer order's historical price fields while the request is created and approved.

The API contract intentionally persists/exposes the campaign ID, rather than a campaign title snapshot. Campaign title is a live catalog/read-model field and is not stored on `orders`.

## Focused verification

Executed with JDK 21 at `C:\Users\kdh\.jdks\corretto-21.0.7`, `JWT_SECRET=sweet-market-local-test-secret-key-32bytes-minimum`, and `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4`:

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.payment.PaymentApiTest' --tests 'com.sweet.market.settlement.SettlementApiTest' --tests 'com.sweet.market.refund.RefundRequestApiTest' --rerun-tasks
```

Result: `BUILD SUCCESSFUL`; 50 tests total (Payment 11, Settlement 8, Refund 31), zero failures/skips.

The required cross-feature compatibility sweep also completed with the same JDK, secret, and Hikari pool configuration:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.promotion.*' --tests 'com.sweet.market.catalog.*' --tests 'com.sweet.market.product.ProductApiTest' --tests 'com.sweet.market.cart.*' --tests 'com.sweet.market.order.*' --tests 'com.sweet.market.payment.*' --tests 'com.sweet.market.settlement.*' --tests 'com.sweet.market.refund.*' --rerun-tasks
```

Result: `BUILD SUCCESSFUL`; 295 tests, 0 failures, 0 errors, and 0 skipped.

## Effective-price query-plan evidence

Docker PostgreSQL 17 was available. The pre-existing `market` database was pre-V8, so the evidence was collected without touching it in a separate `market_m25_query_plan` database: application startup applied Flyway V1–V8 and Hibernate schema initialization, then a deterministic harness seeded 10,000 on-sale products, 1,000 active business stores/store-wide campaigns, and one selected-product campaign target.

`EXPLAIN (ANALYZE, BUFFERS)` used the store catalog effective-price query with a 21-row first page and a keyset deep page (`effectivePrice > 150000 OR (effectivePrice = 150000 AND productId > 5000)`). Both statements use `LIMIT 21`, contain no `OFFSET`, and show the lateral campaign `Limit` at one candidate per product.

| Page | Planning | Execution | Representative indexes |
| --- | ---: | ---: | --- |
| First effective-price page | 2.790 ms | 50.541 ms | `idx_products_store_status_price_id`, `idx_promotion_campaigns_store_lifecycle_period`, `idx_promotion_targets_product_campaign` |
| Deep effective-price keyset page | 0.236 ms | 38.738 ms | `idx_products_store_status_price_id`, `idx_promotion_campaigns_store_lifecycle_period`, `idx_promotion_targets_product_campaign` |

The campaign index constrained `store_id`, lifecycle, and time-window predicates; the target index served the selected-product existence check. The lateral `Limit` executed once per product (10,000 loops in the harness). No additional index was added: the measured queries were bounded and the observed predicates already selected the intended indexes.

## Deferred scope

- Coupons, promotion stacking, personal-store promotions, and platform promotions remain intentionally out of scope.
- The temporary Docker database used solely for query-plan evidence is retained as `market_m25_query_plan`; the application harness on port 18080 was stopped after capture.

## Release verification

After the Task 6 web changes were committed, the following fresh release checks completed:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='4'
.\gradlew.bat test --rerun-tasks

cd ..\web
npm run build

cd ..
git diff --check
```

Results: backend `BUILD SUCCESSFUL` (572 tests, 0 failures, 0 errors, 0 skipped), web build passed (TypeScript checks and Vite production build), and `git diff --check` produced no diagnostics.
