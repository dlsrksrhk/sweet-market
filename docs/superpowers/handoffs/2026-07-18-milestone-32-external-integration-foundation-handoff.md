# Milestone 32 external integration foundation handoff

## Completion boundary

M32 completes the external-integration foundation only. The reproducible evidence is recorded in `docs/superpowers/reports/2026-07-18-milestone-32-external-integration-foundation-verification.md`.

Delivered boundaries:

- Three independently built and bootable Spring Boot applications on ports `8080`, `8081`, and `8082`.
- Three service-owned PostgreSQL databases and Flyway histories on host ports `15432`, `15433`, and `15434`, plus the existing internal Redis dependency for Sweet Market.
- Four OpenAPI 3.1.0 `v1` signed probe contracts and one public HMAC v1 test vector; no shared Java DTO, entity, repository, service, or security implementation module.
- Independent API-key/HMAC verification, overlapping key rotation, plus-or-minus 300-second timestamp checking, UUID replay claims, ten-minute replay retention, bounded cleanup, constant-time signature comparison, a 1,048,576-byte pre-Jackson body limit, and Delivery Provider rejection of contract-unknown JSON properties.
- Signed Sweet Market payment/delivery webhook-shaped probe intake and an outbound signer boundary for later integrations.
- Correlation ID response/MDC propagation and strict lowercase W3C version-00 `traceparent` preservation in all three applications.
- Seven-service Docker Compose topology, independent application images, health-gated startup, environment-only credentials, persistence checks, database-isolation audits, and guaranteed verifier teardown.

M32 does not replace the current Sweet Market payment or delivery ports with production HTTP adapters. Existing in-process fakes remain available for focused tests until their approved integration milestones.

## Verification snapshot

- Fresh forced application suites: Gateway 31/31 tests across 7 suites, Delivery 24/24 across 7 suites, and Sweet Market 892/892 across 134 suites; zero failures, errors, or skips.
- Web: 13 files and 62/62 tests passed; production build transformed 166 modules. The non-failing 595.71 kB Vite chunk advisory remains.
- Contracts: 2/2 Node tests passed. PowerShell static verification passed 216 checks, and the independent Java/database boundary audit passed.
- Complete live verifier: 19/19 ordered steps passed in 528.7 seconds. Positive results were Gateway 200, Delivery 200, payment webhook 204, and delivery webhook 204. Negative results were replay 409, post-signature mutation 401, 301-second expiry 401, and 1,048,577-byte chunked input 413.
- Both simulator restarts retained their replay claims and returned 409 for pre-restart request IDs. The live catalog audit proved that Payment and Delivery own `integration_request_replays` while excluding `external_integration_request_replays`, `members`, `products`, `orders`, and `stores`; Market owns `external_integration_request_replays` while excluding `integration_request_replays`.
- Compose logs and committed M32 artifacts contained no configured credential value, authorization value, private absolute path, unfinished placeholder marker, or raw runtime log artifact.
- Independent post-run topology count was zero. `.env.integration` remained ignored and untracked.

## M33 starting boundary

M33 may add Payment Gateway domain behavior only behind the signed `/api/v1` boundary. It must extend the payment OpenAPI contract before implementation. It must reuse the provider-owned replay/security behavior without importing Sweet Market code. It must add durable payment webhook outbox semantics rather than treating the M32 probe as a business callback engine.

M33 must also preserve the roadmap boundary that the Payment Gateway remains usable and testable without a running Sweet Market instance. M33 is not complete, and this handoff does not change its approved confirmation, cancellation, refund, idempotency, asynchronous processing, fault-control, webhook-outbox, status-query, or operator-screen scope.

## Preserved constraints for M33

- Keep `mock-payment-gateway` a standalone Java 21 build with its own database, migrations, configuration namespace, process, health surface, and tests.
- Extend `contracts/payment-gateway-v1.openapi.json` and the payment webhook contract before adding endpoints or event shapes. Consumer/provider tests must continue to consume contract artifacts rather than a shared runtime model.
- Keep the current signed-envelope rules unchanged unless a separately approved contract version changes them: exact raw target/body, lowercase SHA-256/HMAC hex, seven canonical lines, current/next key IDs, bounded skew, verify-before-replay-claim ordering, hard raw-body limit, and source-owned replay persistence.
- Do not import `com.sweet.market.*` domain or persistence code into the Gateway. Sweet Market must likewise not import Gateway packages.
- Add merchant-scoped payment resources and operation history in Gateway-owned tables. Never access the Sweet Market database or treat an external state as authorization for a Sweet Market command.
- Use stable idempotency keys and request hashes. A lost HTTP response does not prove failure; final state must remain queryable.
- Implement durable callback delivery with stable event IDs, bounded retry/backoff, DEAD isolation, and payload-preserving resend. The probe controller and probe webhook intake are not reusable business queues.
- Keep credentials environment-driven and absent from source, logs, traces, responses, and persisted payloads. Do not store real card, identity, acquirer, or carrier data.

## Reproduction commands

With JDK 21 active, a valid backend `JWT_SECRET` configured, and a populated ignored `.env.integration`:

```powershell
.\backend\gradlew.bat -p mock-payment-gateway cleanTest test --no-daemon
.\backend\gradlew.bat -p mock-delivery-provider cleanTest test --no-daemon
.\backend\gradlew.bat -p backend cleanTest test --no-daemon
Push-Location web
npm install
npm test
npm run build
Pop-Location
node --test contracts/contracts.test.mjs
pwsh -NoProfile -File scripts/verify-m32-scripts.test.ps1
pwsh -NoProfile -File scripts/verify-m32-boundaries.ps1
pwsh -NoProfile -File scripts/verify-m32-integration.ps1 -EnvFile .env.integration
git diff --check
git status --short
```

The live verifier is the topology owner. It must finish through its `finally` cleanup; after any interrupted run, verify `docker compose --env-file .env.integration -f docker-compose.integration.yml ps --all --quiet` returns no container IDs before retrying.

## Remaining M32 limitations

- Probes only; no payment or shipment behavior.
- No durable business webhook sender or business callback processing.
- No Kafka.
- No observability backend; correlation and trace-context preservation are only the propagation foundation.
- Non-blocking review follow-up: Spring environment overrides can enlarge simulator integration-security limits beyond the fixed M32 contract. Current Compose values and defaults comply; follow-up should align exact simulator validation with the backend without changing or completing M33 scope.
