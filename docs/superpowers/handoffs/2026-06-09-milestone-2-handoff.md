# Sweet Market Handoff - 2026-06-09

## Current State

- Branch: `main`
- Main is ahead of `origin/main` by 9 commits.
- Milestone 1 Foundation is merged.
- Milestone 2 Product Domain is implemented and merged locally into `main`.
- Latest commit: `4bf5049 test: localize test case names`
- Existing uncommitted user-local change remains in `backend/src/main/resources/application.yaml`.

## Important Project Rule

All JUnit `@Test` method names must be Korean with underscores between words.

Example:

```java
@Test
void 상품_등록에_성공한다() throws Exception {
}
```

Do not use English camelCase names for test cases. Helper methods may remain English camelCase.

## Completed Work

Milestone 2 added:

- Product aggregate: `Product`, `ProductImage`, `ProductStatus`
- Product repositories
- Product create/update/hide APIs
- Product image add/remove APIs
- Public product list/detail query APIs
- Product ownership checks
- Product-specific error codes
- Cascade persist and orphan removal JPA lab test
- Korean test case names across the backend test suite

## Verification

Last verified from `C:\dev\jpa-study\backend`:

```powershell
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Result:

```text
BUILD SUCCESSFUL
```

Boot verification was also done with `JWT_SECRET`. Port `8080` was occupied by an IntelliJ-run MarketApplication, so Codex verified with `--server.port=0`.

## Local Notes

- `backend/src/main/resources/application.yaml` has a user-local modification:
  - `spring.jpa.hibernate.ddl-auto` changed from `create-drop` to `update`
  - `jwt.secret` has a local default value
- Do not overwrite or revert that file unless the user explicitly asks.
- The previous Codex feature worktree was removed from Git worktree registration. A locked empty directory may remain under `.codex/worktrees/ac45` because the old Codex session held the path.

## Suggested Next Work

Next milestone from the design document is Milestone 3: Order Domain.

Expected scope:

- Order creation
- Order cancel
- Product status transition from `ON_SALE` to `RESERVED`
- Product status restoration on order cancel
- Dirty checking lab
- Optimistic locking lab for concurrent ordering of the same product

Before implementing Milestone 3, create a plan under:

```text
docs/superpowers/plans/YYYY-MM-DD-milestone-3-order-domain.md
```

Use these source docs:

- `docs/superpowers/specs/2026-06-08-sweet-market-design.md`
- `docs/superpowers/plans/2026-06-09-milestone-2-product-domain.md`
- This handoff file

## Recommended First Commands In A New Thread

```powershell
cd C:\dev\jpa-study
git status --short --branch
git log --oneline -12
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```
