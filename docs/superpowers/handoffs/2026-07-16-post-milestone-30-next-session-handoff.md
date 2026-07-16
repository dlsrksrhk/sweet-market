# Post-Milestone 30 Next Session Handoff

## Current Workspace

- Worktree: `C:\dev\study\sweet-market-m30`
- Branch: `codex/milestone-30-high-traffic-catalog-reads`
- Current HEAD: `f4ba15a` (`fix: restore inventory and migration regressions`)
- The M30 implementation and its task-level reviews are complete.
- One intentional untracked planning document remains: `docs/superpowers/plans/2026-07-16-milestone-30-high-traffic-catalog-reads.md`. Preserve it or commit it explicitly; do not discard it as cleanup.

## Delivered M30 Boundary

- Public active-event discovery, event detail cards, and popular-product discovery are available.
- Popularity uses the trailing seven days: wishlist count × 5 plus product-detail view count, with product-ID descending ties.
- Anonymous and authenticated detail views use a seven-day `HttpOnly` visitor cookie and a three-second per-product deduplication boundary.
- Public event summaries use a Caffeine cache limited to one entry and 30 seconds. Campaign, product visibility/image, inventory/purchase availability, and store-status commands publish after-commit invalidation events.
- Home displays active events, popular products, and the existing URL-backed catalog. Event detail uses exact campaign-ID coupon claim-state lookup, so an event is not lost behind the generic 100-item campaign-page limit.
- The local experiment profile, cache-off profile, metrics instrumentation, k6 script, and performance-report template are present.

## Regression Status

The three issues recorded in the original M30 handoff are resolved by `f4ba15a`:

- Inventory adjustment now lets the inner transaction commit inside the outer error-mapping boundary, so a commit-time optimistic-lock exception becomes `INVENTORY_ADJUSTMENT_CONFLICT` and rolls back.
- The two Flyway tests now expect schema version 14 and the fresh-database test verifies the product-view tables/index added by V14.

Verification run after the fix:

```powershell
cd C:\dev\study\sweet-market-m30\backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```

Result: `BUILD SUCCESSFUL` in about four minutes.

## Performance Evidence Still Required

M30 is functionally verified, but it does not yet have representative load-test evidence. Do not claim p50/p95, throughput, error rate, cache hit rate, or cache-off/cache-on benefit until the following are completed:

1. Start with an empty local database and seed a reproducible fixture containing active business stores, promotions, coupon campaigns, wishlist rows, and seven-day product-view rows.
2. Install or otherwise make `k6` available, then run `performance/m30-catalog-reads.js` unchanged in both cache-off and cache-on modes.
3. Provide authorized local Actuator metric access and capture `discovery.jdbc.statements`, discovery timers, and Caffeine statistics.
4. Capture the actual logged SQL with binds and rerun full `EXPLAIN (ANALYZE, BUFFERS)` for global catalog, fixed-store catalog, events, and popularity queries.

The original measurement observations and exact limitations are retained in `docs/superpowers/handoffs/2026-07-16-milestone-30-high-traffic-catalog-reads-handoff.md`.

## Start With M31

M31 should consume only real measured M30 signals. Its operator dashboard may display campaign/coupon/inventory/order data now, but catalog latency, statement counts, and cache effectiveness must remain unavailable or clearly marked unmeasured until the performance-evidence steps above are complete.

Preserve all M29 write invariants: durable purchase idempotency, conditional inventory reservation, deterministic cart locks, and exactly-once coupon/stock compensation.
