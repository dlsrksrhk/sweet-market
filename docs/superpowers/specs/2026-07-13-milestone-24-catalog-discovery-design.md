# Milestone 24 Catalog Discovery, Search, And Buyer UX Design

## Purpose

Milestone 24 replaces the basic buyer browse experience with a responsive, shareable catalog for global and store-scoped discovery. It preserves the existing offset-based product and storefront list APIs while adding dedicated keyset endpoints. Public discovery exposes only purchasable products from active stores and never exposes inventory operations or audit history.

## Confirmed Decisions

- Global and store-scoped catalogs move to the same keyset pagination contract in this milestone.
- Existing `GET /api/products` and existing storefront product-list contracts remain unchanged. The web catalog uses the new endpoints.
- Keyword search is case-insensitive PostgreSQL `pg_trgm` partial matching across product title and description.
- Discovery returns only computed buyer availability `IN_STOCK` or `LOW_STOCK`. Hidden products, inactive stores, single-item reserved/sold products, and zero-available stock-managed products are excluded for every filter combination.
- Product categories are a one-level enum: `COMPUTERS`, `MOBILE`, `HOME_APPLIANCES`, `VEHICLES`, `LIVING_HOBBY`, and `OTHER`. Existing products backfill to `OTHER`.
- Newest ordering is `id DESC`, preserving the current product-list meaning because `Product` does not currently guarantee a product creation timestamp.
- The initial sort modes are newest, price ascending, and price descending. Each sort has an explicit product-ID tie-breaker.

## API Boundary

The new public endpoints are:

```text
GET /api/catalog/products
GET /api/stores/{storeId}/catalog/products
```

Both consume `CatalogSearchRequest` and return `CatalogSearchResponse`. The store endpoint applies its `storeId` as an additional server-owned predicate; callers cannot override it through a query parameter.

`CatalogSearchRequest` contains these validated query parameters:

```text
keyword
category
minPrice
maxPrice
availability
salesPolicy
storeType
storeId              # global endpoint only
sort
cursor
size
```

`availability` is limited to `IN_STOCK` and `LOW_STOCK`; omitting it means both purchasable states. Prices are non-negative and `minPrice` cannot exceed `maxPrice`. Category, sales policy, store type, sort, and page size must be from the documented bounded sets.

The response contains a `content` list of `CatalogProductCardResponse`, a boolean `hasNext`, and nullable `nextCursor`. A card includes only the representative image URL, product ID/title/price/category, buyer availability and low-stock quantity where applicable, sales policy, store ID/public name/type, and buyer-specific `wishlisted`/`carted` flags. It does not include product entities, total/reserved inventory quantities, inventory-adjustment data, operator identity, or audit fields.

## Query Design

`CatalogSearchQueryService` owns public visibility, computed availability, filters, sorting, and cursor application. `CatalogCursorCodec` owns opaque signed cursor encoding and validation. `CatalogSearchRepository` uses PostgreSQL-native SQL and DTO mapping rather than adding combinations of dynamic JPQL methods to `ProductRepository`.

The primary catalog query joins `products`, `stores`, the lightweight `inventories` row, and one representative product image. It always restricts results to an active store and a visible, purchasable product. It never joins, fetches, or queries `inventory_adjustments`.

The query fetches `size + 1` rows and does not execute an offset count query. The extra row produces `hasNext`; the final returned row produces `nextCursor`.

For an authenticated viewer, the service uses the returned product ID set for at most one batched wishlist lookup and one batched cart lookup. Anonymous requests perform neither lookup. No query performs a per-card member lookup.

Keyword queries use `pg_trgm` indexes for case-insensitive partial matching against title and description. The final migration adds only indexes justified by representative `EXPLAIN ANALYZE` results for both global and store-scoped filters and sort modes; no speculative index is added for every filter field.

## Cursor Contract

Supported sort keys are:

```text
NEWEST     -> id DESC
PRICE_ASC  -> price ASC, id ASC
PRICE_DESC -> price DESC, id DESC
```

Each cursor records the sort mode, its seek values, the filter fingerprint, an expiration timestamp, and a signature. The server rejects a malformed or tampered cursor, a cursor for another sort/filter combination, or an expired cursor. A cursor is never assembled or interpreted by the web client.

Filter and sort changes clear the cursor and begin from the first page. Keyset predicates use the exact sort keys above so equal prices cannot duplicate or skip products between adjacent pages.

## Web Experience

The global catalog becomes the primary buyer browsing surface. The store catalog uses the same controls and card component while its store ID remains fixed by the route.

- Desktop presents a persistent filter area and responsive product grid.
- Mobile provides an accessible filter drawer.
- Keyword, filters, sort, and cursor are represented in the URL query string. Refreshing or sharing a URL restores the same request as far as catalog data still permits.
- The next-page action updates the cursor URL state; changing filters or sort resets it.
- Loading, no-result, invalid-filter, and stale-cursor states are distinct. A stale cursor explains that results changed and offers a restart from the first page without exposing database details.
- Cards retain existing wishlist and cart affordances using the returned personalized flags.

## Error Handling

Invalid enum values, negative prices, reversed price ranges, invalid page sizes, malformed/tampered cursors, and cursor/filter mismatches return structured validation errors with HTTP 400. An expired cursor returns a distinct structured stale-cursor error so the web can offer a restart action. An unknown or inactive store for the store-scoped catalog follows the existing public storefront error policy.

## Verification

- Integration tests cover hidden products, inactive stores, reserved/sold single items, zero-stock managed products, each filter, and representative combined filters.
- Tests cover every sort, equal-price deterministic ID ordering, first/next keyset boundaries, malformed/tampered/mismatched/expired cursors, and absence of duplicates across pages.
- Tests verify anonymous cards have no personalization, authenticated cards receive correct wishlist/cart flags, and those flags use bounded batch queries.
- Query inspection proves a catalog page does not load inventory-adjustment history or any to-many collection as part of pagination. Query-count regression tests cover anonymous and authenticated catalog requests.
- `EXPLAIN ANALYZE` on realistic fixtures documents the selected `pg_trgm` and catalog indexes for shallow and deep browsing.
- Web tests or browser verification cover desktop and mobile filters, URL restoration, loading/empty/error states, and overflow behavior.
- Existing direct product, cart, wishlist, order, store, and admin flows remain compatible because their existing list endpoints are unchanged.
- New JUnit test methods use Korean names with underscores.

## Explicitly Out Of Scope

- External search, autocomplete, semantic/vector search, personalized ranking, and recommendation systems.
- Promotions, coupons, event pricing, caching, load-test tooling, and performance dashboards.
- Cross-store checkout and shipping-fee comparison.
