# Realistic Payment And Delivery Integration Roadmap: Milestones 32-39

## Purpose

This document defines the product and engineering phase after the completed M21-M31 hybrid marketplace roadmap. It replaces the in-process fake payment and delivery clients with separately running, production-shaped simulator services and uses them to practice the failure modes that appear at real external-system boundaries.

The phase has four outcomes:

1. Run a Mock Payment Gateway and a Mock Delivery Provider as independent Spring Boot applications with their own ports, databases, migrations, security boundaries, and operator screens.
2. Integrate Sweet Market through synchronous HTTP commands, asynchronous webhooks, durable retries, and periodic reconciliation without sharing Java models or databases.
3. Make ambiguous outcomes, duplicate or missing callbacks, delivery exceptions, returns, and recovery observable and operable without direct database edits.
4. Prove the system with deterministic fault scenarios, seeded probabilistic faults, distributed traces, bounded load and chaos experiments, and an evidence-based Kafka decision.

This is a roadmap, not an implementation plan. Start every milestone in a new session with its own design spec, implementation plan, verification record, and handoff. Names below describe required behavior and boundaries; each milestone design may improve concrete class, table, endpoint, or status names while preserving those decisions.

## Phase Strategy

The phase uses a payment-first vertical sequence:

```text
M32 external integration foundation
 -> M33 Mock Payment Gateway
 -> M34 realistic payment integration and recovery
 -> M35 Mock Delivery Provider
 -> M36 realistic delivery, exception, and return integration
 -> M37 reconciliation and recovery operations
 -> M38 observability and SLO operations
 -> M39 resilience, chaos, capacity, and Kafka gate
```

Payment comes first because ambiguous financial outcomes impose the strictest idempotency and reconciliation requirements. The proven patterns are then adapted to delivery rather than generalized prematurely. Common infrastructure is extracted only when both domains demonstrate the same need.

## Target System Topology

The repository contains three independently running Spring Boot applications:

```text
Sweet Market
  |-- synchronous commands ------> Mock Payment Gateway
  |-- synchronous commands ------> Mock Delivery Provider
  |-- authoritative status query -> each external simulator
  `-- signed webhook intake <----- each external simulator

Each application
  |-- owns one process and port
  |-- owns one PostgreSQL database and Flyway history
  |-- owns its domain model and persistence
  |-- owns its health and observability surface
  `-- can fail, restart, and deploy independently
```

The applications must not share JPA entities, repositories, service implementations, runtime DTO libraries, or database access. OpenAPI documents, protocol examples, and HMAC rules are the public contracts. Consumer and provider tests verify those contracts independently.

Docker Compose provides the local integration environment, including the three applications, their isolated databases, Redis where Sweet Market still requires it, OpenTelemetry Collector, Prometheus, Grafana, and a central log store. Each simulator serves its own minimal operator screen. Sweet Market must not expose simulator fault controls through its buyer, store-operator, or administrator authorization model.

## Cross-Milestone Decisions

### Communication Model

- External commands use HTTP. An immediate response reports rejection, completion, or accepted processing, but a missing response does not prove failure.
- Final or delayed external state changes arrive through signed webhooks.
- Status-query APIs provide the recovery path for unknown outcomes, missing webhooks, and reconciliation.
- Sweet Market keeps the current database-outbox abstraction for internal asynchronous work. Kafka is not introduced before M39 evidence.
- No external HTTP call is held inside a Sweet Market business database transaction. The application first stores an operation intent and a dispatcher performs the call and records its attempt.

### Authority And Consistency

- The Payment Gateway is authoritative for external financial processing state.
- The Delivery Provider is authoritative for shipment scan and carrier-processing state.
- Sweet Market is authoritative for orders, inventory, coupons, refund policy, operator decisions, and its internal state transitions.
- External state is mapped through explicit Sweet Market policy; it is never copied directly into an order status.
- There is no distributed transaction or two-phase commit. Stable idempotency keys, durable inbox/outbox records, state versions, and reconciliation drive eventual convergence.

### Security

Requests and webhooks use an API key identity plus HMAC over a canonical envelope containing:

```text
key-id
timestamp
request-or-event-id
HTTP method
request path
raw-body SHA-256
```

All receivers enforce clock-skew bounds, replay protection, constant-time signature comparison, key-ID-based overlap during rotation, and a hard raw-request size limit before JSON materialization. Secrets come from environment or Docker secrets and never enter source control, application logs, traces, or stored payloads. The simulators never store real card numbers, CVCs, resident identifiers, or real carrier credentials.

### Fault Control

Both simulators support two complementary modes:

- Deterministic rules bind a named outcome to a transaction, operation, order, or shipment identifier. Integration and acceptance tests use this mode.
- Probabilistic rules define latency, failure, response-loss, webhook-loss, duplication, and reordering rates. Every experiment records its seed, active window, and target operations so it can be reproduced.

Fault controls are available only in development and experiment profiles. Each simulator uses a separate local operator credential and session rather than its merchant API key. A simulator's operator can inspect and change its own rules; Sweet Market code and buyers, store operators, or administrators cannot.

### Operation Ledger Instead Of State Explosion

Sweet Market keeps an authoritative resource and a separate operation ledger rather than combining confirmation, cancellation, refund, shipment creation, cancellation, and return states into one cross-product enum.

```text
Payment
  financial state: PENDING / APPROVED / CANCELED / REFUNDED

PaymentOperation
  type: CONFIRM / CANCEL / REFUND
  state: REQUESTED / ACCEPTED / SUCCEEDED / FAILED / UNKNOWN

Shipment
  mapped shipment state and latest authoritative external version

DeliveryOperation
  type: CREATE / CANCEL / RETURN
  state: REQUESTED / ACCEPTED / SUCCEEDED / FAILED / UNKNOWN
```

Each operation records a stable idempotency key, internal and external identifiers, request hash, attempt history, timestamps, final error classification, and correlation identifiers. The precise schema is fixed in the relevant milestone design.

### Retry Classification

- Retry connection failures, timeouts, HTTP 429 responses, and eligible HTTP 5xx responses with bounded exponential backoff and jitter.
- Do not retry business declines, amount mismatches, invalid state transitions, invalid signatures, or contract-validation failures as transient faults.
- A request sent without a conclusive response becomes `UNKNOWN`; do not create a new idempotency key or infer failure.
- Move exhausted transport work to a DEAD queue and unresolved business discrepancies to a manual-review queue. These are separate operational meanings.

## Milestone 32: External Integration Foundation

### Product Goal

Create a reproducible local topology where Sweet Market and two external simulators start independently and communicate only through authenticated public contracts.

### Learning Goal

Practice multi-application repository layout, service-owned persistence, OpenAPI compatibility, secret injection, signed HTTP envelopes, correlation propagation, and integration-profile orchestration.

### Scope

- Add independent `mock-payment-gateway` and `mock-delivery-provider` Spring Boot applications.
- Give each application its own build, configuration, PostgreSQL database, Flyway history, health endpoint, test suite, and container image.
- Add Docker Compose integration profiles and readiness checks without coupling normal unit tests to the full stack.
- Define API versioning, common protocol metadata, error-envelope rules, idempotency headers, webhook acknowledgement, and HMAC canonicalization.
- Add API-key lookup, HMAC verification, timestamp bounds, replay storage, and overlapping key rotation to each receiver.
- Propagate W3C trace context and a stable business correlation ID across requests and callbacks.
- Preserve `PaymentGateway` and `DeliveryClient` as Sweet Market ports. Keep in-process fakes available for focused tests until their integration milestones replace the production adapters.
- Establish provider and consumer contract-test harnesses from OpenAPI without a shared runtime DTO module.

### Verification And Exit Criteria

- The three applications start independently and through Docker Compose.
- Each application can be stopped, restarted, migrated, and tested without connecting to another application's database.
- Signed health-contract probes succeed; invalid keys, body mutation, expired timestamps, and replays fail deterministically.
- Trace and correlation metadata cross a round trip without leaking HMAC data.
- A repository dependency check proves that no simulator imports Sweet Market domain or persistence code.

### Explicitly Out Of Scope

- Payment or shipment business processing.
- A generalized integration framework shared by both domains.
- Kafka, service mesh, Kubernetes, or production secret-management infrastructure.

### Handoff To M33

M33 builds the Payment Gateway entirely behind the M32 public and security contracts. It must remain usable and testable without a running Sweet Market instance.

## Milestone 33: Mock Payment Gateway

### Product Goal

Provide a standalone payment processor that behaves like an external PG for confirmation, cancellation, refund, delayed completion, and ambiguous transport outcomes.

### Learning Goal

Practice merchant-scoped APIs, financial idempotency, external operation ledgers, durable callback delivery, deterministic fault injection, and operator-facing auditability.

### Scope

- Model merchant payment resources and independent confirm, cancel, and refund operations.
- Implement single-step payment confirmation. Authorization/capture separation is not part of this phase.
- Require a merchant order reference, non-negative KRW amount, stable idempotency key, and request hash.
- Return the prior result for the same key and payload; return conflict for the same key and a different payload.
- Support immediate approval, explicit decline, accepted asynchronous processing, and queryable final state.
- Support asynchronous cancel and refund completion without partial capture or multiple partial refunds.
- Store an append-only operation and state-transition history suitable for auditing.
- Create a durable webhook outbox whose event ID remains stable across retries. Use bounded retry, backoff, DEAD state, and operator-triggered payload-preserving resend.
- Add deterministic scenarios for decline, latency, timeout before processing, response loss after success, duplicate command, callback delay, callback loss, duplication, and reordering.
- Add seed-based probabilistic latency and fault rules for later experiments.
- Serve a minimal operator screen for payments, operations, fault rules, callback attempts, and safe resend.

### Verification And Exit Criteria

- Confirmation, cancellation, refund, idempotency, and state-transition tests pass against PostgreSQL.
- A lost HTTP response after successful payment remains discoverable through status query and webhook.
- Duplicate commands cannot produce duplicate financial effects.
- Restarting the Gateway resumes pending asynchronous operations and webhook delivery.
- The operator screen exposes no API secret or sensitive raw payment data.

### Explicitly Out Of Scope

- Real acquirer or card-network integration, card vaulting, PCI DSS scope, installments, foreign currencies, authorization/capture separation, and partial refunds.

### Handoff To M34

M34 replaces Sweet Market's production fake payment adapter with the Gateway while preserving isolated in-process fakes for narrow tests.

## Milestone 34: Realistic Payment Integration And Recovery

### Product Goal

Make Sweet Market payment, cancellation, and refund flows correct when the external result is delayed, duplicated, missing, or temporarily unknowable.

### Learning Goal

Practice transaction-to-HTTP boundaries, ambiguous results, webhook inbox processing, compensation timing, concurrency control, and financial recovery UX.

### Scope

- Add an HTTP Payment Gateway adapter with explicit timeouts, connection pooling, error classification, and circuit-breaker hooks.
- Persist payment operation intent before dispatch and record every attempt separately from the order transaction.
- Add `REQUESTED`, `ACCEPTED`, `SUCCEEDED`, `FAILED`, and `UNKNOWN` operation semantics while keeping confirmed financial state on `Payment`.
- Receive signed Gateway webhooks into a durable inbox. Acknowledge after authentication and inbox persistence; process the business transition asynchronously.
- Enforce unique external event IDs, stable internal operations, payload hashes, and version-aware transition rules.
- On a conclusive decline, run the existing inventory and coupon compensation exactly once.
- On a timeout or response loss, keep the reservation protected while status lookup and reconciliation resolve the result. Never compensate merely because the client timed out.
- Convert cancellation and approved-refund handling to asynchronous external operations. A seller or administrator approval starts a refund; only Gateway success completes it.
- Provide buyer states for processing, approved, declined, and result-checking. Provide ADMIN investigation for attempts, callbacks, mismatches, and safe recovery commands.
- Record operational events without duplicating M31's projection authority or exposing simulator fault controls.

### Verification And Exit Criteria

- Normal approval, explicit decline, timeout before processing, response loss after approval, duplicate request, duplicate callback, callback reordering, cancellation failure, and refund failure pass end to end.
- Concurrent browser retry, webhook, and status lookup cannot charge or compensate twice.
- A clean Gateway restart and a clean Sweet Market restart converge pending work.
- Full M1-M33 backend and web behavior remains compatible unless this milestone explicitly changes a pending-state contract.

### Explicitly Out Of Scope

- Shipment integration, return-dependent refunds, multi-payment orders, chargebacks, and real money movement.

### Handoff To M35

M35 applies the independently proven external-service patterns to a standalone Delivery Provider, while keeping delivery-specific states and failures separate from payment abstractions.

## Milestone 35: Mock Delivery Provider

### Product Goal

Provide a standalone carrier simulator that can advance a shipment through normal parcel delivery, exceptions, and returns while producing realistic callbacks and failures.

### Learning Goal

Practice external state machines, event sequencing, scheduled state progression, exception modeling, return logistics, and carrier-operator tooling.

### Scope

- Implement shipment creation, query, cancellation, and return-request APIs.
- Use a normal lifecycle equivalent to `ACCEPTED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED`.
- Support `DELIVERY_FAILED`, `LOST`, `DAMAGED`, `RETURNING`, `RETURNED`, and valid cancellation boundaries. A failed delivery can move to a new `OUT_FOR_DELIVERY` attempt or into return processing according to an explicit provider transition.
- Give each shipment event an immutable event ID, monotonically increasing external sequence, occurred-at time, and carrier status.
- Support automatic time-based progression and explicit operator progression.
- Reject invalid provider-side transitions while allowing fault scenarios to emit duplicate, delayed, missing, or deliberately reordered callbacks for consumer testing.
- Provide deterministic and seed-based probabilistic rules for processing delay, API failure, response loss, webhook behavior, and exceptional delivery outcomes.
- Use a durable webhook outbox with bounded retry, DEAD isolation, and safe resend.
- Serve a minimal operator screen for shipment timelines, exception rules, callback attempts, and manual transitions.

### Verification And Exit Criteria

- Every normal, exception, cancellation, and return transition has domain and API coverage.
- Duplicate or repeated commands remain idempotent.
- Automatic progression is deterministic under a supplied clock.
- Provider restart preserves shipment scheduling and resumes callback delivery.
- The Provider operates and demonstrates its scenarios without Sweet Market.

### Explicitly Out Of Scope

- Multiple real carriers, rate shopping, labels from real carrier APIs, warehouse hubs, courier assignment, route optimization, and geolocation tracking.

### Handoff To M36

M36 replaces the Sweet Market fake delivery client and connects carrier state to order, refund, and operator policy without surrendering internal business authority.

## Milestone 36: Realistic Delivery, Exception, And Return Integration

### Product Goal

Let buyers, store operators, and administrators follow and recover real external shipment flows, including failed delivery, loss, damage, return, and refund sequencing.

### Learning Goal

Practice provider-state mapping, monotonic event consumption, long-running workflows, return-before-refund policy, and cross-domain orchestration without distributed transactions.

### Scope

- Add an HTTP Delivery Provider adapter and durable delivery-operation dispatch.
- Store the external shipment ID, latest accepted external version, carrier status, internal mapped status, and a full sanitized event timeline.
- Receive signed delivery webhooks through the durable inbox and reject duplicate or regressive state effects.
- Map carrier statuses through explicit Sweet Market policy. Delivery callbacks cannot directly authorize payment, refund, inventory, or order commands.
- Prevent order delivery completion before an accepted `DELIVERED` state.
- Represent failed delivery, loss, and damage as explicit investigation states rather than generic application errors.
- Connect return-required refunds to `RETURN_REQUESTED -> RETURNING -> RETURNED -> REFUND_PENDING -> REFUNDED`.
- Allow pre-shipment cancellation or other no-return cases to skip return logistics when the approved policy permits it.
- Provide buyer shipment timelines, store-operator fulfillment views, and ADMIN exception investigation without exposing simulator controls.
- Preserve existing automatic purchase-confirmation rules, recalibrated to the authoritative delivered timestamp.

### Verification And Exit Criteria

- Normal delivery, retryable failed delivery, loss, damage, cancellation, return, and post-return refund pass through real HTTP and webhook paths.
- Duplicate and reordered carrier callbacks never move internal state backward.
- Return completion and refund dispatch remain exactly once under concurrency.
- Buyer, store, and ADMIN screens distinguish pending, delayed, exceptional, and completed states.
- Existing order, settlement, refund, inventory, coupon, and M31 projection invariants remain correct.

### Explicitly Out Of Scope

- Multi-package orders, split shipments, warehouse inventory, real carrier labels, and automatic compensation policy for every loss or damage case.

### Handoff To M37

M37 unifies the already working payment and delivery status-query paths into an operator-grade reconciliation and recovery system.

## Milestone 37: Reconciliation And Recovery Operations

### Product Goal

Detect and repair payment and delivery divergence without direct database edits, while separating safe automatic convergence from decisions that need a human operator.

### Learning Goal

Practice anti-entropy jobs, discrepancy classification, operator-safe commands, payload-preserving replay, convergence metrics, and recovery audit trails.

### Scope

- Detect `UNKNOWN`, long-running `PENDING`, stale shipment, exhausted webhook, and inconsistent terminal states.
- Query authoritative external status in bounded, rate-limited batches.
- Classify discrepancies as already converged, safe auto-repair, retryable transport work, or manual review.
- Apply safe repairs through the same idempotent application commands used by webhooks; do not write domain tables directly from reconciliation SQL.
- Add durable reconciliation runs, item outcomes, before/after summaries, actor or scheduler identity, and correlation IDs.
- Add webhook DEAD inspection and payload-preserving resend on the simulator side, plus inbox retry and manual-review resolution on Sweet Market.
- Add ADMIN views for internal/external comparison, last attempts, next retry, mismatch reason, trace links, and explicit recovery commands.
- Prevent concurrent webhook, dispatcher, and reconciliation workers from applying the same transition twice.

### Verification And Exit Criteria

- Seeded inconsistencies converge to the authoritative policy result without direct database modification.
- Unsafe contradictions remain visible for manual review and are never silently overwritten.
- Re-running reconciliation is idempotent and produces an auditable no-op when already converged.
- Batch size, rate limit, lock duration, and query count are bounded and measured.

### Explicitly Out Of Scope

- Generic master-data synchronization, a universal saga framework, and automatic resolution of policy disputes such as fraud or liability.

### Handoff To M38

M38 makes the complete command, callback, retry, and reconciliation path explainable through shared traces, metrics, logs, SLOs, and runbooks.

## Milestone 38: Observability And SLO Operations

### Product Goal

Allow an operator to explain one order's payment and delivery history across all three processes and to detect integration degradation before unresolved transactions accumulate.

### Learning Goal

Practice OpenTelemetry propagation, service-level indicators, cardinality control, privacy-safe central logging, alert design, and evidence-backed operational runbooks.

### Scope

- Deploy and configure OpenTelemetry Collector, Prometheus, Grafana, and a central log store in the local integration profile.
- Correlate order, payment operation, shipment, webhook event, dispatcher attempt, and reconciliation item through trace and business correlation identifiers.
- Instrument external API latency and outcomes, circuit state, retries, queue depth, oldest pending age, webhook lag, DEAD count, `UNKNOWN` age, reconciliation mismatch, and convergence time.
- Keep low-cardinality metrics; transaction identifiers belong in traces and logs, not metric labels.
- Create payment, delivery, webhook, reconciliation, and system-health dashboards.
- Define initial local-environment SLIs and SLOs for successful confirmation, callback freshness, unknown-resolution time, shipment-event freshness, and reconciliation convergence.
- Add alerts for sustained failure rate, oldest-pending breach, retry storm, DEAD growth, mismatch growth, and observability pipeline failure.
- Apply centralized redaction and automated scans for API keys, HMAC material, authorization headers, personal data, and simulated payment secrets.
- Write runbooks that link alert -> dashboard -> trace/log query -> safe recovery action.

### Verification And Exit Criteria

- A normal and each approved failure scenario can be traced end to end across processes.
- Metrics reconcile with persisted fixture counts and do not use unbounded identifiers as labels.
- Alerts fire and recover under deterministic scenarios.
- Operators can follow a runbook from symptom to evidence and recovery without database edits.
- Stopping the observability stack does not break commerce processing, and its failure is itself visible after recovery.

### Explicitly Out Of Scope

- A managed production observability vendor, organization-wide on-call processes, and production SLO claims from local hardware.

### Handoff To M39

M39 uses the now-observable system to measure bounded load, controlled chaos, recovery, and whether the DB outbox has reached a transport constraint that justifies Kafka.

## Milestone 39: Resilience, Chaos, Capacity, And Kafka Gate

### Product Goal

Demonstrate that the integrated system converges after realistic failures, quantify its limits, and make the next transport decision from evidence rather than architectural fashion.

### Learning Goal

Practice reproducible chaos, workload provenance, backpressure, retry-storm prevention, recovery-time measurement, capacity interpretation, and architecture decision records.

### Scope

- Build a versioned end-to-end scenario suite covering normal payment and delivery plus every approved deterministic failure.
- Add seeded probabilistic experiments with recorded configuration, code commit, fixture, environment, and time window.
- Exercise simulator restarts, Sweet Market restart, network latency and interruption, response loss, webhook loss/duplication/reordering, webhook bursts, temporary database outage, 429, and eligible 5xx responses.
- Verify bounded exponential backoff, circuit breakers, independent payment/delivery bulkheads, dispatcher rate limits, and backlog recovery.
- Run bounded payment, webhook, delivery-event, and reconciliation load tests. Record throughput, p50/p95/p99 latency, error classification, queue depth, oldest pending age, UNKNOWN count, DEAD count, and convergence time.
- Measure recovery time and unresolved transaction count after faults stop.
- Require every approved scenario to converge through normal commands, webhook processing, retries, or reconciliation without direct database edits.
- Compare the measured DB-outbox behavior with explicit Kafka adoption criteria: sustained backlog, recovery time, throughput headroom, operational complexity, ordering needs, and multi-consumer demand.
- Publish an ADR that either keeps DB outbox with measured headroom or proposes Kafka as the first candidate of a separately approved roadmap. M39 does not install Kafka merely to complete the phase.

### Verification And Exit Criteria

- The deterministic suite is repeatable and all expected terminal states reconcile exactly.
- The same seed reproduces the same probabilistic fault decisions.
- Retry storms, unbounded queues, duplicate financial effects, state regression, and silent loss are absent from the approved workload envelope.
- Dashboards, traces, logs, database evidence, and scenario results agree.
- Recovery-time and convergence results are recorded with environment and hardware provenance and are not presented as universal production guarantees.
- The Kafka ADR contains measured thresholds, observed values, trade-offs, and a clear decision.
- Complete backend, simulator, web, contract, security, migration, integration, and evidence-tool suites pass.

### Explicitly Out Of Scope

- Unbounded destructive chaos, real financial or carrier traffic, production capacity certification, and a Kafka migration without a separately approved design.

### Phase Completion Boundary

M39 completes the phase only when Sweet Market can process and recover approved payment and delivery scenarios through separately running external systems, operators can explain the outcome through trace/metric/log/audit evidence, and no scenario requires direct database repair.

## Cross-Phase Verification Matrix

Every milestone runs the verification layers relevant to its scope and preserves all earlier layers:

1. Domain state-transition tests for valid, duplicate, regressive, and invalid transitions.
2. OpenAPI consumer/provider contract tests without shared runtime DTOs.
3. API-key, HMAC, timestamp, replay, rotation, and secret-leak security tests.
4. Testcontainers service integration tests with fresh migrations and restart recovery.
5. Real-port cross-service tests for synchronous commands, callbacks, status query, and reconciliation.
6. Concurrency tests for duplicate payment, callback-versus-query, cancel-versus-confirm, and return-versus-refund races.
7. Browser tests for buyer, store operator, Sweet Market ADMIN, Payment Gateway operator, and Delivery Provider operator roles.
8. Observability tests that reconcile traces, metrics, logs, and persisted facts.
9. M39 provenance-preserving load and chaos evidence.
10. Complete Sweet Market regression tests, simulator suites, production builds, and repository hygiene checks.

New JUnit test method names continue to use Korean words separated by underscores according to repository policy.

## Preserved M21-M31 Invariants

- Store membership remains the authority for store-owned commands. External systems never infer Sweet Market authorization.
- Orders preserve immutable list, promotion, coupon, and final-price snapshots.
- Inventory and coupon compensation remains durable, conditional, deterministic, and exactly once.
- Payment ambiguity does not release inventory or coupons until authoritative resolution permits compensation.
- OWNER and MANAGER retain their established command boundaries; ADMIN does not impersonate a store operator.
- Applied, realized, canceled, and refunded operational amounts remain separate in M31 projections.
- Replayed external delivery cannot double-count projection facts.
- M30 performance evidence remains historical and is not relabeled as an M39 measurement.
- The existing database-outbox transport remains behind its abstraction until M39 evidence supports another decision.

## Phase-Level Explicit Exclusions

The M32-M39 roadmap does not include:

- Real PG, acquirer, card network, or carrier accounts.
- Storage of card numbers, CVCs, real identity data, or real carrier credentials.
- Authorization/capture separation, partial capture, or multiple partial refunds.
- Multi-carrier rate shopping, warehouse networks, courier routing, or live geolocation.
- Multi-item or multi-store checkout.
- A generic saga, integration, or workflow platform.
- Kafka, service mesh, Kubernetes, or distributed cache introduced without measured need.
- Context-free production RPS, availability, or recovery guarantees derived from local experiments.

Any of these requires a new evidence review, design, and roadmap revision rather than silent scope expansion.

## Final Success Standard

The base completion standard is reproducible recovery: normal, declined, timed-out, response-lost, callback-missing, duplicated, reordered, delivery-exception, and return scenarios execute automatically and converge without direct database changes. M39 adds bounded load and chaos evidence to show that the recovery mechanisms remain controlled under pressure. The phase is successful when correctness, security, observability, and recovery are all demonstrated from preserved evidence rather than asserted from happy-path tests.
