# Demo Data Timeline Design

## Goal

Refresh the local/dev demo data so admin operations, settlement operations, and batch flows feel substantial when the database tables have been removed and recreated.

The data should be generated relative to the day the app starts, cover the previous 180 days, and include enough variety for product, order, delivery, payment, and settlement screens to show meaningful operational state.

## Context

`DemoDataInitializer` currently runs only in `local` and `dev` profiles and skips seeding when `admin@example.com` already exists. That behavior should stay.

The intended workflow is:

1. The user removes the database tables manually.
2. The app starts with `local` or `dev`.
3. The initializer sees that `admin@example.com` does not exist.
4. A full deterministic demo dataset is created.
5. Later app restarts do not duplicate the dataset.

Recent admin and settlement milestones need denser data than the current small seed. The admin console benefits from many products, orders, members, and statuses. Settlement and batch screens benefit from confirmed orders, settled orders, unsettled eligible orders, recent confirmed orders, old delivered orders, and a few exceptional records.

## Decisions

- Keep the existing seed trigger: if `admin@example.com` exists, skip all seeding.
- Do not add database cleanup, reset endpoints, or destructive startup behavior.
- Generate a large deterministic dataset, not fully random data.
- Use the startup date as the anchor date via `LocalDate.now()`.
- Spread business events across the previous 180 days.
- Create domain state through existing entity methods where possible.
- Use `JdbcTemplate` only to backfill past timestamps and small demo-only status adjustments that domain methods cannot express directly.
- Scope this work to backend demo data and tests.
- Do not change the web UI.
- Do not modify `backend/src/main/resources/application.yaml`.

## Data Shape

The initializer should create a large but still local-friendly dataset:

- 1 admin account.
- About 10 seller accounts.
- About 24 buyer accounts.
- At least 120 products.
- At least 240 orders.
- Payments and deliveries matching the order lifecycle.
- A broad settlement set across multiple sellers.

All demo accounts should keep predictable credentials suitable for local testing. Existing well-known accounts such as `admin@example.com`, `seller1@example.com`, and `buyer1@example.com` should remain available.

## Product Data

Products should be distributed across sellers, categories, prices, and statuses.

Required product statuses:

- `ON_SALE`
- `RESERVED`
- `SOLD_OUT`
- `HIDDEN`

The dataset should include enough hidden products to make admin product filtering useful, and enough on-sale products to keep the buyer-facing market page populated.

Product titles and descriptions should be deterministic but varied. Category-like title groups can be simple local demo themes such as electronics, kitchen, fashion, books, sports, home, hobby, and office.

## Order Timeline

Orders should span the previous 180 days from the startup date.

Required order statuses:

- `CREATED`
- `PAID`
- `SHIPPING`
- `DELIVERED`
- `CONFIRMED`
- `CANCELED`

The target distribution should be normal-operation heavy, with visible edge cases:

- Many confirmed orders for settlement history.
- Some confirmed orders old enough to be eligible for settlement but still unsettled.
- Some recently confirmed orders that should not yet be settled.
- Some delivered orders older than the automatic purchase confirmation threshold.
- Some recent delivered orders that are not yet auto-confirmation candidates.
- Some canceled orders for admin order filtering.
- Some created/paid/shipping orders for active lifecycle visibility.

## Payment, Delivery, and Settlement Data

Payment and delivery rows should match the generated order states.

Examples:

- `PAID`, `SHIPPING`, `DELIVERED`, and `CONFIRMED` orders should have approved payment data.
- `SHIPPING`, `DELIVERED`, and `CONFIRMED` orders should have delivery data matching their lifecycle.
- `CANCELED` orders should have cancellation context where the domain model supports it.

Settlements should make the settlement operations page feel real:

- Completed settlements across many sellers and dates.
- Unsettled confirmed orders that the settlement batch can pick up.
- Recent confirmed orders that remain intentionally ineligible.
- A small number of `READY` or `FAILED` settlement rows for admin search and status filtering, created carefully through deterministic post-processing if the domain factory only creates completed settlements.

Normal completed settlement data should remain the dominant case.

## Timestamp Strategy

Domain methods currently set many lifecycle timestamps with the current time. To create a realistic historical demo timeline:

1. Create entities through repositories and domain methods so relationships and statuses are valid.
2. Flush or save enough to obtain entity identifiers.
3. Use `JdbcTemplate` updates to backfill deterministic timestamps.

Columns likely requiring backfill include:

- `orders.ordered_at`
- `orders.canceled_at`
- `orders.confirmed_at`
- `payments.approved_at`
- `payments.canceled_at`
- `deliveries.started_at`
- `deliveries.completed_at`
- `settlements.settled_at`

The backfilled timeline should preserve natural ordering. For example, payment approval should not be earlier than order creation, delivery completion should not be earlier than delivery start, and settlement should not be earlier than order confirmation.

## Components

Keep the implementation inside the existing demo initializer boundary.

Recommended structure:

- `DemoDataInitializer` keeps the `ApplicationRunner` entry point and seed guard.
- Small helper methods create members, products, order scenarios, and settlements.
- A deterministic scenario builder assigns sellers, buyers, products, dates, and statuses.
- A timestamp backfill helper centralizes direct SQL updates.

Avoid introducing a separate seed framework unless the initializer becomes difficult to read. The first implementation should stay close to existing project patterns.

## Error Handling and Safety

The initializer must not drop, truncate, or delete data.

If `admin@example.com` exists, the initializer returns without creating anything. This keeps repeated local starts idempotent.

If seeding fails partway through, the application startup should fail normally rather than swallowing the exception. A partial seed can be handled by the user removing tables again and restarting.

The direct SQL timestamp updates should use parameter binding through `JdbcTemplate`, not string-built SQL.

## Testing Plan

Extend `DemoDataInitializerTest` to cover the larger deterministic seed.

Backend tests should verify:

- Running the initializer once creates a large dataset.
- Running the initializer twice does not duplicate data.
- Required demo accounts exist.
- Member, product, order, payment, delivery, and settlement counts meet minimum thresholds.
- Product statuses include `ON_SALE`, `RESERVED`, `SOLD_OUT`, and `HIDDEN`.
- Order statuses include `CREATED`, `PAID`, `SHIPPING`, `DELIVERED`, `CONFIRMED`, and `CANCELED`.
- Order dates span a meaningful range within the previous 180 days.
- Delivered orders include automatic purchase confirmation candidates.
- Confirmed orders include settled and unsettled examples.
- Settlements include enough completed records for the admin settlement table.
- Any `READY` or `FAILED` settlement examples are present only in small quantities.

New JUnit `@Test` method names must use Korean_with_underscores.

Verification command:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Also run:

```powershell
git diff --check
git status --short --branch --untracked-files=all
```

## Acceptance Criteria

- Empty local/dev tables can be repopulated by starting the app.
- Existing seeded databases are not duplicated on restart.
- Demo data is deterministic and anchored to the startup date.
- Data covers the previous 180 days.
- Admin operations pages have enough products, orders, members, and statuses to demonstrate filtering and detail views.
- Settlement operations have completed, eligible-unsettled, recent-ineligible, and small exceptional examples.
- Automatic purchase confirmation can find old delivered candidates.
- Backend tests pass with JDK 21 and `JWT_SECRET`.
- No web UI changes are required.

## Self-Review

- The scope is limited to local/dev demo data and tests.
- The design keeps destructive reset behavior out of application startup.
- The timestamp strategy explains why direct SQL is needed and where it is allowed.
- The data shape is large enough for admin and settlement demos without becoming production-like fixture infrastructure.
- The plan preserves existing seed idempotency through the admin account guard.
