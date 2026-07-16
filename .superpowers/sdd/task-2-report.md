# Task 2 Report: Public Discovery Projections

## Implemented

- Added public `GET /api/discovery/events`, `GET /api/discovery/events/{eventType}/{eventId}`, and `GET /api/discovery/popular-products` endpoints.
- Added `PROMOTION` and `COUPON` event projections with DTO-only JDBC union queries. Events require an active store when store-owned, an active buyer-visible product, and a representative image; summaries sort by end time, type, and ID.
- Added the seven-day popularity projection with `wishlist_items.created_at >= :since` and `product_view_events.viewed_at >= :since`, score `wishlist_count * 5 + view_count`, descending product-ID ties, and an eight-card limit.
- Reused `CatalogProductCardResponse` and the existing batched wishlist/cart repositories for authenticated viewers. Anonymous discovery does not request either personal-state lookup.
- Permitted the public discovery GET routes in the existing security configuration.

## Tests

- Added API coverage for weighted ranking, product/store visibility, event visibility, event detail, and anonymous card flags.
- Added an optimization test proving anonymous event reads execute one JDBC statement without collection fetches, member-coupon state, inventory-adjustment history, or paging count SQL.
- Red phase observed: the new endpoints were initially unauthorized/absent from the public security contract; production changes were then added until the focused suite passed.

## Verification

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.discovery.*' --tests 'com.sweet.market.catalog.CatalogQueryOptimizationTest' --rerun-tasks
```

Result: `BUILD SUCCESSFUL` (36s).

## Scope and concerns

- No caching or invalidation hooks were added; those remain Task 3 scope.
- The pre-existing M30 plan document was left untracked and excluded from this task commit.
