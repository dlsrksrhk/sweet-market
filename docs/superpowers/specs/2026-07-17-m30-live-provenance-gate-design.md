# M30 live provenance gate design

## Status and scope

This design supersedes neither of the earlier measurements. It defines a new run whose identity and SQL provenance must stand on their own. The parent request explicitly approved this scope and requested execution without another approval pause.

The correction has two gates:

1. The authenticated application identity observed immediately before and after k6 must be identical and must also equal the identity observed during plan capture.
2. Every plan must be derived from prepared SQL and positional binds emitted by the live HTTP requests in that same process and mode. No prior evidence file may supply SQL.

The fixture database remains unchanged. The initializer stays disabled, and OFF completes before ON starts.

## Considered approaches

### Transform a previous SQL template

Rejected. It cannot prove that the SQL or binds came from the live server being measured, and prior runs demonstrated that a plausible bind summary can diverge from the executed SQL.

### PostgreSQL statistics or auto-explain

Rejected. These can expose executed statements but do not reliably connect each statement to the authenticated HTTP request and Spring positional binds without broader database instrumentation.

### Task-local Spring JDBC TRACE byte range

Selected. The server writes `org.springframework.jdbc.core=TRACE` to a known task-local file. Capture records the byte offset, invokes the four HTTP requests sequentially, records the ending offset, and parses only that byte range. HTTP worker-thread `JdbcTemplate` statements are paired with `StatementCreatorUtils` binds; scheduler statements are ignored.

## Collector identity contract

`collect-m30-measurement.ps1` reads `/actuator/info` with the ADMIN token immediately before invoking k6 and immediately after k6 returns, before the post-run metric snapshot. It persists only:

- `serverProcessId`
- `activeProfiles`
- `fixedClock`
- `cacheMode`

under `serverInfo.before` and `serverInfo.after` in `metrics-{mode}.json`. The collector writes evidence and then fails if the snapshots differ. OFF must have exactly `local`, `performance-fixture`, `local-experiment`, and `cache-off`; ON must have exactly the first three.

## Live trace contract

`parse-m30-jdbc-trace.mjs` consumes a trace path plus start/end byte offsets and returns exactly four classified statements:

- `GLOBAL_CATALOG`: three binds, two identical fixed-clock values and `limitPlusOne=21`
- `FIXED_STORE_CATALOG`: four binds, two identical fixed-clock values, `storeId=1`, and `limitPlusOne=21`
- `ACTIVE_EVENTS`: four identical fixed-clock binds
- `POPULARITY`: two identical seven-day `since` binds followed by two identical fixed-clock binds

The parser reconstructs exact SQL by replacing placeholders outside quoted SQL literals with typed literals. OffsetDateTime values become canonical `timestamptz` literals; integral values remain unquoted. It fails on missing, duplicate, unsupported, or inconsistent statements/binds.

`capture-m30-query-evidence.ps1` requires `TraceLogPath` and has no `TemplatePath`. For ON it waits for the 30-second active-event cache entry to expire before recording the trace offset. It records authenticated server identity, byte-range metadata, the raw range SHA-256, the sanitized four-statement SHA-256, prepared SQL, sanitized positional binds, exact SQL, and full JSON EXPLAIN plans. The raw application log is task-local and is not committed.

## Normalization gate

The normalizer validates exact profiles, cache mode, fixed clock, and positive PID for collector before/after and plan capture. It requires:

```text
collector.before PID/config
  = collector.after PID/config
  = plan capture PID/config
```

for each mode. OFF and ON fixed clocks must also match. Prepared SQL, captured binds, exact SQL, full plans, byte offsets, thread names, and trace hashes remain in `query-evidence.json` and are stripped from the registration payload.

## Verification and security

Node tests cover real multiline trace parsing, scheduler-thread exclusion, placeholder replacement, missing/duplicate shapes, bind mismatch, collector identity mismatch, and collector-to-plan mismatch. Static tests enforce authenticated info reads around k6, `TraceLogPath`, byte offsets, and absence of `TemplatePath`.

The final gate additionally requires PowerShell parsing, full JSON validation, exact arithmetic reproduction from route samples, raw-file hash reproduction, registration `valid/comparable=true`, no credentials/visitor identifiers/absolute paths in committed artifacts, focused Java tests, and the full backend suite.
