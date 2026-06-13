# Sweet Market Handoff - 2026-06-13 - Milestone 7 Batch Expansion

## Current State

- Branch: `codex/milestone-7-batch-expansion`
- Remote: `origin` -> `https://github.com/dlsrksrhk/sweet-market.git`
- The branch is pushed to `origin/codex/milestone-7-batch-expansion`.
- Latest implementation commit before this handoff: `8c2e0eb fix: harden settlement batch runtime edges`
- Pull request URL:
  - `https://github.com/dlsrksrhk/sweet-market/pull/new/codex/milestone-7-batch-expansion`

## Important Project Rule

All JUnit `@Test` method names must be Korean with underscores between words.

Example:

```java
@Test
void 상품_등록에_성공한다() throws Exception {
}
```

Helper methods that are not test cases may stay English camelCase.

## Completed Work

Milestone 7 added Spring Batch based settlement automation:

- Added Spring Batch dependencies and Spring Batch test support.
- Added idempotent PostgreSQL Spring Batch metadata schema:
  - `backend/src/main/resources/schema-batch-postgresql.sql`
- Disabled automatic Batch job startup:
  - `spring.batch.job.enabled: false`
- Disabled Spring Batch's non-idempotent schema initializer:
  - `spring.batch.jdbc.initialize-schema: never`
- Enabled runtime SQL init for the idempotent Batch schema:
  - `spring.sql.init.mode: always`
  - `spring.sql.init.schema-locations: classpath:schema-batch-postgresql.sql`
- Added `MemberRole.ADMIN`.
- Added admin member factory.
- Added JWT role claim and role-aware authentication principal.
- Protected `/api/admin/**` with ADMIN authority.
- Added `Order.confirmedAt`, set when `Order.confirm()` succeeds.
- Added settlement Batch job:
  - `settlementJob`
  - `settlementStep`
  - `JdbcPagingItemReader<Long>` for order ids
  - `SettlementItemProcessor`
  - `SettlementItemWriter`
- Added admin settlement batch trigger API:
  - `POST /api/admin/batches/settlements`
  - Request fields: `confirmedBefore`, `limit`, `chunkSize`
- Added request bounds:
  - `limit <= 1000`
  - `chunkSize <= 100`
  - `chunkSize <= limit`
- Added structured batch launch failure response:
  - `ErrorCode.BATCH_LAUNCH_FAILED`
- Added idempotency and skip behavior tests.

## Key Decisions

- Normal settlement reader targets only:
  - `CONFIRMED` orders
  - `confirmed_at < confirmedBefore`
  - orders without an existing settlement
- `forcedOrderId` is a test-only job parameter used to make processor skip behavior deterministic.
  - It is not exposed through the admin API.
  - The forced reader path still requires `CONFIRMED` and `confirmed_at < confirmedBefore`.
- `DataAccessException` is retried but not broadly skipped.
- Business conflicts are represented as `SettlementBatchSkippableException`.
- Writer-time duplicate settlement races are handled with PostgreSQL `on conflict (order_id) do nothing`.
  - If no row is inserted, the writer throws `SettlementBatchSkippableException`.
  - Other database failures still use the retry/fail path.

## Verification

Last verified from `C:\dev\jpa-study\backend`:

```powershell
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.settlement.batch.SettlementBatchJobTest
```

Result: `BUILD SUCCESSFUL`

Focused milestone tests:

```powershell
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.auth.AdminSecurityApiTest --tests com.sweet.market.settlement.batch.* --tests com.sweet.market.order.domain.OrderTest
```

Result: `BUILD SUCCESSFUL`

Full backend test suite:

```powershell
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon cleanTest test
```

Result: `BUILD SUCCESSFUL`

Test naming rule was checked with:

```powershell
rg -n "void [a-zA-Z0-9]+\(" backend\src\test\java
```

Only helper or lifecycle methods were reported.

## Local Notes

- `backend/src/main/resources/application.yaml` has an expected user-local modification that is not committed:
  - `spring.jpa.hibernate.ddl-auto` changed from `create-drop` to `update`
  - `jwt.secret` has a local default value
- Do not overwrite or revert that file unless explicitly requested.
- Older untracked handoff still exists locally:
  - `docs/superpowers/handoffs/2026-06-09-milestone-3-handoff.md`
- That older Milestone 3 handoff is stale for the current Milestone 7 branch and was not pushed as the current handoff.

## Suggested Next Work

- Open a PR from `codex/milestone-7-batch-expansion`.
- Review the operational decision for existing historical `CONFIRMED` orders where `confirmed_at` is `null`.
  - If the target database is non-empty, add a backfill or migration decision before relying on the batch reader.
- After PR review, merge into `main` and re-run the backend test suite on the merged result.

## Recommended First Commands In A New Thread

```powershell
cd C:\dev\jpa-study
git status --short --branch
git log --oneline -12
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```
