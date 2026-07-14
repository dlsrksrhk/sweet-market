# Task 2 Report: Coupon campaign management APIs

## Status

- Completed: store-owner and administrator coupon-campaign management APIs.
- Commit message: `feat: manage coupon campaigns`.
- Deliberately excluded: the pre-existing untracked `web/m26-baseline-npm-install.log`.

## Implemented scope

- Added store APIs under `/api/stores/{storeId}/coupon-campaigns` for create, paged list, detail, update, schedule, pause, resume, and end.
- Added administrator-only platform APIs under `/api/admin/coupon-campaigns` for the same operations. Spring Security already protects this route family with `ADMIN`.
- Store commands require an active business-store owner through `requireActiveBusinessOwner`.
- Store selected targets resolve with `findAllByStoreIdAndIdIn`; platform selected targets use bounded `findAllById` and may span stores. Duplicate, missing, and non-purchasable targets are rejected.
- Converts all request `LocalDateTime` values from `Asia/Seoul` to `Instant`, and maps policy dates back to KST in responses.
- Responses include owner type, nullable safe store summary, policy fields, lifecycle/effective statuses, target count, and selected-target detail.
- Added paged status/period filtering and error codes `COUPON_CAMPAIGN_NOT_FOUND` (404) and `COUPON_LIFECYCLE_NOT_ALLOWED` (409).

## TDD evidence

### RED

Created `CouponCampaignApiTest` before the controller/service implementation. The first focused test run completed compilation but failed five endpoint assertions because the coupon-campaign endpoints did not exist (HTTP 404). This covered store owner selected/all-product creation, platform multi-store targets, store access denials, invalid targets/validity policy, and administrator-only commands.

The documented `C:\java\jdk-21` directory was absent locally, so verification used the available JDK 21 installation at `C:\Users\kdh\.jdks\corretto-21.0.7`.

### GREEN and final verification

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponCampaignApiTest' --rerun-tasks
```

Result: `BUILD SUCCESSFUL` (6 tests, 0 failures). `git diff --check` also completed without diff errors.

## Concerns

- Only the Task 2 focused API class was run; the full backend suite was not run.
- Gradle emitted existing JVM/Mockito dynamic-agent warnings, but the focused test result was successful.
