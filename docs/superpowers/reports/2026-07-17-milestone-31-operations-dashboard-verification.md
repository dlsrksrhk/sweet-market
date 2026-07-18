# Milestone 31 operations dashboard verification

## Result

M31 reconciles the operational projection against a fixed `Asia/Seoul` source fixture and passes the focused operations suite, a fresh clean complete backend suite, complete web test suite, production web build, performance-evidence replay, and repository whitespace checks. Final-review hardening also isolates forward-compatible unknown stored event types without allowing producers to emit the read-side sentinel, makes tracking provenance and drill-down availability fail closed, isolates unsupported rebuild rows, and validates authoritative performance intervals. No M21-M30 regression was observed.

Rendered browser QA completed against the local backend and Vite application for OUTSIDER, OWNER, MANAGER, and ADMIN roles at desktop and mobile widths. The walkthrough confirmed authorization, drill-down, measurement, recovery, privacy, and responsive contracts in addition to the passing backend and jsdom suites.

## Reconciliation fixture

Fixture version: `m31-reconciliation-v1`. The fixture uses the KST calendar date `2026-07-17` and `trackingStartedAt=2026-07-17T00:00:00+09:00`.

The source fixture records four commerce orders for the target store:

| Order | Coupon owner | Promotion / coupon | Outcomes |
| --- | --- | ---: | --- |
| 101 | STORE | 1,000 / 100 won | confirmed |
| 102 | PLATFORM | 2,000 / 200 won | canceled |
| 103 | STORE | 3,000 / 300 won | confirmed, then refunded |
| 104 | PLATFORM | 4,000 / 400 won | payment failure, then canceled |

It also records one promotion-and-platform-coupon-attributed `SOLD_OUT` failure, store and platform coupon claims, four successful coupon redemptions, and a separate foreign-store order/redemption worth 9,000 won in each discount family.

The projected target-store totals reconcile exactly to the independently summed source fixture:

| Metric | Expected and observed |
| --- | ---: |
| Created orders | 4 |
| Purchase failures | 2 |
| Promotion applied / realized / canceled / refunded | 10,000 / 4,000 / 6,000 / 3,000 won |
| Coupon applied / realized / canceled / refunded | 1,000 / 400 / 600 / 300 won |
| Store-visible claims / redemptions | 1 / 4 |

The test additionally proves that the foreign store sees only its own one order, the platform coupon row retains `campaign_owner_type=PLATFORM` and owner store `0` while attributing 600 won to the target commerce store, forced outbox redelivery changes neither totals nor receipt count, and a KST period ending exactly at `trackingStartedAt` returns zero facts with the tracking provenance intact.

## Verification commands

All backend commands ran from `backend` with JDK 21 and a configured `JWT_SECRET=[REDACTED]` value of the required length.

| Check | Exact result |
| --- | --- |
| Reconciliation RED, absent contract | `gradlew.bat test --tests 'com.sweet.market.operations.OperationsDashboardReconciliationTest'` failed in 21 seconds with “No tests found”. |
| Reconciliation assertion RED | The first implemented run executed 1 test and failed at platform attribution because PostgreSQL `SUM(BIGINT)` returned numeric `600` while the assertion required a `Long`. The projection value and dimensions were correct; the assertion now compares `Number.longValue()` without changing the expected value. |
| Reconciliation GREEN | Same focused command: `BUILD SUCCESSFUL in 16s`; 1 test, 0 failures. |
| Final forced reconciliation | Same test with `--rerun-tasks`: `BUILD SUCCESSFUL in 30s`; all 5 Gradle tasks executed. |
| Focused M31 backend | `gradlew.bat test --tests 'com.sweet.market.operations.*'`: 111/111 tests passed with no failures, errors, or skips. |
| Complete backend | Fresh `gradlew.bat --no-daemon cleanTest test`: `BUILD SUCCESSFUL in 4m 49s`; 127 suites, 852 tests, 0 failures, 0 errors, 0 skipped. An immediately preceding complete-suite run reported two campaign-audit projection failures, but the focused class passed and this fresh clean complete suite passed; the failure was non-reproducible, so no speculative code change was made. |
| Complete web | Node `v22.14.0`, npm `10.9.2`; `npm test`: 13 files and 62 tests passed, Vitest 6.26s and wall clock 7.8s. |
| Production web | `npm run build`: TypeScript passed; Vite transformed 166 modules and built in 1.72s, wall clock 6.5s. The main chunk is 595.71 kB and retains the non-failing size advisory. |
| Node performance tools | `node --test performance/normalize-m30-measurement.test.mjs performance/parse-m30-jdbc-trace.test.mjs`: 24 passed, 0 failed. |
| PowerShell tools | Both `collect-m30-measurement.ps1` and `capture-m30-query-evidence.ps1` parsed with zero PowerShell parser errors. |
| Evidence replay | Normalizer replay SHA-256 `73c095ba399e30dfe249840be2cadab91b5c05c880294ceefcdc44ce21518f5e` exactly matched the registered request-file hash. The separately computed server canonical-payload SHA-256 is `122d6823d9c8fccea5678228dcb8d417ae0be5b8535e02c6a6da7d422c8b791c`. |
| Evidence audit | 10 JSON artifacts parsed; collector before/after/plan identity, fixed clock, HTTP trace threads, bind counts, full plans, route-sample allowlist, secret/absolute-path/placeholder scan all passed. |
| Hygiene | Provenance/privacy checks and `git diff --check` passed. Pre-existing Task 2-4 scratch report modifications remained unstaged and unmodified by Task 14. |

Final-review hardening commits `4825671` and `0a18346` cover forward compatibility at both sides of the outbox boundary. An unknown raw `event_type` is mapped to an unsupported read-side sentinel, marked DEAD on its first attempt with no receipt or projection mutation, and does not stop a following valid row in the same batch. `JdbcOperationalEventRecorder` rejects that sentinel before lock or SQL execution, so application producers cannot persist `UNKNOWN`. The focused projection package passed 26/26 tests and `OperationalEventRecorderTest` passed 6/6.

Final hardening commits `ac1ab16` and `c74cc50` complete the remaining review findings:

- Tracking coverage is classified explicitly as `UNTRACKED`, `PARTIAL`, or `TRACKED`; store and administrator drill-downs are enabled only for known partial/tracked coverage and fail closed when overview/provenance resolution is unknown or errors.
- Rebuild replay isolates unknown event types and unsupported schema versions while preserving the active generation. Runtime failures from supported handlers still abort the rebuild rather than silently publishing an incomplete generation. Replay uses the supplied timestamp instead of a new wall-clock value.
- Performance registration validates actual cache OFF/ON elapsed intervals against their authoritative durations with a plus-or-minus 5-second tolerance, including the 361/362-second authoritative evidence case.

The final broad branch re-review found no Critical or Important issues and marked the branch ready to merge.

## Registered M30 measurement consumed by M31

- Measurement UUID: `385b4525-21a2-4f4a-875f-364449f59957`
- Registration: HTTP `201`, run ID `4`, `valid=true`, `comparable=true`
- Measurement commit: `985c8ccf406ed84e51ec76512b0c1a84b28a8bdb`, declared `dirty=true`
- Fixture/scenario: `m30-v1` / `m30-catalog-reads-v1`
- Fixed clock: `2026-07-17T00:00:00Z`
- Persisted records: 8 endpoint metrics and 8 query-evidence records

| Mode | Requests | RPS | Aggregate p50 | Aggregate p95 | Error rate | JDBC delta |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OFF | 62,914 | 174.241 | 175.219 ms | 323.813 ms | 0.0731% | 102,403 |
| ON | 70,583 | 195.481 | 4.784 ms | 91.286 ms | 0% | 94,440 |

The ON events-cache counters are 20,600 hits, 12 misses, and 11 evictions. These are comparable results for the recorded local hardware and fixture, not a context-free production capacity promise.

Full raw and normalized artifacts are under `performance/results/m30-v1`:

- k6 summaries: `k6-off.json`, `k6-on.json`
- collected metrics: `metrics-off.json`, `metrics-on.json`
- sanitized route samples: `route-samples-off.json`, `route-samples-on.json`
- exact SQL, captured binds, complete `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` plans, and live provenance: `query-evidence.json`
- normalized request and sanitized registration: `measurement.json`, `registration-response.json`

The four SQL shapes are global catalog, fixed-store catalog, active events, and popularity. Their final OFF/ON execution times are 0.444/0.406 ms, 0.469/0.466 ms, 68.986/66.201 ms, and 94.922/90.830 ms respectively. Active events touches 107,767 shared-hit blocks and popularity 85,817, so those two shapes remain the evidence-backed candidates for future query work.

## Projection query observations

- Store overview executes no more than four projection SQL statements; every store drill-down executes no more than three. The full suite covers all five drill-downs.
- Every request resolves one ACTIVE generation and applies generation, KST half-open period, store/owner, and optional filters in SQL.
- Migration tests verify normal-planner index use for generation freshness, both campaign owner/commerce-store branches, and period-bounded audit paging on representative data.
- Hourly projection queries passed correctness and bounded-query tests. M31 did not add a daily rollup because no measured dashboard interaction-budget miss justified that schema expansion.

## Role and recovery evidence

- OWNER: backend verifies own-store overview; web verifies filters, drill-down state, and campaign links only for an active business-store owner.
- MANAGER: backend verifies the same scoped overview/drill-down access; web verifies campaign mutation links are absent.
- OUTSIDER: backend returns denial for store operations metrics.
- ADMIN: backend verifies cross-store/platform overview and denies ordinary members; web exposes only the platform-coupon management link and no store campaign mutation.
- ADMIN recovery: backend and web verify payload-preserving DEAD retry and explicit, payload-free rebuild confirmation, including success and conflict states.
- ADMIN measurement: API registration/list/detail and web comparable/unavailable/invalid states are covered; the registered run artifacts independently pass replay and provenance audit.
- MOBILE: native table headers and mobile CSS contracts are covered by source/jsdom tests and the rendered store/admin routes both kept the document root at `375/375` client/scroll width. The admin measurement run list uses its intended internal `overflow-x: auto` container without global page overflow.

## Rendered browser QA

- OUTSIDER: `/me/store/dashboard` rendered the no-authorized-store guidance and did not expose store metrics.
- OWNER: store 1 rendered the overview and drill-downs, kept applied/realized/canceled/refunded amounts separate, and exposed promotion/coupon owner controls. The coupon-outcome drill-down restored its URL-backed state.
- MANAGER: the same store overview and drill-down evidence remained readable, while promotion/coupon owner controls were absent.
- ADMIN: `/admin/dashboard` rendered overview and investigation drill-downs, projector health/rebuild/DEAD privacy controls, and measurement run 4 (`385b4525-21a2-4f4a-875f-364449f59957`) as valid/comparable with OFF/ON details and the `performance/results/m30-v1` evidence path. No SQL execution control was present.
- Responsive inspection: the desktop dashboard root was `1265/1265` client/scroll width. The store and admin routes were each `375/375` on mobile, with only the intentional admin run-list table container scrolling horizontally.
- Fixture note: the performance fixture had no store memberships, so the walkthrough inserted test-only store-1 memberships for member 2 as OWNER and member 13 as MANAGER. These local database rows are not application migrations or repository fixtures.
- Browser-discovered correction: the initial ADMIN render exposed nullable cache counters from run 4 and blanked the route. Commit `0f41e9c` added a failing render regression first, then typed and rendered nullable metrics as `측정값 없음`; the ADMIN desktop/mobile walkthrough passed after the fix.

## Remaining limitations

1. Vite emits a non-failing chunk-size advisory: the main minified JavaScript chunk is 595.71 kB.
2. `projectionLagSeconds` currently describes the age of projection activity, not the true age of the oldest pending backlog item. Operators should pair it with queue health until a pending-backlog lag metric is introduced.
3. The 1 MiB canonical measurement limit is enforced after JSON parsing. A production edge should also enforce a hard HTTP request-body limit before materialization.
4. Dashboard queries have bounded statement counts and plan-shape evidence, but no dedicated dashboard load-test latency distribution. A daily rollup remains conditional on such measurement.
5. The database outbox poller is the current transport. Kafka remains a documented replacement boundary, not an installed dependency or an evidenced immediate need.
