# Post Milestone 8 Roadmap Handoff

## Current Project State

Sweet Market has completed Milestones 1 through 8.

The current shape is:

- Backend: Spring Boot/JPA market service in `backend`.
- Frontend: Vite React TypeScript app in `web`.
- Main branch includes Milestone 8 web market experience.
- Local demo seed data exists for `local` and `dev` profiles.
- Admin settlement batch launch and history screens exist.
- Seller, buyer, and admin login flows are available in the web app.

Important local note:

- `backend/src/main/resources/application.yaml` may have user-local uncommitted changes for local development. Inspect before staging.

## Required Project Rules

Backend commands should use JDK 21:

```powershell
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

Backend tests need:

```powershell
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
```

New JUnit `@Test` method names must use Korean_with_underscores.

## Roadmap Document

The future roadmap is documented at:

```text
docs/superpowers/specs/2026-06-15-post-milestone-8-roadmap-design.md
```

It defines the recommended post-Milestone 8 direction:

```text
Milestone 9  -> automatic purchase confirmation and scheduler basics
Milestone 10 -> settlement operations refinement
Milestone 11 -> admin operations features
Milestone 12 -> seller reports and statistics
Optional     -> frontend usability polish
```

The roadmap intentionally excludes deep production infrastructure such as deployment, Kubernetes, observability stacks, and distributed systems depth. The goal is still practical JPA learning.

## Recommended Next Session Goal

Start Milestone 9: Automatic Purchase Confirmation And Scheduler.

Recommended first task in a new session:

1. Read this handoff.
2. Read `docs/superpowers/specs/2026-06-15-post-milestone-8-roadmap-design.md`.
3. Read existing order, delivery, confirmation, settlement, and batch code.
4. Use Superpowers brainstorming or writing-plans as appropriate to create a Milestone 9 implementation plan before coding.

## Milestone 9 Intent

Milestone 9 should add a learning-friendly automatic purchase confirmation flow.

Candidate behavior:

- Find `DELIVERED` orders older than a configurable threshold.
- Confirm them automatically.
- Transition related product state consistently.
- Keep the operation idempotent.
- Run from a scheduler in local/dev.
- Optionally expose a manual admin trigger for demos.

The implementation should stay modest. Do not add Quartz, distributed locks, or external scheduler infrastructure unless explicitly requested later.

## Areas To Inspect For Milestone 9

Backend:

```text
backend/src/main/java/com/sweet/market/order
backend/src/main/java/com/sweet/market/delivery
backend/src/main/java/com/sweet/market/payment
backend/src/main/java/com/sweet/market/settlement
backend/src/main/java/com/sweet/market/settlement/batch
backend/src/test/java/com/sweet/market
```

Frontend:

```text
web/src/features/orders
web/src/features/deliveries
web/src/features/settlements
web/src/features/admin
web/src/pages/MyOrdersPage.tsx
web/src/pages/AdminSettlementBatchPage.tsx
```

Useful docs:

```text
docs/superpowers/specs/2026-06-13-milestone-8-web-market-experience-design.md
docs/superpowers/plans/2026-06-13-milestone-8-web-market-experience.md
docs/superpowers/handoffs/2026-06-13-milestone-8-web-demo-handoff.md
docs/jpalab-learning-guide.md
docs/learning-test-guide.md
```

## Verification Commands

Backend:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Web:

```powershell
cd web
npm run build
```

Git hygiene:

```powershell
git status --short --branch --untracked-files=all
git diff --check
```

## Suggested Milestone 9 Acceptance Criteria

- Delivered orders older than the threshold are automatically confirmed.
- Recent delivered orders are not confirmed.
- Confirmed, canceled, or otherwise ineligible orders are skipped.
- Re-running the scheduler does not duplicate effects.
- Existing manual confirmation still works.
- Backend tests cover threshold and idempotency cases.
- Web/admin demo can show that automatic confirmation happened.

## Open Decisions For Next Session

Decide these before implementation:

- Default confirmation threshold, for example 7 days after delivery completion.
- Whether scheduler should be active only in `local`/`dev` or also default profile.
- Whether to add a manual admin trigger endpoint for demo/testing.
- Whether automatic confirmation should immediately create settlement or leave that to the existing settlement batch.
- How much frontend surface is needed for Milestone 9.
