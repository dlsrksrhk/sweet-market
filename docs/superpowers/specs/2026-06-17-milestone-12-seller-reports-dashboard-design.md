# Milestone 12 Seller Reports Dashboard Design

## Goal

Milestone 12 adds a seller-facing reports dashboard that summarizes each seller's own market activity.

The feature should help sellers quickly understand their product status, order status, confirmed sales, completed settlements, and unsettled confirmed amount without using admin screens or direct database access. The milestone should deepen JPA learning around aggregate queries, DTO projections, seller-scoped authorization, null-safe sums, and date-windowed reporting.

## Context

Milestones 9 through 11 added automatic purchase confirmation, settlement operations, admin operations, and richer demo data. The seller-facing web app already has separate pages for sales and settlements:

```text
/me/sales
/me/settlements
```

Those pages show lists. Milestone 12 should add a separate reports page instead of overloading either list page.

Existing foundations to reuse:

- Authenticated users can already manage their own products under `/api/products/me`.
- Sellers can already view their own settlements under `/api/settlements/me`.
- Product, order, and settlement repositories already use projection queries for list/admin screens.
- The web app already has `RequireAuth`.
- Existing shared loading, empty, and error states can be reused.
- Demo data now includes enough products, orders, statuses, and settlements to make a dashboard useful.

## Decisions

- Add a new seller reports route at `/me/reports`.
- Add one dashboard API instead of separate widget APIs.
- Use `GET /api/seller/reports/dashboard`.
- Return summary metrics, product status distribution, order status distribution, period information, and generation time in one response.
- Show both all-time metrics and recent 30-day metrics.
- Use metric-specific event dates for recent 30-day values.
- Keep product status distribution as current all-time state because products do not currently have a created timestamp.
- Keep order status distribution as all-time state in the first version.
- Return zero values instead of `null` for missing counts and sums.
- Include every product and order enum status in the status distributions, even when the count is zero.
- Keep this milestone read-only.

## Non-Goals

- CSV or Excel export.
- Charts beyond simple count/amount presentation.
- Time-series graphs.
- Custom date range filters.
- Seller ranking across the marketplace.
- Admin reporting.
- Caching or materialized reporting tables.
- New product/order domain state transitions.
- Changes to settlement creation rules.

## Backend API Design

Add a seller reports API:

```text
GET /api/seller/reports/dashboard
```

The endpoint is authenticated. It uses the current member id as the seller id and never accepts a seller id from the client.

Response shape:

```text
generatedAt
period
summary
productStatusCounts
orderStatusCounts
```

`period` includes:

```text
recentDays
recentFrom
recentTo
```

`recentDays` is `30`. `recentFrom` and `recentTo` describe the inclusive local-date window shown in the UI.

The recent 30-day window includes today and the previous 29 days. Query boundaries should be:

```text
fromInclusive = recentFrom at 00:00:00
toExclusive = day after recentTo at 00:00:00
```

`summary.total` includes:

```text
activeProductCount
soldOutProductCount
confirmedOrderCount
completedSettlementAmount
unsettledConfirmedAmount
```

`summary.recent30Days` includes:

```text
orderedCount
confirmedOrderCount
completedSettlementAmount
unsettledConfirmedAmount
```

Recent 30-day date rules:

- `orderedCount` uses `Order.orderedAt`.
- `confirmedOrderCount` uses `Order.confirmedAt`.
- `completedSettlementAmount` uses `Settlement.settledAt`.
- `unsettledConfirmedAmount` uses `Order.confirmedAt`.

The unsettled confirmed amount is the sum of product prices for confirmed orders whose product belongs to the current seller and whose order has no settlement row.

Metric definitions:

- `activeProductCount` counts products in `ON_SALE`.
- `soldOutProductCount` counts products in `SOLD_OUT`.
- `confirmedOrderCount` counts orders in `CONFIRMED`.
- `completedSettlementAmount` sums settlements in `COMPLETED`.
- `unsettledConfirmedAmount` sums confirmed orders without settlement rows.

## Backend Components

Add a small seller report package:

```text
com.sweet.market.seller.report
```

Recommended components:

- `SellerReportController`
- `SellerReportQueryService`
- `SellerDashboardReportResponse`
- `SellerReportSummaryResponse`
- `SellerReportPeriodResponse`
- `SellerStatusCountResponse`

The controller extracts the authenticated member id and delegates to the query service.

The query service:

- Calculates the recent 30-day boundary.
- Calls repository aggregate queries using the seller id.
- Normalizes missing counts and sums to zero.
- Expands status counts so every enum state is present.
- Builds one response DTO for the web dashboard.

Repository aggregate methods should follow existing projection-query patterns.

Product repository queries:

```text
countBySellerIdAndStatus(...)
countProductStatusesBySellerId(...)
```

Order repository queries:

```text
countConfirmedOrdersBySellerId(...)
countOrdersBySellerIdAndOrderedAtBetween(...)
countConfirmedOrdersBySellerIdAndConfirmedAtBetween(...)
countOrderStatusesBySellerId(...)
sumUnsettledConfirmedAmountBySellerId(...)
sumUnsettledConfirmedAmountBySellerIdAndConfirmedAtBetween(...)
```

Settlement repository queries:

```text
sumCompletedAmountBySellerId(...)
sumCompletedAmountBySellerIdAndSettledAtBetween(...)
```

The sum queries should use `Long` or `Long`-compatible projections internally and convert `null` to `0L` in the query service.

## Web Design

Add a new authenticated route:

```text
/me/reports
```

Recommended files:

```text
web/src/features/reports/sellerReportApi.ts
web/src/pages/MyReportsPage.tsx
```

Add a navigation link for logged-in users:

```text
상품 / 내 주문 / 내 판매 / 정산 / 리포트
```

The page is a compact operations-style dashboard, not a marketing page.

Page sections:

```text
1. Header and period
2. All-time summary metrics
3. Recent 30-day summary metrics
4. Product status distribution
5. Order status distribution
```

All-time metric cards:

- 판매중 상품
- 판매완료 상품
- 확정 주문
- 완료 정산액
- 미정산 확정 금액

Recent 30-day metric cards:

- 신규 주문
- 확정 주문
- 완료 정산액
- 미정산 확정 금액

Status distribution sections should show readable Korean labels for product and order states while preserving the API enum values in TypeScript types.

Loading, error, and empty data behavior:

- Loading uses the existing status-text style.
- API errors use the existing error state pattern.
- A seller with no data sees zero-valued metrics and a calm empty-data message.

## Error Handling

- Anonymous users cannot access the dashboard API.
- The API never accepts a seller id from the request, preventing cross-seller reads by construction.
- Missing data returns zero counts and zero amounts.
- Every enum status appears in the response, even when count is zero.
- Unexpected persistence failures should surface through the existing API exception handling.
- The endpoint should not hide query bugs by catching broad exceptions.

## Testing Plan

Backend tests should cover:

- Authenticated seller can view dashboard summary.
- Dashboard excludes other sellers' products, orders, and settlements.
- Recent 30-day metrics use each metric's event date.
- Unsettled confirmed amount includes only confirmed orders without settlements.
- Completed settlement amount excludes non-completed settlements if any exist in the dataset.
- Seller with no sales data receives zero metrics.
- Anonymous user cannot view seller reports.
- Product status counts include all product statuses with zeroes where needed.
- Order status counts include all order statuses with zeroes where needed.

New JUnit `@Test` method names must use Korean_with_underscores.

Frontend verification should cover:

- `npm run build` passes.
- `/me/reports` is protected by `RequireAuth`.
- The seller report API types match the backend response.
- Navigation includes the reports link for logged-in users.
- Zero-valued metrics render without layout errors.

Full verification commands:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

```powershell
cd web
npm run build
```

```powershell
git diff --check
git status --short --branch --untracked-files=all
```

Do not stage or overwrite `backend/src/main/resources/application.yaml`; it has an existing local-only development change.

## Acceptance Criteria

- Seller can open `/me/reports` from the web navigation.
- Seller can see all-time summary metrics.
- Seller can see recent 30-day summary metrics.
- Seller can see product status distribution.
- Seller can see order status distribution.
- All report values are scoped to the logged-in seller.
- Recent 30-day values use metric-specific event dates.
- Missing data is represented as zero, not `null`.
- Backend tests pass with JDK 21 and `JWT_SECRET`.
- Web build passes.

## Self-Review

- The scope is one read-only milestone: a seller reports dashboard.
- The design keeps exports, charts, custom date ranges, caching, and admin reporting out of scope.
- The API boundary prevents client-supplied seller ids.
- The dashboard uses aggregate queries instead of loading large object graphs.
- The recent 30-day date semantics are explicit per metric.
- The web route is separate from existing sales and settlement list pages.
