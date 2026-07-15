# Milestone 27: First-Come Coupon Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional first-come coupon limits whose normal Redis Lua admission path and database fallback never commit more coupons than the configured campaign limit.

**Architecture:** `CouponCampaign` stores the durable limit and cumulative count. Limited claims reserve capacity through Redis Lua, then synchronously confirm the coupon and a conditional database count increment in one transaction; Redis reservation completion or compensation follows that transaction. The database unique constraint and count condition remain authoritative, while a pessimistic database lock is used when Redis cannot be reached.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL, Spring Data Redis/Lettuce, Redis 7 Lua scripts, Testcontainers, React, TypeScript, TanStack Query.

## Global Constraints

- Backend Gradle commands run from `backend` with JDK 21 and `JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`.
- New JUnit `@Test` methods use Korean underscore-separated names.
- `issueLimit` is nullable for unlimited campaigns; when present it is at least one and only mutable in `DRAFT`.
- `issuedCount` is a durable cumulative count; use, expiry, pause, and end never decrease it.
- A buyer who already owns a coupon receives that coupon successfully even after sellout, pause, or end.
- A new buyer after capacity is consumed receives `409 COUPON_ISSUE_LIMIT_EXCEEDED`.
- Redis is an admission layer, never the sole durable authority; the database must reject every over-limit commit.
- Coupon redemption and order discount application remain out of scope.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `backend/build.gradle` | Redis client and Redis Testcontainers dependencies. |
| `backend/src/main/resources/db/migration/V11__add_coupon_campaign_issue_limits.sql` | Durable campaign limit/count columns and constraints. |
| `backend/src/main/java/com/sweet/market/coupon/domain/CouponCampaign.java` | Limit validation, draft-only mutation, and fallback-path count mutation. |
| `backend/src/main/java/com/sweet/market/coupon/repository/CouponCampaignRepository.java` | Conditional persistent count increment and pessimistic issuance lookup. |
| `backend/src/main/java/com/sweet/market/coupon/application/issuance/*` | Redis-gate interface, reservation value objects, Lua implementation, and unavailable exception. |
| `backend/src/main/resources/redis/coupon-*.lua` | Atomic reserve, complete, and release transitions. |
| `backend/src/main/java/com/sweet/market/coupon/application/CouponIssueService.java` | Existing-coupon-first routing, Redis use, and fallback selection. |
| `backend/src/main/java/com/sweet/market/coupon/application/CouponIssueTransactionService.java` | New transactions that atomically confirm a reservation or issue under the database lock. |
| `backend/src/main/java/com/sweet/market/coupon/api/*Response.java` and `coupon/query/*Row.java` | Limit/count/sold-out API fields and paged projections. |
| `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java` | Shared Redis Testcontainer and dynamic Redis properties. |
| `backend/src/test/java/com/sweet/market/coupon/*Test.java` | Migration, policy, API, Redis concurrency, compensation, and fallback tests. |
| `web/src/features/coupons/couponApi.ts` | TypeScript contract fields and campaign input. |
| `web/src/pages/MyCouponsPage.tsx` | Sold-out buyer card and disabled claim action. |
| `web/src/pages/CouponCampaignWorkspacePage.tsx` | Limit input and store-owner count display. |
| `web/src/pages/CouponCampaignDetailPage.tsx` | Store-owner count and remaining display. |
| `web/src/pages/AdminCouponCampaignsPage.tsx` | Platform-admin limit input and count display. |

### Task 1: Persist And Expose Campaign Issue Limits

**Files:**

- Create: `backend/src/main/resources/db/migration/V11__add_coupon_campaign_issue_limits.sql`
- Modify: `backend/src/main/java/com/sweet/market/coupon/domain/CouponCampaign.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/api/CouponCampaignCreateRequest.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/api/CouponCampaignUpdateRequest.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/api/CouponCampaignResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/query/CouponCampaignSummaryRow.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/repository/CouponCampaignRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/application/CouponCampaignService.java`
- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Test: `backend/src/test/java/com/sweet/market/coupon/domain/CouponCampaignTest.java`
- Test: `backend/src/test/java/com/sweet/market/coupon/CouponCampaignApiTest.java`

**Consumes:** The M26 campaign create/update payload and `coupon_campaigns.version` optimistic-lock column.

**Produces:** `CouponCampaign#getIssueLimit()`, `CouponCampaign#getIssuedCount()`, `CouponCampaign#remainingIssueCount()`, `CouponCampaign#recordIssue()`, and campaign response fields `issueLimit`, `issuedCount`, and `remainingIssueCount`.

- [ ] **Step 1: Write the failing migration/domain/API tests.**

```java
@Test
void 발급한도는_양수이거나_무제한이어야_한다() {
    assertThatThrownBy(() -> campaign(0)).isInstanceOf(DomainException.class);
    assertThat(campaign(null).getIssueLimit()).isNull();
    assertThat(campaign(3).remainingIssueCount()).isEqualTo(3);
}

@Test
void 발급한도는_초안에서만_변경할_수_있다() {
    CouponCampaign campaign = campaign(3);
    campaign.schedule(Instant.parse("2026-07-15T00:00:00Z"));
    assertThatThrownBy(() -> campaign.changeIssueLimit(5))
            .isInstanceOf(DomainException.class);
}
```

Add API assertions that creation with `"issueLimit": 2` returns `issueLimit=2`, `issuedCount=0`, `remainingIssueCount=2`, and that `"issueLimit": 0` returns a validation failure.

- [ ] **Step 2: Run the focused tests to verify they fail.**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.coupon.domain.CouponCampaignTest' --tests 'com.sweet.market.coupon.CouponCampaignApiTest'
```

Expected: FAIL because the limit fields and methods do not exist.

- [ ] **Step 3: Add the migration and minimal domain/API implementation.**

Create the migration with a nullable limit, zero default count, and durable bounds:

```sql
ALTER TABLE coupon_campaigns
    ADD COLUMN issue_limit INTEGER,
    ADD COLUMN issued_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_coupon_campaigns_issue_limit
        CHECK (issue_limit IS NULL OR issue_limit > 0),
    ADD CONSTRAINT chk_coupon_campaigns_issued_count
        CHECK (issued_count >= 0 AND (issue_limit IS NULL OR issued_count <= issue_limit));
```

Add these fields and domain operations to `CouponCampaign`; pass `issueLimit` through both `create` and `update` call chains:

```java
@Column(name = "issue_limit")
private Integer issueLimit;

@Column(name = "issued_count", nullable = false)
private int issuedCount;

public Integer remainingIssueCount() {
    return issueLimit == null ? null : issueLimit - issuedCount;
}

public void changeIssueLimit(Integer nextIssueLimit) {
    validateIssueLimit(nextIssueLimit);
    if (!Objects.equals(issueLimit, nextIssueLimit) && lifecycleStatus != CouponLifecycleStatus.DRAFT) {
        throw new DomainException(CouponDomainError.UPDATE_NOT_ALLOWED);
    }
    issueLimit = nextIssueLimit;
}

public void recordIssue() {
    if (issueLimit != null && issuedCount >= issueLimit) {
        throw new DomainException(CouponDomainError.ISSUE_LIMIT_EXCEEDED);
    }
    issuedCount++;
}

private static void validateIssueLimit(Integer issueLimit) {
    if (issueLimit != null && issueLimit <= 0) {
        throw new DomainException(CouponDomainError.INVALID_ISSUE_LIMIT);
    }
}
```

Add `@Positive Integer issueLimit` to the create/update records, include it in `asCreateRequest`, add `INVALID_ISSUE_LIMIT` and `ISSUE_LIMIT_EXCEEDED` to `CouponDomainError`, and map the latter to `ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED` (`HttpStatus.CONFLICT`). Extend owner list JPQL projections with `campaign.issueLimit` and `campaign.issuedCount`, then map both fields and the derived remaining count in `CouponCampaignResponse`.

- [ ] **Step 4: Run the focused tests to verify they pass.**

Run the command from Step 2.

Expected: PASS; existing campaign creation without `issueLimit` remains unlimited and response values are `null`, `0`, `null`.

- [ ] **Step 5: Commit the persistent campaign policy.**

```powershell
git add backend/build.gradle backend/src/main/resources/db/migration/V11__add_coupon_campaign_issue_limits.sql backend/src/main/java/com/sweet/market/coupon backend/src/main/java/com/sweet/market/common/error/ErrorCode.java backend/src/test/java/com/sweet/market/coupon/domain/CouponCampaignTest.java backend/src/test/java/com/sweet/market/coupon/CouponCampaignApiTest.java
git commit -m "feat: add coupon campaign issue limits"
```

### Task 2: Add Redis Lua Reservation Infrastructure

**Files:**

- Modify: `backend/build.gradle`
- Modify: `backend/src/main/resources/application.yaml`
- Create: `backend/src/main/java/com/sweet/market/coupon/application/issuance/CouponIssuanceGate.java`
- Create: `backend/src/main/java/com/sweet/market/coupon/application/issuance/CouponIssuanceReservation.java`
- Create: `backend/src/main/java/com/sweet/market/coupon/application/issuance/CouponIssuanceGateResult.java`
- Create: `backend/src/main/java/com/sweet/market/coupon/application/issuance/CouponIssuanceGateUnavailableException.java`
- Create: `backend/src/main/java/com/sweet/market/coupon/application/issuance/RedisCouponIssuanceGate.java`
- Create: `backend/src/main/resources/redis/coupon-reserve.lua`
- Create: `backend/src/main/resources/redis/coupon-complete.lua`
- Create: `backend/src/main/resources/redis/coupon-release.lua`
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`
- Test: `backend/src/test/java/com/sweet/market/coupon/RedisCouponIssuanceGateTest.java`

**Consumes:** A limited campaign ID, durable `issuedCount`, `issueLimit`, issue-end instant, member ID, and current instant.

**Produces:** A reservation result of `RESERVED`, `ALREADY_ISSUED`, `IN_PROGRESS`, or `SOLD_OUT`; only a `RESERVED` result contains an opaque token that can be completed or released once.

- [ ] **Step 1: Write failing Redis gate tests against a real Redis container.**

```java
@Test
void 한도만큼만_원자적으로_예약한다() {
    List<CouponIssuanceGateResult> results = IntStream.range(0, 20).parallel()
            .mapToObj(memberId -> gate.reserve(limitedCampaign(5), (long) memberId, now))
            .toList();

    assertThat(results).filteredOn(result -> result.type() == ReservationType.RESERVED).hasSize(5);
    assertThat(results).filteredOn(result -> result.type() == ReservationType.SOLD_OUT).hasSize(15);
}

@Test
void 같은_회원의_진행중_예약은_중복_슬롯을_차지하지_않는다() {
    CouponIssuanceGateResult first = gate.reserve(limitedCampaign(1), 7L, now);
    CouponIssuanceGateResult retry = gate.reserve(limitedCampaign(1), 7L, now);

    assertThat(first.type()).isEqualTo(ReservationType.RESERVED);
    assertThat(retry.type()).isEqualTo(ReservationType.IN_PROGRESS);
}
```

- [ ] **Step 2: Run the gate test to verify it fails.**

Run:

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.coupon.RedisCouponIssuanceGateTest'
```

Expected: FAIL because Redis dependencies, the container, and the gate types are absent.

- [ ] **Step 3: Implement the Redis boundary and scripts.**

Add the runtime and test dependencies:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
testImplementation 'org.testcontainers:testcontainers'
```

Add local defaults that allow the application to start without a Redis daemon; connection failures are handled by the gate:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 500ms
```

Define the boundary and token ownership explicitly:

```java
public interface CouponIssuanceGate {
    CouponIssuanceGateResult reserve(Long campaignId, Long memberId, int issueLimit,
                                     int issuedCount, Instant issueEndsAt, Instant now);
    void complete(CouponIssuanceReservation reservation, Instant now);
    void release(CouponIssuanceReservation reservation, Instant now);
}

public record CouponIssuanceReservation(Long campaignId, Long memberId, String token) {}

public record CouponIssuanceGateResult(ReservationType type, CouponIssuanceReservation reservation) {
    public static CouponIssuanceGateResult reserved(CouponIssuanceReservation reservation) {
        return new CouponIssuanceGateResult(ReservationType.RESERVED, reservation);
    }
}
```

Use campaign-scoped key names `coupon:issue:{campaignId}:count`, `coupon:issue:{campaignId}:pending`, and `coupon:issue:{campaignId}:member:{memberId}`. The reserve Lua script must seed a missing count with `issuedCount`, remove every expired member token listed in the pending sorted set before comparing with `issueLimit`, return `IN_PROGRESS` for a live member reservation, and increment the counter only when it creates the supplied opaque token. The complete script removes the matching pending token and records an issued member marker. The release script decrements only when the provided token still matches the member reservation. All script keys use the same `{campaignId}` hash tag.

Start one `GenericContainer<>("redis:7.4-alpine")` beside PostgreSQL in `IntegrationTestSupport` and register `spring.data.redis.host` and `spring.data.redis.port` from it. Clear `coupon:issue:*` after each test with `StringRedisTemplate` so isolation matches the database truncate.

- [ ] **Step 4: Run the gate test to verify it passes.**

Run the command from Step 2.

Expected: PASS; no more than the requested number of reservations are returned, and duplicate in-progress attempts do not increment the counter.

- [ ] **Step 5: Commit the Redis admission boundary.**

```powershell
git add backend/build.gradle backend/src/main/resources/application.yaml backend/src/main/resources/redis backend/src/main/java/com/sweet/market/coupon/application/issuance backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java backend/src/test/java/com/sweet/market/coupon/RedisCouponIssuanceGateTest.java
git commit -m "feat: add redis coupon issuance gate"
```

### Task 3: Confirm Reservations In The Database And Add Fallback

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/coupon/repository/CouponCampaignRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/application/CouponIssueService.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/application/CouponIssueTransactionService.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/application/issuance/RedisCouponIssuanceGate.java`
- Test: `backend/src/test/java/com/sweet/market/coupon/CouponIssueApiTest.java`

**Consumes:** A Redis `CouponIssuanceReservation`, `CouponCampaignRepository`, existing `MemberCouponRepository` unique constraint, and `MemberRepository`.

**Produces:** `CouponIssueService.claim` that returns one durable coupon for idempotent retries, completes or compensates every reservation, and uses a DB lock only for Redis unavailability.

- [ ] **Step 1: Write failing end-to-end issuance tests.**

```java
@Test
void 선착순_한도를_넘는_동시_발급은_정확히_한도만_성공한다() throws Exception {
    Long campaignId = activeCampaignWithLimit(5);
    List<Long> memberIds = createMembers(20);

    List<Future<ClaimResult>> claims = submitTogether(memberIds,
            memberId -> claimResult(memberId, campaignId));

    List<ClaimResult> results = claims.stream().map(this::await).toList();
    assertThat(results).filteredOn(ClaimResult::success).hasSize(5);
    assertThat(issuedCount(campaignId)).isEqualTo(5);
    assertThat(memberCouponCount(campaignId)).isEqualTo(5);
}

@Test
void 소진후_기존_발급_회원은_기존_쿠폰을_성공으로_받는다() throws Exception {
    Long campaignId = activeCampaignWithLimit(1);
    String firstEmail = "first-come-owner@example.com";
    signupAndLogin(firstEmail);
    Long firstMemberId = memberId(firstEmail);
    couponIssueService.claim(firstMemberId, campaignId);

    assertThat(couponIssueService.claim(firstMemberId, campaignId).campaignId()).isEqualTo(campaignId);
    String lateEmail = "first-come-late@example.com";
    signupAndLogin(lateEmail);
    assertThatThrownBy(() -> couponIssueService.claim(memberId(lateEmail), campaignId))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED);
}
```

Add tests that force the transaction bean to fail after reservation and then prove a second member can claim, and that replace the Redis gate with a throwing test bean to prove the database fallback issues exactly `issueLimit` coupons.

- [ ] **Step 2: Run the issuance tests to verify they fail.**

Run:

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponIssueApiTest'
```

Expected: FAIL because the current claim flow has no capacity reservation, durable count increment, or Redis fallback.

- [ ] **Step 3: Implement the conditional confirmation and fallback transactions.**

Add these repository operations. The conditional update is the non-negotiable second limit guard for the Redis path:

```java
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("""
        update CouponCampaign campaign
           set campaign.issuedCount = campaign.issuedCount + 1,
               campaign.version = campaign.version + 1
         where campaign.id = :campaignId
           and campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.SCHEDULED
           and campaign.issueStartsAt <= :now and campaign.issueEndsAt > :now
           and campaign.issuedCount < campaign.issueLimit
        """)
int incrementLimitedIssuedCount(@Param("campaignId") Long campaignId, @Param("now") Instant now);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select campaign from CouponCampaign campaign where campaign.id = :campaignId")
Optional<CouponCampaign> findByIdForIssuance(@Param("campaignId") Long campaignId);
```

In `CouponIssueTransactionService`, add two `REQUIRES_NEW` methods. The first saves the coupon only after the conditional update succeeds; a unique-key exception rolls back the increment with the transaction. The second rechecks the existing coupon while holding the campaign write lock, uses `campaign.recordIssue()`, and saves the coupon in the same transaction:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public MemberCoupon confirmLimitedIssue(Long memberId, Long campaignId, Instant issuedAt) {
    CouponCampaign campaign = campaignRepository.findById(campaignId).orElseThrow(this::campaignNotFound);
    campaign.requireClaimable(issuedAt);
    if (campaignRepository.incrementLimitedIssuedCount(campaignId, issuedAt) != 1) {
        throw capacityOrLifecycleFailure(campaignId, issuedAt);
    }
    return memberCouponRepository.saveAndFlush(MemberCoupon.issue(findMember(memberId), campaign, issuedAt));
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public MemberCoupon issueWithPessimisticLock(Long memberId, Long campaignId, Instant issuedAt) {
    MemberCoupon existing = memberCouponRepository.findByCampaignIdAndMemberId(campaignId, memberId).orElse(null);
    if (existing != null) return existing;
    CouponCampaign campaign = campaignRepository.findByIdForIssuance(campaignId).orElseThrow(this::campaignNotFound);
    campaign.requireClaimable(issuedAt);
    campaign.recordIssue();
    return memberCouponRepository.saveAndFlush(MemberCoupon.issue(findMember(memberId), campaign, issuedAt));
}
```

Refactor `CouponIssueService.claim` to retain the existing-coupon first check. For an unlimited campaign call the current issue path. For a limited campaign, reserve, confirm, then complete the exact token. On `DataIntegrityViolationException` caused by `uq_member_coupons_campaign_member`, release the reservation and return the committed existing coupon. On every other confirmation failure, release before rethrowing. Catch only `CouponIssuanceGateUnavailableException` to call `issueWithPessimisticLock`; never fall back after a successful reservation result. Map capacity failures to the new `ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED`.

- [ ] **Step 4: Run the issuance tests to verify they pass.**

Run the command from Step 2.

Expected: PASS; exactly five rows and `issued_count = 5` exist for a limit of five, a failed confirmation returns its reserved slot, and the forced Redis-unavailable path has the same result.

- [ ] **Step 5: Commit concurrent issuance control.**

```powershell
git add backend/src/main/java/com/sweet/market/coupon/repository/CouponCampaignRepository.java backend/src/main/java/com/sweet/market/coupon/application/CouponIssueService.java backend/src/main/java/com/sweet/market/coupon/application/CouponIssueTransactionService.java backend/src/main/java/com/sweet/market/coupon/application/issuance/RedisCouponIssuanceGate.java backend/src/test/java/com/sweet/market/coupon/CouponIssueApiTest.java
git commit -m "feat: control first-come coupon issuance"
```

### Task 4: Return Sold-Out And Operational Count Data Without N+1 Queries

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/coupon/repository/CouponCampaignRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/query/AvailableCouponCampaignRow.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/api/AvailableCouponCampaignResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/api/CouponCampaignResponse.java`
- Test: `backend/src/test/java/com/sweet/market/coupon/CouponWalletApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/coupon/CouponQueryOptimizationTest.java`

**Consumes:** Persisted `issueLimit`/`issuedCount` and the existing paged available-campaign owner and buyer projections.

**Produces:** Buyer `soldOut`, owner `issueLimit`/`issuedCount`/`remainingIssueCount`, with sold-out active campaigns retained in the buyer page and no per-row issued-coupon lookup.

- [ ] **Step 1: Write failing buyer and projection tests.**

```java
@Test
void 소진된_캠페인은_목록에_남고_마감상태를_반환한다() throws Exception {
    String issuedBuyer = signupAndLogin("sold-out-issued@example.com");
    String waitingBuyer = signupAndLogin("sold-out-waiting@example.com");
    Long campaignId = activeCampaignWithLimit(1);
    claim(issuedBuyer, campaignId);

    mockMvc.perform(get("/api/coupon-campaigns/available").header(AUTHORIZATION, "Bearer " + waitingBuyer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(campaignId))
            .andExpect(jsonPath("$.data.content[0].soldOut").value(true));
}

@Test
void 운영자_목록은_발급수와_잔여수를_반환한다() throws Exception {
    Long campaignId = createDraftCampaignWithLimit(3);
    mockMvc.perform(get("/api/admin/coupon-campaigns").header(AUTHORIZATION, "Bearer " + adminToken()))
            .andExpect(jsonPath("$.data.content[0].id").value(campaignId))
            .andExpect(jsonPath("$.data.content[0].issuedCount").value(0))
            .andExpect(jsonPath("$.data.content[0].remainingIssueCount").value(3));
}
```

- [ ] **Step 2: Run the query tests to verify they fail.**

Run:

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponWalletApiTest' --tests 'com.sweet.market.coupon.CouponQueryOptimizationTest'
```

Expected: FAIL because the projections do not carry count fields or `soldOut`.

- [ ] **Step 3: Extend projections and response mapping.**

Add `campaign.issueLimit` and `campaign.issuedCount` to the constructor expression for `findAvailableForMember` and expose them in `AvailableCouponCampaignRow`. Derive the response field without loading coupons:

```java
public static AvailableCouponCampaignResponse from(AvailableCouponCampaignRow row) {
    boolean soldOut = row.issueLimit() != null && row.issuedCount() >= row.issueLimit();
    return new AvailableCouponCampaignResponse(
            row.id(), row.ownerType(), store(row.storeId(), row.storeName()), row.scope(), row.discountType(),
            row.discountValue(), row.maxDiscountAmount(), row.minimumPurchaseAmount(), row.stackable(),
            row.title(), row.label(), local(row.issueStartsAt()), local(row.issueEndsAt()), row.validityType(),
            local(row.commonExpiresAt()), row.validityDays(), CouponEffectiveStatus.ACTIVE, row.claimed(), soldOut);
}
```

Keep the existing lifecycle/window predicate unchanged: an active sold-out campaign remains discoverable. Update summary and detail mappings to return count fields directly from the campaign or summary projection. Do not add a collection fetch or a per-campaign count query.

- [ ] **Step 4: Run the query tests to verify they pass.**

Run the command from Step 2.

Expected: PASS; sold-out campaigns remain in buyer discovery, owner counts are correct, and the existing N+1 query limits remain unchanged.

- [ ] **Step 5: Commit the read contract.**

```powershell
git add backend/src/main/java/com/sweet/market/coupon/repository/CouponCampaignRepository.java backend/src/main/java/com/sweet/market/coupon/query/AvailableCouponCampaignRow.java backend/src/main/java/com/sweet/market/coupon/api/AvailableCouponCampaignResponse.java backend/src/main/java/com/sweet/market/coupon/api/CouponCampaignResponse.java backend/src/test/java/com/sweet/market/coupon/CouponWalletApiTest.java backend/src/test/java/com/sweet/market/coupon/CouponQueryOptimizationTest.java
git commit -m "feat: expose coupon issue availability"
```

### Task 5: Update Buyer And Operator Coupon Screens

**Files:**

- Modify: `web/src/features/coupons/couponApi.ts`
- Modify: `web/src/pages/MyCouponsPage.tsx`
- Modify: `web/src/pages/CouponCampaignWorkspacePage.tsx`
- Modify: `web/src/pages/CouponCampaignDetailPage.tsx`
- Modify: `web/src/pages/AdminCouponCampaignsPage.tsx`
- Modify: `web/src/shared/styles.css`

**Consumes:** API fields `issueLimit`, `issuedCount`, `remainingIssueCount`, and `soldOut`.

**Produces:** Draft-only optional issue-limit inputs, operator `발급 수 / 한도` and `잔여 수` labels, and buyer sold-out cards that cannot issue a new claim.

- [ ] **Step 1: Write failing component-level assertions or add the browser QA checklist.**

Add a focused render test if the web project has its test runner configured; otherwise record these browser assertions in the task PR description and exercise them with the local app:

```text
1. A blank limit input serializes as undefined and renders 무제한 in each owner view.
2. A draft limit of 3 renders 발급 0 / 3 and 잔여 3.
3. A sold-out buyer card renders 선착순 마감 and has a disabled button.
4. A claimed sold-out card renders 발급 완료 instead of the sold-out claim action.
```

- [ ] **Step 2: Run the web type check/build to establish the missing contract.**

Run:

```powershell
cd web
npm run build
```

Expected: PASS before the type additions; after adding the backend contract, TypeScript should guide every campaign-card call site to handle the new fields.

- [ ] **Step 3: Implement the TypeScript contract and visuals.**

Extend the API types and input:

```ts
// Add these fields to the existing CouponCampaign type.
issueLimit: number | null;
issuedCount: number;
remainingIssueCount: number | null;

// Add this optional field to the existing CouponCampaignInput type.
issueLimit?: number;

// Add this field after claimed in the existing AvailableCouponCampaign type.
soldOut: boolean;
```

In `CouponCampaignForm`, keep `issueLimit` as `number | undefined`, render `<input type="number" min="1" />`, and include it only when nonblank. Disable that input whenever the form is disabled, which is already true outside `DRAFT`. Add a reusable display expression in the owner list/detail/table:

```tsx
<span>{campaign.issueLimit === null
  ? '발급 무제한'
  : `발급 ${campaign.issuedCount} / ${campaign.issueLimit} · 잔여 ${campaign.remainingIssueCount}`}</span>
```

In `MyCouponsPage`, give claimed state precedence and disable a sold-out new claim:

```tsx
const disabled = campaign.claimed || campaign.soldOut || claimPending;
const label = campaign.claimed ? '발급 완료'
  : campaign.soldOut ? '선착순 마감'
  : claimPending ? '발급 중' : '쿠폰 받기';
```

Use the existing status/button styles and add only the small sold-out color treatment needed for clear contrast.

- [ ] **Step 4: Run the web build and browser checklist.**

Run:

```powershell
cd web
npm run build
```

Expected: PASS. With `npm run dev`, verify the four assertions from Step 1 using a limited campaign and two buyer accounts.

- [ ] **Step 5: Commit the coupon-event interfaces.**

```powershell
git add web/src/features/coupons/couponApi.ts web/src/pages/MyCouponsPage.tsx web/src/pages/CouponCampaignWorkspacePage.tsx web/src/pages/CouponCampaignDetailPage.tsx web/src/pages/AdminCouponCampaignsPage.tsx web/src/shared/styles.css
git commit -m "feat: show first-come coupon event status"
```

### Task 6: Run Compatibility Verification And Write The Handoff

**Files:**

- Create: `docs/superpowers/handoffs/2026-07-15-milestone-27-first-come-coupon-events-handoff.md`
- Modify: only files identified by failures from the commands below.

**Consumes:** All M27 backend and web changes.

**Produces:** Verified backend/web results and a handoff recording Redis local requirements, fallback behavior, test results, and deferred M28 redemption work.

- [ ] **Step 1: Run focused first-come and regression tests.**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.coupon.domain.CouponCampaignTest' --tests 'com.sweet.market.coupon.RedisCouponIssuanceGateTest' --tests 'com.sweet.market.coupon.CouponIssueApiTest' --tests 'com.sweet.market.coupon.CouponWalletApiTest' --tests 'com.sweet.market.coupon.CouponQueryOptimizationTest'
```

Expected: PASS. Docker Desktop must be running because PostgreSQL and Redis Testcontainers are required.

- [ ] **Step 2: Run the full backend suite and web build.**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
cd ..\web
npm run build
```

Expected: all backend tests and the production web build pass. If Testcontainers cannot start because Docker Desktop is unavailable, record that environmental block explicitly and do not characterize it as a product regression.

- [ ] **Step 3: Write the concrete handoff.**

Include the branch and final commit, migration name, Redis configuration (`REDIS_HOST`, `REDIS_PORT`), normal Lua path, database fallback guarantee, exact test/build commands and results, and the retained M28 boundary: coupon redemption/order price changes were not implemented.

- [ ] **Step 4: Commit verification documentation.**

```powershell
git add docs/superpowers/handoffs/2026-07-15-milestone-27-first-come-coupon-events-handoff.md
git commit -m "docs: hand off milestone 27 coupon events"
```
