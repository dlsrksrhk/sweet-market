# Milestone 31 promotion and performance operations dashboard handoff

## Completion boundary

M31 completes the M21-M31 roadmap implementation boundary. The final verification record is `docs/superpowers/reports/2026-07-17-milestone-31-operations-dashboard-verification.md`.

Delivered backend boundaries:

- V15 operational outbox, projection generation/receipt, hourly store/campaign metrics, inventory pressure/failure, campaign audit, and performance-measurement schema with verified indexes.
- Transactional campaign, coupon, purchase, order-status, and inventory operational events; normalized failure outcomes use the focused post-rollback recorder.
- At-least-once database-outbox projection with handler receipts, bounded batches, exponential retry, DEAD isolation, payload-preserving retry, retention, cold-start bootstrap, and atomic generation rebuild/cutover.
- Store operations overview and five paged drill-down APIs with KST periods, ACTIVE-generation selection, store authorization, SQL filters, deterministic pages, bounded sizes, freshness, lag, and `trackingStartedAt`.
- Administrator cross-store overview and four drill-down APIs, projection health/recovery, and measurement registration/list/detail APIs.
- Validated performance snapshot registration with idempotent measurement UUID/hash handling and strict cache OFF/ON comparability.
- Fixed KST end-to-end reconciliation through event factories, outbox, projector, read models, and scoped query service.

Delivered web boundaries:

- `/me/store/dashboard` for OWNER and MANAGER with canonical URL-backed store/period/tab/filter/page state, overview and independent drill-down states, provenance, and links to existing operational pages.
- Campaign mutation links only for an eligible OWNER of the current active business store; platform and foreign-store rows remain inspect-only.
- `/admin/dashboard` with cross-store evidence, platform coupon links, outcome/inventory/audit investigation, measurement comparison, DEAD retry, and projection rebuild.
- Explicit measured-zero, pre-tracking, unavailable, delayed, empty, loading, and failed states.
- Auth/account cache cleanup, native table accessibility, and responsive table/card CSS contracts.

## Registered measurement

M31 consumes the real live-provenance M30 run rather than a fabricated snapshot:

- UUID `385b4525-21a2-4f4a-875f-364449f59957`
- HTTP `201`, run ID `4`, `valid=true`, `comparable=true`
- 8 endpoint metrics and 8 full-plan summaries
- replayed request SHA-256 matches registration
- cache OFF/ON collector-before, collector-after, and plan-capture identity is consistent

The full evidence lives at `performance/results/m30-v1`. Preserve the artifacts and their historical provenance; do not relabel an old run as a new measurement.

## Preserved invariants

- Dashboard projections never authorize price, claim, purchase, inventory, campaign, settlement, or refund commands.
- M29 durable idempotency, conditional inventory reservation, deterministic cart locking, and exactly-once coupon/stock compensation remain authoritative.
- Orders retain immutable list, promotion, coupon, and final-price snapshots. Projection handlers never recalculate historical prices.
- OWNER and MANAGER share scoped read evidence, but MANAGER gains no commercial-policy mutation authority.
- ADMIN manages platform-owned coupons without gaining store-operator authority or store-campaign mutation routes.
- `commerceStoreId` identifies the ordered store while campaign owner dimensions remain separate; platform coupon value appears in the commerce store without becoming store-owned.
- Applied, realized, canceled, and refunded discounts remain separate. Reversals are not netted away.
- Failures and audits before `trackingStartedAt` are not inferred. A zero before tracking is not presented as a measured absence.
- Replayed delivery cannot double count because generation/projection/event receipts remain the deduplication authority.
- Rebuild writes a new generation and atomically activates it; readers never consume a partial generation.
- No Kafka placeholder, daily rollup, BI warehouse, generic metric log, or autonomous campaign mutation was introduced.

## Verification snapshot

- M31 focused backend: 103/103 passed.
- Complete backend: 844/844 passed.
- Complete web: 52/52 passed.
- Production web build: passed.
- Node normalizer/trace parser: 24/24 passed.
- PowerShell parser, evidence replay/audit, and `git diff --check`: passed.
- Rendered browser QA: not run because no browser instance was available; see the verification report limitation.

## Operator notes

- Projection values are eventually consistent; use `projectionUpdatedAt`, lag, and queue health before interpreting a recent zero.
- Retry only DEAD events through the supplied command; payload editing is intentionally unavailable.
- Rebuild is an administrator recovery operation, not a routine refresh button. Concurrent rebuild conflicts are explicit.
- Performance improvement language is valid only for runs marked both `valid` and `comparable`, and only within their recorded fixture/environment/hardware conditions.
- Continue to use existing promotion, coupon, order, inventory, refund, settlement, and report pages for mutations and detailed source-of-truth operations.
