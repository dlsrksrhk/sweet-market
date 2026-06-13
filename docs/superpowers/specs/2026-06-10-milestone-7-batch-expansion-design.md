# Milestone 7 Batch Expansion Design

## Goal

Milestone 7 introduces the first production-shaped batch workflow in Sweet Market.

The feature goal is that an administrator can trigger automatic settlement creation for confirmed orders through a protected admin API. The learning goal is to practice Spring Batch job structure, job parameters, chunk processing, retry/skip behavior, and role-based access control around an operational action.

## Design Direction

Milestone 7 should be redesigned from a simple service batch into a Spring Batch based operational flow.

Recommended flow:

```text
ADMIN login
-> POST /api/admin/batches/settlements
-> AdminBatchController
-> JobLauncher
-> settlementJob
-> settlementStep
-> read unsettled confirmed order ids
-> create settlements in chunks
-> retry failed items
-> skip items that keep failing
-> return JobExecution metadata
```

This milestone does not add a scheduler. A manual admin trigger gives the project a realistic operations entrypoint without adding recurring execution concerns yet. A scheduler can later call the same job with computed parameters.

## Scope

In scope:

- Add Spring Batch dependencies and configuration.
- Add Spring Batch metadata schema support for tests and local runtime.
- Add `MemberRole.ADMIN`.
- Include member role in JWT claims and authenticated principal.
- Restrict `/api/admin/**` to ADMIN members.
- Add an admin-only settlement batch trigger API.
- Add a `settlementJob` and `settlementStep`.
- Accept `confirmedBefore`, `limit`, and `chunkSize` as job input.
- Add `Order.confirmedAt` and set it when an order is confirmed.
- Read only confirmed, unsettled orders confirmed before `confirmedBefore`.
- Create missing settlements in chunks.
- Retry item processing failures.
- Skip items that still fail after retry.
- Return job execution id, job name, status, and submitted parameters.
- Test unauthorized, non-admin, and admin access.
- Test idempotent re-execution.
- Test retry/skip behavior at the batch level.

Out of scope:

- `@Scheduled` recurring execution.
- Admin account creation API.
- Admin UI.
- Distributed lock.
- Failure detail table.
- Reprocessing UI.
- Settlement fee calculation.
- Settlement cancellation.
- External settlement provider integration.

## Authorization Model

`MemberRole` should become:

```text
MEMBER
ADMIN
```

Normal signup continues to create `MEMBER` accounts only. Do not expose public admin signup.

Admin users for tests can be created through repository/entity fixtures. This keeps admin creation out of the public API surface while allowing integration tests to authenticate as an admin.

JWT should include the role claim. The authenticated principal should carry:

```text
id
email
role
```

Spring Security should enforce:

```text
/api/admin/** -> hasRole("ADMIN")
existing protected APIs -> authenticated
auth signup/login -> permitAll
public product reads -> permitAll
```

Expected security behavior:

- Missing JWT on admin API returns `401`.
- MEMBER JWT on admin API returns `403`.
- ADMIN JWT on admin API can launch the settlement batch.

## Admin API

Endpoint:

```text
POST /api/admin/batches/settlements
```

Authentication:

- Required.
- ADMIN only.

Request:

```json
{
  "confirmedBefore": "2026-06-10T00:00:00",
  "limit": 100,
  "chunkSize": 20
}
```

Validation:

- `confirmedBefore` is required.
- `limit` must be positive.
- `chunkSize` must be positive.
- `chunkSize` should not be greater than `limit`.

Response:

```json
{
  "data": {
    "jobExecutionId": 1,
    "jobName": "settlementJob",
    "status": "STARTING",
    "parameters": {
      "confirmedBefore": "2026-06-10T00:00:00",
      "limit": 100,
      "chunkSize": 20
    }
  }
}
```

In tests, use a synchronous job launcher or equivalent configuration so completed executions can be asserted deterministically. In normal runtime, the API may return immediately after launching with `STARTING` or `STARTED`.

## Batch Job

Job name:

```text
settlementJob
```

Step name:

```text
settlementStep
```

Step shape:

```text
ItemReader<Long>      reads unsettled confirmed order ids
ItemProcessor<Long, Settlement> loads order and creates settlement
ItemWriter<Settlement> saves settlements
```

Reader query:

```sql
select o.id
from orders o
where o.status = 'CONFIRMED'
  and o.confirmed_at < :confirmedBefore
  and not exists (
    select 1
    from settlements s
    where s.order_id = o.id
  )
order by o.id asc
limit :limit
```

Use `JdbcPagingItemReader<Long>` for the first implementation. It keeps the reader inside Spring Batch infrastructure, makes `confirmedBefore` and `limit` explicit job parameters, and avoids hiding paging behavior behind a service method.

Processor behavior:

- Load the order with product and seller.
- If the order is no longer eligible, skip it through a batch exception type.
- Create `Settlement` through the existing domain factory.

Writer behavior:

- Save settlements through `SettlementRepository`.
- Rely on `settlements.order_id` unique constraint as the final idempotency guard.

## Domain Changes

`Order` needs a confirmed timestamp:

```text
confirmedAt
```

`Order.confirm()` should set `confirmedAt` when it transitions from `DELIVERED` to `CONFIRMED`.

This field makes settlement batch policy explicit: the system can settle only orders confirmed before a given cutoff. Without it, `confirmedBefore` would have to rely on `orderedAt`, which is not the same business event.

## Idempotency

The job should be safe to run repeatedly with the same parameters.

Idempotency layers:

1. Candidate query excludes orders that already have settlements.
2. Writer saves settlements with the existing unique `order_id` constraint.
3. Duplicate settlement exceptions are treated as skippable item failures, not whole-job failures.

Expected behavior:

- First run creates settlements for eligible orders.
- Second run with the same parameters reads no already settled orders.
- If a race creates a settlement after the reader selected an order, the unique constraint prevents duplication and the item can be skipped.

## Retry And Skip

Failure policy:

```text
retry item failures up to a small limit
skip item when retries are exhausted
continue processing remaining items
```

Recommended default:

```text
retryLimit = 3
```

Skippable situations:

- Duplicate settlement detected.
- Order no longer eligible.
- Domain factory rejects settlement creation.
- A retried item still fails after retry exhaustion.

This milestone should not persist per-item failure details. Spring Batch metadata is enough for the first introduction. A future milestone can add a failure table or operational report.

## Testing Strategy

Security tests:

- JWT 없는 관리 API 호출은 `401`.
- MEMBER가 관리 API를 호출하면 `403`.
- ADMIN이 관리 API를 호출하면 job launch response를 받는다.

Batch tests:

- Eligible confirmed orders before cutoff are settled.
- Confirmed orders after cutoff are not settled.
- Non-confirmed orders are not settled.
- Already settled orders are not settled again.
- Running the same job twice is idempotent.
- A failing item is retried and then skipped while other items continue.
- Step execution exposes expected read/write/skip counts.

Domain tests:

- `Order.confirm()` sets `confirmedAt`.
- Non-confirmed orders have no `confirmedAt`.

Regression tests:

- Existing auth, product, order, payment, delivery, settlement API tests still pass.
- JUnit `@Test` method names remain Korean with underscores.

## Relationship To Existing Plan

The earlier plan at `docs/superpowers/plans/2026-06-10-milestone-7-batch-expansion.md` describes a simpler service-based batch. This design supersedes that direction.

After this design is approved, write a new implementation plan or replace the existing plan so that Milestone 7 follows the Spring Batch and ADMIN API architecture described here.

## Acceptance Criteria

- Admin role exists and is enforced for `/api/admin/**`.
- Admin can launch settlement batch through the API.
- Settlement batch uses Spring Batch job/step infrastructure.
- Job parameters include `confirmedBefore`, `limit`, and `chunkSize`.
- Batch creates settlements only for eligible confirmed unsettled orders.
- Batch retry/skip behavior is covered by tests.
- Re-running the job does not create duplicate settlements.
- Full backend test suite passes.
