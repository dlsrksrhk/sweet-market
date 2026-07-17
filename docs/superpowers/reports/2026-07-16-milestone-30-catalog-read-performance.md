# M30 catalog-read performance experiment

> Correction note (2026-07-17): the original run documented below is retained as historical context but is superseded because its query plans were captured in a later trace session and therefore did not prove same-process, same-mode provenance. The authoritative rerun and registration are appended under **Correction rerun**.

## Scope and reproducibility

The unchanged workload is [`performance/m30-catalog-reads.js`](../../../performance/m30-catalog-reads.js). It uses a one-minute 20-VU warm-up followed by a five-minute 100-VU measured interval. Each iteration chooses the home/catalog batch 70% of the time and product detail 30% of the time. The home path calls active events, popularity, and the global catalog together.

The measured fixture is `m30-v1`, produced by the `performance-fixture` Spring profile with random seed `310031`, fixed fixture instant `2026-07-17T00:00:00Z`, and 500-row persistence/JDBC batches. The local PostgreSQL and Redis Docker volumes were reset once before cache OFF. Cache ON reused the same database and IDs without resetting or rerunning the fixture.

```powershell
cd backend
$env:JAVA_HOME='<JDK 21 installation>'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET=$env:SWEET_MARKET_LOCAL_JWT_SECRET # supply a local value of at least 32 bytes

# First start only: create m30-v1 and measure cache OFF.
.\gradlew.bat bootRun --args='--spring.profiles.active=local,performance-fixture,local-experiment,cache-off'

# After the OFF process stops, reuse the database and measure cache ON.
.\gradlew.bat bootRun --args='--spring.profiles.active=local,local-experiment'

# Run from the repository root with an authorized ADMIN token.
.\performance\collect-m30-measurement.ps1 `
  -BaseUrl http://localhost:8080 `
  -AdminToken $adminToken `
  -ProductId 10000 `
  -Mode OFF `
  -OutputDirectory performance/results/m30-v1
```

`local-experiment` wraps the application `DataSource`, so `discovery.jdbc.statements` counts statements issued through both Hibernate and `NamedParameterJdbcTemplate`. The counter has no endpoint tag. Consequently the structured endpoint rows retain the actual whole-run delta rather than inventing a per-endpoint allocation; the raw authorized before/after snapshots and this method are preserved in `metrics-off.json` and `metrics-on.json`.

## Conditions

| Condition | Value |
| --- | --- |
| Git commit | `492b216e60d0c14fd517a8387f5f4bfc1bf95775` |
| Dirty-worktree declaration | `true` for both modes |
| Environment | Windows, Docker PostgreSQL 17, Redis 7.4, k6 2.1.0, JDK 21 |
| Hardware | Intel Core i7-14700KF, 20 cores / 28 logical processors, 63.7 GiB RAM |
| Scenario | `m30-catalog-reads-v1`; 60-second warm-up + 300-second measurement |
| Cache OFF interval | 2026-07-17 20:01:00.751–20:07:02.396 KST |
| Cache ON interval | 2026-07-17 20:11:13.764–20:17:15.265 KST |
| Product detail ID | `10000` in both modes |

The committed evidence directory is [`performance/results/m30-v1`](../../../performance/results/m30-v1). It contains both k6 summaries, authorized Actuator snapshots, the exact traced SQL and full PostgreSQL plans, metadata, and the normalized registration request.

## Fixture volume

| Fixture | Volume | Source/condition |
| --- | ---: | --- |
| Active business stores | 10 | `type=BUSINESS`, `status=ACTIVE` |
| Buyer-visible catalog products | 10,000 | all stock-managed with inventory and a representative image |
| Inventories | 10,000 | one domain-validated inventory per product |
| Scheduled promotion campaigns | 20 | active at the measurement instant |
| Scheduled coupon campaigns | 20 | active at the measurement instant |
| Wishlist items | 50,000 | unique synthetic buyer/product pairs |
| Product-view events | 200,000 | deterministic synthetic hashes, all within the seven-day window |

Before k6, the public APIs returned 40 active events and eight popular products. No real visitor identifier, credential, or token is present in the artifacts.

## Cache-off / cache-on aggregate result

| Mode | Requests / rate | p50 | p95 | HTTP errors | JDBC statements | Cache gets / evictions |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Cache disabled (`cache-off`) | 57,850 / 160.130 req/s | 272.746 ms | 423.538 ms | 0 / 57,850 (0%) | 94,477 | Not applicable; cache loader runs for every event request |
| Cache enabled (30-second entry) | 70,193 / 194.399 req/s | 7.084 ms | 103.114 ms | 137 / 70,193 (0.1951762%) | 93,958 | 20,455 hits, 12 misses, 11 expiry evictions |

All three unchanged k6 thresholds passed in both modes: `catalog_read_errors<1%`, `http_req_failed<1%`, and `http_req_duration p95<1000ms`. At the cache-ON transition to 100 VUs, 137 localhost connections were refused. The same Java/Tomcat PID remained live with no restart or application exception, and the observed 0.195% error rate is retained rather than removed from the result.

The cache counters cover warm-up plus measurement. `20,455 + 12 = 20,467` exactly matches the whole-run event request count; the 30-second entry reloaded 12 times and expired 11 times.

## Measured endpoint result

Endpoint percentiles, rates, and errors below use only raw k6 samples tagged `scenario=measured_catalog_reads` and the existing `route` tag.

| Mode | Endpoint | Samples | p50 ms | p95 ms | req/s | Error rate |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| OFF | catalog | 16,228 | 235.078 | 366.819 | 54.093 | 0 |
| OFF | events | 16,228 | 293.877 | 425.403 | 54.093 | 0 |
| OFF | popularity | 16,228 | 322.281 | 454.570 | 54.093 | 0 |
| OFF | detail | 6,937 | 230.039 | 358.929 | 23.123 | 0 |
| ON | catalog | 19,680 | 4.314 | 29.099 | 65.600 | 0.0029472 |
| ON | events | 19,680 | 1.591 | 21.886 | 65.600 | 0.0010163 |
| ON | popularity | 19,680 | 88.830 | 119.163 | 65.600 | 0.0027439 |
| ON | detail | 8,406 | 11.356 | 28.000 | 28.020 | 0.0005948 |

The comparison is a system-level result: repeatedly executing the expensive active-event query under cache OFF creates database and request-thread contention that also raises the other route latencies. It is not evidence that the cache directly changes catalog, popularity, or detail SQL.

## Query-plan record

A separate short trace session enabled Spring JDBC TRACE after both load runs. It invoked the four required HTTP requests, captured the prepared SQL and values below, substituted those exact values, and ran `EXPLAIN (ANALYZE, BUFFERS)` twice on the unchanged fixture. Full SQL and complete plans are in [`query-evidence.json`](../../../performance/results/m30-v1/query-evidence.json).

| Query | Required request / captured bind | OFF execution / rows / buffers | ON execution / rows / buffers |
| --- | --- | --- | --- |
| Global catalog | `GET /api/catalog/products?size=20`; `limitPlusOne=21` | 0.498 ms / 21 / hit 179, read 0 | 0.429 ms / 21 / hit 179, read 0 |
| Fixed-store catalog | `GET /api/stores/1/catalog/products?size=20`; `storeId=1`, `limitPlusOne=21` | 0.467 ms / 21 / hit 189, read 0 | 0.474 ms / 21 / hit 189, read 0 |
| Active events | `GET /api/discovery/events`; no binds | 68.384 ms / 40 / hit 107,767, read 0 | 67.231 ms / 40 / hit 107,767, read 0 |
| Popular products | `GET /api/discovery/popular-products`; `since=2026-07-10T20:19:54.632851900+09:00` in both CTEs | 92.745 ms / 8 / hit 85,817, read 0 | 99.239 ms / 8 / hit 85,817, read 0 |

The plan evidence shows the catalog paths returning their 21-row `LIMIT` quickly from warm shared buffers. In contrast, active events touches 107,767 shared blocks for 40 cards, and popularity touches 85,817 shared blocks while aggregating 50,000 wishlist and 200,000 seven-day view facts. Cache ON avoids repeatedly executing active events during the load test; it does not alter the underlying SQL plan.

## Normalization and registration

The normalizer accepted the real k6 2.1.0 summary-export shape, verified exact OFF/ON comparability, required the four endpoints and four query shapes for each mode, and emitted [`measurement.json`](../../../performance/results/m30-v1/measurement.json).

Local ADMIN registration returned:

| Field | Value |
| --- | --- |
| HTTP status | `201 Created` |
| runId | `1` |
| measurementId | `3a59bcfe-afdd-4d3e-8e1c-64e8bf4adc08` |
| payloadHash | `75f3d8a6da68207c63c38b9cc35d1595d574082086dd7d87d990e7c827e5d2c0` |
| valid / comparable | `true` / `true` |
| persisted endpoint metrics / query evidence | 8 / 8 |

## Correction rerun

The corrected run overwrites the current `performance/results/m30-v1` evidence directory with measurement `bbd48853-c163-4b38-9ac1-0bc16d499905`; Git history retains the superseded files. No database reset or fixture initialization occurred during this rerun. Both modes used the existing `m30-v1` rows and the shared fixed discovery clock `2026-07-17T00:00:00Z`. The initializer was explicitly disabled.

The required sequence was executed independently per mode on the dedicated local port `18080`: start server, run k6, invoke the four HTTP query shapes and capture PostgreSQL plans while that same server process remained healthy, then stop that process. OFF used profiles `local,performance-fixture,local-experiment,cache-off` on PID `58992`; ON used `local,performance-fixture,local-experiment` on PID `48100`. Each capture records the server-reported profiles, cache mode, fixed clock, health, PID, timestamp, and sequence number in `query-evidence.json`.

### Corrected conditions and aggregate result

| Condition | OFF | ON |
| --- | --- | --- |
| Git commit | `24719ef70dd59b0e1dadac53cb9a49aea5046985` (`dirty=true`) | same |
| Interval (KST) | 2026-07-17 21:56:39.131–22:02:40.683 | 2026-07-17 22:06:19.985–22:12:21.458 |
| Requests / rate | 61,719 / 170.901 req/s | 70,405 / 194.988 req/s |
| Aggregate p50 / p95 | 198.419 / 362.666 ms | 5.530 / 94.439 ms |
| HTTP failure rate | 0% | 0% |
| JDBC statement delta | 100,340 | 94,543 |
| Cache hit / miss / eviction | not applicable | 20,527 / 12 / 11 |

All thresholds passed in both modes: `catalog_read_errors<1%`, `http_req_failed<1%`, and `http_req_duration p(95)<1000ms`. This final rerun observed no HTTP failures; the retained failure arrays remain the auditable source for that zero rather than an assumed or corrected value.

### Corrected endpoint result

The normalizer recomputed every row from the retained, sanitized `route-samples-{mode}.json` duration/failure arrays rather than trusting a pre-aggregated table.

| Mode | Endpoint | Samples | p50 ms | p95 ms | req/s | Error rate |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| OFF | catalog | 17,247 | 162.830 | 313.342 | 57.490 | 0 |
| OFF | events | 17,247 | 218.951 | 368.748 | 57.490 | 0 |
| OFF | popularity | 17,247 | 246.182 | 397.984 | 57.490 | 0 |
| OFF | detail | 7,252 | 156.013 | 304.797 | 24.173 | 0 |
| ON | catalog | 19,751 | 3.939 | 24.932 | 65.837 | 0 |
| ON | events | 19,751 | 1.516 | 16.499 | 65.837 | 0 |
| ON | popularity | 19,751 | 83.922 | 108.102 | 65.837 | 0 |
| ON | detail | 8,437 | 10.468 | 23.695 | 28.123 | 0 |

### Corrected same-process query evidence

All plans use `now=2026-07-17T00:00:00Z`; popularity additionally uses `since=2026-07-10T00:00:00Z`. The actual request shapes were invoked immediately before `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`.

| Query | OFF execution / rows / buffers | ON execution / rows / buffers |
| --- | --- | --- |
| Global catalog | 0.426 ms / 21 / hit 179, read 0 | 0.428 ms / 21 / hit 179, read 0 |
| Fixed-store catalog | 0.473 ms / 21 / hit 189, read 0 | 0.471 ms / 21 / hit 189, read 0 |
| Active events | 68.890 ms / 40 / hit 107,767, read 0 | 67.117 ms / 40 / hit 107,767, read 0 |
| Popular products | 96.506 ms / 8 / hit 85,817, read 0 | 92.440 ms / 8 / hit 85,817, read 0 |

OFF plans were captured at `2026-07-17T13:05:11.2818154Z` and ON plans at `2026-07-17T13:13:34.7576174Z`. Exact SQL, complete JSON plans, and capture provenance are retained in `query-evidence.json`; only contract fields are copied into `measurement.json`.

### Corrected registration

The local ADMIN endpoint returned `201 Created`, run ID `3`, `valid=true`, and `comparable=true`, persisting all eight endpoint metrics and eight query records. The request file SHA-256 is `0d29d09519d111d0f1cee1221ad2c401f762db8dca9a5f18b57a9490e43e4d63`; the server's canonical payload SHA-256 is `293922cf37a6eaeb8a47fadff10c10c6dbb012ba636b6b9faeb98535da9e00aa`. Their different meanings are explicit in the sanitized `registration-response.json`; no credential or token is stored.

## 2026-07-17 live provenance 최종 보정

직전 보정 UUID `bbd48853-c163-4b38-9ac1-0bc16d499905`도 역사 기록으로만 남깁니다. 당시 plan PID는 기록했지만 collector가 k6 직전·직후 서버 identity를 인증 수집하지 않았고, SQL 증적도 이전 `query-evidence.json`의 template를 변환해 만들었기 때문입니다. 기존 등록을 새 실행으로 다시 표기하지 않고, `performance/results/m30-v1`을 새 UUID `385b4525-21a2-4f4a-875f-364449f59957`의 실제 재실행으로 교체했습니다.

재실행은 commit `985c8ccf406ed84e51ec76512b0c1a84b28a8bdb`에서 수행했으며 worktree는 증적 파일과 다른 task 보고서 때문에 `dirty=true`였습니다. fixture initializer는 끈 채 기존 DB와 동일 fixture를 사용했고, OFF와 ON 사이에 DB reset이나 fixture 재실행은 없었습니다. 두 모드 모두 fixed clock은 `2026-07-17T00:00:00Z`입니다.

| Mode | Profiles | Collector before / after PID | Plan PID |
| --- | --- | ---: | ---: |
| OFF | `local,performance-fixture,local-experiment,cache-off` | 35888 / 35888 | 35888 |
| ON | `local,performance-fixture,local-experiment` | 56244 / 56244 | 56244 |

Normalizer는 각 모드에서 before/after의 PID, profile, fixed clock, cache mode가 같고 plan capture의 서버 정보까지 세 방향으로 일치하지 않으면 중단합니다.

세 threshold `catalog_read_errors<1%`, `http_req_failed<1%`, `http_req_duration p(95)<1000ms`는 모두 통과했습니다. OFF의 46건 일시적 실패는 숨기거나 제거하지 않고 route sample과 error rate에 그대로 보존했습니다.

| Mode | Requests | RPS | Aggregate p50 | Aggregate p95 | Error rate | JDBC delta |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OFF | 62,914 | 174.241 | 175.219 ms | 323.813 ms | 0.0731% | 102,403 |
| ON | 70,583 | 195.481 | 4.784 ms | 91.286 ms | 0% | 94,440 |

| Endpoint | OFF p50 / p95 | ON p50 / p95 | OFF / ON RPS |
| --- | ---: | ---: | ---: |
| catalog | 142.166 / 273.525 ms | 3.758 / 21.753 ms | 58.540 / 65.940 |
| events | 196.519 / 328.187 ms | 1.247 / 13.590 ms | 58.540 / 65.940 |
| popularity | 223.638 / 355.743 ms | 82.430 / 103.847 ms | 58.540 / 65.940 |
| detail | 136.182 / 266.473 ms | 10.457 / 22.547 ms | 24.863 / 28.130 |

ON cache counters는 hit 20,600, miss 12, eviction 11입니다.

### Live JDBC trace query evidence

각 모드의 k6가 끝난 뒤 같은 서버에서 Spring JDBC TRACE를 동적으로 켜고 네 HTTP shape를 호출했습니다. Task-local 로그에서 호출 직전·직후 byte range만 읽어 HTTP worker thread의 prepared SQL과 positional bind를 결합했으며 scheduler SQL은 제외했습니다. `TemplatePath`나 과거 증적 변환은 사용하지 않았습니다. Bind 수는 global catalog 3, 나머지 세 shape 4로 검증했습니다.

| Mode | Task-local source | Byte range | Raw range SHA-256 | Sanitized statements SHA-256 |
| --- | --- | ---: | --- | --- |
| OFF | `m30-live-sql-off-f84a223504dd45209f8ec3972a5de718.log` | 2,771,421–2,807,953 | `50c4c61285e7e30d7cb17d5f40a14d7f3e57835fe6722d92faa6f74d8cc773bb` | `6640388851099b6463a77f9329624207cc1a3fa60a7fb58a8d66f862044d314b` |
| ON | `m30-live-sql-on-717146e3a49f4dd6855352ec5f7fbdcf.log` | 9,655,034–9,691,620 | `6c95f9e25581bfe229f89371548db130a016ac697a62992b9c7037964d62e490` | `e91683f12234ebdc78df084d56bd0e0db336922bd78c2b55c75316926d6a4063` |

| Shape | OFF execution | ON execution | Rows | Buffers hit/read |
| --- | ---: | ---: | ---: | ---: |
| global catalog | 0.444 ms | 0.406 ms | 21 | 179 / 0 |
| fixed-store catalog | 0.469 ms | 0.466 ms | 21 | 189 / 0 |
| active events | 68.986 ms | 66.201 ms | 40 | 107,767 / 0 |
| popularity | 94.922 ms | 90.830 ms | 8 | 85,817 / 0 |

Reconstructed exact SQL에 대해 `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`을 실행했고 full JSON plan을 보존했습니다. Raw task-local 로그는 경로·운영 로그를 포함할 수 있어 commit하지 않으며, artifact에는 basename, offset, hash와 sanitized statement만 남깁니다.

### 최종 등록

새 payload는 ADMIN endpoint에서 `201 Created`, run ID `4`, `valid=true`, `comparable=true`로 등록됐고 endpoint metrics 8개와 query evidence 8개가 저장됐습니다. Request file SHA-256은 `73c095ba399e30dfe249840be2cadab91b5c05c880294ceefcdc44ce21518f5e`, 서버 canonical payload SHA-256은 `122d6823d9c8fccea5678228dcb8d417ae0be5b8535e02c6a6da7d422c8b791c`입니다. Sanitized 등록 응답에는 credential이나 token을 저장하지 않았습니다.

최종 검증으로 JDK 21 전체 backend suite가 `BUILD SUCCESSFUL`(5분 30초), Node normalizer/live trace parser 23개 테스트가 모두 통과했습니다. PowerShell parser, normalizer 재생 SHA-256, 모든 JSON parse, three-way identity/query/privacy audit와 `git diff --check`도 통과했습니다.
