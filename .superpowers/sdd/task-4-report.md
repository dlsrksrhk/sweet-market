# Task 4 report: cleanup, metrics, and load evidence

## Scope completed

- Added `ProductViewRetentionCleanupService` with an injected `Clock` constructor for deterministic cleanup. It deletes product-view events and deduplication records strictly older than seven days in one transaction.
- Added `ProductViewRetentionScheduler`, scheduled once daily by the configurable `product.view-retention-cleanup.cron` property.
- Added the Korean-named integration regression test. It proves that entries one second older than the cutoff are deleted and entries at the seven-day cutoff are retained.
- Added `DiscoveryMetrics` timer instrumentation for catalog, event summaries, popularity, and event detail (`discovery.read.duration`, `endpoint` tag).
- Bound Caffeine active-event cache statistics to Micrometer under cache name `discovery.active-events`.
- Added the `local-experiment` profile. Its `DataSource` post-processor counts JDBC executions from both Hibernate and `NamedParameterJdbcTemplate` as `discovery.jdbc.statements` without affecting the default profile.
- Added the reproducible k6 scenario and evidence report. The workload is one minute at 20 VUs for warm-up, then five minutes at 100 VUs; the decision is home/catalog 70% and detail 30%.

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

Running all `com.sweet.market.productview.*` and `com.sweet.market.discovery.*` tests in one JVM exposed an existing discovery-test isolation issue: `DiscoveryApiTest.활성_이벤트는_종료시각과_유형과_ID_순으로_정렬된다` receives an `ActiveEventCache` entry seeded by an earlier test and expects uncached ordering. The same test passes in isolation. This Task 4 change does not change cache loading or ordering; the remediation belongs with Task 3's cache-test setup (invalidate cache before each discovery API test).

The local machine has no `k6` executable on `PATH`; Docker k6 was used for the required parse validation. No load numbers or SQL plans were fabricated. `docs/superpowers/reports/2026-07-16-milestone-30-catalog-read-performance.md` records the fixture inputs, commands, and result fields to populate from an actual run.
