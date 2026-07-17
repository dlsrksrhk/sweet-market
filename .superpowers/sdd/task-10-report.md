# Task 10 Report: Validated performance measurement persistence

## Status

Complete. The backend now accepts, validates, persists, lists, and retrieves immutable cache OFF/ON performance measurement snapshots through ADMIN-only APIs.

## Delivered

- Added `POST /api/admin/performance-measurements` with strict unknown-field rejection and actor capture from the authenticated ADMIN principal.
- Added paged `GET /api/admin/performance-measurements` summaries and `GET /api/admin/performance-measurements/{runId}` details.
- Added exact allowlists for endpoint metrics (`catalog`, `events`, `popularity`, `detail`) and query evidence (`GLOBAL_CATALOG`, `FIXED_STORE_CATALOG`, `ACTIVE_EVENTS`, `POPULARITY`) for both OFF and ON modes.
- Validated cache mode consistency, metadata comparability, timestamps, positive durations, percentile ordering, error-rate range, nonnegative counters, DB numeric precision/scale, bounded text, and normalized repository-relative artifact paths under `performance/results/`.
- Canonicalized child ordering, serialized with the application `ObjectMapper`, enforced a 1 MiB canonical payload ceiling, and generated lowercase SHA-256 payload hashes.
- Implemented UUID/hash idempotency: identical payloads return the existing run and different payloads return `PERFORMANCE_MEASUREMENT_CONFLICT` (HTTP 409).
- Persisted the run and its eight endpoint metrics/eight query evidence rows in one transaction with insert-only repository methods.
- List responses omit bind/plan evidence text; detail responses return the stored text as inert values without filesystem access, deserialization, SQL execution, or process execution.
- Added structured not-found/conflict error codes and Korean-named integration tests.

## TDD evidence

1. RED: the initial API test suite compiled and failed 8 of 9 tests against the missing endpoints (ADMIN requests returned 404; the existing security rule correctly returned 403 for MEMBER access).
2. GREEN: implemented the minimal controller/service/repository/DTO contract and fixed explicit PostgreSQL `TIMESTAMPTZ` binding.
3. Additional RED/GREEN: reproduced and fixed null child-array NPE handling and scientific-notation values exceeding PostgreSQL numeric precision.

## Verification

- Focused: `backend\\gradlew.bat test --tests 'com.sweet.market.operations.performance.*'` — `BUILD SUCCESSFUL`.
- Full backend: `backend\\gradlew.bat test` with JDK 21 and the required JWT secret — `BUILD SUCCESSFUL` in 4m 59s.
- Full result XML: 826 tests, 0 failures, 0 errors, 0 skipped across 122 suites.
- `git diff --check` — no Task 10 whitespace errors.

## Scope and concerns

- Task 10 stores only validated structured snapshots and artifact directory/hash metadata; it does not run k6, inspect files, execute plans, or generate real evidence. The reproducible fixture, normalizer, artifact files, and real measurement run remain Task 11.
- Pre-existing modifications to `.superpowers/sdd/task-2-report.md`, `task-3-report.md`, and `task-4-report.md` were not edited or staged by this task.

## Review follow-up: integrity hardening

### RED

- Missing/null `dirtyWorktree`, `jdbcStatementCount`, `actualRows`, and shared block counts were accepted because primitive record fields silently became Java defaults.
- String/number/boolean scalar values crossed JSON types through the application mapper and could produce an otherwise valid snapshot.
- Two synchronized registrations of the same UUID reproduced the check-then-insert race: one request escaped as a database unique violation for both identical and different payload cases.
- Reordered children with numerically equivalent decimal representations produced a different payload hash and HTTP 409.
- The rollback regression initially exposed Spring repository exception translation in the test assertion; after asserting the failure message, the database evidence verified transaction behavior directly.

### GREEN

- Changed required primitive evidence inputs to nullable wrappers and added explicit requiredness validation.
- Added a registration-only `ObjectMapper` copy with unknown-field rejection, float-to-integer rejection, and explicit Jackson coercion failures between textual, boolean, integer, and decimal logical types.
- Replaced check-then-insert with atomic `INSERT ... ON CONFLICT DO NOTHING RETURNING id`; only the header winner writes children, while a loser reads the committed snapshot and returns it for an identical canonical hash or raises structured HTTP 409 for a different hash.
- Canonicalized every decimal by stripping trailing zeros and removing negative scale before deterministic child sorting and application-mapper serialization.
- Added real concurrent identical/conflicting registration tests and regressions for all eight comparability fields, absolute/backslash/dot/non-normalized paths, whole-snapshot rollback on forced child failure, multi-page newest-first ordering, size cap, and detail 404.
- Focused verification after the review fixes: `backend\\gradlew.bat test --tests 'com.sweet.market.operations.performance.*'` — 18 tests, 0 failures, `BUILD SUCCESSFUL`.
