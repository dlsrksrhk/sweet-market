# Milestone 21 Task 3: Product Store Ownership Design

## Purpose

Move product ownership from an individual member to a store without breaking the historical seller identity used by orders, settlements, refunds, seller reports, and existing response contracts.

## Scope

This task covers backend schema and domain migration, authorization, public availability rules, API response compatibility, query shape, and regression verification.

It does not implement the product-form store selector or other store screens. Those remain in Milestone 21 Task 5. It also excludes rolling deployment support, product transfer between stores, nickname snapshots, and a full Flyway baseline.

## Confirmed Decisions

- `Store` is the only commercial owner of a product.
- `Product` has a required `Store store` relation and no `Member seller` relation.
- Existing databases are migrated in one release; old and new application versions do not run concurrently.
- After a validated backfill, the legacy `products.seller_id` foreign key and column are removed.
- New databases retain the current hybrid schema strategy: Flyway handles store-transition data and Hibernate `update` creates the pre-existing JPA tables and new mapped columns.
- `Order.seller` records the seller member at order creation. It preserves member identity only; seller nicknames continue to come from the current member profile.
- A product never moves between stores in this task.

## Ownership And Authorization

`StoreMembership` is the database-backed operation boundary, not a second ownership model.

- Product creation accepts `storeId` and requires `StoreAccessService.requireCatalogOperator(storeId, memberId)`.
- Product update and hide resolve the product's existing store, then require the same active owner or manager authority.
- An active personal store may operate its catalog. A business store may do so only while `ACTIVE`.
- Pending, rejected, and suspended business stores cannot create, edit, hide, or publish products.
- Ownership and sensitive business data remain owner-only operations; managers receive catalog-operation authority only.

## Data Migration

The migration path is deliberately fail-fast.

1. On a legacy database, verify that every product has a non-null `store_id` that references an existing store. The M21 store foundation migration supplies the backfill.
2. Abort with an actionable database error if any row violates that invariant. Do not delete products or infer a replacement owner.
3. Remove the obsolete `products.seller_id` foreign key and column once validation succeeds.
4. Ensure the indexes needed for deterministic store/status product reads exist.

On a fresh database, migration scripts can run before Hibernate has created the historical `products` and `orders` tables. Therefore, the new JPA mappings must declare the required product-to-store and order-to-seller relations and indexes. PostgreSQL startup tests must verify the final columns, foreign keys, and indexes rather than only successful application startup.

## Order History And Compatibility

At order creation, the order service stores `product.store.owner` in `Order.seller`. Historical seller-oriented flows use this order relation, not a later lookup through the product's store.

- Product responses expose `storeId`, `storeName`, and `storeType` from the product store.
- Product `sellerId` and `sellerNickname` remain temporarily compatible fields derived from the product store owner.
- Order `sellerId` and `sellerNickname` derive from `Order.seller`.
- Cart, wishlist, refund, settlement, seller report, administrator product, and order-query paths stop reading `Product.seller`.

This keeps product ownership current and store-based while retaining an unambiguous seller identity for completed transactions. A seller nickname is intentionally not copied into the order schema; later nickname edits appear in historical views.

## Availability Rules

- Normal buyer product lists exclude products belonging to pending, rejected, or suspended business stores.
- A direct product read may remain accessible but reports that the product is unavailable for purchase.
- Cart insertion and order creation revalidate store/product availability within their transactions, so a stale product page cannot create a purchase after a store state change.

Authorization failures return the existing authorization error contract. Inactive store operations return a store-state error, and attempted purchase of an unavailable product returns the existing product-availability error contract or a dedicated equivalent only where no suitable contract exists.

## Query Boundaries

Public product reads use a projection or bounded join for store id, name, type, and status. They do not load store memberships or all store products. Membership queries occur only for command authorization.

Product and order read paths must avoid per-row store or membership queries. Store/status ordering and filtering receive only the indexes justified by the final query paths.

## Verification

- A fresh PostgreSQL startup test asserts `products.store_id`, `orders.seller_id`, related foreign keys, and required indexes.
- A legacy-schema migration test proves that products are backfilled to exactly one store and that `products.seller_id` is removed.
- Product command tests cover owner, manager, outsider, inactive membership, and inactive business-store behavior.
- Availability tests cover public-list exclusion, direct-read purchase unavailability, cart revalidation, and order revalidation.
- Regression coverage spans product, order, cart, wishlist, refund, settlement, seller report, and administrator query paths.
- Product-list query verification proves that the ownership migration adds no store or membership N+1 behavior.
- New JUnit test method names use Korean words separated by underscores.
- Completion requires the backend test suite with JDK 21, the web build, and `git diff --check` to pass.

## Handoff

After this task, Task 4 can optimize store-aware public query shapes and Task 5 can expose selected operable stores in product creation UI. Milestone 22 must reuse this store ownership and membership authorization model for the storefront and operator console rather than introducing another seller-profile model.
