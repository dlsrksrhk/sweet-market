# M30 Live Provenance Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a fresh M30 OFF/ON measurement whose collector identity and live HTTP SQL/binds prove same-process provenance through registration.

**Architecture:** The collector persists sanitized authenticated identity snapshots around k6. A standalone Node parser turns a task-local Spring JDBC TRACE byte range into four exact bound statements, and the capture script executes EXPLAIN from only that parsed material. The normalizer cross-checks collector before/after identity against plan identity before emitting the registration contract.

**Tech Stack:** PowerShell 7, Node.js test runner, Spring Boot/JDBC TRACE, PostgreSQL 17 JSON EXPLAIN, k6 2.1.0, JDK 21.

## Global Constraints

- Do not reset the database or rerun the `m30-v1` fixture initializer.
- Do not infer, relabel, or adopt any earlier measurement.
- OFF and ON each run `boot → collector before identity → k6 → collector after identity → live trace HTTP requests → plans → stop`.
- JUnit `@Test` method names remain Korean with underscores.
- Task 2/3/4 dirty reports remain untouched and unstaged.

---

### Task 1: Collector and normalizer identity gate

**Files:**
- Modify: `performance/collect-m30-measurement.ps1`
- Modify: `performance/normalize-m30-measurement.mjs`
- Modify: `performance/normalize-m30-measurement.test.mjs`
- Modify: `performance/fixtures/normalizer-input.json`

**Interfaces:**
- Produces: `metrics.serverInfo.before/after` with `serverProcessId`, `activeProfiles`, `fixedClock`, and `cacheMode`.
- Consumes: plan `provenance.serverInfo` with the same shape.

- [ ] **Step 1: Write failing normalizer and collector tests**

Add fixture server identities and tests that mutate before/after PID, plan PID, fixed clock, profiles, and cache mode. Add static assertions that `$serverInfoBefore` appears before `& k6 run`, `$serverInfoAfter` appears immediately after, and both are persisted.

- [ ] **Step 2: Verify RED**

Run `node --test performance/normalize-m30-measurement.test.mjs` and expect failures for missing `serverInfo` validation and missing collector calls.

- [ ] **Step 3: Implement the minimum gate**

Add an authenticated sanitizer returning only the four fields. Persist both snapshots, compare them after artifact creation, and make the normalizer require exact per-mode profiles plus equality with plan identity.

- [ ] **Step 4: Verify GREEN**

Run the Node test file and PowerShell parser; expect zero failures.

### Task 2: Template-free live trace parser and capture

**Files:**
- Create: `performance/parse-m30-jdbc-trace.mjs`
- Create: `performance/parse-m30-jdbc-trace.test.mjs`
- Create: `performance/fixtures/m30-jdbc-trace.log`
- Modify: `performance/capture-m30-query-evidence.ps1`

**Interfaces:**
- Parser CLI: `node performance/parse-m30-jdbc-trace.mjs --trace-log <path> --start-offset <bytes> --end-offset <bytes>`.
- Parser result: `{traceSegmentSha256, sanitizedTraceSha256, statements[]}` where each statement has `queryShape`, `requestPath`, `threadName`, `preparedSql`, `capturedBinds`, `exactSql`, and `bindSummary`.

- [ ] **Step 1: Write failing parser/static tests**

Use a multiline fixture containing the four HTTP statements plus interleaved scheduler SQL. Assert exact classification, bind extraction, typed placeholder replacement, and rejection of missing/duplicate/inconsistent binds. Assert capture requires `TraceLogPath`, records offsets before HTTP calls, calls the parser, and contains no `TemplatePath`.

- [ ] **Step 2: Verify RED**

Run both Node test files and expect module-not-found/parser/static failures.

- [ ] **Step 3: Implement parser and orchestration**

Parse log records by thread, classify only the four expected SQL prefixes, validate bind sequences, replace placeholders outside quoted literals, hash the byte range and sanitized statements, then execute four JSON EXPLAIN plans from `exactSql`.

- [ ] **Step 4: Verify GREEN**

Run both Node suites and the capture PowerShell parser; expect zero failures.

### Task 3: Fresh experiment, registration, and report

**Files:**
- Replace: `performance/results/m30-v1/*.json`
- Modify: `docs/superpowers/reports/2026-07-16-milestone-30-catalog-read-performance.md`
- Modify: `.superpowers/sdd/task-11-report.md`

**Interfaces:**
- Consumes: unchanged fixture, collector/capture scripts, normalizer CLI, ADMIN registration API.
- Produces: fresh UUID, corrected artifacts, sanitized registration response, and report appendix.

- [ ] **Step 1: Run OFF**

Launch on a dedicated port with initializer disabled, JDBC TRACE file logging, and exact OFF profiles. Run collector, live capture, verify the three identity PIDs/configs match, then stop the exact PID.

- [ ] **Step 2: Run ON**

Repeat with exact ON profiles and a separate trace file, preserving the same database and fixed clock.

- [ ] **Step 3: Normalize and register**

Generate a fresh UUID/metadata, combine the two capture bundles, normalize, POST to the ADMIN API, require HTTP 201 and `valid/comparable=true`, and write the sanitized response with raw and canonical hashes.

- [ ] **Step 4: Update reports**

Append the new gate and mark the immediately preceding run as provenance-incomplete historical evidence without relabeling it.

- [ ] **Step 5: Verify and commit**

Run both Node suites, PowerShell parsers, focused Java tests, full backend tests, normalizer hash reproduction, JSON/security/absolute-path scans, `git diff --check`, then stage only Task 11 files and commit.
