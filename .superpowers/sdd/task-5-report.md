# Task 5 Report: Buyer discovery screens

## Delivered

- Added the discovery API client for active events, popular products, event details, and best-effort product-view recording. View recording posts with `credentials: 'include'` and suppresses analytics failures locally.
- Added the home-page active-event strip and eight-card popular-product grid. Both have fixed-shape loading skeletons plus local empty and error states.
- Added public event routing at `/events/:eventType/:eventId`, with the event's eligible `CatalogProductCard` grid and separate authenticated coupon state/claim handling for coupon events.
- Recorded a product view only after the product-detail page has become visible; catalog URL-backed filters and cursors were not changed.
- Added responsive discovery styles: event cards remain horizontally accessible on mobile and popular products use an overflow-free two-column grid.
- Added Vitest and a Korean-named regression test proving a failed view-recording request does not reject into the product-detail UI.

## TDD evidence

1. RED: `npm test -- --run src/features/discovery/discoveryApi.test.ts` failed because `discoveryApi` did not yet exist.
2. GREEN: after implementing `recordProductView`, the focused test passed.

## Verification

```powershell
cd web
npm test
npm run build
```

Result: both commands exited `0`. Vitest ran 2 tests with 2 passes. Vite built successfully; it retained the pre-existing post-minification chunk-size warning (528.90 kB JavaScript chunk).

`git diff --check` also completed without whitespace errors.

## Review correction

- Extended `EventDetailResponse` with up to eight eligible catalog-card projections and reused the existing batched wishlist/cart flag lookups for authenticated viewers.
- Updated the event-detail screen to render those cards directly instead of a generic store link. Platform coupon events retain nullable store metadata and render as `전체 상점`, never as `/stores/null`.
- Coupon status now distinguishes authentication loading, coupon-state loading, state-query failure, and an active event that is unavailable for issuance.
- Added discovery API coverage for promotion detail cards and platform coupon detail cards with a nullable store.

### Review-fix TDD evidence

1. RED: the new discovery API tests failed because `$.data.products` was absent from both event-detail responses.
2. GREEN: after adding the event-product projection and response mapping, `DiscoveryApiTest` passed.

### Review-fix verification

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.discovery.*' --rerun-tasks

cd ../web
npm test
npm run build
```

Result: backend discovery suite `BUILD SUCCESSFUL` (37s); web Vitest 2/2 passed and the production build exited `0`.

## Scope

- The pre-existing untracked milestone plan under `docs/superpowers/plans/` was intentionally excluded from the task commit.

## P1 claim-state correction

- Replaced the event-detail page's `page=0&size=100` available-campaign scan with authenticated `GET /api/coupon-campaigns/{campaignId}/claim-state`.
- The endpoint filters by the exact campaign ID and active issue window, then returns the existing claim-state response (or `null` when it is no longer claimable). It retains the member-specific `claimed` flag without a campaign-list scan.
- Added an integration regression that creates the target campaign, then 101 newer active campaigns, and proves the target still resolves by ID. Added a web API regression asserting the exact claim-state URL and response.

### P1 verification

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponIssueApiTest' --rerun-tasks

cd ../web
npm test
npm run build
```

Result: coupon API suite `BUILD SUCCESSFUL` (36s); web Vitest 3/3 passed and the production build exited `0`.
