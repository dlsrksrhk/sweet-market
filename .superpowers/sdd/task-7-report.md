# Task 7 Report: Bootstrap and rebuild projection generations

## Outcome

- Added repeatable-read projection bootstrapping for the maximum 90-day KST window.
- Bootstrapped source-backed order, coupon claim, and current inventory facts without inferring historical coupon redemption, failures, or campaign audits.
- Added durable generation rebuild orchestration with non-derivable replay, high-water replay, exclusive advisory-lock cutover, atomic activation, and failure preservation.
- Closed PostgreSQL sequence/commit-order gaps by recording which outbox rows were visible to the bootstrap snapshot and replaying late commits at or below the high-water ID during final cutover.
- Added startup initialization and retired-generation cleanup, both conditional on `market.operations-projector.enabled`.

## TDD RED / GREEN

### Initial RED

Command:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.operations.projection.ProjectionGenerationServiceTest'
```

Result: expected compilation failure because `ProjectionGenerationService` and `ProjectionBootstrapRepository` did not exist.

### Initial GREEN

After the minimal bootstrap, rebuild, activation, failure, initializer, and cleanup implementation, the focused Task 7 suite passed all 8 tests.

### Review RED

Read-only review identified two uncovered production risks. Tests were strengthened before changing production code:

1. A lower outbox sequence ID allocated by an uncommitted writer could commit after a higher ID had already become the bootstrap high-water ID.
2. Store-owned coupon claims used a different `commerce_store_id` during bootstrap than live event projection, and a same-hour claim/order row needed additive conflict merging.

Focused result: 8 tests, 2 expected assertion failures reproducing those defects.

### Final GREEN

- Added bootstrap visibility receipts and final replay for late commits at or below high-water.
- Changed store-owned claim bootstrap dimensions to match `CouponOutcomeEventFactory` and merged same-hour rows additively.
- Focused Task 7 result: 8/8 passed, `BUILD SUCCESSFUL`.
- Read-only re-review: no Critical or Important findings; verdict `Ready`.

## Verification

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.projection.ProjectionGenerationServiceTest'
```

- Result: `BUILD SUCCESSFUL`, 8 tests passed.

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.projection.*'
```

- Result: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat test --tests 'com.sweet.market.store.migration.StoreSpringBootFlywayTest'
```

- Result: `BUILD SUCCESSFUL` after disabling the operations projector in this intentionally partial legacy-schema migration context.

```powershell
.\gradlew.bat test
```

- Result: `BUILD SUCCESSFUL in 4m 39s`.
- XML totals: 117 suite files, 785 tests, 0 failures, 0 errors, 0 skipped.

```powershell
git diff --check
```

- Result: clean.

## Files

Created:

- `backend/src/main/java/com/sweet/market/operations/projection/ProjectionBootstrapRepository.java`
- `backend/src/main/java/com/sweet/market/operations/projection/ProjectionBootstrapSnapshot.java`
- `backend/src/main/java/com/sweet/market/operations/projection/ProjectionGenerationCleanupScheduler.java`
- `backend/src/main/java/com/sweet/market/operations/projection/ProjectionGenerationInitializer.java`
- `backend/src/main/java/com/sweet/market/operations/projection/ProjectionGenerationService.java`
- `backend/src/main/java/com/sweet/market/operations/projection/ProjectionRebuildResult.java`
- `backend/src/test/java/com/sweet/market/operations/projection/ProjectionGenerationServiceTest.java`
- `.superpowers/sdd/task-7-report.md`

Modified:

- `backend/src/main/java/com/sweet/market/operations/projection/OperationalProjectionCoordinator.java`
- `backend/src/main/java/com/sweet/market/operations/projection/OperationalProjectionRepository.java`
- `backend/src/test/java/com/sweet/market/store/migration/StoreSpringBootFlywayTest.java`

## Concerns / Notes

- Bootstrap visibility tracking adds one generation-scoped receipt per retained outbox row visible at the snapshot. V15 cascade deletion and the seven-day retired-generation cleanup bound this data to retained generations.
- The 90-day window implementation uses the required KST-derived lower bound and half-open cutoff. Review found no implementation defect; exact boundary instants are not separately asserted beyond the broader cutoff/window test.
- Pre-existing uncommitted changes in Task 2–4 report files were preserved and excluded from this task's commit.
