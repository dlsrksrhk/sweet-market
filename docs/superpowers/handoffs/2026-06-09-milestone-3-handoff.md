# Sweet Market Handoff - 2026-06-09 - Milestone 3

## Current State

- Branch: `main`
- `main` is synced with `origin/main`.
- Latest commit: `ad51ddc fix: protect reserved products from seller mutation`
- Milestone 1 Foundation is merged.
- Milestone 2 Product Domain is merged.
- Milestone 3 Order Domain is implemented, locally merged into `main`, and pushed to `origin/main`.
- Existing uncommitted user-local change remains in `backend/src/main/resources/application.yaml`.

## Important Project Rule

All JUnit `@Test` method names must be Korean with underscores between words.

Example:

```java
@Test
void 상품_등록에_성공한다() throws Exception {
}
```

Do not add English camelCase test method names for test cases. Helper methods may remain English camelCase.

## Completed Work

Milestone 3 added:

- Order aggregate: `Order`, `OrderStatus`
- Order repository with entity graph loading for buyer and product response data
- Order create API: `POST /api/orders`
- Order cancel API: `POST /api/orders/{orderId}/cancel`
- Product status transition from `ON_SALE` to `RESERVED` on order create
- Product status restoration from `RESERVED` to `ON_SALE` on order cancel
- Product `@Version` field for optimistic locking
- Order-specific error codes
- Optimistic locking conflict response mapping to `ORDER_CONFLICT`
- Dirty checking JPA lab test
- Optimistic locking JPA lab test
- Persistence regression test allowing a product to be ordered again after a canceled order
- Regression guard preventing seller mutation of `RESERVED` products

## Key Decisions

- `Order.product` uses `@ManyToOne`, not `@OneToOne`.
  - Reason: a canceled order remains historical data, but the product returns to `ON_SALE` and must be orderable again.
  - A previous `@OneToOne(unique = true)` plan was corrected because it would block cancel-then-reorder at the database constraint level.
- `Product` owns reservation state transitions:
  - `reserve()` allows only `ON_SALE -> RESERVED`.
  - `restoreOnSaleFromReservation()` allows only `RESERVED -> ON_SALE`.
- Seller mutations are blocked while a product is `RESERVED`.
  - Blocked operations: product update, hide, image add, image remove.
  - API error: `PRODUCT_CHANGE_NOT_ALLOWED` with HTTP 409.
  - Reason: allowing `RESERVED -> HIDDEN` would make buyer order cancellation fail and leave an active order stuck.
- Optimistic locking is tested at the JPA lab level with two separate `EntityManager` instances and two transactions.
  - The assertion checks `RollbackException` caused by `OptimisticLockException`; Hibernate's stale object exception may appear as the root cause.

## Verification

Last verified from `C:\dev\jpa-study\backend` after merging into `main`:

```powershell
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Result:

```text
BUILD SUCCESSFUL
```

Test naming rule was checked with:

```powershell
rg -n "void [a-zA-Z0-9]+\(" backend\src\test\java
```

Only helper/lifecycle methods were reported:

```text
backend\src\test\java\com\sweet\market\jpalab\OptimisticLockTest.java:76:    private void rollbackIfActive(EntityTransaction transaction) {
backend\src\test\java\com\sweet\market\support\IntegrationTestSupport.java:39:    static void overrideProperties(DynamicPropertyRegistry registry) {
backend\src\test\java\com\sweet\market\support\IntegrationTestSupport.java:49:    void cleanUp() {
```

## Local Notes

- `backend/src/main/resources/application.yaml` has a user-local modification:
  - `spring.jpa.hibernate.ddl-auto` changed from `create-drop` to `update`
  - `jwt.secret` has a local default value
- Do not overwrite or revert that file unless the user explicitly asks.
- The Milestone 3 implementation was pushed to GitHub:

```text
origin/main -> ad51ddc fix: protect reserved products from seller mutation
```

- A detached Codex worktree may still exist outside the repo at:

```text
C:/Users/kdh/.codex/worktrees/856e/jpa-study
```

It is unrelated to the current pushed `main` state.

## Suggested Next Work

Next milestone from the design document is Milestone 4: Payment and Delivery.

Expected scope:

- fake `PaymentGateway`
- Payment approve API
- Payment cancel API
- Order status transition from `CREATED` to `PAID`
- fake `DeliveryClient`
- Delivery start API
- Delivery complete API
- Order status transitions through `SHIPPING` and `DELIVERED`
- Order state transition tests

Before implementing Milestone 4, create a plan under:

```text
docs/superpowers/plans/YYYY-MM-DD-milestone-4-payment-delivery.md
```

Use these source docs:

- `docs/superpowers/specs/2026-06-08-sweet-market-design.md`
- `docs/superpowers/plans/2026-06-09-milestone-3-order-domain.md`
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

Expected `git status` shape:

```text
## main...origin/main
 M backend/src/main/resources/application.yaml
```

The `application.yaml` modification is expected local state.
