# Task 4 report: cleanup, metrics, and load evidence

## Scope completed

- Added `ProductViewRetentionCleanupService` with an injected `Clock` constructor for deterministic cleanup. It deletes product-view events and deduplication records strictly older than seven days in one transaction.
- Added `ProductViewRetentionScheduler`, scheduled once daily by the configurable `product.view-retention-cleanup.cron` property.
- Added the Korean-named integration regression test. It proves that entries one second older than the cutoff are deleted and entries at the seven-day cutoff are retained.
- Added `DiscoveryMetrics` timer instrumentation for catalog, event summaries, popularity, and event detail (`discovery.read.duration`, `endpoint` tag).
- Bound Caffeine active-event cache statistics to Micrometer under cache name `discovery.active-events`.
- Added the `local-experiment` profile. Its `DataSource` post-processor counts JDBC executions from both Hibernate and `NamedParameterJdbcTemplate` as `discovery.jdbc.statements` without affecting the default profile.
- Added the reproducible k6 scenario and evidence report. The workload is one minute at 20 VUs for warm-up, then five minutes at 100 VUs; the decision is home/catalog 70% and detail 30%. The report starts `local` fixtures, derives `PRODUCT_ID` from the public catalog response, and supports `cache-off` as an executable comparison profile.

## TDD evidence

1. Added `칠일을_지난_조회이벤트와_중복제거_행을_삭제한다` before the cleanup service existed.
2. Ran the focused test; compilation failed because `ProductViewRetentionCleanupService` was absent.
3. Added the minimal repository delete methods and cleanup service/scheduler.
4. Re-ran the focused test successfully. The test uses real PostgreSQL repositories and a fixed `Clock`.

## Verification evidence

| Check | Result |
| --- | --- |
| `backend\gradlew.bat test --tests 'com.sweet.market.productview.*' --rerun-tasks` | `BUILD SUCCESSFUL` (2026-07-16) |
| `backend\gradlew.bat test --tests 'com.sweet.market.productview.ProductViewRetentionCleanupServiceTest' --rerun-tasks` with `SPRING_PROFILES_ACTIVE=local-experiment` | `BUILD SUCCESSFUL` (2026-07-16) |
| Isolated Discovery API event-order test | `BUILD SUCCESSFUL` (2026-07-16) |
| `docker run ... grafana/k6:latest inspect performance/m30-catalog-reads.js` | Parsed: warm-up 20 VUs/1m and measured 100 VUs/5m |
| `git diff --check` | Clean before commit |

## Known concern

The local machine has no `k6` executable on `PATH`; Docker k6 was used for the required parse validation. No load numbers or SQL plans were fabricated. `docs/superpowers/reports/2026-07-16-milestone-30-catalog-read-performance.md` records the fixture inputs, commands, and result fields to populate from an actual run.

## Review follow-up evidence

- Added `cache-off` as a real Spring profile (`discovery.active-event-cache.enabled=false`) and a cache test proving that disabled mode invokes the loader on every read.
- The report now starts `local,local-experiment`, then obtains `PRODUCT_ID` from `GET /api/catalog/products?size=1` and stops if no buyer-visible product exists. It documents both cache-off and default cache-on server commands.
- `DiscoveryApiTest` now invalidates the singleton cache before each fixture setup. The retention test also clears its two product-view tables before setup because Hibernate's test schema does not create the deduplication foreign-key cascade.
- Fresh command: `backend\gradlew.bat test --tests 'com.sweet.market.productview.*' --tests 'com.sweet.market.discovery.*' --rerun-tasks` → `BUILD SUCCESSFUL` (18 tests, 2026-07-16).
- Fresh command: `docker run --rm -v "${PWD}:/work" -w /work grafana/k6:latest inspect performance/m30-catalog-reads.js` → parsed the 20-VU/1m warm-up and 100-VU/5m scenario (2026-07-16).
