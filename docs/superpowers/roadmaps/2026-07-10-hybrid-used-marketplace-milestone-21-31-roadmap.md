# Hybrid Used Marketplace Roadmap: Milestones 21-31

## Purpose

This document is the long-range reference for the next product phase. It starts with Store, catalog, and inventory foundations, then turns Sweet Market from a personal used-goods marketplace into a hybrid marketplace where both individual sellers and business stores sell inspected used goods.

It is deliberately a roadmap, not an implementation plan. Start each milestone in a new session by creating its own design spec, then its own implementation plan. Do not treat every API, table, or class name below as a mandatory implementation detail when the surrounding code suggests a cleaner fit.

The phase emphasizes three outcomes:

1. Make the buyer, store operator, and platform administrator web experiences feel like real commerce software.
2. Build promotion, coupon, stock, and price-calculation rules that remain correct under concurrency.
3. Use production-shaped read and write traffic to practice JPA query design, transaction boundaries, locking, indexes, and measurement.

## Product Model And Starting Assumptions

The platform serves two seller patterns at the same time:

- An individual sells a one-off used item, such as one personal MacBook.
- A business store sells multiple inspected used items of the same listing, such as thirty used MacBooks or a fleet of used cars.

The recommended normalization is that every seller operates through a store:

- A member receives one personal store by default.
- A business store has a legal business name, public brand name, verification status, and one or more store operators.
- Products belong to a store. The member who performs an operation is still recorded where auditability matters.
- Store membership distinguishes at least owner and manager responsibilities. Do not encode a business identity directly into the `Member` aggregate.

The earlier milestones in this track are expected to establish the following foundations before Milestone 25 starts:

```text
M21  Store foundation: personal and business stores, verification, store operators
M22  Storefront and core store console: public store page and basic catalog management
M23  Product sales policy and inventory: SINGLE_ITEM and STOCK_MANAGED listings
M24  Catalog discovery: responsive search, filters, sort, and keyset pagination
```

The current application uses one product per order and cart checkout creates one order for each selected cart item. Keep that boundary through Milestone 31. A coupon is therefore applied to one order for one product and its store. A future multi-item, multi-store checkout aggregate is a separate project; it must not be pulled into coupon work merely because it is a familiar commerce feature.

## Cross-Milestone Business Decisions

These decisions keep the detailed milestones consistent. A future design spec may improve their names or storage shape, but must preserve the behavior unless the roadmap is explicitly revised.

### Product Availability

`SINGLE_ITEM` products retain the used-market lifecycle:

```text
ON_SALE -> RESERVED -> SOLD_OUT
```

`STOCK_MANAGED` products expose available quantity. Their listing remains saleable while available quantity is positive and becomes sold out at zero. A canceled or failed pre-shipping order restores one unit according to the inventory reservation rules defined in Milestone 29.

### Money And Discount Calculation

- Store all monetary values in non-negative Korean won as `long` values; do not use floating point.
- Percentage discounts round down to whole won.
- A percentage coupon may have a maximum discount amount.
- A discount can never make the payable amount negative.
- Product list price remains the seller-entered reference price. A promotion price and the final order price are computed values, not destructive edits to the original list price.
- Persist price and discount snapshots with the order so later campaign edits or expiration cannot rewrite historical payments, settlements, refunds, or reports.

### Promotion And Coupon Scope

- A promotion is automatic: it changes the displayed and quoted selling price while active.
- A coupon requires issue or claim by a member and explicit selection during checkout.
- A campaign is owned by either the platform or one store. Platform administration must not impersonate a store campaign.
- A store coupon applies only to orders for its own store. A platform coupon can apply to any eligible order.
- A member can own at most one coupon from one campaign unless a later campaign type intentionally changes that rule.

### Coupon Combination Policy

Milestone 28 uses one deterministic baseline:

```text
list price
  -> one automatic promotion
  -> selected store coupon, when stackable
  -> selected platform coupon, when both coupons are stackable
  -> final payable amount
```

If a selected coupon is exclusive, it must be the only selected coupon. The client may show a suggested best discount, but the server calculates the quote and validates all eligibility rules. If multiple promotions target one product, use only the single best valid promotion. Ties resolve by explicit campaign priority and then campaign id. Promotions do not stack with one another in this phase.

### Campaign Lifecycle

Campaigns move through a clear lifecycle:

```text
DRAFT -> SCHEDULED -> ACTIVE -> ENDED
                     -> PAUSED
```

Only `ACTIVE` campaigns can issue coupons or affect a quote. Ending or pausing a campaign blocks new issuance and new use; it does not erase historical issued-coupon or order-price records. Details such as whether a previously issued coupon remains usable after a campaign pause must be fixed in each milestone design. The default recommendation is: pause blocks new issue and new redemption immediately.

## Milestone 21: Store Foundation And Business Seller Governance

### Product Goal

Every seller operates through a public store. Individuals receive a simple personal store, while businesses can operate a branded, verified store with legal business information and multiple operators. Buyers can tell who they are buying from without exposing a private individual seller's unnecessary information.

### Learning Goal

Practice aggregate boundary migration, one-to-many membership authorization, ownership backfill, role-scoped queries, compatibility-aware API evolution, and transactional creation of dependent defaults.

### Scope

- Add a `Store` aggregate with stable public identifier, display name, store type, status, introduction, and creation/update timestamps.
- Support `PERSONAL` and `BUSINESS` store types. Business stores hold legal business name, registration identifier, public brand name, and verification status. Treat the registration identifier as sensitive operational data; it is not a buyer-facing field.
- Add `StoreMembership` or an equivalent relationship. Start with `OWNER` and `MANAGER` roles; a member can operate a store only through an active membership.
- Create one personal store when a new ordinary member is created. Backfill one personal store for every existing seller member without creating duplicates on repeated startup or migration runs.
- Let a personal-store owner request conversion or creation of a business store. An administrator approves, rejects, suspends, or reactivates the business store according to an explicit status transition table.
- Move product ownership from a direct member seller relationship to a store relationship. Preserve the historical member information required by existing orders, settlements, refunds, reviews, and response contracts while each affected read model is migrated deliberately.
- Extend product and order responses with `storeId`, `storeName`, and `storeType`. Keep legacy seller id/nickname fields temporarily only when consumers still need them; document their deprecation rather than returning contradictory identities indefinitely.
- Add buyer-facing store identity to product detail and product summaries.
- Add a minimal My Store settings page and an administrator business-store review list/detail flow.

### Store States And Authorization

Recommended store status model:

```text
PERSONAL: ACTIVE, SUSPENDED
BUSINESS: PENDING_VERIFICATION, ACTIVE, REJECTED, SUSPENDED
```

- A personal store is active after automatic creation unless an administrator suspends it.
- A business store cannot publish new products or issue future store campaigns until it is `ACTIVE`.
- `OWNER` can manage store profile, membership, and future commercial policies. `MANAGER` can manage catalog and operational work but cannot change ownership or sensitive business information.
- An administrator alone approves, rejects, suspends, or reactivates a business store. A rejection includes a non-public operator-visible reason.
- Every store-owned write checks membership in the database-backed authorization path; the client route is not an authorization boundary.

### Migration And Compatibility Rules

The current application models `Product` as owned by one `Member`. M21 is allowed to make this migration because every later milestone needs store ownership, but it must protect existing commerce history:

- Existing products move to the default personal store of their current seller.
- Existing orders, payments, settlements, refunds, and reviews preserve their historical seller/member relationship until a later explicit migration has a tested reason to change it.
- New product operations resolve their operating member from store membership and their commercial owner from the store.
- Public responses should present store information where available without breaking existing buyer, seller, admin, settlement, and refund views in the same change.
- Demo data and integration-test helpers create stores through the same application path or a focused fixture factory; no test should depend on a hidden database-only shortcut.

### Web Experience

- Product cards and detail pages show a clickable store name and a compact type/trust label. Personal sellers do not expose business-style registration information.
- My Store settings separates public profile fields from business verification fields and explains status with concise operational copy.
- Business application/review is a practical form-and-detail flow, not a marketing onboarding page.
- The administrator page is a table-plus-detail operational surface with status, applicant/store identifiers, dates, reason input, and explicit approve/reject/suspend actions.
- Existing personal sellers continue to reach their current product management routes during the transition; redirect only after the replacement route is usable.

### Suggested Domain Boundaries

- `Store`: public identity, type, lifecycle, profile, and business verification fields.
- `StoreMembership`: member-to-store role and active state.
- `StoreGovernanceService`: business application and administrator state transitions.
- `StoreAccessService`: reusable store-role resolution for store-owned commands.
- Product ownership resolution: one clear relation from product to store; do not scatter `storeId` primitives across unrelated aggregates.

### JPA And Database Focus

- Enforce the one-personal-store-per-member invariant with a database constraint or an equivalent unique ownership model; do not rely only on registration code.
- Index membership lookups by member/store and store-role queries according to the final authorization access pattern.
- Use explicit projections for administrator review list pages and avoid loading all memberships or products for each store row.
- Plan the product/store migration query shape before updating repository entity graphs. Product catalog and order history must not gain a new N+1 store lookup.
- Run the migration/backfill path against PostgreSQL/Testcontainers or a production-shaped local database snapshot where feasible.

### Verification And Exit Criteria

- Tests cover automatic personal-store creation, idempotent backfill, business application, approval/rejection/suspension, and owner/manager/outsider authorization.
- Existing products appear under exactly one migrated store and remain visible through existing product/order/refund read paths.
- Existing seller-owned operations still reject an unrelated member after the ownership migration.
- Buyer product responses show consistent seller/store information; no response maps a product to a different store and member without an explicit historical reason.
- Backend full suite, web build, and `git diff --check` pass.

### Explicitly Out Of Scope

- Subscription plans, store fees, legal KYC integration, tax invoices, multiple warehouses, and staff invitation email delivery.
- Store follow/subscribe, chat, offers, and public seller reputation redesign.
- Promotions, coupons, inventory quantity, and large-scale search. These have later dedicated milestones.

### Handoff To M22

M22 uses the store public identity and operator authorization to build a buyer storefront and a durable catalog-management console. It must not create a second, parallel seller profile model.

## Milestone 22: Storefront And Core Store Operations Console

### Product Goal

Buyers can inspect a store as a coherent selling surface, and personal/business store operators can manage their catalog from a focused, efficient workspace rather than scattered product forms.

### Learning Goal

Practice role-specific read models, paged store catalog queries, operator workspace information architecture, aggregate action authorization, and frontend query invalidation across public and private views.

### Prerequisites

- M21 store, membership, business-store status, and product-to-store ownership migration.
- Existing product image, wishlist, cart, order, review, settlement, and seller report behavior remains available.

### Scope

- Add a public store route such as `/stores/{storeId}` with store profile, verification/type signal, active product summary, ratings/review summary where already supported, and paginated store catalog.
- Add store catalog filtering by product availability and a basic sort contract. Keep rich global search and keyset browsing for M24.
- Add an operator route such as `/me/store` with summary counts, product list, visibility/status controls, product creation/edit entry points, and direct links to existing sales/refund/settlement/report work.
- Add batch-safe catalog actions appropriate to the current product model, such as hide/show selected products. Do not add inventory bulk adjustment before M23 establishes its rules.
- Allow owner-managed operator membership list and manager removal, but keep owner transfer and invitation delivery outside this milestone.
- Update existing product forms and product ownership checks to use store access instead of direct seller id equality.
- Make personal and business store presentation differ only where the business data adds value; do not force a personal seller to fill in corporate-style fields.

### Storefront Rules

- Buyers see only products visible under the existing product visibility/status rules.
- A suspended or rejected business store is not presented as a normal active commercial storefront. Define whether its historical products are hidden or show a clear unavailable state during the milestone design.
- Store product count reflects buyer-visible products on public pages, not all drafts/hidden catalog rows.
- The public store page never exposes membership records, private business registration data, admin notes, or staff identifiers.
- Store operators may manage only their own store; manager permissions must be narrower than owner permissions where M21 specified that difference.

### Web Experience

- The storefront begins with actual store identity, verification signal, representative products, and useful catalog controls. It is a commerce destination, not a generic profile card.
- Store catalog cards use the same visual language as the buyer home/catalog experience so product comparison remains easy.
- The operator console is dense but readable: summary strip, status-aware product table/grid, filters, bulk action controls, and clearly separated public-store preview link.
- On mobile, catalog management collapses dense columns into scan-friendly rows without hiding status or primary actions.
- Existing seller report, sales, settlement, and refund routes become reachable from the store workspace navigation without duplicating their business logic.

### Suggested Read And Write Boundaries

- `StorefrontQueryService`: public store profile and buyer-visible product projection.
- `StoreCatalogQueryService`: role-scoped operator catalog projection, separate from public visibility filtering.
- `StoreCatalogCommandService`: product visibility and bulk-operation commands after `StoreAccessService` authorization.
- Reuse existing product application services where their aggregate rules fit. Do not make the store console directly write repository state.

### JPA And Performance Focus

- Use an explicit projection for public store catalog cards, including one representative image and store summary without loading all product images/reviews.
- Add or verify indexes for store id, product status, and the chosen deterministic catalog ordering.
- Do not apply a fetch join to a to-many image collection inside a paginated query. Use representative-image projection or a bounded second query.
- Test query count for a full public-store page and a full operator page; membership/authorization lookup must not occur once per product row.

### Verification And Exit Criteria

- Public pages show correct active-store identity and do not leak private store/operator data.
- Owner/manager/outsider permissions are covered for catalog operations and membership actions.
- Personal and business stores can create, edit, hide, and display products through the intended flows.
- Storefront pagination and operator filtering produce deterministic results with no duplicate/missing rows across page boundaries.
- Existing product detail, wishlists, cart, order, settlement, refund, and review tests keep their prior behavior.

### Explicitly Out Of Scope

- Stock quantity and inventory reservation.
- Advanced global search, recommendations, popularity, cache, and load testing.
- Promotions, coupons, events, bulk imports, CSV upload, and multi-store operator organizations.

### Handoff To M23

M23 adds a sales policy to the store-owned product catalog. The storefront and console must render the new availability distinction without hardcoding business-store assumptions into personal-store flows.

## Milestone 23: Product Sales Policy And Stock-Managed Used Inventory

### Product Goal

The marketplace supports both one-off personal used goods and repeatable inspected inventory from business stores, while preserving clear buyer availability and practical operator stock management.

### Learning Goal

Practice domain polymorphism through explicit policy rather than inheritance sprawl, inventory aggregate modeling, state transitions, validation across order/cart/product boundaries, and audit-friendly stock adjustments.

### Scope

- Add a product sales policy such as `SINGLE_ITEM` and `STOCK_MANAGED`.
- Existing products migrate to `SINGLE_ITEM` with their current `ON_SALE -> RESERVED -> SOLD_OUT` lifecycle.
- `STOCK_MANAGED` products hold a managed available quantity. Start without color/size/condition variants; one listing represents one inspected grade/specification and one quantity pool.
- Restrict stock-managed listing creation to active business stores unless the milestone design deliberately permits verified high-volume personal sellers.
- Add operator stock adjustment with required adjustment reason, resulting quantity, actor, timestamp, and optional reference note.
- Display availability on product cards/detail, cart, storefront, and operator console. Buyers may see `재고 있음`, `품절`, or a permitted low-stock count; exact quantity display is a store policy, not a default privacy requirement.
- Update cart/order prechecks so a sold-out stock-managed item cannot be added or purchased in ordinary sequential use.
- Preserve a simple sequential reserve/release behavior needed for functional ordering. M29 replaces its contention-sensitive implementation with a measured concurrency-safe strategy.
- Add inventory history page or operator panel sufficient to understand manual adjustments and order-driven reservation/release changes.

### Availability Model

Recommended minimum fields/concepts:

```text
Product sales policy: SINGLE_ITEM | STOCK_MANAGED
Inventory: available quantity, reserved quantity, version/audit boundary
Stock adjustment: before quantity, delta, after quantity, reason, actor, occurred at
```

For `SINGLE_ITEM`, the existing product status remains the availability source. For `STOCK_MANAGED`, quantity is the availability source and product status is derived or coordinated so buyer reads cannot contradict stock. The milestone design must choose one authoritative representation; do not maintain independent mutable `availableQuantity` and `ON_SALE/SOLD_OUT` fields with no invariant.

### Core Rules

- Quantity is a non-negative integer. Manual adjustment cannot reduce it below active reservations.
- A stock-managed product with zero available units is not purchasable.
- A new order reserves one unit; cancellation or eligible payment failure releases one unit exactly once.
- Inventory adjustment is blocked or requires an explicit conflict result when it would violate active reservations.
- Single-item products cannot be transformed into stock-managed products after an order exists without a separate audited migration process. For this phase, make the sales policy immutable after product publishing.
- Product visibility and inventory availability are distinct: a hidden product cannot be bought even when it has positive stock.

### Web Experience

- Product registration makes the sales policy explicit with a segmented control and shows only the relevant inputs. A personal one-off listing does not display an irrelevant quantity control.
- Business operator inventory views emphasize available, reserved, and adjustment history in a work-focused table, with an explicit adjustment modal and reason input.
- Buyer-facing availability language stays concise and does not expose internal reserved counts or operator notes.
- Cart/order flows revalidate availability and report a product becoming unavailable in a way that lets the buyer continue with other cart items.

### Suggested Domain Boundaries

- `ProductSalesPolicy`: business decision for single versus quantity-managed selling.
- `Inventory`: quantity and reservation/adjustment invariants for a stock-managed product.
- `InventoryAdjustment`: immutable audit record.
- `InventoryService`: commands for setup, adjustment, sequential reservation, and release. It should not become a general product-edit service.

### JPA And Database Focus

- Enforce one inventory row per stock-managed product and protect quantity integrity with database constraints where supported.
- Use an entity version on the inventory boundary for ordinary operator adjustment conflicts. M29 will compare this with conditional updates under high contention.
- Fetch inventory only on product/admin paths that need it; public product grids should project a lightweight availability value.
- Index inventory lookup by product id and inventory-history query by product/time. Avoid an eager audit collection on the product aggregate.

### Verification And Exit Criteria

- Existing items migrate to `SINGLE_ITEM` and retain their current reservation/confirmation behavior.
- Stock-managed products reject negative initial quantity, invalid manual adjustments, purchases at zero availability, and cross-store operator actions.
- Sequential order/cancel scenarios reserve and restore exactly one stock unit.
- Product, cart, order, storefront, and operator responses present availability consistently.
- Inventory history is immutable and attributes adjustments to the responsible operator.

### Explicitly Out Of Scope

- High-contention locking comparison, flash-sale load tests, variants/SKUs, warehouse locations, serial-number tracking, and backorders.
- Bulk file import and external ERP synchronization.
- Promotion/coupon price rules and global search.

### Handoff To M24

M24 consumes public product/store/availability projections. It must filter out unavailable or hidden products correctly and must not load the full inventory history as part of catalog search.

## Milestone 24: Catalog Discovery, Search, And Buyer UX

### Product Goal

Buyers can find used goods through a fast, credible browsing experience that distinguishes personal listings from business-store inventory and supports practical filters for condition, price, availability, seller type, and store.

### Learning Goal

Practice production-shaped catalog query contracts, dynamic predicates, DTO projections, representative-image selection, keyset pagination, URL-backed frontend state, and query-plan-based indexing.

### Scope

- Replace the basic buyer product list with a responsive catalog surface that supports keyword, category, price range, availability, sales policy, store type, and store filters.
- Add a bounded product category taxonomy appropriate for used goods, such as computers, mobile devices, home appliances, vehicles, and other. Keep it one-level in this phase.
- Add sort modes with explicit deterministic secondary keys. Recommended starting modes: newest, price low-to-high, price high-to-low, and popularity-ready placeholder only when M30 supplies real ranking data.
- Use keyset/cursor pagination for the buyer catalog and store catalog paths that support sequential browsing. Keep an explicit order/cursor contract in the URL.
- Add keyword search against title and description with the PostgreSQL capability appropriate to the project. Start with an indexed simple search if full-text relevance would distract from catalog correctness; do not introduce an external search service.
- Add clear empty, loading, invalid-filter, and stale-cursor handling states.
- Preserve wishlist/cart personalization without turning the public catalog query into a per-row member lookup.
- Add search/filter analytics only as lightweight server logs/metrics required for M30; no user profiling or tracking platform is needed.

### Catalog Rules

- Public discovery returns only buyer-visible products from active stores under the visibility and availability policies of M21-M23.
- A `SINGLE_ITEM` reserved/sold product is excluded from normal availability search but can remain visible through direct historical order links according to existing rules.
- A `STOCK_MANAGED` product is excluded from available-only search at zero purchasable quantity.
- Cursor is valid only for the selected sort and filter fingerprint. A mismatched or malformed cursor returns a structured validation result, not an arbitrary page.
- Every sort includes a stable id tie-breaker so records do not duplicate or disappear across adjacent pages when prices/timestamps match.
- The API validates price range and category values before querying; frontend controls mirror but do not replace this validation.

### Web Experience

- The catalog is the primary buyer screen, not a landing-page hero. It prioritizes product imagery, scanable price/condition/store cues, filters, sort, and stable result density.
- Desktop uses a persistent filter area and responsive grid; mobile uses an accessible filter drawer while retaining current search/sort context.
- Search, filters, sort, and cursor state appear in the URL so a shared or refreshed link reproduces the result set as closely as data changes allow.
- Product cards show representative image, title, current price, list price only when later promotions apply, availability, store brand/type, and personalized wishlist/cart affordances.
- Error and empty states tell the buyer whether no results match, the cursor is stale, or a retry is appropriate. They do not expose raw database errors.

### Suggested Query Boundaries

- `CatalogSearchRequest`: validated input with filter and cursor data.
- `CatalogProductCardResponse`: lightweight projection for the exact fields rendered by one card.
- `CatalogSearchQueryService`: public visibility, store, availability, filters, sort, and cursor rules.
- `CatalogCursorCodec`: opaque signed/validated cursor representation owned by the catalog boundary rather than assembled in the client.

### JPA And Performance Focus

- Build catalog queries with projections rather than loading `Product` entities plus images/store/inventory associations.
- Compare offset and keyset query plans at shallow and deep positions using realistic fixture volume. Document why keyset is chosen for buyer feeds and where offset remains appropriate.
- Use indexed predicates matching the final filters and sort order. Candidate indexes must be justified by `EXPLAIN ANALYZE`, not added preemptively for every field.
- Prevent N+1 queries for representative images, store identity, availability, wishlist state, and cart state. Use bounded secondary lookups for member-specific flags when a single query would multiply rows.
- Define query-count and response-time baseline measurements that M30 will later extend under load.

### Verification And Exit Criteria

- Integration tests cover visibility, active/suspended store filtering, each filter, combined filters, sort order, cursor boundaries, invalid cursor, and stable tie breaking.
- Buyer personalization is correct for the authenticated buyer and absent/anonymous-safe where required.
- Web tests or manual browser verification cover desktop/mobile filters, URL restoration, loading/empty/error states, and no layout overflow.
- Query inspection demonstrates bounded queries for a catalog page and no to-many fetch-join pagination regression.
- Existing direct product, cart, wishlist, order, store, and admin workflows remain compatible.

### Explicitly Out Of Scope

- Search cluster, external recommendation engine, semantic/vector search, autocomplete service, and personalized ranking.
- Promotions, coupons, first-come events, cache, high-volume load tools, and performance dashboard. M25-M31 add these over the stable catalog contract.
- Cross-store checkout and shipping fee comparison.

### Handoff To M25

M25 extends catalog price cards with an effective promotion read model. It must preserve M24 filter/cursor contracts and add price data without reintroducing entity-graph or per-card query problems.

## Milestone 25: Store Promotions And Price Policy

### Product Goal

Store operators can run time-bounded events for inspected used products, while buyers see honest list, promotional, and final display prices on storefront, search, product detail, cart, and order screens.

This is the first promotion milestone. It creates price-policy vocabulary and automatic promotions, but does not yet issue coupons or combine multiple discounts.

### Learning Goal

Practice a separate pricing read model, time-range queries, rule ordering, immutable order snapshots, DTO projections, and query plans for promotion-heavy catalog reads.

### Prerequisites

- M21 store ownership and operator authorization.
- M22 public storefront and seller catalog operations.
- M23 stock-managed products and single-item products.
- M24 buyer catalog discovery surfaces.

### Scope

- Store operators create, edit, schedule, pause, resume, and end a store promotion.
- A promotion targets either selected products or all currently eligible products in one store. Start with selected product targeting if store-wide selection makes the query model too broad; add store-wide targeting only when its query and audit semantics are explicit.
- A promotion uses fixed-amount or percentage reduction, start and end timestamps, priority, public title, and optional short buyer-facing label.
- A product may have several configured promotions but receives only one effective promotion at any instant.
- Buyer surfaces show list price, effective promotion price, discount amount or rate, campaign label, and time validity when meaningful.
- Store console shows product coverage, campaign status, scheduled time window, and a preview of effective prices.
- The order creation path captures list price, promotion identifier, promotion amount, and promotional subtotal before any future coupon reduction.
- The existing personal-store model is supported, but business stores are the primary operational use case.

### Web Experience

- Store landing page gets an event band and promotion-aware product grid rather than an intrusive marketing hero.
- Product cards display a restrained list-price strike-through only when an active promotion exists; never invent a discount display for regular products.
- Product detail displays the price breakdown near the purchase action and identifies the responsible store event.
- Store console uses a searchable product table, date/time controls, clear campaign status chips, and a campaign detail/edit view. It must remain usable with hundreds of products.
- Buyer views must render a stable price placeholder while promotion data is loading, rather than shifting cards after the image grid appears.

### Core Rules

- A store operator can manage promotions only for stores where they have the required operator role.
- A promotion cannot start after it ends, have a negative discount, or create an effective price below zero.
- Hidden, sold-out, and reserved single-item products are not buyer-visible promotion targets. Their campaign relationship may remain for audit.
- If no effective promotion applies, the quoted promotional subtotal equals the list price.
- The server calculates effective promotion price at every read and order quote; the client never sends a promotion amount as an authoritative value.
- A promotion that ends between a product-detail read and order creation is not applied to the order.

### Suggested Domain Boundaries

- `PromotionCampaign`: lifecycle, owner store, time range, priority, display metadata, and discount definition.
- `PromotionTarget`: campaign-to-product relationship when selected product targeting is used.
- `PricingQuoteService`: pure application-level calculation of effective promotion for a product and time. It must be reusable by catalog reads and order creation.
- `OrderPriceSnapshot` or equivalent fields owned by the order/payment pricing boundary: immutable amounts and applied campaign reference.

Avoid embedding a growing collection of promotion conditions inside `Product`. Product ownership, availability, and images are already a coherent aggregate; price policy changes on a different cadence.

### JPA And Performance Focus

- Use a projection for catalog price cards. Do not load product images, store, promotion targets, and all campaign collections for every product entity.
- Inspect the plan for active-promotion lookups using store, product target, lifecycle, and time-range predicates.
- Add only indexes that are justified by the final query shape. Likely candidates include campaign owner/status/time and target product/campaign keys.
- Explicitly test that catalog queries do not introduce an N+1 promotion lookup per product.
- Document whether the effective promotion is joined in one query, queried in a bounded batch, or served by a targeted read model. Make the trade-off visible in the milestone design.

### Verification And Exit Criteria

- Store authorization, lifecycle transitions, target validation, overlap resolution, and order snapshot behavior have integration tests.
- Tests cover a promotion expiring just before order creation and a tie between equally discounted promotions.
- Product list, product detail, store page, cart, and order summary build and display consistent pricing.
- A query-count or SQL-log-focused test demonstrates that a page of catalog cards does not execute one promotion query per card.
- Backend full tests, web build, and `git diff --check` pass.

### Explicitly Out Of Scope

- Coupons and coupon issuance.
- Cross-store promotions.
- Shipping discounts, bundles, buy-one-get-one, and free gifts.
- A platform-wide promotion console. That arrives in M31.
- Multi-item cart discount allocation.

### Handoff To M26

M26 consumes the campaign lifecycle, pricing quote boundary, store authorization, and order price snapshot conventions. It adds member-owned coupons without changing automatic promotion overlap rules.

## Milestone 26: Coupon Campaigns And Standard Coupon Issuance

### Product Goal

Platform administrators and verified-store operators can publish reusable coupon campaigns. Eligible buyers can claim a coupon once and see their usable, used, expired, and unavailable coupons in a dedicated wallet.

### Learning Goal

Practice campaign-to-instance modeling, uniqueness constraints, issuance idempotency, pagination by ownership and status, secure role-scoped APIs, and buyer-facing stateful UI.

### Scope

- Add platform-owned and store-owned coupon campaigns.
- Support fixed-amount and percentage coupons with a maximum discount amount, minimum purchase amount, issue period, redemption period, and stackability policy.
- Support normal issuance with no global cap in this milestone.
- Let a buyer claim an active eligible campaign exactly once.
- Add a coupon wallet at a buyer-scoped route such as `/me/coupons`.
- Add store operator campaign list/create/edit/status surfaces under the store console.
- Add a focused platform administrator coupon campaign list and lifecycle control surface.
- Show where a coupon can be used, why it is not currently usable, and its expiration date without exposing internal authorization details.
- Keep issued coupons after a campaign ends for history; their redemption eligibility follows their own valid-until field and campaign status rules.

### Core Rules

- A coupon campaign owner is immutable after the campaign becomes active.
- A store coupon must be linked to one store. A platform coupon has no store owner.
- Use a database uniqueness rule for `(coupon_campaign_id, member_id)`; client-side disabling is only a convenience layer.
- Repeated claim requests from the same authenticated buyer return the already issued coupon or a stable idempotent result, not two coupon rows.
- A buyer cannot claim a paused, ended, future, or ineligible campaign.
- Coupon amount and eligibility terms are copied or versioned at issue time as needed so past user coupons are auditable if a draft campaign is edited later.
- Issuance does not reserve inventory and does not modify an order.

### Web Experience

- The coupon wallet separates usable, used, expired, and unavailable states with compact filters and clear reason text.
- Campaign cards show discount terms, minimum purchase, source badge (`플랫폼` or store brand), remaining issue period, and applicable store/product scope.
- Claiming is a direct action with disabled pending state and an explicit successful-claim result. Do not rely on a disappearing button as the only confirmation.
- Store console has a practical table for active and scheduled campaigns, with totals for issued and redeemed coupons but without a dense dashboard in this milestone.
- Admin operations use the existing utilitarian operations style rather than a buyer-facing promotional layout.

### Suggested Domain Boundaries

- `CouponCampaign`: campaign policy and lifecycle.
- `MemberCoupon`: member-owned issue record and lifecycle (`ISSUED`, `USED`, `EXPIRED`, optionally `VOIDED`).
- `CouponEligibilityService`: validates buyer, time window, source/scope, and once-per-member rule.
- `CouponIssueService`: performs issuance and returns an idempotent outcome.

`MemberCoupon` must not be a mutable copy of every campaign field unless the business rule requires a snapshot. Keep policy source clear and persist only the terms needed to preserve correct historical redemption.

### JPA And Performance Focus

- Enforce once-per-member with a database unique constraint and handle the resulting conflict intentionally.
- Use paged projections for the wallet; do not load campaign owner, all targets, and all member coupons as nested entity graphs.
- Index wallet reads by member, status, expiration, and deterministic secondary order as proven by the query plan.
- Ensure store campaign reads are scoped by store id in the query itself, not filtered after loading platform-wide data.

### Verification And Exit Criteria

- Integration tests cover platform/store ownership, one-time issuance, lifecycle restrictions, member scoping, and paginated wallet filtering.
- Two sequential claim requests for the same user produce one coupon record and a stable response.
- Wallet and campaign management UI distinguish successful claim, already claimed, expired, and unavailable states.
- Existing order, payment, settlement, and refund tests keep passing; coupons are not yet redeemed by orders.

### Explicitly Out Of Scope

- Limited-quantity and first-come issuance.
- Coupon redemption at checkout.
- Coupon stacking or exclusive-coupon behavior.
- Targeted segmentation based on personal data, external CRM, or notification delivery.

### Handoff To M27

M27 extends `CouponCampaign` with a limited issuance policy. It must reuse the same member-coupon uniqueness and wallet behavior rather than creating a parallel event-coupon model.

## Milestone 27: First-Come Coupon Events And Concurrent Issuance

### Product Goal

Platform administrators and store operators can run an event such as “first 1,000 claimants receive a 10,000 won coupon.” The platform issues exactly the promised number of coupons even when many buyers claim at the same time.

### Learning Goal

Practice contention-aware updates, transaction isolation, optimistic locking versus conditional update approaches, retry policy, idempotency under races, and concurrent integration testing against PostgreSQL.

### Scope

- Add `UNLIMITED` and `LIMITED` issuance policy to coupon campaigns.
- For limited campaigns, configure `issueLimit`, issued count, and remaining claimable count.
- Reuse the M26 claim API and wallet behavior; buyers do not use a different endpoint merely because a campaign is first-come.
- Show remaining coupon quantity while it is positive, then show a sold-out state when the limit is exhausted. Treat the displayed count as advisory because it may become stale before a click.
- Record claim time and issuance result for operator audit and aggregate reporting.
- Let campaign owners pause or end an active event; this blocks subsequent claims.
- Provide store and admin event views with configured limit, issued count, remaining count, claim velocity summary, and failure reason categories.

### Concurrency Contract

The official semantic is **first N successful issuance transactions**, not first N users to open the page and not first N users to complete payment.

For one campaign with limit `N`:

- At most `N` distinct member coupons can reach `ISSUED`.
- A member can obtain at most one coupon from the campaign.
- Repeating a successful member claim is idempotent and does not consume another slot.
- A failed claim caused by exhaustion, inactivity, or ineligibility consumes no slot.
- A retry after an ambiguous network outcome must return the member's already issued coupon when it exists.

### Recommended Implementation Alternatives

The milestone design should compare both options before choosing one:

1. Optimistic locking on the campaign version with a bounded retry for lock conflicts.
2. A conditional update such as decrementing remaining quantity only when it is positive, then inserting the member coupon within a carefully designed transaction.

The recommended final baseline is a conditional update for the scarce-capacity mutation plus the unique `(campaign, member)` constraint. It keeps the contention query narrow and makes the “no remaining capacity” result explicit. Still implement at least one focused comparison test or documented experiment with optimistic locking so the trade-off is learned, not merely asserted.

### Web Experience

- Event cards are visually distinct but remain consistent with the marketplace design; no marketing-style oversized hero is necessary.
- The claim button explains whether the event is scheduled, active, already claimed, sold out, paused, or ended.
- After a claim, the buyer receives a durable wallet link and the exact coupon terms.
- Store/admin event screens surface real counters and event status. Never imply that the browser-side remaining counter guarantees success.

### JPA And Database Focus

- Keep the capacity mutation to one indexed row per campaign.
- Decide transaction order deliberately: check an existing member coupon first, attempt capacity acquisition, then persist the coupon; compensate capacity only when insertion fails for a race that did not represent an already-issued coupon.
- Run concurrency tests on PostgreSQL/Testcontainers, not only an in-memory database.
- Use `ExecutorService` and `CountDownLatch` or an equivalent barrier to submit more claim attempts than the cap.
- Capture SQL and test transaction behavior under repeated contention. Avoid unbounded application retries.

### Verification And Exit Criteria

- A concurrent test with more unique buyers than the cap produces exactly `N` issued coupons and no negative remaining count.
- A concurrent repeated claim by one buyer produces one coupon and consumes one capacity slot at most.
- Pause/end races, cap exhaustion, and retry-after-success behaviors are covered.
- Store and admin counts agree with member-coupon data after the test scenario.
- The event UI handles an optimistic display count becoming stale without presenting a false success.

### Explicitly Out Of Scope

- Queue-backed waiting rooms, Redis distributed locks, Kafka, or external notification systems.
- Lottery draws, invitation-only campaigns, and referral programs.
- Coupon use at checkout; M28 owns redemption.

### Handoff To M28

M28 consumes `MemberCoupon` records that are correctly issued under concurrency. It must not change campaign capacity while pricing a quote or redeeming a coupon.

## Milestone 28: Coupon Combination And Order Price Calculation

### Product Goal

Buyers can select eligible store and platform coupons during a single-product order flow, understand the resulting price before payment, and receive a correct final amount even when coupons are exclusive, expired, already used, or no longer applicable.

### Learning Goal

Practice explicit pricing-domain services, read-versus-write validation, deterministic rule ordering, immutable financial snapshots, unique redemption guarantees, and cross-aggregate transactional consistency.

### Scope

- Add a server-calculated order quote for one product and the current buyer.
- The quote returns list price, automatic promotion reduction, eligible store coupons, eligible platform coupons, selected coupon reductions, final payable amount, and ineligible-coupon reasons.
- Let a buyer select zero, one store coupon, and zero or one platform coupon when the selected policies allow stacking.
- Support exclusive coupons that cannot be combined with any other coupon.
- Validate minimum purchase amount against the amount after automatic promotion and before the coupon's own discount, unless the milestone spec explicitly chooses another rule.
- On order creation or payment approval, atomically revalidate the selected coupon(s), snapshot all price components, mark member coupons used, and persist redemption references.
- Add buyer-facing order price breakdown and coupon selection UI.
- Continue using one product per order. Cart checkout can create several priced orders, but it does not allocate one coupon across multiple order lines in this phase.

### Deterministic Pricing Rules

```text
1. Start with the product list price.
2. Apply the best valid automatic promotion from M25.
3. If a valid selected store coupon is stackable, calculate and apply it.
4. If both selected coupons are stackable, calculate the valid platform coupon on the remaining amount.
5. If either selected coupon is exclusive, use that coupon alone after the automatic promotion.
6. Clamp each reduction and the final payable amount to valid non-negative money.
```

When a buyer selects an exclusive coupon together with another coupon, return a structured validation result identifying the conflict. The UI may automatically clear the incompatible selection after user confirmation, but it must not silently substitute a different coupon.

The buyer selects coupons; the server does not silently maximize the discount in this milestone. A future “best coupon recommendation” feature can call the same quote service with candidate combinations.

### Suggested Domain Boundaries

- `OrderPricingService`: calculates a quote from product, buyer, clock, and selected coupon ids.
- `OrderPriceQuote`: read-only result with detailed line items and reason codes.
- `CouponRedemption`: links a used member coupon to an order and preserves applied amount.
- Order pricing snapshot: list, promotion, store-coupon, platform-coupon, and final payable amounts.

The quote service should be deterministic and independently testable. It should not perform state mutations. The order/payment transaction invokes the same rules again and then performs coupon state changes.

### Web Experience

- Product detail and order screens provide a compact price breakdown that updates from an authoritative server quote when coupon selection changes.
- Coupon picker lists eligible and unavailable coupons separately. It gives a specific reason for unavailable choices, such as expired, already used, minimum purchase not met, wrong store, or exclusive conflict.
- Selected coupons have stable visual state. A pending quote must not allow an old final price to be submitted as though it were current.
- Orders and payment history show immutable discount lines so the user can reconcile what happened later.

### JPA And Transaction Focus

- Fetch only the selected member coupons and the required campaign/store/product context. Do not load the entire wallet inside checkout.
- Use a uniqueness guarantee so one member coupon cannot be redeemed by two orders.
- Revalidate at the mutation boundary because a coupon can expire, be paused, or be used between quote and payment.
- Define payment ordering explicitly with the existing fake gateway. If payment capture can fail after coupon use, the transaction/compensation design must keep coupon state and payment state consistent.
- Add indexes only after examining quote and redemption queries. Likely access patterns include selected coupon id + member id and redemption by member coupon/order.

### Verification And Exit Criteria

- Unit or focused service tests cover fixed and percentage discount, caps, minimum purchase, rounding, zero floor, promotion overlap, stacking, and exclusive conflicts.
- Integration tests cover buyer ownership, wrong-store coupon rejection, already-used rejection, expiration between quote and order, and exactly-once redemption.
- The final amount stored with the order matches the quote produced under the same valid state.
- UI build verifies quote refresh, selection conflicts, unavailable explanations, and immutable price history rendering.

### Explicitly Out Of Scope

- One coupon spread across multiple cart items.
- Cross-store bundle discounts.
- Shipping fees, tax invoices, loyalty points, and external payment-provider discount APIs.
- Automatic best-combination search beyond client/server reuse of the quote contract.

### Handoff To M29

M29 keeps the M28 pricing transaction intact while adding inventory-safe order creation. Its locking strategy must not weaken coupon redemption correctness.

## Milestone 29: Concurrent Purchase And Inventory Reservation

### Product Goal

The platform prevents overselling when many buyers attempt to purchase a scarce single item or a popular stock-managed used listing at the same time. Buyers receive a clear outcome instead of duplicate orders or misleading success.

### Learning Goal

Practice high-contention write paths, optimistic and pessimistic locking trade-offs, conditional SQL updates, reservation restoration, deadlock awareness, and concurrent integration tests.

### Scope

- Preserve current single-item behavior: only one buyer can reserve an `ON_SALE` product.
- Add inventory reservation semantics for `STOCK_MANAGED` products: an order reserves one available unit before payment and releases it when cancellation or payment failure allows release.
- Decide and document a reservation timeout policy. The recommended first version reuses existing unpaid-order cancellation mechanics rather than introducing a new distributed expiration service.
- Integrate coupon revalidation and final pricing from M28 into the same order creation boundary.
- Add buyer-facing messages for sold out, concurrent purchase loss, and unavailable quantity.
- Add store console inventory indicators and order-result visibility appropriate for business stores.
- Create a repeatable concurrency experiment/report that compares at least two locking approaches for the same scenario.

### Concurrency Contract

For `SINGLE_ITEM`:

- At most one active order may reserve the product.
- The winner moves the product to `RESERVED`.
- A canceled pre-shipping order restores `ON_SALE`; confirmed completion makes it `SOLD_OUT`.

For `STOCK_MANAGED`:

- Available quantity never becomes negative.
- Each successful new order reserves exactly one unit.
- Cancellation or failed payment releases exactly one previously reserved unit once.
- When available quantity reaches zero, the product is no longer purchasable.
- Retrying an already successful order command does not reserve a second unit.

### Recommended Locking Study

Implement the production baseline only after measuring the alternatives against PostgreSQL:

1. Optimistic locking with a `@Version` field and bounded application retries.
2. Conditional update of inventory, such as decrement only where available quantity is positive.
3. Pessimistic write lock as a comparison experiment for the one-row hot-product case.

The expected baseline for stock quantity is a conditional update because it expresses the invariant in the database and keeps the write narrow. The existing version field remains valuable for aggregate edits and the single-item reservation path. Pessimistic locking is useful as a learning comparison, not a default everywhere.

### JPA And Database Focus

- Use PostgreSQL/Testcontainers for concurrent integration tests.
- Be explicit about entity state after bulk/conditional updates; clear or refresh persistence context where required.
- Do not perform remote payment calls while holding a database lock. Reserve inventory and validate price in the transactional portion, then handle gateway interaction with a documented compensating path appropriate to the current fake gateway.
- Inspect lock waits, update counts, retries, and failure modes in the experiment report.
- Check indexes for product/inventory primary access and active order/reservation recovery queries.

### Verification And Exit Criteria

- More concurrent buyers than available stock produce exactly the available number of successful reservations/orders.
- A single-item concurrency test produces exactly one winner.
- Cancellation/payment-failure restoration is idempotent and cannot inflate stock.
- Coupon redemption and stock reservation remain consistent when a purchase race is lost.
- The experiment report records scenario size, chosen strategy, success count, failure count, elapsed time, and observed SQL/locking behavior.

### Explicitly Out Of Scope

- Warehouse allocation, multi-location inventory, serial-number tracking, partial quantities per order, and backorders.
- Kafka/SQS reservations, distributed locks, or globally coordinated flash-sale queues.
- Real payment gateway integration.

### Handoff To M30

M30 concentrates on read scale. It should use the products, stores, campaigns, and event traffic created by M25-M29 without changing their write invariants.

## Milestone 30: High-Traffic Catalog Reads, Caching, And Query Measurement

### Product Goal

Buyers can browse popular used products, store collections, event pages, and filtered search results quickly under realistic read traffic, while the team can explain which query and cache decisions improved the result.

### Learning Goal

Practice query-plan-driven index design, DTO projections, keyset pagination, N+1 prevention, bounded caching, cache invalidation, metrics, and reproducible load experiments.

### Scope

- Add buyer-facing popular-product and active-event discovery modules that use M24 search and catalog contracts.
- Add store collection, category/filter, and price-aware summary projections suitable for a dense responsive product grid.
- Use keyset pagination for deep browse feeds where ordering can be made deterministic; retain offset pagination only where administrative jump-to-page behavior needs it.
- Add a bounded in-process cache for carefully selected public, high-read, low-change views such as active event summaries or popular product ids. Add a cache dependency only after the milestone design identifies one concrete cacheable read.
- Invalidate or version cache entries on product visibility, inventory availability, promotion lifecycle, and relevant store changes. Prefer targeted invalidation over a broad global cache clear.
- Add request timing/query-count observation suitable for local development and repeatable experiments. Spring Boot Actuator/Micrometer may be introduced here if the design demonstrates how the data will be consumed.
- Create a reproducible load script or profile. `k6` is the recommended external runner unless the project chooses an equivalent supported tool during the design session.
- Produce a before-and-after performance note with data volume, endpoints, concurrency, p50/p95 latency, error rate, database query count, and relevant query plans.

### Read-Model Principles

- Never use a fully hydrated `Product` entity graph as the default search-card query.
- Select only product summary, representative image, store brand, effective price, availability, and sort keys needed by the card.
- Avoid joining multiple to-many collections in a paginated query.
- Keep cache keys explicit about filters, cursor/order, store, and authenticated-personalization state. Do not cache member-specific cart/wishlist flags as though they were public results.
- Personalized additions such as carted/wishlisted state should be fetched efficiently in a bounded secondary query or supplied by a separate endpoint; they must not destroy the public catalog query shape.

### Web Experience

- Home and search pages have stable loading skeletons, responsive dense grids, useful empty states, and URL-backed filter/sort/cursor state.
- Active events and popular product sections use actual product imagery and store context, not generic promotional decoration.
- Filter updates avoid layout shifts and unnecessary whole-page refetch behavior.
- Buyer pages can tolerate stale public ranking/event data briefly but must refresh availability and final price at product detail and checkout boundaries.

### JPA And Database Focus

- Capture and compare `EXPLAIN ANALYZE` output for representative catalog, store, active-event, and popular-product queries.
- Verify every new index supports a demonstrated predicate and ordering. Avoid redundant single-column indexes where an existing composite index covers the access path.
- Measure the offset-to-keyset transition with a deep-page scenario rather than claiming its advantage abstractly.
- Compare no-cache and cache-enabled traffic with warm-up separated from measured traffic.
- Do not introduce Redis, a search cluster, or a materialized ranking pipeline unless measurements demonstrate that local projections and bounded cache no longer meet the learning target.

### Verification And Exit Criteria

- Integration tests preserve authorization/visibility rules for cached and uncached reads.
- Query-count tests or SQL inspection show the selected catalog page has bounded query behavior.
- A load profile runs from documented setup steps and produces an interpretable result report.
- Cache invalidation tests cover a product becoming hidden/sold-out and a promotion/event ending.
- Web build confirms stable filter navigation, skeletons, no-overflow mobile layout, and cached-data refresh behavior.

### Explicitly Out Of Scope

- Elasticsearch/OpenSearch, Redis cluster, CDN configuration, sharding, replicas, or production deployment topology.
- Personalized recommendation machine learning.
- A promise of a fixed requests-per-second target without hardware and data-volume context.

### Handoff To M31

M31 turns campaign, coupon, inventory, and read-performance signals into operator tools. It must consume measured metrics and real domain aggregates instead of visualizing fabricated dashboard numbers.

## Milestone 31: Promotion And Performance Operations Dashboard

### Product Goal

Store operators and platform administrators can run events responsibly: they can inspect promotion and coupon performance, inventory pressure, failed claims/purchases, and catalog-read health, then make scoped operational decisions.

### Learning Goal

Practice role-specific dashboard projections, aggregate queries, date-range filtering, operational auditability, index-backed reporting, and frontend information design for dense work surfaces.

### Scope

- Extend the store console with promotion/coupon/event summaries: issued, claimed, redeemed, remaining issue capacity, discount amount, affected products, and inventory risk.
- Add platform-level campaign management for platform coupons and event campaigns. Platform administrators can list, pause, resume, end, and inspect platform-owned campaigns.
- Add a platform operational dashboard for campaign activity, coupon issue/redeem outcomes, stock reservation failures, order outcomes, active-event traffic indicators, slow catalog-read summaries, and cache effectiveness when M30 exposes the metrics.
- Add time-window, owner/store, campaign status, and product filters where the data supports them.
- Provide drill-down from a summary card to paginated, role-scoped records. Do not turn the dashboard into one massive eager-load screen.
- Record important campaign lifecycle actions with actor, timestamp, action, and before/after summary sufficient for learning-oriented audit trails.
- Keep existing seller reports, settlement operations, and refund pages compatible; link to them where useful rather than rewriting them.

### Dashboard Design Principles

- Store console emphasizes the operator's own products, active campaigns, coupon exposure, and inventory attention points.
- Administrator console emphasizes cross-store aggregation, platform-owned campaigns, anomaly investigation, and action history.
- Use compact cards for key totals, filterable tables for detail, and clear empty/error states. Avoid decorative card stacks that hide operational data.
- Every aggregate must state its time range and refresh time.
- Counters that are eventually consistent or cache-backed must be labeled as such in the product design; order/payment mutations remain the source of truth.

### Suggested Read Models

- `StorePromotionSummary`: campaign status, product coverage, issued/redeemed counts, remaining capacity, discount total, and time window.
- `PlatformPromotionSummary`: platform-wide equivalents with owner/store breakdown.
- `InventoryPressureSummary`: low available quantity, reservation failures, and recent sold-out transitions.
- `CouponOutcomeSummary`: issue success, already-claimed, exhausted, expired, and redemption failure groups.
- `CatalogPerformanceSummary`: endpoint/query profile, traffic window, latency percentiles, cache hit/miss counts, and error counts when instrumentation supplies them.

Prefer projection queries or purpose-built reporting tables over loading campaign/order/coupon entity graphs and aggregating them in Java. A reporting table is appropriate only if M30 measurement proves live aggregation cannot meet the intended dashboard interaction budget.

### JPA And Performance Focus

- Write group-by queries with explicit time windows and scoped owner predicates.
- Analyze counts on high-cardinality coupon and order tables; do not rely on unbounded scans for every dashboard refresh.
- Paginate drill-down records and cap dashboard date-range defaults.
- Design indexes from the actual filter/group/order clauses and verify with query plans.
- Separate transactionally correct source-of-truth writes from eventually refreshed operational aggregates.

### Verification And Exit Criteria

- Store users cannot view or mutate another store's campaigns or reports.
- Administrators can manage platform campaigns without gaining accidental store-operator privileges.
- Report totals reconcile against fixture data and documented time-zone rules.
- Dashboard filter changes produce bounded, paged queries and preserve empty/loading/error states.
- Audit records exist for lifecycle actions selected in the milestone design.
- Backend full tests, web build, `git diff --check`, and a final manual operator-flow pass succeed.

### Explicitly Out Of Scope

- Full observability platform integration, alert paging, BI warehouse, and external data export pipelines.
- Automated campaign optimization or autonomous price changes.
- Legal tax reporting and accounting ledger replacement.

## Suggested Session Template For Every Milestone

Use this sequence when the user starts one milestone in a future session:

1. Read this roadmap and the most recent post-milestone handoff.
2. Confirm prerequisites and inspect the actual current code; do not assume the roadmap stayed perfectly current.
3. Run brainstorming for the milestone's unresolved business decisions.
4. Write and commit `docs/superpowers/specs/YYYY-MM-DD-milestone-XX-<topic>-design.md`.
5. Ask for review of the written design.
6. Write and commit the detailed implementation plan.
7. Use an isolated `codex/` worktree and execute the approved plan with focused tests, full backend tests, web build, and diff hygiene checks.
8. Write both milestone handoff and post-milestone next-session handoff before merge/push.

## Milestone Dependency Map

```text
M21 Store foundation
  -> M22 Storefront and store console
  -> M23 Sales policy and inventory
  -> M24 Search and catalog discovery
      -> M25 Store promotion and price policy
          -> M26 Coupon campaign and standard issuance
              -> M27 First-come coupon concurrency
                  -> M28 Coupon combination and order pricing
                      -> M29 Concurrent purchase and inventory reservation
                          -> M30 High-traffic reads, cache, and measurement
                              -> M31 Promotion and performance operations dashboard
```

## Deferred Candidates

Keep these outside the M25-M31 commitment unless a future roadmap revision promotes one deliberately:

- Multi-item, multi-store checkout and coupon allocation.
- Shipping fee calculation, tax invoices, and loyalty points.
- Store follow/subscribe, notifications, chat, price offers, and trade negotiation.
- External payment gateway refund integration and return logistics.
- Distributed cache, message broker, search cluster, warehouse inventory, and production deployment architecture.
- Recommendation engine, customer segmentation, and marketing automation.

## Self-Review

- The roadmap keeps individual one-off used goods and stock-managed business listings distinct without creating two unrelated storefronts.
- First-come coupon issuance is separated from coupon redemption so capacity and money calculations stay understandable.
- Coupon combination rules are deterministic, server-authoritative, and constrained to the current one-product-per-order model.
- Read scaling follows measured query behavior and does not prematurely introduce infrastructure services.
- Each milestone has a practical web outcome, a bounded backend/domain outcome, and explicit JPA or transaction learning goals.
