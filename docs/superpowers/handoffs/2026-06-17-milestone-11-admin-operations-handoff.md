# Milestone 11 Admin Operations Handoff

## Current Project State

Sweet Market has completed Milestones 1 through 10.

The current `main` branch is pushed to `origin/main` through:

```text
ae35d75 feat: add admin settlement operations UI
```

Recent completed milestones:

- Milestone 9 added automatic purchase confirmation and scheduler/admin trigger support.
- Milestone 10 added admin settlement operations: settlement search, settlement detail, and one-order settlement retry.
- The admin web route remains `/admin/batches/settlements`, now serving as a compact operations console for auto confirmation, settlement batch execution, settlement search, retry, execution history, and execution detail.

Important local note:

- `backend/src/main/resources/application.yaml` has an existing local-only uncommitted change for development convenience.
- Do not stage or overwrite that file unless the user explicitly asks.

## Required Project Rules

Backend commands should use JDK 21.

On this PC, the verified JDK 21 path is:

```powershell
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

The project docs also mention `C:\java\jdk-21`; verify the path before running.

Backend tests need:

```powershell
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
```

New JUnit `@Test` method names must be Korean_with_underscores.

## Roadmap Position

The roadmap is documented at:

```text
docs/superpowers/specs/2026-06-15-post-milestone-8-roadmap-design.md
```

The relevant remaining roadmap is:

```text
Milestone 11 -> admin operations features
Milestone 12 -> seller reports and statistics
Optional     -> frontend usability polish
```

Milestone 11 is the recommended next milestone because Milestone 10 finished the settlement-specific admin surface. The next useful learning step is broader admin read/write operations across products, orders, and members while keeping domain invariants intact.

## Recommended Next Session Goal

Start Milestone 11: Admin Operations Features.

Recommended first steps in a new chat:

1. Read this handoff.
2. Read `docs/superpowers/specs/2026-06-15-post-milestone-8-roadmap-design.md`.
3. Read the Milestone 10 spec and plan to understand the current admin operations style:
   - `docs/superpowers/specs/2026-06-16-milestone-10-settlement-operations-design.md`
   - `docs/superpowers/plans/2026-06-16-milestone-10-settlement-operations.md`
4. Use Superpowers brainstorming before writing the Milestone 11 spec.
5. Decide the first admin slice before planning implementation.

## Milestone 11 Intent

Milestone 11 should add practical admin-facing operations that inspect core market state without direct database access.

Roadmap scope:

- Admin product lookup and status inspection.
- Admin order lookup and lifecycle inspection.
- Admin member lookup by email or nickname.
- Admin-only safe operational actions for a learning project, such as hiding inappropriate products or viewing order detail.
- Clear authorization tests for admin/member/anonymous access.
- Web admin pages that use the APIs without building deep operational infrastructure.

Out of scope:

- Admin role management UI.
- User suspension policy depth.
- Full moderation workflow.
- Audit compliance or legal logs.
- Customer support messaging.

## Recommended Milestone 11 Shape

Start with a narrow, practical admin console rather than a full admin product.

Recommended first slice:

```text
Admin product/order/member search + detail,
with one safe write action: hide a product.
```

Why this slice fits:

- Product/order/member lookup covers the roadmap's core admin read models.
- Product hiding reuses existing product visibility concepts and is safer than changing orders or member roles.
- Order detail can expose lifecycle context without adding risky state transitions.
- Member lookup can stay read-only and avoid suspension/role complexity.
- The current `/api/admin/**` security boundary and `RequireAdmin` web route pattern already exist.

Possible backend API boundary:

```text
GET  /api/admin/products
GET  /api/admin/products/{productId}
POST /api/admin/products/{productId}/hide

GET  /api/admin/orders
GET  /api/admin/orders/{orderId}

GET  /api/admin/members
GET  /api/admin/members/{memberId}
```

Keep filters practical and small:

- Products: `sellerId`, `status`, `keyword`, `page`, `size`
- Orders: `buyerId`, `sellerId`, `status`, `productId`, `page`, `size`
- Members: `email`, `nickname`, `role`, `page`, `size`

## Areas To Inspect

Backend:

```text
backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java
backend/src/main/java/com/sweet/market/product
backend/src/main/java/com/sweet/market/order
backend/src/main/java/com/sweet/market/member
backend/src/main/java/com/sweet/market/settlement/admin
backend/src/test/java/com/sweet/market/auth/AdminSecurityApiTest.java
backend/src/test/java/com/sweet/market/settlement/admin/AdminSettlementApiTest.java
```

Frontend:

```text
web/src/app/router.tsx
web/src/shared/layout/Shell.tsx
web/src/features/auth/RequireAdmin.tsx
web/src/features/admin/adminBatchApi.ts
web/src/pages/AdminSettlementBatchPage.tsx
web/src/shared/styles.css
```

Existing admin patterns to reuse:

- Backend controllers under `/api/admin/**`.
- Existing Spring Security rule: `/api/admin/**` requires admin role.
- MockMvc authorization coverage for admin/member/anonymous access.
- Frontend admin route guarded by `RequireAdmin`.
- TanStack Query API client style in `web/src/features/admin/adminBatchApi.ts`.
- Dense admin panel styling in `web/src/shared/styles.css`.

## Open Decisions For Next Session

Decide these before implementation:

- Should Milestone 11 be one combined admin page or separate admin routes?
- Should product hide be the only write action, or should the milestone stay read-only?
- Should member lookup search by partial email/nickname or exact values only?
- Should order detail include settlement and delivery context, or only order/product/buyer/seller state?
- Should the existing admin route `/admin/batches/settlements` be renamed or should a new `/admin` landing/index page be introduced?

Recommended default:

- Add a new admin operations page, for example `/admin/operations`, and keep `/admin/batches/settlements` as the settlement operations page.
- Keep Milestone 11 read-heavy with only product hide as the safe write action.
- Use DTO projections for lists and fetch joins/projections for details to avoid obvious N+1 behavior.

## Verification Commands

Backend:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
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

Expected git status before starting new work:

```text
## main...origin/main
 M backend/src/main/resources/application.yaml
```

The `application.yaml` change is pre-existing local state and should not be included in Milestone 11 commits.

## Suggested Acceptance Criteria

- Admin can search products, orders, and members with practical filters.
- Admin can inspect product, order, and member detail pages with enough context to understand current state.
- Non-admin and anonymous users cannot access the new admin APIs.
- Admin list endpoints avoid obvious N+1 behavior.
- Product hide, if included, is idempotent and respects domain invariants.
- Existing buyer, seller, settlement, and admin settlement batch flows still work.
- Backend tests pass with JDK 21 and `JWT_SECRET`.
- Web build passes.

## Suggested First Prompt For New Chat

```text
docs/superpowers/handoffs/2026-06-17-milestone-11-admin-operations-handoff.md 읽고 Milestone 11 슈퍼파워즈 brainstorming 시작해줘.
```
