# Post-Milestone 25 Next Session Handoff

## Current State

- Milestone 25 (Store Promotions And Price Policy) implementation is complete on `codex/milestone-25-completion`.
- The branch is based on `main` commit `ef7b3a4` and currently contains these M25 completion commits:

```text
aa2d169 feat: expose promotion prices to buyer reads
c59c160 feat: use effective prices in catalog search
7fc12e3 feat: add promotion workspace and buyer prices
14f9243 test: verify promotion pricing compatibility
```

- These commits have **not** been merged into `main` or pushed. Merge and push this branch before starting M26.
- `main` already contains M25 Tasks 1–3 and the test-pool configuration through `ef7b3a4`.
- Keep the main checkout's pre-existing local-only changes untouched:
  - `backend/src/main/resources/application.yaml`
  - `docs/superpowers/handoffs/2026-07-08-post-milestone-18-next-session-handoff.md`

## M25 Delivered

- Active BUSINESS-store OWNERs can create and operate selected-product or store-wide automatic promotions.
- Buyers see list, promotion discount, and effective prices in product detail, storefront, cart, legacy cards, and catalog cards.
- Catalog price filters, price sorts, and signed keyset cursors use effective price without an N+1 promotion read.
- Orders snapshot list price, optional campaign id, discount amount, and final price; payment, settlement, and refund reads retain the historical money values.
- `/me/store/promotions` provides the business-owner promotion workspace.
- Test-only Hikari maximum pool size is permanently set to 4 in `backend/src/test/resources/application.yaml`, preventing the previous PostgreSQL Testcontainer connection-limit failures.

Detailed implementation, query-plan, and verification evidence is in:

```text
docs/superpowers/handoffs/2026-07-14-milestone-25-store-promotions-and-price-policy-handoff.md
```

## Verification Already Completed

With JDK 21 at `C:\Users\kdh\.jdks\corretto-21.0.7` and `JWT_SECRET=sweet-market-local-test-secret-key-32bytes-minimum`:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --rerun-tasks

cd ..\web
npm run build
```

Results: backend `BUILD SUCCESSFUL` with 572 tests, 0 failures, 0 errors, and 0 skipped; web TypeScript/Vite build passed; `git diff --check` passed.

## Required First Steps Next Session

1. Confirm the completion worktree is clean and inspect its four commits.
2. Merge `codex/milestone-25-completion` into `main`, run a post-merge verification appropriate to the merge, and push `main`.
3. Start M26 in a new isolated `codex/` worktree; do not reuse the M25 completion worktree for feature edits.
4. Read the M25 handoff and the M26 section of `docs/superpowers/roadmaps/2026-07-10-hybrid-used-marketplace-milestone-21-31-roadmap.md`.
5. Run brainstorming, save and review an M26 design, then write the implementation plan before coding.

## Recommended Next Goal: M26 Coupon Campaigns And Standard Coupon Issuance

Build platform-owned and store-owned coupon campaigns, a once-per-member claim flow, and a buyer coupon wallet. Store and platform ownership must remain distinct; normal M26 issuance has no global capacity limit and does not redeem coupons during checkout.

Key constraints for the M26 design:

- Enforce `unique (coupon_campaign_id, member_id)` in the database and return a stable idempotent repeated-claim result.
- A store coupon belongs to exactly one store; a platform coupon has no store owner.
- Keep issued coupons as history while separately determining their current usability.
- Use paged projection queries for wallet and owner-management lists.
- Do not add first-come capacity (M27), checkout redemption/stacking (M28), or coupon allocation across cart items.

## Notes For The Next Agent

- Follow `AGENTS.md`; new JUnit test names use Korean with underscores.
- Use JDK 21. This machine's available JDK is `C:\Users\kdh\.jdks\corretto-21.0.7`, not the stale `C:\java\jdk-21` path.
- `backend/src/test/resources/application.yaml` already constrains Hikari to four connections; do not remove it unless the integration-test strategy changes deliberately.
- The order price snapshot stores campaign ID but not a campaign title snapshot; title is a live buyer read-model field.
- Preserve the one-product-per-order boundary until the roadmap explicitly changes it.
