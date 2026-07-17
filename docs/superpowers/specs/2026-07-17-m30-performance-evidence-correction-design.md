# M30 Performance Evidence Correction Design

## Goal

Milestone 30 cache OFF/ON 성능 증거를 하나의 고정 시각, 동일 fixture, 실제 mode별 서버 프로필에서 다시 수집한다. endpoint 원시 근거와 SQL plan 출처, 등록 응답을 저장해 결과를 독립적으로 재계산하고 출처를 확인할 수 있게 한다.

## Evidence replacement policy

현재 `performance/results/m30-v1` 파일은 Git 이력에 보존하되 작업 트리에서는 새 측정 UUID의 결과로 교체한다. 이전 파일을 새 mode나 새 시각으로 재라벨링하지 않는다. Task 10 계약이 요구하는 `artifactDirectory`는 계속 정확히 `performance/results/m30-v1`이다.

## Fixed clock architecture

`discoveryClock`이라는 named `Clock` bean을 사용한다. 일반 profile은 `Clock.systemUTC()`를 제공하고 `performance-fixture` profile은 fixture와 동일한 `2026-07-17T00:00:00Z` fixed clock을 제공한다. `DiscoveryQueryService`, `DiscoveryRepository`, `CatalogSearchRepository`는 같은 bean을 주입받는다.

모든 discovery/catalog campaign 활성 구간 조건은 DB의 `CURRENT_TIMESTAMP` 대신 `:now` bind를 사용한다. 인기 상품의 7일 시작 시각도 같은 clock의 `now - 7 days`에서 계산한다. 한 repository 호출 안에서는 `Instant.now(clock)`를 한 번만 평가해 모든 관련 predicate에 같은 값을 전달한다.

기존 fixture를 바꾸지 않고 재측정하기 위해 initializer 실행 여부를 property로 제어한다. `performance-fixture` profile은 계속 fixed clock을 제공하지만 재측정 boot에서는 initializer만 비활성화한다. 기본값은 initializer 실행이며, 비어 있지 않은 DB에서 중단되는 기존 안전 동작은 유지한다.

## Endpoint evidence

Collector는 k6 raw JSON을 읽어 URL, token, visitor 정보가 없는 endpoint별 artifact를 만든다. 각 endpoint는 measured scenario의 `durationsMillis`와 `failureFlags`만 포함한다. raw JSON은 sanitized artifact를 쓴 뒤 삭제한다.

Normalizer는 OFF/ON sample 파일을 필수 입력으로 받고 다음을 다시 계산한다.

- sample count와 failure flag count 일치
- p50/p95 interpolation과 collector의 3자리 반올림
- measured seconds 기준 throughput
- 실패 합계 기준 error rate

재계산 값이 `metrics-off.json` 또는 `metrics-on.json`과 다르면 measurement 생성을 거부한다. Sample provenance는 등록 요청 계약에 넣지 않는다.

## Mode-specific SQL evidence

실험 순서는 다음과 같이 고정한다.

1. OFF 서버를 `local,performance-fixture,local-experiment,cache-off`로 시작한다. Initializer만 property로 비활성화한다.
2. OFF k6를 실행한다.
3. 같은 OFF 프로세스가 실행 중일 때 네 HTTP query shape를 호출하고 실제 bind SQL을 확인한 다음, 네 `EXPLAIN (ANALYZE, BUFFERS)`를 수집한다.
4. OFF 서버를 중지한다.
5. ON 서버를 `local,performance-fixture,local-experiment`로 시작한다. 같은 DB와 fixture를 사용한다.
6. ON k6를 실행한다.
7. 같은 ON 프로세스가 실행 중일 때 네 plan을 별도로 수집한다.
8. ON 서버를 중지한다.

`query-evidence.json`의 각 mode는 `capturedAt`, `provenance`, `evidence`를 가진다. Provenance에는 cache mode, 실제 active profile 목록, fixed clock, process ID, capture sequence가 포함된다. Normalizer는 OFF에 `cache-off`가 있고 ON에는 없으며 두 mode 모두 `performance-fixture`와 동일 fixed clock을 사용함을 검증한다. Task 10 request record가 provenance 필드를 받지 않으므로 검증 후 `evidence`의 계약 필드만 measurement에 쓴다.

## Registration evidence

`registration-response.json`은 다음 sanitized 정보를 저장한다.

- 등록 시각과 HTTP status
- repository-relative request artifact 경로
- measurement JSON 파일 byte의 SHA-256
- server가 반환한 run ID, measurement ID, valid/comparable
- server canonical payload hash

파일 byte hash와 server canonical hash는 서로 다른 의미이므로 별도 필드와 별도 보고서 행으로 기록한다. Authorization header와 JWT는 저장하지 않는다.

## Testing

JUnit 테스트는 fixed profile이 fixture instant를 제공하는지, catalog promotion과 discovery campaign/event/popularity query가 wall clock 대신 bound fixed time을 사용하는지 검증한다. Test method는 한국어 underscore 이름을 사용한다.

Node 테스트는 provenance 누락/잘못된 mode profile, fixed clock 불일치, route sample 변조를 각각 거부하는지 먼저 실패로 확인한 뒤 구현한다. 실제 수집 뒤 normalizer를 다시 실행해 committed sample에서 metrics가 재계산되는지 확인한다.

전체 backend 테스트, focused discovery/performance 테스트, Node 테스트, PowerShell parser, JSON parse, secret/absolute-path/미완성 표식 검사를 통과한 뒤 correction commit을 만든다.

## Reporting corrections

M30 보고서와 Task 11 보고서에는 새 UUID 결과를 append하고 기존 측정이 provenance/time-stability 부족으로 대체됐음을 명시한다. JDK는 `JDK 21`로만 표기한다. Visitor hash는 deterministic synthetic pseudonym으로 표현하고 비가역적이라고 주장하지 않는다. k6 threshold 이름은 실제 `http_req_duration`으로 기록한다.
