# Post-Milestone 31 next-session handoff

## Roadmap status

The M21-M31 hybrid used-marketplace roadmap is complete:

```text
M21 stores and business governance
 -> M22 storefront and core store console
 -> M23 sales policy and inventory
 -> M24 catalog discovery
 -> M25 promotions and price policy
 -> M26 standard coupon campaigns
 -> M27 first-come coupon concurrency
 -> M28 coupon redemption and order pricing
 -> M29 concurrent purchase and inventory reservation
 -> M30 high-traffic reads, cache, and real measurement
 -> M31 promotion and performance operations dashboard
```

Do not infer an M32 scope from this handoff. A future product phase needs its own evidence review, design, and plan.

## Verified baseline

- Store operators and administrators have separate, role-scoped operations dashboards.
- Operational projections reconcile to fixed KST source facts and remain replay-idempotent.
- Projection retry, DEAD inspection, and atomic rebuild are available to administrators.
- M30 measurement UUID `385b4525-21a2-4f4a-875f-364449f59957` is registered as run `4`, valid and comparable, with live SQL provenance.
- JDK 21 backend: a fresh `cleanTest test` run passed 852 tests across 127 suites in 4m49s with no failures/errors/skips. An immediately preceding complete run had two transient campaign-audit failures; the focused class and clean complete rerun passed, so no speculative code change was made.
- Web on Node `v22.14.0`/npm `10.9.2`: 13 files and 62 tests passed (Vitest 6.26s, wall clock 7.8s); the 166-module production build completed in 1.72s (wall clock 6.5s) with a 595.71 kB advisory chunk.
- Evidence-tool and artifact integrity checks passed.
- Rendered OUTSIDER/OWNER/MANAGER/ADMIN desktop flows passed. Store/admin mobile document roots remained `375/375`, the desktop root remained `1265/1265`, and only the admin run-list table used intentional internal horizontal scrolling.
- ADMIN rendered run 4 as valid/comparable with OFF/ON evidence and projector recovery/privacy controls, without browser-side SQL execution controls.
- Browser QA found nullable cache counters blanking the ADMIN route; TDD commit `0f41e9c` renders them as `측정값 없음`, and the desktop/mobile retest passed.
- Final review hardened the event boundary in commits `4825671` and `0a18346`: unknown stored types become first-attempt DEAD rows without blocking valid rows, while `JdbcOperationalEventRecorder` rejects producer-side `UNKNOWN`. Projection tests passed 26/26 and recorder tests passed 6/6.
- Final hardening commits `ac1ab16` and `c74cc50` classify tracking as `UNTRACKED`/`PARTIAL`/`TRACKED` and fail closed on unknown/error provenance, isolate unknown/schema rebuild rows while supported-handler runtime failures abort and the active generation survives, reuse supplied replay timestamps, and validate authoritative actual OFF/ON intervals within plus or minus 5 seconds (including 361/362 seconds).
- The final broad branch re-review reported no Critical or Important issues and marked the branch ready to merge.

## Evidence-backed deferred candidates only

1. **Route-level web code splitting.** Vite reports the main minified chunk at 595.71 kB, above its 500 kB advisory threshold.
2. **Active-event and popularity SQL work followed by remeasurement.** The live plans record roughly 69/66 ms and 95/91 ms OFF/ON execution and 107,767/85,817 shared-hit blocks. Any optimization must preserve the exact fixture/workload/provenance gate and be followed by a new UUID, not overwrite or relabel run 4.
3. **Production-context capacity measurement before production claims.** The registered comparison is valid for its recorded Windows/Docker/PostgreSQL 17/Redis 7.4/JDK 21 environment and hardware; it is not a fixed production RPS guarantee.
4. **True pending-backlog lag.** `projectionLagSeconds` is projection-activity age rather than the age of the oldest pending row. Continue to interpret it with queue health until a dedicated backlog lag metric is evidenced and implemented.
5. **HTTP-layer measurement body limit.** The 1 MiB canonical payload limit is checked after JSON parsing. Add a hard pre-materialization HTTP request-body limit at the production edge before treating this as complete resource protection.

Daily projection rollups, Kafka transport, distributed cache, search infrastructure, warehouse inventory, multi-store checkout, and autonomous campaign optimization are not promoted here: the current evidence does not establish an immediate requirement. Revisit them only when a measured constraint or new approved product phase supplies that evidence.

## Preservation notes

- Preserve the M29 purchase/coupon/inventory invariants and the source-of-truth authorization boundaries.
- Preserve `trackingStartedAt` semantics when changing projection retention or bootstrap.
- Preserve the measurement artifacts under `performance/results/m30-v1` and distinguish historical superseded runs from the authoritative run 4.
- Preserve unrelated primary-worktree documents and the Task 2-4 scratch reports; they are not Task 14 cleanup targets.
- The browser walkthrough used test-only store-1 memberships (member 2 OWNER, member 13 MANAGER) in the local performance database; do not treat them as repository fixture data.
- Use the M31 verification report and milestone handoff as the starting evidence for any next design.
