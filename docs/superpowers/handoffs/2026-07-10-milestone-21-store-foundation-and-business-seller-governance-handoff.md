# Milestone 21 Store Foundation And Business Seller Governance Handoff

## Completed Scope

- Product commercial ownership now belongs to one required `Store`; mutable member ownership is no longer retained on `Product`.
- Every ordinary signup provisions one active personal store and active `OWNER` membership in the signup transaction.
- A member may own one separate business store and move it through application, rejection, correction/resubmission, approval, suspension, and reactivation rules.
- `StoreMembership` is the command authorization boundary. Active `OWNER` and `MANAGER` memberships may operate catalog commands; owner-only profile/legal-data and administrator-only governance rules remain separate.
- Product create requires an explicit `storeId`; update and hide remain scoped to the product's existing store and cannot transfer it.
- Public product and store reads expose store identity without exposing business registration, review, membership, or operator data.
- The web application includes My Store, business application/resubmission, minimal public store profile, administrator business-store review, and store-aware product create/detail/card behavior.

## Migration Behavior

M21 uses Flyway versions V1-V3 and keeps the current hybrid startup boundary documented in `docs/milestone-21-store-foundation-implementation-notes.md`.

- `V1__add_store_foundation.sql` creates `stores` and `store_memberships`, their checks, foreign keys, and authorization/public-read indexes when the referenced legacy tables exist. It backfills one `ACTIVE` personal store and one active `OWNER` membership for every existing ordinary member, also covering legacy product sellers, without duplicating an existing personal store.
- V1 adds nullable `products.store_id`, maps each legacy product to its seller's personal store, and adds the store foreign key/index. It also adds/backfills nullable `orders.seller_id` from the legacy product seller so order history has an explicit seller snapshot.
- `V2__enforce_one_business_store_per_owner.sql` refuses to discard or guess between duplicate business stores. It fails with a corrective error if duplicates exist, then installs the partial unique index enforcing one owned business store per member.
- `V3__complete_product_store_ownership.sql` fails before tightening the schema if a product has a null or orphaned `store_id`. It makes `products.store_id` non-null, adds the store/status/id index, completes and validates the order seller backfill, makes `orders.seller_id` non-null, and only then drops legacy `products.seller_id`.
- Migration integration coverage verifies both a legacy schema upgraded through Flyway and a fresh PostgreSQL startup. The fresh-database path intentionally still uses Hibernate `ddl-auto: update` to create pre-M21 tables because the repository does not yet have a complete Flyway baseline. After a full baseline exists, startup should return to Flyway-owned DDL with Hibernate `validate`.
- V1 remains a transactional migration for the project's current data scale. Large production tables need a separately planned online-index rollout because PostgreSQL `CREATE INDEX CONCURRENTLY` cannot run inside that migration transaction.

## API And Web Routes

Backend store routes:

- `GET /api/stores/me`
- `POST /api/stores/business-applications`
- `PATCH /api/stores/business-applications/{storeId}`
- `PATCH /api/stores/{storeId}/profile`
- `GET /api/stores/{storeId}`
- `GET /api/admin/business-stores` with optional `status`, `page`, and `size`
- `GET /api/admin/business-stores/{storeId}`
- `POST /api/admin/business-stores/{storeId}/approve`
- `POST /api/admin/business-stores/{storeId}/reject`
- `POST /api/admin/business-stores/{storeId}/suspend`
- `POST /api/admin/business-stores/{storeId}/reactivate`

Web routes:

- `/me/store` — authenticated personal/business store settings and lifecycle guidance.
- `/me/store/business-application` — authenticated first application or rejected-store correction/resubmission.
- `/stores/:storeId` — public minimal profile only.
- `/admin/business-stores` — administrator status-filtered list/detail governance surface.
- `/products/new` sends the chosen active store id; `/products/:productId/edit` keeps the existing store read-only.

The server remains authoritative for every command. Route guards and disabled controls are user-experience boundaries, not authorization substitutes.

## Compatibility Policy

- Product detail and summary responses retain temporary `sellerId` and `sellerNickname` fields for existing clients. They are derived from the product store's immutable owner and must stay consistent with `storeId`, `storeName`, and `storeType`.
- Order detail and summary responses expose the same store fields, while their seller compatibility fields come from the immutable `Order.seller` snapshot captured at order creation.
- Refund, settlement, seller-report, and historical order authorization/read paths continue to use the order seller snapshot where the data represents a completed or in-flight historical transaction.
- Existing orders, payments, refunds, settlements, and reviews are not reassigned to a mutable current store operator. M21 changes product ownership, not historical transaction identity.
- New clients should use store identity for commercial ownership and authorization. The seller compatibility fields must not be removed until all existing backend/web consumers have migrated and a separate deprecation decision is recorded.

## Verification Evidence

### Required full commands

Backend environment and required command:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

This exited 0 with `BUILD SUCCESSFUL`; Gradle reported the test task up-to-date. Fresh branch-specific execution was therefore forced with `.\gradlew.bat test --rerun-tasks`: `BUILD SUCCESSFUL in 3m 56s`, with 412 tests, 0 failures, 0 errors, and 0 skipped from 59 JUnit XML suites.

```powershell
cd web
npm run build
```

This exited 0. TypeScript checks passed, Vite transformed 133 modules, and produced `dist/index.html`, CSS, and JavaScript assets.

```powershell
git diff --check
```

This passed from the repository root with no whitespace errors before commit.

### Focused executable scenario suite

The store/domain/migration/product/order/refund/settlement integration classes were run explicitly with Gradle `--tests` selectors. The focused run executed 130 tests across 9 suites with 0 failures, 0 errors, and 0 skipped.

This focused evidence covers signup personal-store provisioning; business lifecycle and authorization; migration backfill/fresh startup; public privacy; inactive-store product visibility and purchase rejection; product/store response consistency; and historical order/refund/settlement seller behavior.

### Executed isolated HTTP flow

The following was exercised against a real local `bootRun` on port 18080 and a uniquely named disposable PostgreSQL 17 container on host port 25432. The database used tmpfs and no shared Compose project or volume. The administrator role was bootstrapped only inside this disposable database by updating the test admin member after signup because the application has no public administrator-creation endpoint.

1. Signed up owner, administrator fixture, and buyer through `/api/auth/signup`; `GET /api/stores/me` returned exactly one personal store, id 1, with `ACTIVE` status for the owner.
2. Applied for a business store, rejected it with an administrator reason, corrected/resubmitted it, and approved it. Observed `PENDING -> REJECTED -> PENDING -> ACTIVE` through the HTTP responses.
3. Uploaded valid PNG images and created three products through `POST /api/products` with the active business `storeId`. Every response returned `BUSINESS`, the expected store id, and `purchasable: true`.
4. Read the store without authentication. The response property set was exactly `storeId`, `type`, `publicName`, and `introduction`; it contained no legal business name, registration identifier, rejection reason, owner id, or memberships.
5. Created and delivered an order, then created a refund request. Created and confirmed a second order, then created its settlement.
6. Suspended the business store through the administrator route. A remaining product's direct read returned `purchasable: false`; a buyer order attempt returned HTTP 409 with `PRODUCT_NOT_ON_SALE`.
7. After suspension, order detail, seller refund list, and seller settlement list each retained the same historical seller identity: `sellerId: 1`, `sellerNickname: m21-owner`.

The disposable application process and PostgreSQL container were stopped and removed after the checks. Existing local `ddwalk-postgres` and `ddwalk-redis` containers were left running with their original images and ports. No browser-driven visual walkthrough was executed; web evidence is the production build plus source/test contract review, while all lifecycle scenarios above were directly executed at the HTTP boundary.

## Known Limitations

- The public store page is intentionally a minimal profile and has no product catalog, ratings summary, filters, or pagination yet.
- The owner web entry uses stores returned by `/api/stores/me`. Backend manager catalog authorization exists, but manager invitation, assignment/removal flow, and manager-facing web entry remain deferred.
- Pending, rejected, and suspended business stores are excluded from ordinary public listings. Direct product reads remain available only as non-purchasable unavailable records.
- Legal-data changes trigger re-verification; public brand name and introduction changes apply immediately. Buyer surfaces must continue to keep legal/review/operator data private.
- Browser-level layout, responsive behavior, and click-by-click visual states were not manually inspected in this gate.
- Full Flyway ownership of all database DDL and large-table online index procedures remain future infrastructure work, as described in the migration section.

## Concrete Milestone 22 Prerequisites

- Reuse `Store`, `StoreMembership`, `StoreAccessService`, and the required `Product.store` relation. Do not introduce another seller-profile or mutable member/product owner model.
- Expand `GET /api/stores/{storeId}` and `/stores/:storeId` into a buyer storefront using a dedicated paginated public projection that returns only buyer-visible products and never private store/operator fields.
- Expand `/me/store` into the core operator console with summary counts, store-scoped catalog filtering, product status/visibility actions, and links to existing sales, refunds, settlements, and reports rather than duplicating those domains.
- Define owner/manager/outsider permissions for every new catalog and membership command. Add the planned owner-managed membership list and manager removal while keeping owner transfer and invitation delivery outside M22 unless its design explicitly changes scope.
- Preserve inactive business-store behavior at storefront and checkout boundaries: excluded from normal discovery, clear unavailable direct state, and server-side cart/order revalidation.
- Keep paginated read models bounded: project one representative image, avoid fetch-joining to-many collections, and verify query counts for a full public storefront page and operator catalog page.
- Maintain the temporary seller compatibility fields until downstream migration is complete, and keep all historical order/refund/settlement/report paths on the order seller snapshot.
- Run the M22 design session against the actual migrated schema and this handoff, then add focused authorization, deterministic pagination, query-count, full backend, web build, and manual operator-flow evidence.
