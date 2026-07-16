# Post-Milestone 29 Next Session Handoff

## Repository State

- `main` is pushed to `origin/main` at `92509bb` (`docs: hand off milestone 29 inventory reservation`).
- M29 implementation, concurrency verification, and web handling are complete; begin new work from `main`.
- There is one unrelated local untracked file: `docs/superpowers/plans/2026-07-13-milestone-24-catalog-discovery.md`. Preserve it unless the user explicitly asks to handle M24 planning.

## Start With M30

M30 is the next milestone. Begin it in a fresh worktree with design and planning; do not expand M29 incidentally.

## Invariants That M30 Must Preserve

- Purchase endpoints require a non-blank `Idempotency-Key`; replay returns the originally persisted response.
- A reused key with a different fingerprint remains a conflict, and an active identical request remains `409 ORDER_REQUEST_IN_PROGRESS`.
- Single-item and stock reservations must remain conditional database updates. Do not pre-check availability and then update.
- Cart purchase keeps the ascending store/product lock order and preserves cart rows with per-item failure reasons on unsuccessful checkout.
- Coupon and stock compensation occurs once only. Losing coupon requests must not consume a coupon.
- Operator catalog inventory fields are `totalQuantity`, `reservedQuantity`, and `availableQuantity`; buyer availability must be based on available quantity.

## Verification Note

M29’s backend suite has one documented non-M29 residual: storefront query optimization expects an on-sale count of 20 and observes 22. Investigate it separately; do not change M29 reservation logic to make that expectation pass.

M29-focused verification passed:

- Backend focused suite: 76 tests, 0 failures.
- Web: `npm run build` passed (the existing Vite chunk-size warning remains).
- Manual API QA: a repeated direct-purchase request with the same key replayed the original `201 CREATED` order response.

For a full backend run, use JDK 21 and the local JWT secret:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```

Start local services when needed:

```powershell
cd backend
docker compose up -d
```
