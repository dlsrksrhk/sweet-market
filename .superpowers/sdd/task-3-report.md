# Task 3 Report: First-Come Coupon Claim Integration

## Status

- Completed the claim-service integration for limited campaigns.
- Kept checkout and redemption code out of scope.

## Implemented scope

- Added the conditional `issued_count` increment query and the campaign pessimistic-write-lock query.
- Added `REQUIRES_NEW` confirmation that increments durable capacity before saving the coupon. A unique-key failure rolls back that increment in the same transaction.
- Added Redis-unavailable fallback that rereads the existing coupon and serializes issuance with the campaign lock.
- Retained the existing-coupon-first check, so an already-issued buyer succeeds even after capacity is exhausted or the campaign is paused/ended.
- Limited claims reserve in Redis, confirm in the database, then complete the same reservation token. Confirmation failures release that token exactly once; release failures are suppressed onto the original failure. A completion failure is not converted into a database fallback because the durable coupon is already committed.
- Only `CouponIssuanceGateUnavailableException` around reservation selects the locked database fallback. A successful gate response never does.
- Added focused integration coverage for concurrent capacity, existing buyer after sellout, confirmation compensation, and Redis-unavailable fallback.

## TDD and verification evidence

### RED

Added the limited issuance integration tests before the claim-service integration. The focused Gradle test compiled but could not execute because Testcontainers could not initialize Docker; the prior claim path has no reservation or durable conditional-count behavior.

### Green/static verification

The documented `C:\java\jdk-21` location was not installed. Used the detected JDK 21 instead:

```powershell
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
cd backend
.\gradlew.bat compileJava compileTestJava test --tests 'com.sweet.market.coupon.domain.CouponCampaignTest'
```

Result: `BUILD SUCCESSFUL`.

`git diff --check` completed without whitespace errors.

### Focused integration test

```powershell
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponIssueApiTest'
```

Result: blocked before test execution. The shared Testcontainers setup throws `IllegalStateException` from `DockerClientProviderStrategy` because Docker Desktop's Linux engine named pipe is unavailable. All eight tests therefore fail during container initialization; this is not a behavioral pass/fail result.

## Self-review

- The conditional update carries both lifecycle/time predicates and `issued_count < issue_limit`, making the database the final capacity authority for the Redis route.
- `clearAutomatically` on the update detaches the campaign. The confirmation transaction reloads it before constructing `MemberCoupon`, so lazy coupon-target access remains valid.
- Capacity failures map to `COUPON_ISSUE_LIMIT_EXCEEDED` (HTTP 409); lifecycle failures retain the lifecycle error mapping.

## Concern

- Start Docker Desktop (Linux containers) and rerun `CouponIssueApiTest` before merging to obtain the required live Redis/PostgreSQL concurrency proof.

## Review follow-up

- Changed the database fallback transaction to acquire `PESSIMISTIC_WRITE` on the campaign before rereading the member coupon. A Redis confirmation that commits while the fallback waits can now be observed as the already-issued coupon instead of consuming capacity or returning a conflict.
- Added Korean-named coverage for that lock-then-reread ordering, exact same-object one-time reservation release and completion, and retries by an existing buyer after pause and end.
- Re-ran `compileJava compileTestJava` plus `CouponCampaignTest`: passed. The 11-test `CouponIssueApiTest` remains blocked before execution by the unavailable Docker/Testcontainers engine.
