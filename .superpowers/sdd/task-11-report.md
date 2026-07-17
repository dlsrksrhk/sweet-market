# Task 11 Report — Catalog Read Performance Evidence

## Outcome

Milestone 30의 카탈로그 읽기 성능 실험을 재현 가능한 고정 fixture로 실행하고, cache OFF/ON 실측 결과와 PostgreSQL 실행 계획을 Task 10 측정 계약으로 정규화했습니다. 정규화 결과는 로컬 운영 API에 실제 등록했으며 `valid=true`, `comparable=true`로 저장됐습니다.

요구사항의 `wishlists`는 실제 스키마와 런타임 조회가 사용하는 `wishlist_items`로 해석했습니다. 그 외 fixture 규모, seed, version, clock, warm-up/measured duration은 명세 그대로 고정했습니다.

## TDD 기록

1. Fixture initializer
   - RED: `M30PerformanceFixtureInitializerTest`를 먼저 추가했으며 대상 클래스가 없어 컴파일 실패하는 것을 확인했습니다.
   - GREEN: initializer와 전용 profile 설정을 구현한 뒤 fixture 성공/비어 있지 않은 DB 중단 테스트가 통과했습니다.
2. Normalizer
   - RED: fixture JSON을 사용하는 Node 테스트를 먼저 추가했으며 모듈 부재로 실패했습니다.
   - GREEN: 정확한 CLI 플래그와 Task 10 계약 검증을 구현했습니다.
   - 실제 k6 2.1.0의 summary-export가 직접 metric 속성을 사용한다는 차이를 재현 테스트로 추가한 뒤, 기존 `values` 형식과 새 형식을 모두 지원했습니다.
3. Collector
   - cache OFF 실측에서 아직 생성되지 않은 actuator metric이 404를 반환하는 문제를 재현하는 정적 테스트를 먼저 추가했습니다.
   - 기본 discovery timer 조회에도 `-AllowMissing`을 적용해 초기 metric 부재를 정상적인 0 상태로 처리했습니다.

## 구현

- `performance-fixture` profile의 `ApplicationRunner`가 core table이 비어 있는지 먼저 확인하고, 고정 version `m30-v1`, seed `310031`, instant `2026-07-17T00:00:00Z`, batch size `500`으로 fixture를 생성합니다.
- 도메인 factory와 상태 전이를 사용해 회원, 승인된 사업자 매장, 상품, 재고, 행사, 쿠폰을 생성합니다.
- 대량 데이터인 `wishlist_items`와 `product_view_events`만 JDBC batch로 적재합니다. visitor hash는 결정적으로 생성한 64자 synthetic pseudonym이며 실제 식별자는 포함하지 않습니다.
- 수집 스크립트는 DB reset/delete를 수행하지 않습니다. 지정된 서버의 actuator authorization을 확인하고, 동일한 k6 scenario로 warm-up 60초와 measured 300초를 수집합니다.
- k6 raw JSON은 endpoint별 measured sample 집계 후 삭제하고, summary/metrics/normalized evidence만 보존합니다.
- JDBC actuator metric은 endpoint tag가 없는 전체 실행 delta입니다. 계약상 각 endpoint 기록에 동일한 실측 delta를 보존했으며 이를 M30 보고서에도 명시했습니다.
- SQL evidence는 Spring JDBC TRACE에서 실제 prepared SQL/bind를 추출하고 PostgreSQL 17에서 `EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)`를 두 번 실행해 수집했습니다.

## 실험 환경과 fixture

- Git commit at measurement: `492b216e60d0c14fd517a8387f5f4bfc1bf95775` (`dirty=true`)
- Java: JDK 21
- k6: `v2.1.0` (`windows/amd64`)
- Docker Engine: `28.0.4`; Docker Compose: `2.34`
- PostgreSQL: 17; Redis: 7.4
- CPU: Intel Core i7-14700KF, 20 cores / 28 logical processors
- Memory: 63.7 GiB
- 측정 전 승인된 명령으로 `backend_market-postgres-data`, `backend_market-redis-data` volume을 삭제 후 재생성했습니다.
- 생성 검증: members 261, active business stores 10, products 10,000, inventories 10,000, scheduled promotions 20, scheduled coupons 20, wishlist items 50,000, seven-day view events 200,000.

## 실측 결과

공통 threshold인 `catalog_read_errors < 1%`, `http_req_failed < 1%`, `http_req_duration p(95) < 1000ms`는 두 모드 모두 통과했습니다.

| Mode | Requests | RPS | Aggregate p50 | Aggregate p95 | Error rate | JDBC delta |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OFF | 57,850 | 160.130 | 272.746 ms | 423.538 ms | 0% | 94,477 |
| ON | 70,193 | 194.399 | 7.084 ms | 103.114 ms | 0.1952% | 93,958 |

| Endpoint | OFF p50 / p95 | ON p50 / p95 | OFF / ON RPS |
| --- | ---: | ---: | ---: |
| catalog | 235.078 / 366.819 ms | 4.314 / 29.099 ms | 54.093 / 65.600 |
| events | 293.877 / 425.403 ms | 1.591 / 21.886 ms | 54.093 / 65.600 |
| popularity | 322.281 / 454.570 ms | 88.830 / 119.163 ms | 54.093 / 65.600 |
| detail | 230.039 / 358.929 ms | 11.356 / 28.000 ms | 23.123 / 28.020 |

ON 전체 실행에서 cache hit 20,455, miss 12, expiry eviction 11이 측정됐고 hit+miss는 event endpoint 호출 20,467건과 일치했습니다. ON 전환 직후 localhost connection refusal 137건이 발생해 전체 error rate가 0.1952%였지만 threshold는 통과했습니다. 당시 Java PID와 Tomcat은 유지됐고 애플리케이션 restart/exception은 발견되지 않아 결과를 제거하거나 보정하지 않고 그대로 기록했습니다.

## Query evidence

| Shape | OFF execution | ON execution | Rows | Buffers hit/read |
| --- | ---: | ---: | ---: | ---: |
| globalCatalog | 0.498 ms | 0.429 ms | 21 | 179 / 0 |
| fixedStoreCatalog | 0.467 ms | 0.474 ms | 21 | 189 / 0 |
| eventList | 68.384 ms | 67.231 ms | 40 | 107,767 / 0 |
| popularity | 92.745 ms | 99.239 ms | 8 | 85,817 / 0 |

전체 exact SQL과 full plan은 `performance/results/m30-v1/query-evidence.json`에 보존했습니다. Task 10 등록 payload에는 계약 필드만 정규화하여 포함했습니다.

## 정규화와 등록

- measurement ID: `3a59bcfe-afdd-4d3e-8e1c-64e8bf4adc08`
- artifact: `performance/results/m30-v1/measurement.json`
- local admin API: HTTP 201
- registered run ID: 1
- payload hash: `75f3d8a6da68207c63c38b9cc35d1595d574082086dd7d87d990e7c827e5d2c0`
- validation: `valid=true`, `comparable=true`
- persisted counts: endpoint metrics 8, query evidence 8

## 검증

다음 검증을 JDK 21과 테스트용 `JWT_SECRET` 환경 변수로 실행했습니다.

```powershell
.\gradlew.bat test --tests 'com.sweet.market.discovery.M30PerformanceFixtureInitializerTest' --tests 'com.sweet.market.operations.performance.*'
# BUILD SUCCESSFUL (55s)

.\gradlew.bat test
# BUILD SUCCESSFUL (5m25s)

node --test performance/normalize-m30-measurement.test.mjs
# 8 passed, 0 failed
```

추가로 PowerShell parser, collector의 DB destructive command 정적 검사, 결과 JSON parse, `git diff --check`, placeholder/secret/absolute-path 검사를 수행했습니다. Task 11 변경만 선별 stage하고 `perf: record catalog read evidence` 메시지로 커밋합니다.

## Self-review

- 기존 fixture가 있는 DB에서 중간 적재 없이 중단되는지 검증했습니다.
- cache OFF/ON은 같은 DB와 같은 fixture를 사용했고 ON 전환 시 reset이나 fixture 재실행을 하지 않았습니다.
- 실측 당시 worktree가 dirty였다는 사실을 metadata와 보고서에서 숨기지 않았습니다.
- endpoint별 JDBC 수치의 한계와 ON의 일시적인 connection failure를 명시했습니다.
- 사용자 token, JWT secret, 실제 visitor ID, 로컬 절대 경로는 결과 artifact에 포함하지 않았습니다.
- Task 2/3/4의 기존 미커밋 보고서는 다른 작업 소유이므로 수정·stage하지 않습니다.

## 2026-07-17 증적 보정

### 보정 사유와 성공 기준

최초 결과는 k6 부하 프로세스와 별도의 후속 TRACE 세션에서 쿼리 계획을 수집해, 각 계획이 해당 cache 모드의 동일 서버 프로세스에서 나왔다는 provenance를 증명하지 못했습니다. 따라서 최초 측정 ID `3a59bcfe-afdd-4d3e-8e1c-64e8bf4adc08`은 역사 기록으로만 남기고, 현재 `performance/results/m30-v1`은 아래 새 UUID의 보정 재실행으로 교체했습니다. Git 이력은 이전 증적을 그대로 보존합니다.

보정 기준은 다음과 같습니다.

1. catalog/discovery 조회와 fixture가 같은 이름 있는 `Clock`을 사용한다.
2. fixture initializer를 끈 상태에서 기존 DB를 재사용한다.
3. OFF와 ON 각각 `boot → k6 → 네 HTTP shape/plan capture → stop` 순서를 지킨다.
4. 각 plan에 서버가 보고한 profile, cache mode, fixed clock, health, PID, capture timestamp/sequence를 남긴다.
5. endpoint 원시 duration/failure sample을 비식별 형태로 보존하고 normalizer가 이를 다시 집계한다.
6. 새 payload를 실제 등록하고 정규화 요청 해시와 서버 canonical hash를 구분해 기록한다.

### TDD와 구현 보정

- RED/GREEN으로 `DiscoveryClockConfigurationTest`, `DiscoveryFixedClockQueryTest`를 추가해 catalog/discovery의 모든 측정 쿼리가 고정 `now`를 bind하는지 검증했습니다. PostgreSQL 드라이버가 `Instant` SQL type을 추론하지 못하는 실제 실패를 재현해 `OffsetDateTime` bind로 수정했습니다.
- RED/GREEN으로 `M30ExperimentInfoContributorTest`를 추가해 서버가 active profiles, fixed clock, cache mode, process ID를 `/actuator/info`로 자체 보고하도록 했습니다.
- collector/normalizer Node 테스트를 확장해 OFF/ON route sample 필수화, sample 기반 p50/p95/RPS/error 재계산, 모드별 provenance 검증을 추가했습니다.
- PowerShell `Math.Round`와 JavaScript 반올림 차이로 ON catalog p50 `3.7325`가 달라지는 문제를 회귀 테스트로 먼저 고정하고 midpoint-to-even으로 맞췄습니다.
- 별도 capture 스크립트는 권한 있는 actuator 확인, 네 실제 HTTP shape 호출, PostgreSQL `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` 순서를 강제합니다. PowerShell 날짜 자동 변환이 fixed instant를 변형하는 문제도 raw UTF-8 응답과 `DateKind String` parsing으로 보정했습니다.

### 보정 재실행 provenance

- fixture version/seed/instant: `m30-v1` / `310031` / `2026-07-17T00:00:00Z`
- measurement commit: `24719ef70dd59b0e1dadac53cb9a49aea5046985` (`dirty=true`)
- 새 measurement ID: `bbd48853-c163-4b38-9ac1-0bc16d499905`
- DB 재설정/fixture 재실행: 없음. 기존 261 members, 10 active business stores, 10,000 products/inventories, 20 promotions/coupons, 50,000 wishlist items, 200,000 view events를 그대로 사용했습니다.
- OFF: profiles `local,performance-fixture,local-experiment,cache-off`, PID `58992`, plan capture `2026-07-17T13:05:11.2818154Z`, sequence 1.
- ON: profiles `local,performance-fixture,local-experiment`, PID `48100`, plan capture `2026-07-17T13:13:34.7576174Z`, sequence 2.
- 다른 작업의 서버와 섞이지 않도록 두 모드 모두 전용 local port `18080`을 사용했습니다.
- 두 capture 모두 server health `UP`, fixed clock 일치, 해당 k6 이후이자 해당 PID stop 이전임을 증적에 기록했습니다.

### 보정 실측 결과

세 threshold `catalog_read_errors<1%`, `http_req_failed<1%`, `http_req_duration p(95)<1000ms`는 모두 통과했습니다. 최종 재실행에서는 HTTP failure가 없었으며, 그 0도 가정한 값이 아니라 보존된 route failure sample에서 재계산한 값입니다.

| Mode | Requests | RPS | Aggregate p50 | Aggregate p95 | Error rate | JDBC delta |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OFF | 61,719 | 170.901 | 198.419 ms | 362.666 ms | 0% | 100,340 |
| ON | 70,405 | 194.988 | 5.530 ms | 94.439 ms | 0% | 94,543 |

| Endpoint | OFF p50 / p95 | ON p50 / p95 | OFF / ON RPS |
| --- | ---: | ---: | ---: |
| catalog | 162.830 / 313.342 ms | 3.939 / 24.932 ms | 57.490 / 65.837 |
| events | 218.951 / 368.748 ms | 1.516 / 16.499 ms | 57.490 / 65.837 |
| popularity | 246.182 / 397.984 ms | 83.922 / 108.102 ms | 57.490 / 65.837 |
| detail | 156.013 / 304.797 ms | 10.468 / 23.695 ms | 24.173 / 28.123 |

ON cache counters는 hit 20,527, miss 12, eviction 11입니다. Sanitized `route-samples-off.json`과 `route-samples-on.json`에는 endpoint별 duration과 failure flag만 있으며 URL, token, visitor 값은 없습니다.

### 동일 프로세스 query evidence

| Shape | OFF execution | ON execution | Rows | Buffers hit/read |
| --- | ---: | ---: | ---: | ---: |
| global catalog | 0.426 ms | 0.428 ms | 21 | 179 / 0 |
| fixed-store catalog | 0.473 ms | 0.471 ms | 21 | 189 / 0 |
| active events | 68.890 ms | 67.117 ms | 40 | 107,767 / 0 |
| popularity | 96.506 ms | 92.440 ms | 8 | 85,817 / 0 |

모든 plan은 `now=2026-07-17T00:00:00Z`를 bind하고 popularity는 `since=2026-07-10T00:00:00Z`도 bind합니다. Exact SQL, full JSON plan, capture provenance는 `query-evidence.json`에 보존하고 계약 필드만 `measurement.json`에 정규화했습니다.

### 새 등록 결과

- HTTP status / run ID: `201 Created` / `3`
- measurement ID: `bbd48853-c163-4b38-9ac1-0bc16d499905`
- validation: `valid=true`, `comparable=true`
- persisted: endpoint metrics 8, query evidence 8
- request file SHA-256: `0d29d09519d111d0f1cee1221ad2c401f762db8dca9a5f18b57a9490e43e4d63`
- server canonical payload SHA-256: `293922cf37a6eaeb8a47fadff10c10c6dbb012ba636b6b9faeb98535da9e00aa`

두 hash는 계산 대상이 다르므로 일치할 필요가 없습니다. 토큰을 제외한 등록 결과만 `registration-response.json`에 저장했습니다.

### 최종 검증

- JDK 21 집중 테스트: 고정 Clock/query bind, experiment info, fixture initializer, performance contract 범위 `BUILD SUCCESSFUL` (53초)
- JDK 21 백엔드 전체 `gradlew test`: `BUILD SUCCESSFUL` (5분 9초)
- Node normalizer/collector/capture 회귀 테스트: 15개 통과
- collector/capture PowerShell parser 통과
- 모든 JSON parse, route sample 비식별 필드 검사, 등록 payload 내부 증적 필드 누출 검사, secret/absolute-path/placeholder scan 통과
- `git diff --check` 통과 후 Task 11 파일만 선별 stage
