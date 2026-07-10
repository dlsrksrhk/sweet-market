# Milestone 21 Store Foundation — Task 3 Handoff

## Current State

- Worktree: `C:\dev\jpa-study\.worktrees\milestone-21-store-foundation`
- Branch: `codex/milestone-21-store-foundation`
- Completed scope: M21 Task 1 (store domain and migration foundation) and Task 2 (personal-store provisioning and business-store governance APIs).
- Next scope: M21 Task 3, product ownership migration and historical seller compatibility.
- Do not modify the main checkout's local-only `backend/src/main/resources/application.yaml` change or its untracked legacy handoff file.

## Completed

### Store Foundation

- Added `Store` and `StoreMembership` with `PERSONAL`/`BUSINESS`, `OWNER`/`MANAGER`, lifecycle transitions, ownership invariants, and optimistic locking.
- Added Flyway V1 to create store tables and backfill legacy sellers, products, and orders when the legacy tables are present.
- Added V2 to enforce one business store per owner. If pre-existing duplicate business stores are detected, the migration preserves every row and stops with an actionable error instead of deleting or merging data automatically.
- Added legacy-schema and fresh-database Testcontainers boot tests for the current Flyway/JPA transition.

### Store Provisioning And Governance

- New ordinary-member signup creates one active personal store and its active owner membership in the signup transaction.
- Added My Store, public store profile, business application/resubmission, and administrator review/lifecycle APIs.
- Business-store persistence runs in a separate `REQUIRES_NEW` transaction. A concurrent duplicate application returns `DUPLICATE_BUSINESS_STORE` (409) instead of an unexpected rollback error.
- Public store responses exclude legal business data, rejection reasons, and membership data.
- My Store responses use a deterministic order: personal store, then business store, then id.

## Verified

The focused M21 backend checks passed with:

```powershell
cd C:\dev\jpa-study\.worktrees\milestone-21-store-foundation\backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.store.StoreApiTest' --tests 'com.sweet.market.store.migration.StoreMigrationTest' --tests 'com.sweet.market.store.migration.StoreSpringBootFlywayTest'
```

The final focused results were 11 `StoreApiTest` tests, 2 `StoreMigrationTest` tests, and 1 `StoreSpringBootFlywayTest`, all passing. `git diff --check` also passed.

The full backend suite and web build have not been rerun after the Task 1 and Task 2 implementation commits; run them before declaring the milestone complete.

## Required Next Work: Task 3

1. Replace `Product.seller` with required `Product.store` ownership.
2. Add `Order.seller` as a seller-member snapshot at order creation so settlements, seller refunds, seller reports, and historical response contracts do not depend on a later store-owner change.
3. Change product create, update, and hide to resolve authorization through `StoreAccessService.requireCatalogOperator`, allowing active owners and managers and rejecting inactive memberships or inactive business stores.
4. Add `storeId`, `storeName`, and `storeType` to product and order responses while retaining compatible `sellerId` and `sellerNickname` values from the immutable store owner or order seller snapshot.
5. Exclude inactive business-store products from normal public lists. Direct reads may show the product as unavailable, but cart and order commands must reject it.
6. Update cart, wishlist, refund, settlement, seller-report, admin-product, and order query paths that currently read `Product.seller`.

## Task 3 Database Requirement

On a fresh database, V1 runs before JPA creates the pre-M21 `products` and `orders` tables. Its guarded `store_id` and `seller_id` migration blocks therefore do not run in that first boot. When Task 3 adds the JPA mappings, ensure the fresh schema receives the two columns, foreign keys, and required indexes through explicit JPA index mapping or a documented reconciliation/baseline migration. Add a fresh-PostgreSQL boot test that asserts those columns, foreign keys, and indexes, not merely table existence.

## Key References

- `docs/superpowers/specs/2026-07-10-milestone-21-store-foundation-and-business-seller-governance-design.md`
- `docs/superpowers/plans/2026-07-10-milestone-21-store-foundation-and-business-seller-governance.md`
- `docs/milestone-21-store-foundation-implementation-notes.md`
- `backend/src/main/resources/db/migration/V1__add_store_foundation.sql`
- `backend/src/main/resources/db/migration/V2__enforce_one_business_store_per_owner.sql`

## Recent Implementation Commits

- `d8f2065 feat: add store domain foundation`
- `bcfa533 feat: add store provisioning and governance APIs`
- `0d7f71f fix: isolate business store application transaction`
- `6f8df66 fix: preserve duplicate business store upgrades`

## Completion Gate

After Task 3 and remaining M21 web work, run the full backend suite with JDK 21, `npm run build` in `web`, `git diff --check`, and buyer/operator/administrator manual flows before creating the final M21 handoff.
