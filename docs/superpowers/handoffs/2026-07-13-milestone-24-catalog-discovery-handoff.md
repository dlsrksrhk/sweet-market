# Milestone 24: Catalog Discovery Handoff

## Query-budget regression

`CatalogQueryOptimizationTest` protects the public catalog's page budget with real service calls:

- An authenticated 20-product page returns 12 cards, executes at most three Hibernate-prepared statements for bounded personalization, and has zero collection fetches.
- An anonymous 20-product page returns 12 cards, has zero Hibernate-prepared statements and zero collection fetches; the page projection is captured through a transaction-aware test-only DataSource and is exactly one JDBC query.
- The captured catalog query contains neither `COUNT(` nor `inventory_adjustments`. The authenticated case also seeds one wishlist and one cart row on products inside the first page, so both bounded personalization paths are exercised.

The shared `QueryOptimizationTestSupport` is public so the catalog package can reuse the established Hibernate Statistics helpers rather than duplicate their measurement logic.

Focused verification:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.catalog.CatalogQueryOptimizationTest' --rerun-tasks
```

Result: `BUILD SUCCESSFUL` (two tests).

## EXPLAIN (ANALYZE, BUFFERS) evidence

Measurements used PostgreSQL 17 with 100,000 eligible `ON_SALE` single-item products across two active business stores (51,500 in store 1 and 48,500 in store 2), one representative image per product, and fresh `ANALYZE` statistics. The harness reproduced the repository's product/store/inventory/image relations and the V3/V4/V6 catalog indexes. The integration-test schema intentionally disables Flyway, so this isolated migrated-schema harness was used for index-plan evidence.

The measured SQL was the `CatalogSearchRepository` global/fixed-store NEWEST projection, including its representative-image lateral query, `size + 1` limit (`LIMIT 13`), and keyset predicate. No plan contained `OFFSET`.

| Path | Cursor | Key plan evidence | Planning / execution |
| --- | --- | --- | --- |
| Global | first | backward `products_pkey`; V7 representative-image index | 1.152 ms / 0.119 ms |
| Global | near-end (`p.id < 10000`) | `products_pkey` `Index Cond: (id < 10000)`; 13 rows returned | 1.253 ms / 0.165 ms |
| Fixed store 1 | first | backward `products_pkey`; 47,000 other-store rows filtered before the page | 1.082 ms / 3.753 ms |
| Fixed store 1 | near-end (`p.id < 10000`) | `products_pkey` `Index Cond: (id < 10000)`; 13 rows returned | 1.128 ms / 0.140 ms |

The deep plans prove the keyset predicate starts at the cursor's ID rather than discarding rows with an offset. The fixed-store first-page plan's cross-store filtering is an existing query-shape concern: the existing V3 `(store_id, status, id)` index is present, but PostgreSQL selected `products_pkey` because the route constraint arrives through the `stores` join. No additional products index was added because it would duplicate that existing index without fixing the join predicate; a future query-shape change should add an explicit `p.store_id` condition and remeasure.

Keyword plans used both V6 trigram indexes through `BitmapOr`:

| Search | Selected index evidence | Planning / execution |
| --- | --- | --- |
| title token | `idx_products_title_trgm` plus `idx_products_description_trgm` | 1.532 ms / 1.268 ms |
| description token | `idx_products_title_trgm` plus `idx_products_description_trgm` | 1.355 ms / 0.952 ms |

## Added migration from plan evidence

Before the new index, the repository's lateral representative-image selection performed a full `product_images` scan and sort for each of 13 cards: `Rows Removed by Filter: 99999`, 13,416 shared-buffer hits, and 48.269 ms execution time. `V7__add_catalog_representative_image_index.sql` adds `idx_product_images_product_representative_sort_order_id` on `(product_id, representative DESC, sort_order ASC, id ASC)`, which matches the lateral predicate and order. With the index in the same 100,000-product fixture, PostgreSQL used an index scan for each lateral lookup; the global first page used 56 buffers and completed in 0.119 ms. This is a new migration, not an edit to any applied migration.

## Remaining follow-up

The fixed-store first-page `products_pkey` scan is bounded by `LIMIT 13` but can discard other-store rows when IDs are interleaved or clustered. Addressing it requires a repository query-shape change, not another duplicate index; it is outside this task's query-budget and migration scope.
