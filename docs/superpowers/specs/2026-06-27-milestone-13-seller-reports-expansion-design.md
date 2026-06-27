# Milestone 13 Seller Reports Expansion Design

## Goal

Milestone 13 expands the seller reports page from a static dashboard into a practical seller analytics surface.

The feature should let a seller choose a date range, inspect sales and settlement performance for that range, see which products are performing best, and review recent report-driving records without leaving the reports page. The milestone should deepen JPA learning around bounded date filters, aggregate queries, DTO projections, ranking queries, list projections, and seller-scoped reporting.

## Context

Milestone 12 added:

```text
GET /api/seller/reports/dashboard
/me/reports
```

That dashboard currently shows all-time summary metrics, recent 30-day metrics, product status distribution, and order status distribution. It is intentionally read-only and has no custom date range, ranking, trend, or drill-down data.

Milestone 13 should build on the existing seller report package instead of replacing it. The existing dashboard API remains useful as the top-level overview. The new work adds a period report that the web page can request when the seller changes filters.

## Decisions

- Keep `GET /api/seller/reports/dashboard` unchanged for the top summary.
- Add a new seller-scoped period report API.
- Default the period report to the last 30 days, including today.
- Allow custom `from` and `to` dates with inclusive local-date semantics.
- Limit the accepted period to at most 180 days.
- Include period summary, product sales ranking, recent confirmed sales, recent settlements, and daily sales trend data in one response.
- Keep the milestone read-only.
- Keep all APIs seller-scoped by authentication; the client never sends a seller id.
- Keep the first version local-time based and aligned with the current app's `LocalDate` and `LocalDateTime` usage.
- Use DTO projection queries instead of loading large entity graphs for report data.
- Improve the `/me/reports` frontend to support filters, rankings, trend display, and recent report rows.

## Non-Goals

- CSV or Excel export.
- Admin reporting.
- Buyer-facing reports.
- Caching or materialized reporting tables.
- New settlement lifecycle states.
- New order state transitions.
- Product image upload or product image UX changes.
- Wishlist, cart, review, cancellation, or refund features.
- External analytics infrastructure.
- A large charting library.

Product image upload and broader product UX improvements should be handled in a later product-focused milestone.

## Backend API Design

Add a period report endpoint:

```text
GET /api/seller/reports/period?from=2026-06-01&to=2026-06-30
```

Query parameters:

```text
from optional ISO local date
to optional ISO local date
```

Default behavior:

- If both dates are omitted, use the last 30 days including today.
- If one date is provided without the other, return validation error.
- If `from` is after `to`, return validation error.
- If the range is longer than 180 days, return validation error.

Date boundaries:

```text
fromInclusive = from at 00:00:00
toExclusive = day after to at 00:00:00
```

Response shape:

```text
generatedAt
period
summary
productRankings
dailySales
recentSales
recentSettlements
```

`period` includes:

```text
from
to
days
```

`summary` includes:

```text
orderedCount
confirmedOrderCount
confirmedSalesAmount
completedSettlementAmount
unsettledConfirmedAmount
averageConfirmedOrderAmount
```

Metric date rules:

- `orderedCount` uses `Order.orderedAt`.
- `confirmedOrderCount`, `confirmedSalesAmount`, and `averageConfirmedOrderAmount` use `Order.confirmedAt`.
- `completedSettlementAmount` uses `Settlement.settledAt`.
- `unsettledConfirmedAmount` uses `Order.confirmedAt` and includes confirmed orders without settlement rows.

`productRankings` returns the top products in the selected period by confirmed sales amount:

```text
productId
title
thumbnailUrl
confirmedOrderCount
confirmedSalesAmount
lastConfirmedAt
```

Ranking tie-breakers:

1. `confirmedSalesAmount` descending
2. `confirmedOrderCount` descending
3. `lastConfirmedAt` descending
4. `productId` descending

Limit the first version to the top 5 products. This keeps the UI focused and avoids introducing ranking pagination before there is a clear need.

`dailySales` contains one row per day in the selected period:

```text
date
confirmedOrderCount
confirmedSalesAmount
```

Days without confirmed orders must be returned with zero values so the web page does not need to infer missing dates.

`recentSales` returns the most recent confirmed orders in the selected period:

```text
orderId
productId
productTitle
buyerNickname
amount
confirmedAt
settlementStatus
```

Limit the first version to 10 rows ordered by `confirmedAt desc`, then `orderId desc`.

`recentSettlements` returns the most recent completed or failed settlements in the selected period:

```text
settlementId
orderId
productId
productTitle
amount
status
settledAt
```

Limit the first version to 10 rows ordered by `settledAt desc`, then `settlementId desc`.

## Backend Components

Extend the existing package:

```text
com.sweet.market.seller.report
```

Recommended new or updated components:

- `SellerReportController`
- `SellerReportQueryService`
- `SellerPeriodReportRequest`
- `SellerPeriodReportResponse`
- `SellerPeriodResponse`
- `SellerPeriodSummaryResponse`
- `SellerProductRankingResponse`
- `SellerDailySalesResponse`
- `SellerRecentSaleResponse`
- `SellerRecentSettlementResponse`

The controller:

- Extracts the authenticated member id.
- Binds optional `from` and `to` request parameters.
- Delegates date validation and report assembly to the query service or a small request object.

The query service:

- Resolves the default period.
- Validates custom period input.
- Converts local dates to inclusive/exclusive `LocalDateTime` boundaries.
- Calls seller-scoped repository projection queries.
- Expands daily sales rows so every date in the range appears.
- Normalizes nullable sums and averages to zero.
- Builds one response DTO for the web page.

## Repository Query Design

Order repository period queries:

```text
countOrdersBySellerIdAndOrderedAtBetween(...)
countConfirmedOrdersBySellerIdAndConfirmedAtBetween(...)
sumConfirmedSalesAmountBySellerIdAndConfirmedAtBetween(...)
sumUnsettledConfirmedAmountBySellerIdAndConfirmedAtBetween(...)
findTopProductRankingsBySellerIdAndConfirmedAtBetween(...)
findDailyConfirmedSalesBySellerIdAndConfirmedAtBetween(...)
findRecentConfirmedSalesBySellerIdAndConfirmedAtBetween(...)
```

Settlement repository period queries:

```text
sumCompletedAmountBySellerIdAndSettledAtBetween(...)
findRecentSettlementsBySellerIdAndSettledAtBetween(...)
```

Product thumbnail handling:

- Existing product summary queries use a subquery over `ProductImage`.
- The ranking query may use the same pattern for `thumbnailUrl`.
- If thumbnail ordering is not yet explicit, use the same existing thumbnail rule as current product summaries to avoid changing image semantics in this milestone.

Daily sales query:

- The database aggregate may return only days with confirmed orders.
- The service must fill missing days with zero-count, zero-amount rows.
- Keep the maximum date range at 180 days so this expansion remains cheap and predictable.

## Validation And Errors

Add report-specific validation behavior while reusing the existing error response shape.

Validation cases:

- Only one of `from` or `to` is provided.
- `from` is after `to`.
- The date range exceeds 180 days.
- Date parameters cannot be parsed as ISO local dates.

Recommended error code:

```text
INVALID_REPORT_PERIOD
```

If the project already has a general invalid request code that fits this use case, reuse it instead of adding a new one.

Anonymous users cannot access the period report API. The endpoint must not accept a seller id from the request.

## Web Design

Keep the existing route:

```text
/me/reports
```

Enhance the page into a practical reports workspace:

```text
1. Header with generated time
2. Date range filter
3. Existing dashboard overview
4. Period summary metrics
5. Daily sales trend
6. Product ranking
7. Recent confirmed sales
8. Recent settlements
```

Date range filter:

- Default to last 30 days.
- Use simple date inputs.
- Include quick range buttons for 7 days, 30 days, and 90 days.
- Disable submit while loading.
- Show a validation message for invalid ranges before sending when possible.

Daily sales trend:

- Use a lightweight in-app visual, such as compact bars or a simple table-style trend.
- Do not add a charting library in this milestone.
- The trend should remain readable when all values are zero.

Product ranking:

- Show rank, thumbnail or fallback, product title, confirmed order count, confirmed sales amount, and last confirmed date.
- Link each product row to the product detail page.
- Show an empty state when there are no confirmed sales in the selected period.

Recent confirmed sales:

- Show order id, product title, buyer nickname, amount, confirmed date, and settlement status.
- Link product title to product detail.
- Use existing status badge style where it fits.

Recent settlements:

- Show settlement id, order id, product title, amount, status, and settled date.
- Use existing status badge style where it fits.

The page should feel like an operational dashboard: dense enough to scan, but not a decorative landing page.

## Frontend API Design

Extend:

```text
web/src/features/reports/sellerReportApi.ts
```

Add:

```text
getSellerPeriodReport(input)
```

Recommended query key shape:

```text
['seller-period-report', memberId, from, to]
```

The existing dashboard query can remain:

```text
['seller-dashboard-report', memberId]
```

The page should keep authenticated query cleanup behavior aligned with the existing auth provider.

## Testing Plan

Backend tests should cover:

- Authenticated seller can view the default period report.
- Custom date range filters period summary metrics.
- Only the logged-in seller's data appears in summaries, rankings, recent sales, daily sales, and recent settlements.
- Product ranking is ordered by amount, then count, then recent confirmation, then product id.
- Daily sales includes zero rows for days without confirmed orders.
- Recent sales are limited to 10 and ordered by confirmation time.
- Recent settlements are limited to 10 and ordered by settlement time.
- Unsettled confirmed amount includes only confirmed orders without settlement rows.
- Missing aggregate data returns zero values.
- Invalid period inputs return validation errors.
- Anonymous users cannot access the period report.

New JUnit `@Test` method names must use Korean_with_underscores.

Frontend verification should cover:

- `npm run build` passes.
- The report API types match backend response shapes.
- The date range filter state is reflected in the period report query.
- Empty ranking, daily trend, sales, and settlement states render cleanly.
- Long product titles and zero metrics do not break the layout.

Full verification commands:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
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

- Seller can open `/me/reports` and see the existing dashboard summary.
- Seller can choose a custom report period.
- Seller can see period summary metrics for the selected dates.
- Seller can see daily confirmed sales trend rows with zero-filled missing dates.
- Seller can see top product rankings for confirmed sales in the selected period.
- Seller can see recent confirmed sales and recent settlements for the selected period.
- All period report data is scoped to the logged-in seller.
- Invalid date ranges return clear validation errors.
- Missing data is represented as zero or empty lists, not `null`.
- Backend tests pass with JDK 21 and `JWT_SECRET`.
- Web build passes.

## Self-Review

- The scope is one read-only seller reports expansion milestone.
- The existing Milestone 12 dashboard remains compatible.
- The API boundary prevents client-supplied seller ids.
- The milestone deepens JPA aggregate and projection learning without adding unrelated buyer or product image features.
- The date range semantics are explicit.
- The ranking and recent lists have fixed first-version limits.
- The web work supports backend learning by making report queries visible and demoable.
