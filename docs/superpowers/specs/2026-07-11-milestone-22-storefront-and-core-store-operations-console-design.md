# Milestone 22 Storefront And Core Store Operations Console Design

## Context

Milestone 21 moved commercial product ownership to `Store`, made active `StoreMembership` the catalog authorization boundary, and preserved historical seller identity on orders. It also added a minimal public store profile, owner-focused store settings, business-store governance, and store-aware product forms.

Milestone 22 turns that foundation into two usable product surfaces:

- a buyer storefront with store identity, trust signals, and a bounded public catalog;
- an owner/manager operations console with catalog visibility controls and links to existing commerce operations.

This design uses the M21 final handoff and the actual migrated schema as its starting point. It does not introduce a second seller profile, mutable member ownership on products, or a replacement for existing sales, refund, settlement, and report domains.

## Goals

- Let buyers browse an active store's products by public status with deterministic pagination and basic sorting.
- Preserve a clear but privacy-safe direct state for suspended stores.
- Let owners and managers operate one store-scoped catalog from a focused console.
- Support atomic individual and batch hide/show commands without changing reservation or completed-sale state.
- Let owners inspect active store memberships and remove managers.
- Keep public, operator, and owner-private read models separate.
- Bound query counts and avoid paginated to-many fetch joins.
- Preserve all M21 compatibility and historical-transaction rules.

## Non-Goals

- Manager invitation, direct manager assignment, membership reactivation, or owner transfer.
- Inventory quantity, stock reservation, or bulk stock adjustment.
- Rich global search, keyset pagination, recommendations, or popularity sorting.
- Recent-review feeds or paginated store review pages.
- Promotions, coupons, bulk imports, CSV operations, or multi-store organizations.
- Reimplementation of sales, refunds, settlements, or seller reports inside the store console.
- Removal of legacy `sellerId` or `sellerNickname` response fields.
- Full Flyway ownership of all application DDL.

## Product Scope

M22 uses a balanced minimum-complete slice. The buyer storefront, operator catalog, and owner membership controls are each deliberately small but usable end to end.

### Buyer storefront

- Active store profile, type signal, introduction, rating summary, and public-product count.
- Default `ON_SALE` catalog with optional `RESERVED` and `SOLD_OUT` filters.
- Newest, price-low-to-high, and price-high-to-low sorts.
- Twelve products per page by default.
- Suspended-store direct state without catalog exposure.

### Operations console

- All stores where the current member has an active `OWNER` or `MANAGER` membership.
- Store status and current member role.
- Counts for `ON_SALE`, `RESERVED`, `SOLD_OUT`, and `HIDDEN` products.
- Store-scoped catalog filtering, title search, deterministic pagination, and individual/batch actions.
- Product create/edit entry points and links to existing sales, refunds, settlements, and reports.
- Read-only catalog access while the store is inactive.

### Membership management

- Active membership list for owners.
- Owner-managed manager removal by deactivating the membership.
- No manager creation path in M22.

## Architecture And Service Boundaries

### `StorefrontQueryService`

Owns unauthenticated and optionally viewer-aware buyer reads. It returns dedicated public projections for the store header and public product page. Its queries must not select legal business data, administrator-review data, or memberships. The product-card projection may select the immutable owner identity only to preserve the documented `sellerId` and `sellerNickname` compatibility fields.

### `StoreCatalogQueryService`

Owns role-scoped operator reads. It returns operable-store choices, product-status counts, and the filtered operator catalog. Active owners and managers can read their catalog even when the store is inactive. The service must authorize once per endpoint, not once per product row.

### `StoreCatalogCommandService`

Owns individual and batch hide/show commands. It reuses `StoreAccessService` to require an active store and an active `OWNER` or `MANAGER` membership. It loads all requested products by store and ID in one transaction, validates the complete set, then applies domain transitions. A validation failure rolls back the whole command.

### `StoreMembershipQueryService` and `StoreMembershipCommandService`

Own owner-only active-membership listing and manager removal. They use the owner authorization path and never expose membership data through a public projection.

### Existing services

- Existing product create and edit APIs remain authoritative and are linked from the console.
- Existing profile and legal-data commands remain owner-only.
- Existing sales, refunds, settlements, and reports remain separate domains and routes.
- Existing order/refund/settlement/report reads continue to use the immutable order seller snapshot.

## API Design

### Public APIs

#### `GET /api/stores/{storeId}`

Returns the public storefront header:

- `storeId`
- `type`
- `publicName`
- `introduction`
- `operatingStatus`: `ACTIVE` or `SUSPENDED`
- `averageRating`: one decimal place using half-up rounding; `null` when no review exists
- `reviewCount`
- `publicProductCount`: count of `ON_SALE`, `RESERVED`, and `SOLD_OUT`; excludes `HIDDEN`

State handling:

- `ACTIVE`: return the complete header.
- `SUSPENDED`: return public identity and the unavailable state with `averageRating: null`, `reviewCount: 0`, and `publicProductCount: 0`. The web omits rating and catalog presentation in this state.
- `PENDING` and `REJECTED`: return `STORE_NOT_FOUND`.
- A missing store also returns `STORE_NOT_FOUND`.

The response never includes legal business name, registration identifier, rejection reason, administrator notes, owner ID, staff identifiers, or memberships.

#### `GET /api/stores/{storeId}/products`

Parameters:

- `status`: defaults to `ON_SALE`; allowed values are `ON_SALE`, `RESERVED`, and `SOLD_OUT`.
- `sort`: defaults to `NEWEST`; allowed values are `NEWEST`, `PRICE_ASC`, and `PRICE_DESC`.
- `page`: defaults to `0`.
- `size`: defaults to `12`, maximum `40`.

Stable ordering:

- `NEWEST`: `product.id DESC`
- `PRICE_ASC`: `product.price ASC, product.id DESC`
- `PRICE_DESC`: `product.price DESC, product.id DESC`

Only an active store returns products. A suspended store returns an empty public catalog page so the storefront can render its unavailable state without leaking catalog data. Pending, rejected, and missing stores return `STORE_NOT_FOUND`.

The card projection contains product ID, title, price, status, one representative-image URL, store ID/name/type, and the temporary seller compatibility fields. When an authenticated viewer is present, it also contains wishlist and cart membership flags. It does not load the image or review collections.

### Operator APIs

#### `GET /api/store-operations`

Returns every store where the current member has an active `OWNER` or `MANAGER` membership:

- `storeId`
- `type`
- `publicName`
- `status`
- current member `role`

It deliberately omits owner-private legal and review fields. This endpoint is separate from the existing owner-private `/api/stores/me` contract so a manager never receives owner-only data.

#### `GET /api/store-operations/{storeId}/summary`

Returns:

- `onSaleCount`
- `reservedCount`
- `soldOutCount`
- `hiddenCount`
- `catalogWritable`

`catalogWritable` is true only when the store is active and the current member has an active owner or manager membership. Authorized operators may read the summary while it is false.

#### `GET /api/store-operations/{storeId}/products`

Parameters:

- `status`: optional; allowed values are all four product statuses.
- `keyword`: optional trimmed title substring.
- `sort`: defaults to `NEWEST`; allowed values are `NEWEST` and `OLDEST`.
- `page`: defaults to `0`.
- `size`: defaults to `20`, maximum `100`.

Stable ordering uses product ID as the final key. The projection contains only the fields needed by the console: product ID, representative image, title, price, status, creation time, and update time.

#### `POST /api/store-operations/{storeId}/products/hide`

#### `POST /api/store-operations/{storeId}/products/show`

Both endpoints accept:

```json
{
  "productIds": [1, 2, 3]
}
```

Rules:

- One to fifty unique positive IDs are allowed.
- Hide requires every product to belong to the path store and be `ON_SALE`.
- Show requires every product to belong to the path store and be `HIDDEN`.
- `RESERVED` and `SOLD_OUT` products cannot be changed by these commands.
- A missing or cross-store product fails the entire request.
- The store must be active and the requester must be an active owner or manager.

### Membership APIs

#### `GET /api/store-operations/{storeId}/memberships`

Owner-only. Returns active memberships with:

- `membershipId`
- `memberId`
- `memberNickname`
- `role`
- `joinedAt`

#### `DELETE /api/store-operations/{storeId}/memberships/{membershipId}`

Owner-only. The target must be an active manager membership in the same store. Success sets `active` to false. Owner memberships are immutable through this endpoint.

## Authorization Matrix

| Capability | OWNER | MANAGER | OUTSIDER |
|---|---:|---:|---:|
| List own operable stores | Allowed | Allowed | No unrelated stores returned |
| Read operator summary/catalog | Allowed | Allowed | Denied |
| Create/edit products | Allowed for active store | Allowed for active store | Denied |
| Hide/show products | Allowed for active store | Allowed for active store | Denied |
| Edit public profile | Allowed | Denied | Denied |
| Edit legal data | Allowed | Denied | Denied |
| List/remove memberships | Allowed | Denied | Denied |

Route guards and disabled buttons are usability measures only. Every command repeats authorization and store-status checks on the server.

## State And Error Handling

- Unauthenticated operator access: HTTP 401.
- Missing operator access: HTTP 403 with `STORE_ACCESS_DENIED`.
- Owner-only access by a non-owner: HTTP 403 with `STORE_OWNER_REQUIRED`.
- Missing or non-public store: HTTP 404 with `STORE_NOT_FOUND`.
- Empty, duplicate, non-positive, or oversized product ID list: HTTP 400 with `VALIDATION_ERROR`.
- Missing or cross-store batch product: HTTP 404 with `PRODUCT_NOT_FOUND`.
- Invalid hide/show source state: HTTP 409 with a dedicated product-state conflict code.
- Catalog command against an inactive store: preserve M21 behavior, HTTP 403 with `STORE_ACCESS_DENIED`.
- Owner membership removal attempt: HTTP 409 with a dedicated owner-membership protection code.

The web presents field-independent operational messages and never treats client state as authoritative. A stale button can result in a server conflict, after which the relevant catalog and summary queries are refreshed.

## Query And Persistence Design

### Public storefront

- Header, rating aggregate, and public-product count: one projection query.
- Page content: one projection query.
- Page count: one count query.
- Full service-level storefront budget: at most three queries.

Store ratings aggregate the same persisted reviews exposed by the existing product-review read path for products belonging to the store. The average is rounded to one decimal place using half-up rounding. No review list or review collection fetch is added.

### Operator console

- Operable-store choices: one projection query.
- Summary authorization and aggregate: at most two queries.
- Catalog authorization, content, and count: at most three queries.
- Full initial service-level console budget: at most six queries.

These budgets exclude framework security-principal resolution and count database statements issued by the application services under test. No membership or store lookup may repeat per product row.

### Images and pagination

The representative image follows the existing priority: representative image with the lowest applicable sort order, otherwise the first ordered image. Paginated queries do not fetch-join `Product.images` or reviews. A correlated representative-image projection or an equivalent bounded query is allowed.

Offset `Page` pagination remains the M22 contract. Determinism is guaranteed for a stable dataset by including product ID as the final ordering key. Global keyset browsing remains M24 scope.

### Indexes

- Reuse `(store_id, status, id)` for status/newest catalog reads.
- Add `(store_id, status, price, id)` for public price sorting.
- Recheck the existing store/member active-membership indexes against the final predicates.
- Do not add a trigram index for operator title substring search in M22. M24 owns the broader search strategy.

Because the application still has a hybrid DDL boundary:

- add a conditional Flyway V4 migration for an existing products table;
- mirror the price index in the JPA `Product` table index declaration so Hibernate creates it during the fresh-database path;
- verify both the legacy-upgrade and fresh-startup paths.

## Batch Command Transaction Flow

1. Authenticate the member.
2. Require active catalog-operator access to the path store.
3. Validate one to fifty unique positive product IDs.
4. Load products once by `storeId` and the ID set.
5. Confirm the loaded count equals the requested count.
6. Confirm every product is in the required source state.
7. Apply each domain transition.
8. Commit all transitions together or roll back all of them.

M22 does not add special locking beyond the existing product concurrency boundary. Inventory and higher-contention catalog operations are handled by later milestones.

## Web Experience

### `/stores/:storeId`

The public page renders:

1. store name, type, operating state, and introduction;
2. rating average, review count, and public-product count for an active store;
3. `ON_SALE`, `RESERVED`, and `SOLD_OUT` filters;
4. newest and price sort controls;
5. the existing buyer-card visual language in a twelve-item page;
6. loading, empty, error, and page-navigation states.

A suspended store renders its public identity and a clear `현재 운영이 중지된 상점입니다` state. It does not render ratings or products. Pending and rejected stores use the normal not-found state.

### `/me/store`

The console uses one continuous workspace:

- store selector, current role, lifecycle status, and public-store preview link;
- a summary strip for the four product statuses;
- `카탈로그`, `프로필`, and `운영자` tabs;
- status filter, title search, ordering control, create action, product rows, selection, and batch actions;
- direct links to existing sales, refunds, settlements, and reports.

Owners see all three tabs. Managers see the catalog workspace but not profile or membership management. An inactive store remains readable and clearly marks all catalog commands unavailable.

Desktop uses a dense product table. Mobile converts each product into a scan-friendly vertical row while retaining status, selection, and the primary edit/hide/show action.

### Cache invalidation

Query keys include store, filters, ordering, and page. Hide/show success invalidates the affected store's operator summary, operator catalogs, and public catalogs. Profile mutation invalidates owner-private store data and the public storefront header. Manager removal invalidates membership and operable-store queries for the acting user; the removed manager's next server request is denied even if their client cache is stale.

## Testing Strategy

All new JUnit `@Test` method names use Korean words joined with underscores.

### Public API and privacy

- Active, suspended, pending, rejected, and missing store behavior.
- Absence of private legal, review-process, owner, and membership fields.
- Rating average, zero-review null average, review count, and public-product count.
- Status filter, all three sorts, invalid parameters, and size bounds.
- Stable page boundaries when several products have equal prices.
- Representative-image fallback and no image-collection pagination issue.

### Operator catalog and authorization

- Owner, manager, outsider, removed manager, and unauthenticated access.
- Active-store commands and inactive-store read-only behavior.
- Summary counts and catalog status/title filters.
- Individual and batch hide/show success.
- Empty, duplicate, oversized, missing, cross-store, and invalid-state batches.
- Full rollback when one item in a batch is invalid.

### Membership

- Owner list success and manager/outsider denial.
- Active memberships only.
- Manager deactivation and immediate authorization loss.
- Owner-membership removal protection.

### Query and migration

- Public storefront query budget of three.
- Initial operator console query budget of six.
- No per-row store, membership, image, or review query.
- Legacy schema migration through V4.
- Fresh PostgreSQL startup with the matching JPA index.

### Regression and web verification

- Existing product detail, image, wishlist, cart, order, review, refund, settlement, and seller-report suites remain green.
- Web TypeScript checks and production build pass.
- No new web test framework is introduced in M22; the current project has none.
- Manual flows cover anonymous active/suspended storefronts, owner catalog and membership work, manager catalog work, inactive read-only console behavior, and desktop/mobile layout.
- An HTTP-boundary flow rechecks that hidden products and inactive-store products cannot be ordered.

## Completion Gate

M22 is complete only when all of the following pass:

- focused storefront, authorization, pagination, batch-atomicity, membership, migration, and query-count tests;
- the JDK 21 backend full test suite;
- the web production build;
- `git diff --check`;
- an isolated HTTP lifecycle flow;
- a manual owner/manager and desktop/mobile browser walkthrough;
- an M22 implementation note for any retained hybrid-DDL constraint;
- the final M22 handoff documenting verification evidence and M23 prerequisites.

## Compatibility Constraints

- Product commercial ownership remains the required `Product.store` relation.
- Product `sellerId` and `sellerNickname` remain temporary compatibility fields derived from the store's immutable owner.
- Order compatibility seller fields continue to come from `Order.seller`.
- Historical order, refund, settlement, and report authorization remains on the order seller snapshot.
- Cart and order creation revalidate current product and store availability on the server.
- Inactive business stores remain excluded from normal discovery.
- Public projections never expose legal business data, administrator-review data, memberships, or staff identifiers.
