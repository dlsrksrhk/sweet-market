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
- JDK 21 backend: 846 tests across 127 suites passed in 4m16s with no failures/errors/skips.
- Web: 53 tests passed in 26.93s; the 165-module production build completed in 1.70s.
- Evidence-tool and artifact integrity checks passed.
- Rendered OUTSIDER/OWNER/MANAGER/ADMIN desktop flows passed. Store/admin mobile document roots remained `375/375`, the desktop root remained `1265/1265`, and only the admin run-list table used intentional internal horizontal scrolling.
- ADMIN rendered run 4 as valid/comparable with OFF/ON evidence and projector recovery/privacy controls, without browser-side SQL execution controls.
- Browser QA found nullable cache counters blanking the ADMIN route; TDD commit `0f41e9c` renders them as `측정값 없음`, and the desktop/mobile retest passed.
- Final review hardened the event boundary in commits `4825671` and `0a18346`: unknown stored types become first-attempt DEAD rows without blocking valid rows, while `JdbcOperationalEventRecorder` rejects producer-side `UNKNOWN`. Projection tests passed 26/26 and recorder tests passed 6/6.

## Evidence-backed deferred candidates only

1. **Route-level web code splitting.** Vite reports the main minified chunk at 592.83 kB (165.46 kB gzip), above its 500 kB advisory threshold.
2. **Active-event and popularity SQL work followed by remeasurement.** The live plans record roughly 69/66 ms and 95/91 ms OFF/ON execution and 107,767/85,817 shared-hit blocks. Any optimization must preserve the exact fixture/workload/provenance gate and be followed by a new UUID, not overwrite or relabel run 4.
3. **Production-context capacity measurement before production claims.** The registered comparison is valid for its recorded Windows/Docker/PostgreSQL 17/Redis 7.4/JDK 21 environment and hardware; it is not a fixed production RPS guarantee.

Daily projection rollups, Kafka transport, distributed cache, search infrastructure, warehouse inventory, multi-store checkout, and autonomous campaign optimization are not promoted here: the current evidence does not establish an immediate requirement. Revisit them only when a measured constraint or new approved product phase supplies that evidence.

## Preservation notes

- Preserve the M29 purchase/coupon/inventory invariants and the source-of-truth authorization boundaries.
- Preserve `trackingStartedAt` semantics when changing projection retention or bootstrap.
- Preserve the measurement artifacts under `performance/results/m30-v1` and distinguish historical superseded runs from the authoritative run 4.
- Preserve unrelated primary-worktree documents and the Task 2-4 scratch reports; they are not Task 14 cleanup targets.
- The browser walkthrough used test-only store-1 memberships (member 2 OWNER, member 13 MANAGER) in the local performance database; do not treat them as repository fixture data.
- Use the M31 verification report and milestone handoff as the starting evidence for any next design.
