# Milestone 26 Coupon Campaigns And Standard Coupon Issuance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Enable platform administrators and active business-store owners to manage coupon campaigns, let buyers claim each campaign once, and provide a buyer coupon wallet without changing checkout or order pricing.

**Architecture:** CouponCampaign owns live policy, product targets, and lifecycle. MemberCoupon is one buyer-owned, issue-time policy snapshot; its unique campaign/member constraint makes claims idempotent. M25-style owner APIs manage campaigns while paged buyer projections serve discovery and wallet views.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL/Flyway, JUnit 5/MockMvc/Testcontainers, React, TypeScript, TanStack Query, Vite.

## Global Constraints

- New JUnit test names use Korean with underscores.
- Use JDK C:\Users\kdh\.jdks\corretto-21.0.7, JWT_SECRET=sweet-market-local-test-secret-key-32bytes-minimum, and the existing test Hikari pool limit of four.
- Store campaigns require an active BUSINESS store OWNER; platform campaigns require ADMIN.
- Store targets belong to the owning store; platform targets may mix purchasable products from multiple stores.
- Pausing/ending a campaign blocks claims and makes issued coupons immediately unavailable, without deleting history.
- Claims, carts, orders, payments, settlements, refunds, and M25 promotion prices remain separate. Coupon redemption and stacking are deferred to M28.
- Persist Korean won in non-negative long values. Convert operator dates using Asia/Seoul and persist Instant values.

---

## File Structure

| Area | Files | Responsibility |
| --- | --- | --- |
| Schema/domain | V9 and coupon/domain | Campaign, targets, issued snapshots, lifecycle, validity rules |
| Owner APIs | coupon/application/CouponCampaignService, coupon/api, CouponCampaignRepository | Store/admin create, edit, list, detail, lifecycle |
| Buyer APIs | CouponIssueService, CouponWalletQueryService, query repositories | Atomic claim, available campaigns, wallet |
| Web | web/src/features/coupons, coupon pages, router, navigation, styles | Buyer wallet and owner/admin campaign surfaces |
| Verification | coupon tests and handoff | Contract, query shape, regression, release evidence |

### Task 1: Coupon schema, aggregates, and validity rules

**Files:**
- Create: backend/src/main/resources/db/migration/V9__add_coupon_campaigns_and_member_coupons.sql
- Create: backend/src/main/java/com/sweet/market/coupon/domain/CouponCampaign.java
- Create: backend/src/main/java/com/sweet/market/coupon/domain/CouponCampaignTarget.java
- Create: backend/src/main/java/com/sweet/market/coupon/domain/MemberCoupon.java
- Create: backend/src/main/java/com/sweet/market/coupon/domain/CouponCampaignOwnerType.java
- Create: backend/src/main/java/com/sweet/market/coupon/domain/CouponScope.java
- Create: backend/src/main/java/com/sweet/market/coupon/domain/CouponDiscountType.java
- Create: backend/src/main/java/com/sweet/market/coupon/domain/CouponValidityType.java
- Create: backend/src/main/java/com/sweet/market/coupon/domain/CouponLifecycleStatus.java
- Create: backend/src/main/java/com/sweet/market/coupon/domain/CouponEffectiveStatus.java
- Create: backend/src/main/java/com/sweet/market/coupon/domain/MemberCouponStatus.java
- Create: backend/src/main/java/com/sweet/market/coupon/domain/CouponDomainError.java
- Create: backend/src/main/java/com/sweet/market/coupon/repository/CouponCampaignRepository.java
- Create: backend/src/main/java/com/sweet/market/coupon/repository/MemberCouponRepository.java
- Create: backend/src/test/java/com/sweet/market/coupon/domain/CouponCampaignTest.java
- Create: backend/src/test/java/com/sweet/market/store/migration/CouponMigrationTest.java
- Modify: backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java

**Interfaces:**
- Produces CouponCampaign.create(ownerType, store, scope, discountType, discountValue, maxDiscountAmount, minimumPurchaseAmount, stackable, title, label, issueStartsAt, issueEndsAt, validityType, commonExpiresAt, validityDays, targets).
- Produces CouponCampaign.effectiveStatus(now), schedule(now), pause(now), resume(now), end(), update(..., now), and resolveValidUntil(issuedAt).
- Produces MemberCoupon.issue(member, campaign, issuedAt) and MemberCoupon.walletStatus(now).

- [ ] **Step 1: Write failing lifecycle, validity, and migration tests.**

~~~
@Test
void 발급일_기준_유효기간_쿠폰은_발급시각으로_만료시각을_고정한다() {
    MemberCoupon coupon = MemberCoupon.issue(member, daysCampaign, Instant.parse("2026-07-14T00:00:00Z"));

    assertThat(coupon.getValidUntil()).isEqualTo(Instant.parse("2026-07-21T00:00:00Z"));
}

@Test
void 종료된_캠페인의_미사용_쿠폰은_즉시_사용불가다() {
    campaign.end();

    assertThat(coupon.walletStatus(now)).isEqualTo(MemberCouponStatus.UNAVAILABLE);
}
~~~

Migration assertions must cover the campaign, target, and member-coupon tables; campaign-target and campaign-member unique constraints; and issued snapshot columns. Update IntegrationTestSupport cleanup to truncate member_coupons, coupon_campaign_targets, and coupon_campaigns before stores/products/members.

- [ ] **Step 2: Run focused tests to verify the missing types/schema.**

~~~powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.coupon.domain.CouponCampaignTest' --tests 'com.sweet.market.store.migration.CouponMigrationTest'
~~~

Expected: compilation or schema failure naming absent coupon classes/tables.

- [ ] **Step 3: Add V9 and the minimal aggregates.**

Create coupon_campaigns with nullable store_id only for PLATFORM ownership, coupon_campaign_targets, and member_coupons. Add unique (coupon_campaign_id, product_id), unique (coupon_campaign_id, member_id), foreign keys, lifecycle/owner/scope/validity check constraints, and owner/period, target, and wallet indexes. MemberCoupon stores issuedAt, validUntil, discount type/value, max discount, minimum purchase, scope, stackable, and persisted ISSUED/USED state.

~~~
public MemberCouponStatus walletStatus(Instant now) {
    if (status == MemberCouponStatus.USED) return MemberCouponStatus.USED;
    if (!now.isBefore(validUntil)) return MemberCouponStatus.EXPIRED;
    if (!campaign.isUsableForIssuedCoupon(now)) return MemberCouponStatus.UNAVAILABLE;
    return MemberCouponStatus.ISSUED;
}

public static MemberCoupon issue(Member member, CouponCampaign campaign, Instant issuedAt) {
    return new MemberCoupon(member, campaign, issuedAt, campaign.resolveValidUntil(issuedAt),
            campaign.getDiscountType(), campaign.getDiscountValue(), campaign.getMaxDiscountAmount(),
            campaign.getMinimumPurchaseAmount(), campaign.getScope(), campaign.isStackable());
}
~~~

Use LAZY many-to-one mappings and a campaign-owned target list with cascade/orphan removal. Require common expiry at or after issue end; require validity days greater than zero; allow max discount only for PERCENTAGE; require non-empty unique targets only for SELECTED_PRODUCTS.

- [ ] **Step 4: Add repository signatures and run the focused suite.**

Provide findByIdAndStoreId, findByIdAndOwnerType, and findByCampaignIdAndMemberId. Keep wallet mapping outside entity collections. Run the Step 2 command; expected BUILD SUCCESSFUL.

- [ ] **Step 5: Commit the foundation.**

~~~powershell
git add backend/src/main/resources/db/migration/V9__add_coupon_campaigns_and_member_coupons.sql backend/src/main/java/com/sweet/market/coupon backend/src/test/java/com/sweet/market/coupon/domain/CouponCampaignTest.java backend/src/test/java/com/sweet/market/store/migration/CouponMigrationTest.java backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
git commit -m "feat: add coupon campaign domain foundation"
~~~

### Task 2: Store-owner and administrator campaign management APIs

**Files:**
- Create: backend/src/main/java/com/sweet/market/coupon/application/CouponCampaignService.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/CouponCampaignController.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/AdminCouponCampaignController.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/CouponCampaignCreateRequest.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/CouponCampaignUpdateRequest.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/CouponCampaignSearchRequest.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/CouponCampaignResponse.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/CouponTargetProductResponse.java
- Modify: backend/src/main/java/com/sweet/market/common/error/ErrorCode.java
- Test: backend/src/test/java/com/sweet/market/coupon/CouponCampaignApiTest.java

**Interfaces:**
- Produces createStoreCampaign(memberId, storeId, request), createPlatformCampaign(request), store/admin findPage, find, update, schedule, pause, resume, and end.
- Store endpoints are /api/stores/{storeId}/coupon-campaigns; platform endpoints are /api/admin/coupon-campaigns.
- Store commands use requireActiveBusinessOwner. SecurityConfig already limits /api/admin/** to ADMIN.

- [ ] **Step 1: Write failing ownership and validation API tests.**

Cover active business owner selected/all-product creation, platform selected targets across two stores, manager/outsider/personal/inactive-store denial, invalid target IDs/status, invalid validity-policy combinations, and administrator-only platform commands.

~~~
@Test
void 관리자는_여러_상점_상품을_대상으로_플랫폼_쿠폰을_예약한다() throws Exception {
    createPlatformCampaign(adminToken, "SELECTED_PRODUCTS", List.of(firstProductId, secondProductId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.ownerType").value("PLATFORM"))
            .andExpect(jsonPath("$.data.targetCount").value(2));
}
~~~

- [ ] **Step 2: Run the API class to verify endpoint absence.**

~~~powershell
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponCampaignApiTest'
~~~

Expected: FAIL because coupon campaign controller/service types are absent.

- [ ] **Step 3: Implement KST request conversion and bounded target resolution.**

~~~
public record CouponCampaignCreateRequest(
        @NotNull CouponScope scope, @NotNull CouponDiscountType discountType,
        @PositiveOrZero long discountValue, @PositiveOrZero Long maxDiscountAmount,
        @PositiveOrZero long minimumPurchaseAmount, boolean stackable,
        @NotBlank @Size(max = 100) String title, @Size(max = 200) String label,
        @NotNull LocalDateTime issueStartsAt, @NotNull LocalDateTime issueEndsAt,
        @NotNull CouponValidityType validityType, LocalDateTime commonExpiresAt,
        @Positive Integer validityDays, List<@Positive Long> productIds
) {}
~~~

Convert all LocalDateTime fields using Asia/Seoul. Store campaigns resolve targets with findAllByStoreIdAndIdIn. Platform campaigns use a bounded findAllById and may retain products from multiple stores. Reject duplicate/missing/non-purchasable IDs before aggregate creation.

- [ ] **Step 4: Implement list/detail/lifecycle controllers and error mapping.**

Return owner type, nullable store summary, KST policy dates, lifecycle/effective status, target count, and detail targets. Add COUPON_CAMPAIGN_NOT_FOUND (404) and COUPON_LIFECYCLE_NOT_ALLOWED (409); use existing validation/access errors for all other failures. Active campaigns expose only pause/end transitions.

- [ ] **Step 5: Run API tests and commit.**

~~~powershell
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponCampaignApiTest' --rerun-tasks
~~~

Expected: BUILD SUCCESSFUL.

~~~powershell
git add backend/src/main/java/com/sweet/market/coupon backend/src/main/java/com/sweet/market/common/error/ErrorCode.java backend/src/test/java/com/sweet/market/coupon/CouponCampaignApiTest.java
git commit -m "feat: manage coupon campaigns"
~~~

### Task 3: Idempotent claim, discovery, and paged wallet APIs

**Files:**
- Create: backend/src/main/java/com/sweet/market/coupon/application/CouponIssueService.java
- Create: backend/src/main/java/com/sweet/market/coupon/application/CouponIssueTransactionService.java
- Create: backend/src/main/java/com/sweet/market/coupon/query/CouponDiscoveryQueryService.java
- Create: backend/src/main/java/com/sweet/market/coupon/query/CouponWalletQueryService.java
- Create: backend/src/main/java/com/sweet/market/coupon/query/AvailableCouponCampaignRow.java
- Create: backend/src/main/java/com/sweet/market/coupon/query/MemberCouponWalletRow.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/CouponClaimController.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/AvailableCouponCampaignResponse.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/AvailableCouponCampaignSearchRequest.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/MemberCouponResponse.java
- Create: backend/src/main/java/com/sweet/market/coupon/api/MemberCouponSearchRequest.java
- Modify: backend/src/main/java/com/sweet/market/coupon/repository/CouponCampaignRepository.java
- Modify: backend/src/main/java/com/sweet/market/coupon/repository/MemberCouponRepository.java
- Test: backend/src/test/java/com/sweet/market/coupon/CouponIssueApiTest.java
- Test: backend/src/test/java/com/sweet/market/coupon/CouponWalletApiTest.java

**Interfaces:**
- Produces MemberCouponResponse claim(memberId, campaignId).
- Produces Page<AvailableCouponCampaignResponse> findAvailable(memberId, request) and Page<MemberCouponResponse> findMine(memberId, request).
- Exposes GET /api/coupon-campaigns/available, POST /api/coupon-campaigns/{campaignId}/claim, and GET /api/me/coupons.

- [ ] **Step 1: Write failing claim and wallet tests.**

~~~
@Test
void 같은_캠페인을_두번_발급해도_한장만_생성하고_같은_쿠폰을_반환한다() throws Exception {
    String firstBody = claim(token, campaignId).andReturn().getResponse().getContentAsString();
    Long firstCouponId = objectMapper.readTree(firstBody).path("data").path("id").asLong();

    claim(token, campaignId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(firstCouponId));
    assertThat(countMemberCoupons(campaignId, memberId)).isEqualTo(1);
}
~~~

Cover future/paused/ended claim rejection, both validUntil calculations, available-list claimed state, member isolation, pagination, and every wallet status. Pausing after issue must return UNAVAILABLE and retain the issued row.

- [ ] **Step 2: Run buyer tests to verify endpoint absence.**

~~~powershell
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponIssueApiTest' --tests 'com.sweet.market.coupon.CouponWalletApiTest'
~~~

Expected: FAIL because the buyer coupon APIs are absent.

- [ ] **Step 3: Implement atomic idempotent issuance.**

~~~
// CouponIssueService itself is not transactional: a duplicate-key failure must
// roll back only the nested attempt, leaving this method able to re-read.
public MemberCouponResponse claim(Long memberId, Long campaignId) {
    MemberCoupon existing = memberCouponRepository.findByCampaignIdAndMemberId(campaignId, memberId).orElse(null);
    if (existing != null) return MemberCouponResponse.from(existing, now());
    try {
        return MemberCouponResponse.from(issueTransactionService.issue(memberId, campaignId, now()), now());
    } catch (DataIntegrityViolationException duplicate) {
        return MemberCouponResponse.from(findExistingDuplicate(campaignId, memberId, duplicate), now());
    }
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public MemberCoupon issue(Long memberId, Long campaignId, Instant issuedAt) {
    CouponCampaign campaign = campaignRepository.findById(campaignId).orElseThrow(this::campaignNotFound);
    campaign.requireClaimable(issuedAt);
    return memberCouponRepository.saveAndFlush(MemberCoupon.issue(findMember(memberId), campaign, issuedAt));
}
~~~

CouponIssueTransactionService is a separate Spring bean so REQUIRES_NEW is applied. The outer service treats only the named campaign/member unique-constraint violation as idempotent, then re-reads the committed row after the nested transaction rolls back; every other persistence failure is rethrown. Do not inject coupon services into cart or order services.

- [ ] **Step 4: Implement bounded buyer queries.**

AvailableCouponCampaignRow and MemberCouponWalletRow are explicit repository projections containing all fields required by their response plus the campaign lifecycle/effective-state inputs. Available campaigns filter to current active issue windows and include one viewer-specific claimed existence predicate. Wallet queries filter by member in SQL, paginate by issuedAt/id descending, and map status with one Clock value. Return a buyer-safe unavailabilityReason only for UNAVAILABLE. Do not load target or coupon collections per row.

- [ ] **Step 5: Run buyer tests and commit.**

~~~powershell
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponIssueApiTest' --tests 'com.sweet.market.coupon.CouponWalletApiTest' --rerun-tasks
~~~

Expected: BUILD SUCCESSFUL.

~~~powershell
git add backend/src/main/java/com/sweet/market/coupon backend/src/test/java/com/sweet/market/coupon/CouponIssueApiTest.java backend/src/test/java/com/sweet/market/coupon/CouponWalletApiTest.java
git commit -m "feat: issue coupons and add buyer wallet APIs"
~~~

### Task 4: Buyer, store-owner, and administrator coupon interfaces

**Files:**
- Create: web/src/features/coupons/couponApi.ts
- Create: web/src/pages/MyCouponsPage.tsx
- Create: web/src/pages/CouponCampaignWorkspacePage.tsx
- Create: web/src/pages/CouponCampaignDetailPage.tsx
- Create: web/src/pages/AdminCouponCampaignsPage.tsx
- Modify: web/src/app/router.tsx
- Modify: web/src/pages/MyStorePage.tsx
- Modify: web/src/shared/layout/Shell.tsx
- Modify: web/src/shared/styles.css

**Interfaces:**
- Produces CouponCampaign, CouponCampaignInput, AvailableCouponCampaign, MemberCoupon, and couponQueryKeys.
- Adds /me/coupons, /me/store/coupons, /me/store/coupons/:storeId/:campaignId, and /admin/coupons.
- Consumes server-returned eligibility and never computes a discount or status in the browser.

- [ ] **Step 1: Define client contracts and request functions.**

~~~
export type CouponValidityType = 'COMMON_EXPIRY' | 'DAYS_FROM_ISSUANCE';
export type MemberCouponStatus = 'ISSUED' | 'USED' | 'EXPIRED' | 'UNAVAILABLE';

export type MemberCoupon = {
  id: number; campaignId: number; title: string; source: 'PLATFORM' | 'STORE';
  storeName: string | null; discountText: string; minimumPurchaseAmount: number;
  stackable: boolean; validUntil: string; status: MemberCouponStatus;
  unavailabilityReason: string | null;
};
~~~

Define fetch/create/update/transition functions for store and admin owners plus available-list, claim, and wallet requests. Query keys distinguish available filters, wallet filters, store ID, admin list, and detail.

- [ ] **Step 2: Implement the buyer wallet.**

MyCouponsPage renders claimable campaigns and wallet results independently. Claim mutation disables the selected button, presents the returned issue result, and invalidates available and wallet query keys. Wallet filters render ISSUED, USED, EXPIRED, and UNAVAILABLE; unavailable cards show the server reason. Add an authenticated 내 쿠폰 navigation link in Shell.

- [ ] **Step 3: Implement owner/admin campaign pages.**

Follow PromotionWorkspacePage and PromotionDetailPage patterns. Store owners select an active BUSINESS store with OWNER role; other users see access guidance. The store form uses existing owner catalog search for targets. The platform admin form permits a cross-store searchable product selection. Show max-discount fields only for PERCENTAGE and show exactly the selected validity input. Lifecycle mutations invalidate owner lists/details, available campaigns, and wallet queries.

- [ ] **Step 4: Add routes and responsive styles.**

Wrap buyer/store routes in RequireAuth and the admin route in RequireAdmin. Add 쿠폰 to MyStorePage owner links. Use the existing operational table style for admin; stack card metadata on narrow screens without hiding expiry or status.

- [ ] **Step 5: Verify and commit the web changes.**

~~~powershell
cd web
npm run build
~~~

Expected: exit code 0.

~~~powershell
git add web/src/features/coupons web/src/pages/MyCouponsPage.tsx web/src/pages/CouponCampaignWorkspacePage.tsx web/src/pages/CouponCampaignDetailPage.tsx web/src/pages/AdminCouponCampaignsPage.tsx web/src/app/router.tsx web/src/pages/MyStorePage.tsx web/src/shared/layout/Shell.tsx web/src/shared/styles.css
git commit -m "feat: add coupon campaign and wallet interfaces"
~~~

### Task 5: Query-budget, compatibility, and release verification

**Files:**
- Create: backend/src/test/java/com/sweet/market/coupon/CouponQueryOptimizationTest.java
- Modify: backend/src/test/java/com/sweet/market/jpalab/QueryOptimizationTestSupport.java only if a reusable SQL assertion is necessary
- Create: docs/superpowers/handoffs/2026-07-14-milestone-26-coupon-campaigns-and-standard-coupon-issuance-handoff.md

**Interfaces:**
- Consumes all final coupon APIs and existing M25 order/cart services.
- Produces release evidence that coupon reads stay bounded and checkout remains coupon-free.

- [ ] **Step 1: Write query and compatibility regressions.**

Seed more rows than a page. Assert wallet and available-campaign pages avoid target-collection fetches and per-card issued-coupon queries. Claim a coupon, then assert direct order and cart checkout continue to create only M25 promotion price snapshots.

~~~
@Test
void 쿠폰지갑_한페이지는_캠페인별_N플러스일_조회없이_반환한다() {
    Page<MemberCouponResponse> page = couponWalletQueryService.findMine(memberId, request);

    assertThat(page.getContent()).hasSize(20);
    assertThat(queryOptimizationTestSupport.getCollectionFetchCount()).isZero();
}
~~~

- [ ] **Step 2: Run the focused compatibility command.**

~~~powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='4'
.\gradlew.bat test --tests 'com.sweet.market.coupon.*' --tests 'com.sweet.market.promotion.*' --tests 'com.sweet.market.cart.*' --tests 'com.sweet.market.order.*' --tests 'com.sweet.market.payment.*' --rerun-tasks
~~~

Expected: BUILD SUCCESSFUL; claims are idempotent and checkout receives no coupon behavior.

- [ ] **Step 3: Capture query-plan evidence.**

Use an isolated PostgreSQL database with more wallet/campaign rows than a page. Capture EXPLAIN (ANALYZE, BUFFERS) for available campaigns and wallet pages, documenting owner/period, target, and member/status/expiry index use, deterministic ordering, and no per-row target/claim reads. Add an index only if observed evidence requires it.

- [ ] **Step 4: Run final verification.**

~~~powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='4'
.\gradlew.bat test --rerun-tasks

cd ..\web
npm run build

cd ..
git diff --check
~~~

Expected: backend BUILD SUCCESSFUL, web exit 0, and no diff-check diagnostics.

- [ ] **Step 5: Write handoff and commit final evidence.**

Record endpoints, ownership/validity/idempotency rules, test totals, query-plan evidence, and deferred M27 capacity/M28 redemption work.

~~~powershell
git add backend/src/test/java/com/sweet/market/coupon/CouponQueryOptimizationTest.java backend/src/test/java/com/sweet/market/jpalab/QueryOptimizationTestSupport.java docs/superpowers/handoffs/2026-07-14-milestone-26-coupon-campaigns-and-standard-coupon-issuance-handoff.md
git commit -m "test: verify coupon campaign compatibility"
~~~

## Plan Self-Review

- **Spec coverage:** Task 1 covers ownership, targets, validity modes, snapshots, and lifecycle. Task 2 covers owner/admin management. Task 3 covers idempotent issue, discovery, and all wallet states. Task 4 covers buyer/store/admin web surfaces. Task 5 protects query shape and the M26 checkout boundary.
- **Placeholder scan:** Every task contains exact files, interfaces, test behaviors, commands, expected outcomes, and a commit boundary.
- **Type consistency:** CouponCampaign provides live lifecycle and resolveValidUntil; MemberCoupon provides issued snapshots and walletStatus; MemberCouponStatus is shared by backend and TypeScript. Store APIs remain owner-scoped, platform APIs remain under /api/admin, and buyer APIs remain authenticated.
