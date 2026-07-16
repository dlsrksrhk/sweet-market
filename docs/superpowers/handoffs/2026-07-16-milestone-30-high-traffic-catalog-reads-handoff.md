# M30 high-traffic catalog reads handoff

## Verification record

All commands below were run on 2026-07-16 in the `C:\dev\study\sweet-market-m30` worktree with JDK 21 and `JWT_SECRET=sweet-market-local-test-secret-key-32bytes-minimum`.

| Check | Command/result |
| --- | --- |
| Focused backend suites | `backend\\gradlew.bat test --tests 'com.sweet.market.discovery.*' --tests 'com.sweet.market.productview.*' --tests 'com.sweet.market.catalog.*' --rerun-tasks` completed `BUILD SUCCESSFUL` in 37 seconds. |
| Complete backend suite | `backend\\gradlew.bat test --rerun-tasks` failed in 4 minutes 1 second: 725 tests completed, 3 failed. See **Blocking regressions**. |
| Web production build | `web\\npm run build` exited 0. Vite built in 1.67 seconds; it warned that the minified JavaScript chunk is 528.96 kB. |
| Whitespace check | `git diff --check` exited 0. |

## Blocking regressions

The full backend suite is not green. These are not changed by this documentation-only task and must be resolved before M31 accepts a release baseline.

1. `InventoryServiceTransactionTest.내부_메서드_완료_후_커밋_충돌도_재고_충돌로_변환하고_롤백한다` expected `BusinessException`, but received `ObjectOptimisticLockingFailureException` for `Inventory` id 1.
2. `StoreFreshDatabaseStartupTest.빈_PostgreSQL에서도_Flyway와_JPA_업데이트로_애플리케이션이_시작된다` expected Flyway schema version `13`, but observed `14`.
3. `StoreSpringBootFlywayTest.스프링_부트_Flyway_설정으로_상점_마이그레이션을_실행한다` expected Flyway schema version `13`, but observed `14`.

## Local services and fixture observation

`backend\\docker-compose.yml` services were started with `docker compose up -d`; PostgreSQL 17 and Redis 7.4 were both healthy. The backend was launched with `local,local-experiment` profiles. `DemoDataInitializer` is idempotent and exits when `admin@example.com` exists, so this run does **not** prove that it created a fresh fixture.

The already-present database state observed after launch was:

| Table/data shape | Observed count |
| --- | ---: |
| Members | 35 |
| Stores | 10 |
| Products | 370 |
| Orders | 251 |
| Promotion campaigns | 0 |
| Coupon campaigns | 0 |
| Product-view events | 0 |

All stores were `ACTIVE` and `PERSONAL`; product states were 129 `ON_SALE`, 134 `SOLD_OUT`, 97 `RESERVED`, and 10 `HIDDEN`. This is not a representative M30 discovery experiment fixture: it has no active events, business stores, view data, or campaign distribution.

One-request public-route smoke checks returned HTTP 200: events (296 ms, 11 bytes), popularity (26 ms, 11 bytes), global catalog (106 ms, 8,990 bytes), and product detail id 1 (102 ms, 606 bytes). These are startup-adjacent individual observations only, not k6 percentiles or a performance result.

## Performance evidence status

The prescribed workload remains [`performance/m30-catalog-reads.js`](../../../performance/m30-catalog-reads.js): one-minute 20-VU warm-up followed by five minutes at 100 VUs, with a 70/30 home-detail mix.

`k6` was not installed or available on `PATH` in this environment. Consequently, `k6 run performance/m30-catalog-reads.js` was not run and no p50, p95, throughput, error-rate, cache-off/cache-on, or JDBC-statement metrics were produced. Do not infer a performance result from the smoke checks.

The local Actuator metrics endpoint returned HTTP 401, so the required `discovery.jdbc.statements`, `discovery.read.duration`, and `cache.gets` snapshots could not be collected without an authorized observability credential/configuration. Cache statistics are therefore not measured.

## Locally executed query-plan evidence

The following `EXPLAIN (ANALYZE, BUFFERS)` checks ran directly against the observed local PostgreSQL fixture. They are representative SQL shapes derived from the catalog/discovery repositories, not bind-captured full HTTP request statements; repeat them with the final representative fixture and logged request SQL before making index or capacity decisions.

| Shape | Observed plan highlights |
| --- | --- |
| Global catalog | `Seq Scan` of `products` (129 returned before limiting; 241 filtered), store PK lookup with `Memoize` (119 hits/10 misses), and inventory unique-index lookup. Execution 0.368 ms; 298 shared-buffer hits. |
| Fixed-store catalog (`store_id = 1`) | `Bitmap Index Scan` on `idx_products_store_status_price_id`; inventory scan was empty. Execution 0.248 ms; 15 shared-buffer hits. |
| Active events | Promotion lookup used `idx_promotion_campaigns_store_lifecycle_period`; coupon lookup used `idx_coupon_campaigns_owner_lifecycle_issue_period`. Zero rows because the fixture has no campaigns. Execution 0.068 ms; 22 shared-buffer hits. |
| Popular products | Empty wishlist/view aggregates, product index-only scan on `idx_products_store_status_price_id`, and inventory unique-index lookup. Execution 0.381 ms; 285 shared-buffer hits. |

## M31 prerequisites

1. Repair or explicitly update the three full-suite regressions above, then rerun the complete backend suite.
2. Reset to a known empty local database before invoking the idempotent `local` fixture; add representative active business promotion/coupon campaigns plus wishlist and seven-day product-view distributions.
3. Install k6, run the unmodified workload for cache-off and cache-on modes against the same fixture, and preserve the k6 summaries.
4. Provide authorized Actuator metric access (or an approved local-only configuration), then record JDBC counter deltas, endpoint p50/p95/error rates, and Caffeine hit/miss totals.
5. Capture the exact logged global-catalog, fixed-store-catalog, active-event, and popularity SQL with real binds; rerun `EXPLAIN (ANALYZE, BUFFERS)` and retain complete output.
