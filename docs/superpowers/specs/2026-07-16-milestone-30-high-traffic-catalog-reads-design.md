# Milestone 30: High-Traffic Catalog Reads, Caching, And Query Measurement Design

## Purpose

M30 makes buyer catalog discovery usable under realistic read traffic without changing the M29 purchase, reservation, coupon, or compensation write invariants. Buyers discover active promotions and coupon events, see popular products, and retain the existing shareable keyset catalog experience. The team records query, cache, and load-test evidence for each performance decision.

## Confirmed Decisions

- Popularity uses the trailing seven days of signals: `wishlistCount * 5 + viewCount * 1`.
- A product-detail view is recorded for authenticated and anonymous visitors only after the detail screen renders successfully.
- The same anonymous visitor viewing the same product again within three seconds does not create another counted view.
- Anonymous visitor identity is a random, first-party, seven-day `HttpOnly` cookie. The database stores only a hash of that identifier.
- Active events include both promotion campaigns and coupon campaigns. They lead to public event detail pages; promotion content leads to product discovery and coupon content exposes claim flow.
- The first server-side cache is the public active-event summary only. It is a bounded Caffeine in-process cache with a maximum staleness of 30 seconds.
- Redis ranking, materialized ranking pipelines, and other distributed read infrastructure remain deferred. They can be considered only after M30 measurements show the selected local projection and bounded-cache design cannot meet the experiment goal.
- The load experiment uses k6 with 100 virtual users for a five-minute measured interval, after a separate warm-up. Home/catalog traffic is 70 percent and product-detail traffic is 30 percent.

## Existing Contracts To Preserve

- `GET /api/catalog/products` and `GET /api/stores/{storeId}/catalog/products` remain the existing keyset catalog contracts. Their opaque cursor, URL-backed filter/sort state, card DTO shape, and bounded personalization queries remain compatible.
- Catalog and event cards expose only buyer-visible products from active stores. Hidden, reserved/sold single items, and stock-managed products with no available quantity are excluded.
- Public cache entries never contain member-specific `wishlisted`, `carted`, coupon-claim, or other personalized state.
- Product detail, coupon claim, and checkout always re-evaluate availability, effective price, lifecycle, coupon eligibility, and purchase invariants against source-of-truth data.
- M29 durable idempotency, conditional product/inventory reservations, deterministic cart lock order, and exactly-once coupon/stock compensation are not changed.

## Read APIs And Query Boundaries

### Active events

Add `GET /api/discovery/events` for a bounded active-event summary and `GET /api/discovery/events/{eventType}/{eventId}` for an event detail. An event summary is a DTO projection, not a hydrated campaign aggregate. It includes:

- event type (`PROMOTION` or `COUPON`) and source ID;
- title, label, effective discount description, start/end time;
- store name and public store ID where applicable;
- a representative image from a currently buyer-visible product; and
- only the public event state necessary to render a card.

An event detail returns the same public summary plus its currently eligible product cards. Coupon-specific claimed state is fetched separately for an authenticated member and is never part of the shared public cache. An event with no buyer-visible product is excluded from public discovery.

The active-event summary returns at most eight cards with deterministic ordering: ending soonest first, then `PROMOTION` before `COUPON`, then source ID ascending. The detail query uses catalog-style DTO projections and must not fetch campaign targets, images, inventory history, or member collections per card.

### Popular products

Add `GET /api/discovery/popular-products` returning eight catalog-card projections. It ranks only currently buyer-visible products by:

```text
recentPopularityScore = recentWishlistCount * 5 + recentViewCount
```

`recentWishlistCount` and `recentViewCount` cover the trailing seven-day window. Ties use the product ID descending so the output is deterministic. The ranking query first identifies the eight product IDs and then uses a bounded public card projection. For an authenticated viewer, wishlist/cart flags use the existing one-batch-per-flag approach after the public card IDs are known.

### Product-detail view recording

Add public `POST /api/products/{productId}/views` for a rendered product detail to record a view. It does not change the existing product-detail `GET` endpoint. This avoids accidental writes from prefetching, retries, and bots that never render the screen.

The endpoint reads or issues the random `HttpOnly` visitor cookie. Its dedicated browser request uses `credentials: include`, and the configured CORS origins explicitly allow credentials; existing API calls do not need a global credential-mode change. JWT authentication remains header-based and stateless.

The server hashes the visitor value before persistence. It uses an atomic last-counted-view record for the `(product, visitor hash)` pair: a view event is added only when the stored timestamp is older than three seconds, including concurrent attempts. Both view events and stale deduplication rows are deleted once their timestamps are older than seven days; they are not a user-profile feature.

## Cache Design And Invalidation

The active-event summary is the only M30 server cache. It uses Caffeine with `maximumSize(1)`, a 30-second expiry, and cache statistics enabled. Its key contains no member identity, filters, or cursor because the cached result is a single public bounded summary.

Invalidate the active-event cache immediately when any of these changes can alter the public result:

- promotion or coupon campaign creation, update, schedule, pause, resume, or end;
- a campaign target becoming ineligible because its product is hidden, sold out/reserved, or stock availability changes;
- a relevant store changing public/active status; or
- a product image or visibility change that affects an event's representative card.

The 30-second expiry remains a safety bound for missed invalidation. A stale event card is acceptable briefly; an action reached from it is validated by the authoritative promotion, coupon, product, and purchase services.

## Web Experience

The home page is ordered as follows:

1. existing introduction;
2. horizontally scrolling active-event cards;
3. an eight-card popular-product grid; and
4. the existing full catalog panel.

Event and popular-product sections have fixed-shape skeletons, useful empty states, and recoverable error states to avoid layout shift. The mobile layout keeps event cards horizontally accessible and product cards in an overflow-free two-column grid. The existing catalog keeps URL-backed keyword/filter/sort/cursor state and updates only its result area when filters change.

Event cards navigate to public event detail pages. Promotion details guide the buyer to eligible products. Coupon details use a separate authenticated query/claim flow for claim state and action. Product cards retain existing product-detail, wishlist, and cart affordances.

If view recording fails, the product-detail screen remains successful; it is best-effort analytics rather than a buyer-facing dependency.

## Measurement And Database Evidence

Introduce Actuator/Micrometer instrumentation for active-event, popular-product, catalog, and product-detail request timing. Record Caffeine hit, miss, and eviction statistics. In a local experiment profile, instrument the JDBC boundary so request-level statement counts cover both Hibernate and JDBC catalog projections without changing the production response contract.

The experiment dataset and report must document:

- product/store/event/view/wishlist data volume and distribution;
- exact k6 setup, warm-up, five-minute measured run, and 100-virtual-user mix;
- p50/p95 request latency and error rate by endpoint;
- SQL statement counts and cache statistics;
- cache-disabled and cache-enabled comparison; and
- `EXPLAIN (ANALYZE, BUFFERS)` for representative global catalog, fixed-store catalog, active-event, and popular-product queries.

M24 observed that a fixed-store first page can scan other-store IDs because the route constraint arrives through the `stores` join. M30 must remeasure this query shape under its representative dataset before changing it. Any new index must support an observed predicate/order and must be added in a new Flyway migration only when the plan demonstrates need.

## Error Handling And Security

- Anonymous event/popular discovery returns only public, active, buyer-visible data.
- Event detail for an inactive, unavailable, or unknown event follows the existing structured not-found/validation policy without exposing operator-only state.
- Invalid product IDs for view recording receive the existing public product-not-found response; duplicate views within three seconds succeed without creating another event.
- Cache failure degrades to the source projection. Cache contents never authorize an action.
- View recording accepts no caller-supplied visitor identifier. Cookies are scoped to the API host, use `HttpOnly`, and use secure attributes appropriate to the deployed HTTPS environment.

## Verification

- Integration/API tests prove public visibility, store status, product availability, and authorization rules for cached and uncached discovery reads.
- Query-budget tests prove bounded statements for anonymous and authenticated popular/event card reads, no N+1 collections, and no inventory-adjustment history in paginated card queries.
- Tests cover seven-day score boundaries, wishlist/view weights, deterministic ties, anonymous and authenticated view recording, three-second concurrent deduplication, and retention cleanup.
- Cache tests prove the 30-second entry behavior, cache statistics, and targeted invalidation for campaign lifecycle, product visibility/availability/image, and store status changes.
- Web verification covers skeleton, empty/error, mobile no-overflow, URL-backed catalog filters, event navigation, and best-effort view recording.
- The documented k6 profile runs against the stated dataset and produces the before/after evidence report.
- New JUnit `@Test` methods use Korean names with underscores.

## Explicitly Out Of Scope

- Redis-based ranking, distributed cache, message broker, materialized ranking pipeline, search cluster, CDN, replica/sharding, and production topology.
- Personalized recommendations, user profiling, or caching any member-specific flag in public discovery.
- Changes to checkout, inventory reservation, idempotency, coupon redemption/compensation, or order price snapshots.
- M31 operational dashboards and any unsupported fixed requests-per-second promise.

## Completion Criteria

M30 is complete when active-event and popular-product discovery work with the stated public/read boundaries, active-event cache invalidation is verified, representative query plans and a reproducible k6 result explain the performance outcome, web build succeeds with stable responsive states, and existing purchase/coupon/catalog regressions remain compatible.
