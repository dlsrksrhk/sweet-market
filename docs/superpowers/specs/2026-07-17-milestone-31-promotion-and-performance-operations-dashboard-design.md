# Milestone 31: Promotion And Performance Operations Dashboard Design

## Purpose

M31 gives store operators and platform administrators a reliable operating surface for promotions, coupons, inventory pressure, purchase outcomes, campaign history, and measured catalog-read performance. It completes the M21-M31 roadmap without changing M29 purchase correctness or treating unmeasured M30 observations as performance evidence.

The milestone uses an event-driven aggregation model from the beginning:

```text
transactional OLTP source
  -> transactional outbox
  -> asynchronous projector
  -> dashboard read models
  -> scoped overview and drill-down APIs
```

The first transport is a database outbox poller. Event contracts, handlers, idempotency, and read models remain independent of that transport so a future Kafka relay and listener can replace only the delivery adapter.

## Confirmed Product Decisions

- M31 is one integrated completion boundary. Domain operations dashboards may be implemented before the M30 performance experiment finishes, but catalog latency, statement counts, and cache effectiveness remain explicitly unmeasured until real evidence is registered.
- Store owners and managers can read their store's operations dashboard. Only owners retain campaign creation, update, and lifecycle control.
- Operational outcome tracking starts with M31. Historical failures and audit actions are not inferred or fabricated.
- Dashboard periods use `Asia/Seoul` calendar boundaries. Presets cover today, 7 days, 30 days, and 90 days, with a custom range capped at 90 days.
- Discount reporting separates applied, realized, canceled, and refunded amounts. Promotion and coupon discounts remain distinct.
- Campaign audit covers creation, draft update, schedule, pause, resume, and end commands.
- Store and administrator dashboards are dedicated routes that link to existing management pages for action and detail.
- Each dashboard has one overview API and separate paged drill-down APIs.
- A stock-managed product is low stock when available quantity is at most five. Single-item products are excluded from the low-stock list but remain represented in reservation failures and sold-out transitions.
- Existing campaign ownership remains unchanged. Administrators operate platform coupons and inspect store promotions and store coupons without impersonating their owners. M31 does not add platform-owned automatic promotions or a generic event aggregate.
- M30 performance evidence is stored as validated measurement-run snapshots. The application never launches k6 or database analysis commands from an administrator request.

## Existing Contracts To Preserve

- M29 durable purchase idempotency, conditional inventory reservation, deterministic cart locking, and exactly-once coupon and stock compensation remain unchanged.
- Orders remain one-product orders and retain list-price, promotion, coupon, and final-price snapshots.
- Product detail and checkout continue to validate current price, availability, campaign lifecycle, and coupon eligibility against OLTP source data. Dashboard projections never authorize commerce actions.
- Active business-store owners remain the only actors allowed to create or mutate store promotions and store coupon campaigns.
- Store managers retain catalog and operational responsibilities but do not gain commercial-policy mutation rights.
- Administrators manage platform-owned coupon campaigns without gaining store-operator privileges.
- Existing seller reports, sales, settlement, refund, store catalog, promotion, coupon, and administrator pages remain compatible and are linked rather than rewritten.

## Architecture And Kafka Evolution Boundary

### Event recording

Application services record an `OperationalEventEnvelope` through an `OperationalEventRecorder`. Successful commands persist the source change and outbox row in the same database transaction.

The transport-neutral envelope contains:

- `eventId`: stable UUID used for deduplication;
- `eventType` and `schemaVersion`;
- `aggregateType`, `aggregateId`, and `aggregateVersion` where ordering matters;
- `storeId` and `campaignId` when applicable;
- `partitionKey`, chosen from the aggregate or store boundary;
- `occurredAt`; and
- a versioned JSON payload containing only the operational facts needed by projections.

The outbox is a transport buffer, not the permanent source of OLTP business truth. Processed operational events are nevertheless retained for at least 100 days so every interactive 90-day failure and audit projection can be rebuilt with a safety margin. Cleanup never removes pending, retrying, dead, or active-generation checkpoint data.

### Current delivery adapter

`DbOutboxProjector` polls up to 100 eligible rows in creation order using `FOR UPDATE SKIP LOCKED`. It invokes an `OperationalEventHandler`, updates projection state, records projection deduplication, and marks the outbox row complete in one short transaction.

The projector applies at-least-once delivery semantics:

- `(projectionName, eventId)` has a unique constraint;
- duplicate delivery succeeds without a second aggregate change;
- additive hourly metrics are order independent; and
- current-state projections apply only a newer `aggregateVersion`.

### Future Kafka adapter

Kafka introduction adds an outbox relay and Kafka listener:

```text
outbox -> relay -> Kafka -> listener -> existing OperationalEventHandler
```

The database poller is then disabled. Event handlers, deduplication, aggregation rules, read-model tables, query services, and dashboard APIs remain unchanged. Kafka topic, partition, offset, and consumer-group concepts stay out of domain and projection code. The existing `partitionKey` supports aggregate-level ordering when a Kafka transport is added.

M31 deliberately does not install Kafka, create placeholder Kafka classes, or make the local test environment depend on a broker.

## Operational Event Contracts

Events are purpose-specific rather than a generic metric-name/value log.

### Campaign command events

`CampaignCommandCompleted` records successful promotion or coupon commands:

- `CREATED`
- `UPDATED`
- `SCHEDULED`
- `PAUSED`
- `RESUMED`
- `ENDED`

The payload includes campaign kind, immutable owner scope, actor member ID, command, effective timestamp, and a bounded before/after summary of operational fields. It excludes private business registration data and unrelated entity state.

### Coupon outcome events

`CouponClaimOutcome` records success or one normalized failure reason:

- `ALREADY_CLAIMED`
- `EXHAUSTED`
- `INACTIVE`
- `INELIGIBLE`
- `UNAVAILABLE`

`CouponRedemptionOutcome` records success or normalized eligibility, expiry, combination-policy, reservation, and availability failures. Redemption succeeds only when order creation commits and the selected member coupon becomes used; a temporary quote or reservation is not counted as redemption. The event identifies the relevant campaign and commerce store without storing buyer-facing personal information.

### Purchase and inventory events

`PurchaseOutcome` records order-creation success, sold-out race loss, unavailable-product failure, and payment failure. A successful event contains the order ID, commerce store ID, applied promotion and coupon campaign IDs, and immutable discount snapshots so the projector can update each applicable campaign dimension without recalculating price. `OrderStatusChanged` records transitions that affect discount realization or reversal, including cancellation, refund, and confirmation.

`InventoryOutcome` records reservation success or failure, release, restore, and sold-out transitions. It includes product sales policy, resulting available quantity where applicable, and aggregate version.

### Failure recording

Failures occur after their source transaction rolls back, so a focused outcome recorder uses a separate short transaction. Failure recording is best effort with explicit error logging and a missing-outcome counter. A recording failure must never replace or mask the original buyer-facing error response.

## Projection Read Models

### Campaign hourly metrics

`campaign_metric_hourly` is keyed by KST hour bucket, commerce store ID, campaign kind and ID, and campaign-owner dimensions. `commerceStoreId` always identifies the store whose product was ordered, while `campaignOwnerType` and `campaignOwnerStoreId` identify platform or store campaign ownership. This lets a store dashboard include platform-coupon effects on its own orders without presenting the platform campaign as store-owned. Typed count and amount columns cover:

- coupon claim and redemption success counts;
- normalized claim and redemption failure counts;
- successful order count and purchase failure count;
- promotion discount applied, realized, canceled, and refunded amounts; and
- coupon discount applied, realized, canceled, and refunded amounts.

Applied discount uses orders created inside the selected period. Realized discount uses orders confirmed inside the period. Cancellation and refund reversals are shown separately rather than silently subtracted from either figure.

The dashboard sums at most 90 days of hourly buckets. M31 does not add a daily rollup until measurement demonstrates that hourly aggregation misses the intended interaction budget.

### Inventory pressure

`inventory_pressure_projection` keeps one current row per product with:

- store and product identifiers;
- sales policy;
- current available quantity;
- low-stock state for stock-managed products at quantity five or below;
- most recent sold-out transition time;
- recent reservation-failure count and last failure time; and
- last applied aggregate version and projection update time.

Single-item products never appear as low stock merely because their quantity is inherently one. Their reservation failures and sold-out transitions still contribute to outcome metrics and drill-down records.

### Campaign audit

`campaign_audit_projection` stores campaign kind, owner scope, actor member ID, command, occurrence time, aggregate version, event ID, and bounded before/after JSON summaries. It is paged and ordered by occurrence time, aggregate version, and event ID.

### Performance measurement snapshots

`performance_measurement_run` stores:

- external measurement UUID and payload hash;
- Git commit and dirty-state declaration;
- fixture and k6 scenario versions;
- environment and hardware description;
- warm-up and measured intervals;
- registration actor and timestamps; and
- a completed validation status.

Child records store endpoint metrics and query evidence:

- `performance_endpoint_metric`: cache mode, endpoint, p50, p95, throughput, error rate, JDBC statement count, and cache statistics;
- `performance_query_evidence`: query shape, sanitized bind summary, plan summary, execution time, rows, and buffer statistics.

M31 stores summarized, structured evidence for the dashboard. Full raw k6 and `EXPLAIN` artifacts remain versioned files referenced by the measurement run rather than unrestricted database blobs.

## Projection Processing, Retry, And Recovery

Temporary failures retry with exponential delay up to ten attempts. A terminally failed row moves to `DEAD` with attempt count, next-attempt history, and a bounded last-error summary. Unsupported event schema versions are terminal immediately because retry cannot make them compatible.

The administrator dashboard exposes:

- pending event count;
- retrying event count;
- dead event count;
- oldest unprocessed occurrence time; and
- projection lag in seconds.

Administrators may inspect and retry dead events but cannot edit payloads. Retry creates no duplicate metric because projection deduplication remains authoritative.

### Initial bootstrap

M31 records a bootstrap cutoff instant. A bootstrap projector derives the previous 90 days of successful coupon and order facts from authoritative tables into a new `projectionGeneration`. Events committed after the cutoff are then replayed into that generation.

Failure results and audit actions are not inferred. APIs return `trackingStartedAt` so a zero before that point is never presented as measured absence. Rebuilds within the supported 90-day window replay retained operational events; requests older than the retention-backed interactive window are rejected rather than returning incomplete failure or audit totals.

### Rebuild

Read models are replaceable projections. An administrator-only rebuild command writes into a new generation, bootstraps source facts, replays eligible outbox events, verifies checkpoints, and atomically switches the active generation. The previous generation remains available for rollback until bounded cleanup. The active dashboard never reads a partially rebuilt generation.

## Dashboard Query And API Boundaries

All period parameters resolve to `Asia/Seoul` calendar boundaries and become half-open instants internally. The server accepts today, 7-day, 30-day, and 90-day presets or a custom inclusive date range no longer than 90 days.

Every overview response includes:

- requested period and timezone;
- `generatedAt`;
- `projectionUpdatedAt`;
- `projectionLagSeconds`; and
- `trackingStartedAt` for newly collected outcome families.

### Store APIs

```text
GET /api/stores/{storeId}/operations/dashboard
GET /api/stores/{storeId}/operations/campaigns
GET /api/stores/{storeId}/operations/coupon-outcomes
GET /api/stores/{storeId}/operations/inventory-pressure
GET /api/stores/{storeId}/operations/purchase-outcomes
GET /api/stores/{storeId}/operations/campaign-audits
```

The overview returns compact totals and attention counts. Drill-down endpoints use deterministic pagination and explicit store predicates in SQL. They never fetch campaign targets, orders, coupons, or inventory histories as nested entity graphs.

`StoreAccessService.requireOperator` protects reads for owners and managers. Existing active-business-owner checks continue to protect all campaign writes. Personal stores may view applicable order and purchase outcomes; unavailable commercial campaign sections return explicit empty states.

### Administrator APIs

```text
GET  /api/admin/operations-dashboard
GET  /api/admin/operations-dashboard/campaigns
GET  /api/admin/operations-dashboard/outcomes
GET  /api/admin/operations-dashboard/inventory-pressure
GET  /api/admin/operations-dashboard/audits

POST /api/admin/performance-measurements
GET  /api/admin/performance-measurements
GET  /api/admin/performance-measurements/{runId}

GET  /api/admin/operational-events/dead
POST /api/admin/operational-events/{eventId}/retry
```

Administrator dashboard queries support period, store, owner type, campaign kind/status, product, and normalized outcome filters where the data applies. Administrators can navigate to existing platform coupon management commands. Store-owned campaigns remain read-only to administrators.

Performance registration enforces an allowlisted JSON schema, request-size limit, numeric ranges, units, and cache-off/on metadata compatibility. Re-registering the same external measurement UUID and payload hash succeeds idempotently; the same UUID with a different hash is rejected.

## Web Experience

### Store dashboard

Add `/me/store/dashboard` as a dedicated operations route with:

1. store selector and KST period controls;
2. issue, redemption, order, and failure totals;
3. separate applied and realized promotion/coupon discount amounts;
4. active campaign performance;
5. inventory attention queue;
6. leading normalized failure reasons; and
7. links to existing promotion, coupon, order, inventory, refund, settlement, and report pages.

Owners and managers see the same scoped operational evidence. Campaign controls are shown only to eligible owners and remain implemented by existing command APIs.

### Administrator dashboard

Add `/admin/dashboard` with:

1. platform totals and store/campaign filters;
2. campaign and coupon outcome summaries;
3. stock pressure and purchase-failure investigation;
4. platform coupon management links;
5. campaign audit history;
6. performance measurement-run comparison; and
7. projector lag, retry, and dead-event health.

Every aggregate displays its period and refresh time. Eventually consistent values and projection lag are labeled. The UI distinguishes `measured zero`, `tracking not started`, `measurement unavailable`, `projection delayed`, and `request failed`.

The performance section compares cache-off and cache-on runs only when commit, fixture, scenario, environment, hardware, warm-up, and measured interval match. Runs from different conditions remain individually viewable but do not receive an automatic improvement verdict.

## M30 Performance Evidence Workflow

The reproducible experiment uses the existing unmodified M30 k6 workload. Completion requires:

1. reset to an empty local database;
2. seed a versioned representative fixture with active business stores, promotions, coupon campaigns, wishlists, and seven-day product views;
3. run cache-off after its documented warm-up;
4. reset process metrics and run cache-on under the same conditions;
5. capture endpoint p50, p95, throughput, and error rate;
6. capture `discovery.read.duration`, JDBC statement deltas, and Caffeine hit, miss, and eviction values;
7. capture real bound SQL and full `EXPLAIN (ANALYZE, BUFFERS)` evidence for global catalog, fixed-store catalog, active events, and popularity;
8. normalize evidence through a repository script into the validated JSON contract; and
9. register the completed measurement through the administrator API.

The server rejects negative measurements, invalid percentages, `p50 > p95`, unit mismatches, and incompatible cache-off/on pairs. Reports state their hardware and data volumes and make no fixed RPS promise detached from those conditions.

## Error Handling And Security

- Dashboard projections never authorize claims, pricing, purchases, inventory changes, or campaign actions.
- Event payloads exclude member email, nickname, JWT, cookies, private registration identifiers, and raw exception text.
- Unknown event schema versions are isolated as dead events and never partially projected.
- A projector failure does not corrupt a completed business transaction or expose a partially updated dashboard generation.
- A failure-outcome recording error preserves the original API response and emits an explicit server-side missing-outcome signal.
- Store reads always enforce active database membership and store scope. Client routes are not authorization boundaries.
- Administrator measurement and dead-event commands require the existing administrator authority.
- Performance evidence accepts structured summaries only; it cannot invoke a process, execute SQL, or upload arbitrary executable content.

## JPA And Database Focus

- Use typed projections for overview and drill-down reads.
- Index hourly metrics by generation, store/owner scope, bucket, campaign kind, and campaign ID according to actual filters.
- Index inventory pressure by generation, store, low-stock state, recent failure count, and product ID for deterministic paging.
- Index audit history by generation, owner/store scope, occurrence time, and event ID.
- Index outbox polling by delivery state and next-attempt time, and keep event ID unique.
- Keep projector transactions bounded to one batch and avoid remote calls while rows are locked.
- Measure maximum-range overview and drill-down queries with representative projection volume before introducing daily rollups or additional indexes.
- Do not hydrate source aggregates to calculate dashboard cards in Java.

## Verification Strategy

### Backend

Tests cover:

- atomic source change and outbox persistence;
- rollback when successful-command outbox persistence fails;
- post-rollback failure outcome recording without replacing the original error;
- duplicate event delivery applying each projection once;
- out-of-order inventory events retaining the newest aggregate version;
- retry delay, terminal dead state, and administrator retry;
- unknown schema-version isolation;
- bootstrap cutoff without gaps or duplicates;
- generation rebuild and atomic activation;
- KST day boundaries and the 90-day range cap;
- applied, realized, canceled, and refunded promotion/coupon amounts against fixtures;
- fixed low-stock behavior and single-item exclusion;
- owner, manager, outsider, personal-store, inactive-store, and administrator authorization;
- store and platform ownership isolation;
- paged, bounded drill-down query behavior;
- performance JSON validation, compatible run pairing, payload hashing, and idempotent registration; and
- reconciliation of projection totals against authoritative fixture data.

The same `OperationalEventHandler` contract suite runs with the database adapter and an in-memory fake source to prove transport independence. M31 does not add Kafka-specific tests or dependencies.

New JUnit `@Test` methods use Korean names with underscores.

### Web

Verification covers:

- URL-backed store, period, and filter state;
- owner and manager visibility with owner-only controls;
- overview loading and independent drill-down failures;
- measured-zero, tracking-not-started, unmeasured, delayed, and error states;
- navigation to existing operational pages;
- cache-off/on measurement comparison and incompatible-run treatment;
- paginated administrator tables; and
- mobile no-overflow behavior.

### Completion checks

- Focused backend suites pass with JDK 21.
- The complete backend test suite passes with the required JWT secret.
- The web production build passes.
- The representative M30 cache-off/on experiment and SQL evidence are registered and visible.
- `git diff --check` passes.
- Manual owner, manager, and administrator flows pass.

## Explicitly Out Of Scope

- Kafka, another message broker, distributed consumer deployment, and broker operations.
- Elasticsearch/OpenSearch, Redis-based reporting, an OLAP warehouse, or a general observability platform.
- Platform-owned automatic price promotions or a new generic event-campaign aggregate.
- Live process execution from the administrator UI.
- Full event sourcing or replacing OLTP aggregates with the outbox.
- More than 90 days of interactive dashboard history, automatic long-term daily compression, or external exports.
- Automated campaign optimization, automatic price changes, alert paging, or legal/accounting reporting.

## Completion Criteria

M31 is complete when owners and managers can inspect correctly scoped operational evidence, owners retain exclusive campaign mutation authority, administrators can investigate platform and cross-store outcomes without impersonating stores, campaign actions are auditable, outcome and inventory projections are idempotent and rebuildable, projector failures are operable, and real M30 cache-off/on evidence is registered and displayed with its conditions.

Completion also requires preserving all M29 write invariants, bounding dashboard queries, passing backend and web verification, and showing no fabricated performance or pre-tracking failure values.
