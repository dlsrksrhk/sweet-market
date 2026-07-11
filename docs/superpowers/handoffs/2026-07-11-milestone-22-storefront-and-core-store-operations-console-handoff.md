# Milestone 22 storefront and core store operations console handoff

Milestone 22 delivers a buyer storefront and an owner/manager store operations console while preserving M21 store ownership, membership authorization, buyer privacy, checkout revalidation, and historical seller compatibility rules.

## Fresh verification evidence

- Focused backend command: `gradlew.bat test --tests 'com.sweet.market.store.StorefrontApiTest' --tests 'com.sweet.market.store.StoreOperationsApiTest' --tests 'com.sweet.market.jpalab.StorefrontQueryOptimizationTest' --tests 'com.sweet.market.store.migration.*' --rerun-tasks`
  - Exit 0, `BUILD SUCCESSFUL in 40s`.
  - 35 tests in 6 suites; 0 failures, 0 errors, 0 skipped.
- Full backend command: `gradlew.bat test --rerun-tasks`
  - Exit 0, `BUILD SUCCESSFUL in 2m 33s`.
  - 442 tests in 62 suites; 0 failures, 0 errors, 0 skipped.
- Web command: `npm run build`
  - Exit 0; TypeScript checks passed; Vite 6.4.3 transformed 138 modules and built in 1.35s.
- Final `git diff --check`: exit 0, no output.

Query budgets are public header plus first 12-product page at no more than 3 prepared statements and operable-store list plus summary plus first 12-product page at no more than 6. Both assert zero collection fetches.

## Isolated HTTP lifecycle

The clean assertion run used PostgreSQL 17 container `m22t11-postgres-20260711-1947` on host port 55432 and backend port 18081. HTTP covered signup/login, store application/governance, image upload, product creation, public/operator reads, catalog commands, membership removal, and order creation. SQL was limited to the planned admin role, manager membership, two status fixtures, and browser-fixture reactivation because M22 has no manager-add or admin-create API.

Verified assertions:

1. Active header returned `ACTIVE`, 14 public products, 0 reviews/null rating, and no legal, membership, or owner fields. Default catalog returned 12 `ON_SALE` products; `RESERVED` and `SOLD_OUT` filters each returned one; price ascending/descending endpoints were 1,000 and 12,000.
2. Suspended business header returned `SUSPENDED`; its public catalog returned zero rows.
3. Owner and manager operations returned the expected stores, summaries, and full operator catalog.
4. Manager hide/show returned 200; outsider hide returned 403 `STORE_ACCESS_DENIED`.
5. Mixed `ON_SALE`/`HIDDEN` hide returned 409 `PRODUCT_CHANGE_NOT_ALLOWED`; the valid product remained `ON_SALE`, proving rollback.
6. Owner membership list contained owner and manager; removal returned 200; the removed manager then received 403 `STORE_ACCESS_DENIED`.
7. Ordering a hidden product and a product belonging to a suspended store each returned 409 `PRODUCT_NOT_ON_SALE`.

## In-app browser walkthrough

The verified browser origin was `http://localhost:5173`, backed by the isolated application on port 18083. The initial 127.0.0.1 origin was not used as evidence because it did not match the configured CORS origin.

- Anonymous active `/stores/1`: public identity, type, and status rendered; `publicProductCount` was 17. `ON_SALE` contained 15 products, with 12 cards on page 1 and 3 on page 2. Page 2 canonicalized to `status=ON_SALE&sort=NEWEST&page=1`. `RESERVED` rendered only product 13. Selecting ascending price produced `status=RESERVED&sort=PRICE_ASC&page=0`. No horizontal overflow was observed.
- Anonymous suspended `/stores/4`: public identity and `현재 운영이 중지된 상점입니다` rendered; no catalog region or product list was exposed.
- Owner `/me/store`: store 1 and suspended store 4 were selectable, the role rendered as `OWNER`, and the active summary was 15/1/1/0. Catalog, profile, and membership tabs rendered along with historical sales, refund, settlement, and report links. The profile form rendered. Memberships showed protected owner and removable manager rows. Hiding `Live Catalog 17` changed the summary to 14/1/1/1; showing it restored 15/1/1/0 and returned the row to `ON_SALE`.
- Owner suspended selection: store 4 retained its single operator catalog row while showing the read-only notice. Product register, edit, and hide controls were disabled.
- Manager `/me/store`: store 1 selected as `MANAGER` while the signup-created owner store 2 remained available. Only the catalog tab rendered; profile and membership tabs/private query surface were absent. Hiding and showing `Live Catalog 16` succeeded and restored the product to `ON_SALE`.
- Manager `/products/new?storeId=1`: store 1 was checked and enabled; owned store 2 was also available, confirming active operator-store preselection.
- Responsive 390x844 override (reported inner width 391): the manager catalog had no horizontal overflow. Mobile rows retained checkbox, status, price, edit, and hide actions while profile/membership tabs remained absent. The active storefront showed 12 cards without horizontal overflow and retained card price, status, store, and actions.
- Browser console warning/error log was empty.

## Migration and compatibility boundary

`V4__add_storefront_price_index.sql` conditionally creates `idx_products_store_status_price_id` only when `products` and its required columns exist. The identical JPA `@Table` index declaration covers the fresh Hibernate schema-update path. Flyway remains the durable upgrade history; the JPA declaration mirrors this index so fresh and upgraded schemas converge.

Buyer product `sellerId` and `sellerNickname` remain temporary compatibility fields derived from the store's immutable owner. Historical order seller fields still come from `Order.seller`. Buyer projections do not expose legal business data, administrator review data, memberships, or operator identity.

## Cleanup

All three disposable stacks used during verification were removed after their gates. The final live browser stack (`m22t11-live-20260711-2020`, PostgreSQL 55434, backend 18083, Vite 5173) was stopped and removed after the walkthrough. Final checks found no listeners on those ports and no named disposable container. Existing `ddwalk-postgres` on 5432 and `ddwalk-redis` on 6379 remained running.

## Known limitations and M23 prerequisites

- Manager invitation/assignment/reactivation and owner transfer remain deferred; browser fixtures require SQL-only manager membership bootstrap.
- Broad operator title substring indexing remains deferred to M24; M22 adds no trigram index.
- M23 can add inventory/availability policy on the store-owned catalog. It must preserve current buyer privacy, inactive-store read-only behavior, checkout revalidation, operator authorization, historical seller compatibility, and bounded public/operator query shapes.
