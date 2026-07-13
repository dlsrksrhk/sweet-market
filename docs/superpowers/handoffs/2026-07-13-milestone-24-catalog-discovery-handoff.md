# Milestone 24: Catalog Discovery Handoff

## Query-budget regression

`CatalogQueryOptimizationTest` protects the public catalog's page budget with real service calls:

- An authenticated 20-product page returns 12 cards, executes at most two Hibernate/JPA statements plus one JDBC projection statement (three total), and has zero collection fetches.
- An anonymous 20-product page returns 12 cards, has zero Hibernate-prepared statements and zero collection fetches; its transaction-aware test-only DataSource records exactly one JDBC projection statement.
- A Hibernate `StatementInspector` records every JPA statement after fixture setup. Together with the JDBC recorder it asserts the complete per-page SQL set contains neither `COUNT(` nor `inventory_adjustments`, so either cannot hide in the personalization path.
- Every fixture product has a fallback and representative `ProductImage`; returned cards assert a nonblank representative URL, exercising the lateral image lookup while retaining zero to-many collection fetches.

### Per-page statement counts

The following counts include the catalog projection and the bounded request-path statements after fixture setup. The global anonymous/authenticated counts are captured by `CatalogQueryOptimizationTest`; the fixed-store counts add the route's single active-store `existsByIdAndStatus` validation before the same shared service path.

| Endpoint scope | Anonymous buyer | Authenticated buyer |
| --- | --- | --- |
| Global catalog | **1 statement**: one JDBC card projection. | **3 statements**: one JDBC card projection plus one bounded wishlist-ID batch lookup and one bounded cart-ID batch lookup. |
| Fixed-store catalog | **2 statements**: one active-store existence check plus one JDBC card projection. | **4 statements**: the active-store existence check, the JDBC card projection, and the two bounded wishlist/cart ID batch lookups. |

All four paths issue no count query and do not read `inventory_adjustments`. The page projection remains a single query; fixed-store scope is a predicate on that projection, not a second catalog query.

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

Measurements used PostgreSQL 17 with 100,000 eligible `ON_SALE` single-item products across two active business stores (51,500 in store 1 and 48,500 in store 2), one representative image per product, and fresh `ANALYZE` statistics. The harness reproduced the repository's product/store/inventory/image relations and the V3/V4/V6/V7 catalog indexes. The integration-test schema intentionally disables Flyway, so this isolated migrated-schema harness was used for index-plan evidence.

### Reproducible isolated harness

Run the following from the repository root. It starts an isolated PostgreSQL 17 container, creates the minimum catalog schema, reproduces the V3/V4/V6 indexes, and seeds the exact 100,000-row distribution used for these measurements.

```powershell
$name = 'sweet-market-catalog-plan'
docker rm -f $name 2>$null
docker run --name $name -e POSTGRES_PASSWORD=market -e POSTGRES_DB=market -d postgres:17-alpine
while ((docker exec $name pg_isready -U postgres -d market 2>$null) -notmatch 'accepting connections') { Start-Sleep -Milliseconds 500 }

$schema = @'
CREATE EXTENSION pg_trgm;
CREATE TABLE stores (id BIGSERIAL PRIMARY KEY, status VARCHAR(20) NOT NULL, type VARCHAR(20) NOT NULL, public_name VARCHAR(100) NOT NULL);
CREATE TABLE products (id BIGSERIAL PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES stores(id), title VARCHAR(100) NOT NULL, description VARCHAR(2000) NOT NULL, price BIGINT NOT NULL, category VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL, sales_policy VARCHAR(20) NOT NULL, low_stock_threshold INTEGER);
CREATE TABLE inventories (id BIGSERIAL PRIMARY KEY, product_id BIGINT NOT NULL UNIQUE REFERENCES products(id), total_quantity INTEGER NOT NULL, reserved_quantity INTEGER NOT NULL DEFAULT 0);
CREATE TABLE product_images (id BIGSERIAL PRIMARY KEY, product_id BIGINT NOT NULL REFERENCES products(id), image_url VARCHAR(1000) NOT NULL, representative BOOLEAN NOT NULL, sort_order INTEGER NOT NULL);
CREATE INDEX idx_products_store_status_id ON products (store_id, status, id);
CREATE INDEX idx_products_store_status_price_id ON products (store_id, status, price, id);
CREATE INDEX idx_products_title_trgm ON products USING GIN (title gin_trgm_ops);
CREATE INDEX idx_products_description_trgm ON products USING GIN (description gin_trgm_ops);
INSERT INTO stores (status, type, public_name) VALUES ('ACTIVE', 'BUSINESS', 'Plan Store A'), ('ACTIVE', 'BUSINESS', 'Plan Store B');
'@
$schema | docker exec -i $name psql -U postgres -d market -v ON_ERROR_STOP=1

$seed = @'
INSERT INTO products (store_id, title, description, price, category, status, sales_policy)
SELECT CASE WHEN n <= 1500 THEN 1 ELSE 2 END, CASE WHEN n % 500 = 0 THEN 'ultrarare-title-token-' || n ELSE 'catalog title ' || n END, CASE WHEN n % 500 = 1 THEN 'ultrarare-description-token-' || n ELSE 'catalog description ' || n END, 10000 + n, 'OTHER', 'ON_SALE', 'SINGLE_ITEM' FROM generate_series(1, 3000) AS n;
INSERT INTO products (store_id, title, description, price, category, status, sales_policy)
SELECT CASE WHEN n <= 50000 THEN 1 ELSE 2 END, CASE WHEN n % 20000 = 0 THEN 'ultrarare-title-token-' || n ELSE 'catalog title bulk ' || n END, CASE WHEN n % 20000 = 1 THEN 'ultrarare-description-token-' || n ELSE 'catalog description bulk ' || n END, 20000 + n, 'OTHER', 'ON_SALE', 'SINGLE_ITEM' FROM generate_series(1, 97000) AS n;
INSERT INTO product_images (product_id, image_url, representative, sort_order) SELECT id, 'https://example.com/' || id || '.jpg', TRUE, 0 FROM products;
ANALYZE;
'@
$seed | docker exec -i $name psql -U postgres -d market -v ON_ERROR_STOP=1
```

To reproduce the V7 before/after evidence, first run the page query below without the image index, then create the exact V7 index and run it again:

```powershell
$globalFirst = @'
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id AS product_id, p.title, p.price, p.category,
       representative_image.image_url AS representative_image_url,
       p.sales_policy, i.total_quantity - i.reserved_quantity AS available_quantity,
       p.low_stock_threshold, s.id AS store_id, s.public_name AS store_name, s.type AS store_type
FROM products p
JOIN stores s ON s.id = p.store_id AND s.status = 'ACTIVE'
LEFT JOIN inventories i ON i.product_id = p.id
LEFT JOIN LATERAL (SELECT image_url FROM product_images pi WHERE pi.product_id = p.id ORDER BY pi.representative DESC, pi.sort_order ASC, pi.id ASC LIMIT 1) representative_image ON TRUE
WHERE p.status = 'ON_SALE' AND (p.sales_policy = 'SINGLE_ITEM' OR i.total_quantity - i.reserved_quantity > 0)
ORDER BY p.id DESC LIMIT 13;
'@
$globalFirst | docker exec -i $name psql -U postgres -d market -P pager=off
docker exec $name psql -U postgres -d market -c 'CREATE INDEX idx_product_images_product_representative_sort_order_id ON product_images (product_id, representative DESC, sort_order ASC, id ASC); ANALYZE product_images;'
$globalFirst | docker exec -i $name psql -U postgres -d market -P pager=off
```

The exact deep/fixed command variants are:

```powershell
$globalDeep = $globalFirst.Replace("ORDER BY p.id DESC", "AND p.id < 10000 ORDER BY p.id DESC")
$fixedFirst = $globalFirst.Replace("ORDER BY p.id DESC", "AND s.id = 1 ORDER BY p.id DESC")
$fixedDeep = $globalFirst.Replace("ORDER BY p.id DESC", "AND s.id = 1 AND p.id < 10000 ORDER BY p.id DESC")
$globalDeep | docker exec -i $name psql -U postgres -d market -P pager=off
$fixedFirst | docker exec -i $name psql -U postgres -d market -P pager=off
$fixedDeep | docker exec -i $name psql -U postgres -d market -P pager=off
```

The exact keyword checks use the repository's dedicated materialized candidate CTE. The `UNION` deduplicates IDs while allowing PostgreSQL to select title and description trigram indexes independently:

```powershell
$titlePlan = @'
EXPLAIN (ANALYZE, BUFFERS)
WITH keyword_matches AS MATERIALIZED (
    SELECT id FROM products WHERE title ILIKE '%ultrarare-title-token%'
    UNION
    SELECT id FROM products WHERE description ILIKE '%ultrarare-title-token%'
)
SELECT p.id AS product_id, p.title, p.price, p.category, representative_image.image_url AS representative_image_url, p.sales_policy, i.total_quantity - i.reserved_quantity AS available_quantity, p.low_stock_threshold, s.id AS store_id, s.public_name AS store_name, s.type AS store_type
FROM keyword_matches km JOIN products p ON p.id = km.id
JOIN stores s ON s.id = p.store_id AND s.status = 'ACTIVE'
LEFT JOIN inventories i ON i.product_id = p.id
LEFT JOIN LATERAL (SELECT image_url FROM product_images pi WHERE pi.product_id = p.id ORDER BY pi.representative DESC, pi.sort_order ASC, pi.id ASC LIMIT 1) representative_image ON TRUE
WHERE p.status = 'ON_SALE' AND (p.sales_policy = 'SINGLE_ITEM' OR i.total_quantity - i.reserved_quantity > 0)
ORDER BY p.id DESC LIMIT 13;
'@
$descriptionPlan = $titlePlan.Replace('ultrarare-title-token', 'ultrarare-description-token')
$titlePlan | docker exec -i $name psql -U postgres -d market -P pager=off
$descriptionPlan | docker exec -i $name psql -U postgres -d market -P pager=off
docker rm -f $name
```

The measured SQL was the `CatalogSearchRepository` global/fixed-store NEWEST projection, including its representative-image lateral query, `size + 1` limit (`LIMIT 13`), and keyset predicate. No plan contained `OFFSET`.

| Path | Cursor | Key plan evidence | Planning / execution |
| --- | --- | --- | --- |
| Global | first | backward `products_pkey`; V7 representative-image index | 1.258 ms / 0.134 ms |
| Global | near-end (`p.id < 10000`) | `products_pkey` `Index Cond: (id < 10000)`; 13 rows returned | 1.363 ms / 0.247 ms |
| Fixed store 1 | first | backward `products_pkey`; 47,000 other-store rows filtered before the page | 1.330 ms / 4.351 ms |
| Fixed store 1 | near-end (`p.id < 10000`) | `products_pkey` `Index Cond: (id < 10000)`; 13 rows returned | 1.680 ms / 0.172 ms |

The deep plans prove the keyset predicate starts at the cursor's ID rather than discarding rows with an offset. The fixed-store first-page plan's cross-store filtering is an existing query-shape concern: the existing V3 `(store_id, status, id)` index is present, but PostgreSQL selected `products_pkey` because the route constraint arrives through the `stores` join. No additional products index was added because it would duplicate that existing index without fixing the join predicate; a future query-shape change should add an explicit `p.store_id` condition and remeasure.

Keyword plans use the materialized CTE's two independent branches, each with a `Bitmap Index Scan` on its matching V6 trigram index; `UNION` performs ID deduplication before the unchanged card projection:

| Search | Selected index evidence | Planning / execution |
| --- | --- | --- |
| title token | `idx_products_title_trgm` (and the empty description branch considers `idx_products_description_trgm`) | 1.899 ms / 9.142 ms |
| description token | `idx_products_description_trgm` (and the empty title branch considers `idx_products_title_trgm`) | 1.813 ms / 11.678 ms |

## Added migration from plan evidence

Before the new index, the full repository projection's lateral representative-image selection performed a full `product_images` scan and sort for each of 13 cards: `Rows Removed by Filter: 99999`, 13,416 shared-buffer hits, and 63.542 ms execution time. `V7__add_catalog_representative_image_index.sql` adds `idx_product_images_product_representative_sort_order_id` on `(product_id, representative DESC, sort_order ASC, id ASC)`, which matches the lateral predicate and order. With the index in the same 100,000-product fixture, PostgreSQL used an index scan for each lateral lookup; the exact global first projection used 56 buffers and completed in 0.134 ms. V7 wraps creation in `to_regclass('public.product_images')` because Flyway precedes JPA schema creation on fresh/legacy databases. When V7 records version 7 before that table exists, the matching `ProductImage` JPA `@Index` creates the same index once Hibernate creates the fresh table; the fresh-startup migration test verifies it. This is a new migration, not an edit to any applied migration.

## Remaining follow-up

The fixed-store first-page `products_pkey` scan is bounded by `LIMIT 13` but can discard other-store rows when IDs are interleaved or clustered. Addressing it requires a repository query-shape change, not another duplicate index; it is outside this task's query-budget and migration scope.

## Final delivery verification (2026-07-13)

All final backend verification used JDK 21, `JWT_SECRET=sweet-market-local-test-secret-key-32bytes-minimum`, and `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4`.

| Check | Result |
| --- | --- |
| Focused catalog/product/store/cart/wishlist command | `BUILD SUCCESSFUL` in 1m 05s. The matching XML suites contain 109 tests with 0 failures, 0 errors, and 0 skipped. |
| Full backend `./gradlew.bat test --rerun-tasks` | `BUILD SUCCESSFUL` in 3m 23s. Fresh XML aggregation: 75 suites, 532 tests, 0 failures, 0 errors, and 0 skipped. |
| Web `npm run build` | Exit 0 in 7.4s: both TypeScript checks and Vite production build completed. |
| `git diff --check` | Exit 0 with no diagnostics. |

The exact focused compatibility command was:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='4'
.\gradlew.bat test --tests 'com.sweet.market.catalog.*' --tests 'com.sweet.market.product.ProductApiTest' --tests 'com.sweet.market.store.StorefrontApiTest' --tests 'com.sweet.market.cart.CartApiTest' --tests 'com.sweet.market.wishlist.WishlistApiTest' --rerun-tasks
```

The focused budget command was rerun with the same environment and `--rerun-tasks` after this documentation correction:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='4'
.\gradlew.bat test --tests 'com.sweet.market.catalog.CatalogQueryOptimizationTest' --rerun-tasks
```

It completed `BUILD SUCCESSFUL` in 35 seconds; its two tests had 0 failures, 0 errors, and 0 skipped.

The final backend run covered the new catalog flow alongside the existing product, storefront, cart, wishlist, order, store, and admin regressions. The pre-existing offset storefront endpoint remains unchanged; catalog discovery is additive through `GET /api/catalog/products` and `GET /api/stores/{storeId}/catalog/products`. Existing product API and storefront API tests are also included in the focused verification above.

The query-budget and PostgreSQL 17 `EXPLAIN (ANALYZE, BUFFERS)` evidence recorded above is the delivery evidence for the catalog path: no page count query, no `inventory_adjustments` load, at most three authenticated statements / exactly one anonymous projection statement, zero collection fetches, keyset pagination without `OFFSET`, dedicated V6 trigram candidate-index probes, and the V7 representative-image index improvement.

No interactive browser session was run as part of this final verification. Task 7 did confirm that Vite served the compiled application at `http://localhost:5173/` (HTTP 200); responsive interaction, URL transitions, and authenticated buyer actions remain covered by component/CSS review and backend/API regression tests rather than a browser-driven end-to-end test.
