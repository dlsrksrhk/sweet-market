# Milestone 32 external integration foundation verification

## Result

M32 passes the three fresh forced application suites, complete web regression and production build, public-contract checks, PowerShell static and boundary suites, and the complete live Docker Compose verifier. The live verifier exercised four signed positive probes and replay, mutation, expiry, and raw-body-limit negatives over ports `8080`-`8082`; restarted both simulators; re-proved replay persistence and database isolation; scanned Compose logs for configured credentials; and removed the topology in `finally`.

The post-review verification was executed against the complete change set containing this report, based on merge base `1a6ea43daf8b74337eadcf626991a9b9b447a3c8`. Test and runtime evidence was generated on 2026-07-19 in the `Asia/Seoul` time zone. Durations below are local verification timings, not production performance claims.

## Environment and application versions

| Component | Verified version |
| --- | --- |
| Operating system | Windows 11 `10.0` amd64 |
| Java | OpenJDK `21`, runtime build `21+35-2513` |
| Gradle wrapper | `8.14.5` |
| Spring Boot plugin, all three applications | `3.5.15-SNAPSHOT` |
| Sweet Market artifact | group `com.sweet`, version `0.0.1-SNAPSHOT` |
| Mock Payment Gateway artifact | Gradle default group, version `unspecified` |
| Mock Delivery Provider artifact | group `com.sweet`, version `0.0.1-SNAPSHOT` |
| Node.js / npm | `v22.14.0` / `10.9.2` |
| React / TypeScript | `19.2.7` / `5.9.3` |
| Vitest / Vite | `3.2.7` / `6.4.3` |
| PowerShell | `7.6.3` |
| Docker client / server | `28.0.4` / `28.0.4` |
| Docker Compose | `v2.34.0-desktop.1` |
| PostgreSQL / Redis images | `postgres:17-alpine` / `redis:7-alpine` |

## Fresh forced application suites

JDK 21 was active for every command and the backend process received a valid configured `JWT_SECRET`. No credential value was printed or recorded.

```powershell
.\backend\gradlew.bat -p mock-payment-gateway clean test --no-daemon
.\backend\gradlew.bat -p mock-delivery-provider clean test --no-daemon
.\backend\gradlew.bat -p backend clean test --no-daemon
```

Every `TEST-*.xml` produced by the exact live verifier's fresh `clean test` runs was parsed. Duration is the sum of the XML `testsuite.time` values; build wall time is the corresponding observed live-step time.

| Build | XML suites | Tests | Failures | Errors | Skips | XML duration | Build wall time |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Mock Payment Gateway | 7 | 31 | 0 | 0 | 0 | 1.948 s | 34.5 s |
| Mock Delivery Provider | 7 | 24 | 0 | 0 | 0 | 1.344 s | 31.6 s |
| Sweet Market backend | 134 | 892 | 0 | 0 | 0 | 209.990 s | 348.0 s |

These are the fresh application suites the full live verifier completed before starting Compose.

## Web and protocol verification

Run from the repository root with a populated, ignored `.env.integration` and the same JDK/JWT prerequisites:

```powershell
Push-Location web
npm install
npm test
npm run build
Pop-Location
node --test contracts/contracts.test.mjs
pwsh -NoProfile -File scripts/verify-m32-scripts.test.ps1
pwsh -NoProfile -File scripts/verify-m32-integration.ps1 -EnvFile .env.integration
```

| Check | Exact result |
| --- | --- |
| Dependency installation | 162 packages audited; 0 vulnerabilities; dependencies already up to date |
| Web tests | 13 files, 62/62 tests passed; Vitest duration 32.95 s |
| Production web build | TypeScript checks passed; Vite transformed 166 modules and built in 1.99 s |
| Node contracts | 2/2 tests passed; 0 failures, skips, or cancellations; 74.3272 ms |
| PowerShell script suite | 216 checks passed |
| Complete live verifier | 19/19 ordered steps passed; command wall time 528.7 s |

The production build retains one non-failing Vite advisory: the minified JavaScript chunk is 595.71 kB, above the default 500 kB warning threshold; gzip size is 166.06 kB.

## Versioned contracts and public vector

All four OpenAPI documents declare OpenAPI `3.1.0` and `info.version` `v1`.

| Artifact | Versioned path | SHA-256 |
| --- | --- | --- |
| `contracts/payment-gateway-v1.openapi.json` | `POST /api/v1/probes` | `ad7147aec8f460164941a503ed0cdac6a49e9eafea329c27d13323ed587f2ae9` |
| `contracts/payment-gateway-webhooks-v1.openapi.json` | `POST /api/integrations/payment-gateway/v1/probes` | `d446112d2eefcd764c33cceea2814fdc7e484d731c68addcbba55d1eaddd4a00` |
| `contracts/delivery-provider-v1.openapi.json` | `POST /api/v1/probes` | `16ca10071b7f3f18a75c028ff6cb881c4e6593f9b2a5bbc3e21166797bb403ce` |
| `contracts/delivery-provider-webhooks-v1.openapi.json` | `POST /api/integrations/delivery-provider/v1/probes` | `f6ef7e18871e7fa66e52081c1d50a34fb1c57ce759f428b81ef62ee60d560803` |
| `contracts/hmac-v1-test-vectors.json` | Public, non-production HMAC v1 vector | `ebc2271817a5ae8985f2cda9306083756bc6fae827d3698d0d78c095aff58aee` |

The contract tests independently recomputed the raw-body SHA-256, seven-line canonical payload, and lowercase HMAC-SHA256 result. The public vector is deliberately non-production test material; no environment credential is copied into it or this report.

## Live signed boundary evidence

The exact command was:

```powershell
pwsh -NoProfile -File scripts/verify-m32-integration.ps1 -EnvFile .env.integration
```

The verifier buffers build and runtime output, prints sanitized step names and timings only, and exits nonzero on any assertion or cleanup failure.

| Live step | Verified observation | Time |
| --- | --- | ---: |
| Node contract tests | Passed before application startup | 0.2 s |
| PowerShell parser/static tests | All executable-flow/static checks passed | 2.6 s |
| Boundary audit | Java import and database-name boundaries passed | 10.4 s |
| Payment Gateway complete tests | Clean standalone build passed | 34.5 s |
| Delivery Provider complete tests | Clean standalone build passed | 31.6 s |
| Sweet Market complete backend tests | Clean complete regression passed | 348.0 s |
| Compose config and build | Render and three application images passed | 57.2 s |
| Compose up and health | Three applications reached health before the bounded deadline | 21.4 s |
| Signed Payment Gateway probe | HTTP 200; exact `mock-payment-gateway` service, message, request ID, and correlation ID echo | 0.2 s |
| Signed Delivery Provider probe | HTTP 200; exact `mock-delivery-provider` service, message, request ID, and correlation ID echo | 0.1 s |
| Signed payment webhook probe | HTTP 204 on the payment webhook v1 path | 0.1 s |
| Signed delivery webhook probe | HTTP 204 on the delivery webhook v1 path | 0.0 s |
| Replay negative | Reused Payment Gateway request ID returned HTTP 409 | 0.0 s |
| Mutated-body negative | Body changed after signing returned HTTP 401 | 0.0 s |
| Expired-timestamp negative | Delivery request 301 seconds old returned HTTP 401 | 0.0 s |
| Chunked oversize negative | 1,048,577-byte chunked Sweet Market webhook returned HTTP 413 | 0.0 s |
| Simulator restart replay persistence | Both simulators restarted healthy and continued returning HTTP 409 for their pre-restart request IDs | 16.3 s |
| Database isolation audit | All ownership and foreign-table absence assertions passed | 0.7 s |
| Compose logs secret scan | No configured API key, HMAC credential, or database password value appeared | 0.3 s |

An independent post-run `docker compose --env-file .env.integration -f docker-compose.integration.yml ps --all --quiet` query returned zero containers. `.env.integration` remained ignored and untracked.

## Runtime topology and persistence

| Service | Host port | Owned store | Host database port | Image/runtime |
| --- | ---: | --- | ---: | --- |
| `market` | 8080 | `market-postgres` / `market` | 15432 | independent Sweet Market image |
| `mock-payment-gateway` | 8081 | `payment-postgres` / `payment_gateway` | 15433 | independent Gateway image |
| `mock-delivery-provider` | 8082 | `delivery-postgres` / `delivery_provider` | 15434 | independent Provider image |
| `redis` | not published | internal Redis store | not applicable | `redis:7-alpine` |

Each PostgreSQL service uses `postgres:17-alpine`, its own database/user/password inputs, Flyway history, and named volume. The live audit queries each owning database with `current_database()` and `to_regclass`. Payment and delivery must own `integration_request_replays` and must not contain `external_integration_request_replays`, `members`, `products`, `orders`, or `stores`; Market must own `external_integration_request_replays` and must not contain simulator table `integration_request_replays`. The restart proof demonstrates that both provider replay ledgers survive application-container restart; it is not a claim about payment or shipment business-state persistence, because M32 has no such behavior.

## Correlation and trace-context evidence

The two signed provider probes generated independent UUID request and correlation IDs. Each HTTP 200 response had to echo the exact request ID and correlation ID before its step could pass. The webhook probes used separate correlation IDs and passed their signed HTTP 204 boundaries.

All three fresh application suites include a six-test `CorrelationIdFilterTest`; all 18 tests passed with zero failures, errors, or skips. They prove valid correlation IDs reach the response and MDC, missing/invalid values become UUIDs for general request context, MDC is cleared after completion, a valid lowercase W3C version-00 `traceparent` is preserved unchanged as a request attribute, and invalid trace context is not preserved. Signed filters still validate the original correlation header independently, so early context generation does not weaken HMAC input validation. M32 preserves trace context for later clients; it does not yet export distributed traces.

## Security, privacy, and repository hygiene

```powershell
pwsh -NoProfile -File scripts/verify-m32-boundaries.ps1
git diff --check
git status --short
```

- The independent boundary command passed after the live run. It lexically masks Java comments, strings, chars, and text blocks; handles CR, LF, and CRLF logical lines; rejects cross-application Java imports; and verifies three distinct configured database names.
- The Delivery Provider configures Jackson to reject unknown request properties. A signed regression over the raw body containing valid `message` plus unknown `extra` receives HTTP 400 with the exact three-field `IntegrationError` response and authenticated request ID.
- The live verifier's in-memory log scan found no configured secret or password value and persisted no raw runtime log.
- A value-aware scan compared 16 non-empty `.env.integration` secret, password, and API-key values with 933 tracked text artifacts without printing those values; it found zero matches.
- A structural scan found no credential assignments in committed runtime configuration, no authorization-header values, no private absolute paths, no unfinished placeholder markers, and no committed raw Gradle, npm, Docker, HTTP, or application log artifact.
- `git diff --check` passed. Before documentation edits, `git status --short` was empty.

## Known limitations

1. M32 exposes signed probes only. It implements no payment, cancellation, refund, shipment, or return business behavior.
2. The webhook-shaped endpoints are authentication and replay probes, not a durable business webhook sender or callback-processing engine. M33 must introduce a durable Payment Gateway webhook outbox.
3. Kafka is not installed or implied. The existing database-outbox boundary remains authoritative until M39 produces evidence for a transport decision.
4. Correlation and `traceparent` propagation exist, but no OpenTelemetry Collector, trace store, metrics backend, dashboard, or central log backend exists yet; those remain M38 scope.
5. The Vite 595.71 kB chunk-size advisory is non-failing and remains visible for later web optimization.
6. Non-blocking review follow-up: simulator integration-security properties can still be enlarged through Spring environment overrides even though the M32 contract fixes limits such as 300-second skew, 1,048,576-byte body size, ten-minute replay retention, and 1,000-row cleanup batches. Current Compose values and application defaults comply; a later hardening change should align exact simulator property validation with the backend without expanding M33 scope.
