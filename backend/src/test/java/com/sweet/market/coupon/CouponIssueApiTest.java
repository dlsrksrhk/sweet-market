package com.sweet.market.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.coupon.api.MemberCouponResponse;
import com.sweet.market.coupon.application.CouponIssueService;
import com.sweet.market.coupon.application.CouponIssueTransactionService;
import com.sweet.market.coupon.application.issuance.CouponIssuanceGate;
import com.sweet.market.coupon.application.issuance.CouponIssuanceGateResult;
import com.sweet.market.coupon.application.issuance.CouponIssuanceReservation;
import com.sweet.market.coupon.application.issuance.CouponIssuanceGateUnavailableException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.application.issuance.ReservationType;
import com.sweet.market.coupon.repository.CouponCampaignRepository;
import com.sweet.market.coupon.repository.MemberCouponRepository;
import com.sweet.market.support.IntegrationTestSupport;

class CouponIssueApiTest extends IntegrationTestSupport {

    @Autowired
    private CouponIssueService couponIssueService;

    @MockitoSpyBean
    private CouponIssueTransactionService issueTransactionService;

    @MockitoSpyBean
    private CouponIssuanceGate issuanceGate;

    @MockitoSpyBean
    private CouponCampaignRepository campaignRepository;

    @MockitoSpyBean
    private MemberCouponRepository memberCouponRepository;

    @Test
    void 같은_캠페인을_두번_발급해도_한장만_생성하고_같은_쿠폰을_반환한다() throws Exception {
        String token = signupAndLogin("coupon-claim@example.com");
        Long campaignId = activeCampaign();

        String firstBody = claim(token, campaignId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long firstCouponId = objectMapper.readTree(firstBody).path("data").path("id").asLong();

        claim(token, campaignId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstCouponId));
        assertThat(countMemberCoupons(campaignId, memberId("coupon-claim@example.com"))).isEqualTo(1);
    }

    @Test
    void 동시에_발급하면_유니크_충돌후_기존_쿠폰을_재조회해_반환한다() throws Exception {
        signupAndLogin("coupon-concurrent-claim@example.com");
        Long memberId = memberId("coupon-concurrent-claim@example.com");
        Long campaignId = activeCampaign();
        CountDownLatch issueAttempts = new CountDownLatch(2);
        doAnswer(invocation -> {
            issueAttempts.countDown();
            assertThat(issueAttempts.await(5, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(issueTransactionService).issue(anyLong(), anyLong(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MemberCouponResponse> first = executor.submit(() -> couponIssueService.claim(memberId, campaignId));
            Future<MemberCouponResponse> second = executor.submit(() -> couponIssueService.claim(memberId, campaignId));

            assertThat(first.get(10, TimeUnit.SECONDS).id()).isEqualTo(second.get(10, TimeUnit.SECONDS).id());
        } finally {
            executor.shutdownNow();
        }

        assertThat(countMemberCoupons(campaignId, memberId)).isEqualTo(1);
        verify(issueTransactionService, times(2)).issue(anyLong(), anyLong(), any());
    }

    @Test
    void 공통만료일과_발급일기준_유효기간으로_쿠폰을_발급한다() throws Exception {
        String token = signupAndLogin("coupon-validity@example.com");
        Long commonExpiryCampaign = activeCampaign("COMMON_EXPIRY", 5);
        Long issuanceDaysCampaign = activeCampaign("DAYS_FROM_ISSUANCE", 3);

        claim(token, commonExpiryCampaign).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validUntil").exists());
        claim(token, issuanceDaysCampaign).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validUntil").exists());
        assertThat(jdbcTemplate.queryForObject("select valid_until = (select common_expires_at from coupon_campaigns where id = ?) from member_coupons where coupon_campaign_id = ?", Boolean.class, commonExpiryCampaign, commonExpiryCampaign)).isTrue();
        assertThat(jdbcTemplate.queryForObject("select valid_until - issued_at = interval '3 days' from member_coupons where coupon_campaign_id = ?", Boolean.class, issuanceDaysCampaign)).isTrue();
    }

    @Test
    void 미래와_일시중지와_종료된_캠페인은_발급할_수_없다() throws Exception {
        String token = signupAndLogin("coupon-rejection@example.com");
        Long futureCampaign = campaign(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1), LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(2), "DAYS_FROM_ISSUANCE", 7);
        schedule(futureCampaign);
        Long pausedCampaign = activeCampaign();
        pause(pausedCampaign);
        Long endedCampaign = activeCampaign();
        end(endedCampaign);

        claim(token, futureCampaign).andExpect(status().isConflict());
        claim(token, pausedCampaign).andExpect(status().isConflict());
        claim(token, endedCampaign).andExpect(status().isConflict());
    }

    @Test
    void 선착순_한도를_넘는_동시_발급은_정확히_한도만_성공한다() throws Exception {
        Long campaignId = activeCampaignWithLimit(5);
        List<Long> memberIds = createMembers(20, "first-come-concurrent");

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
        signupAndLogin("first-come-owner@example.com");
        Long firstMemberId = memberId("first-come-owner@example.com");
        couponIssueService.claim(firstMemberId, campaignId);

        assertThat(couponIssueService.claim(firstMemberId, campaignId).campaignId()).isEqualTo(campaignId);
        signupAndLogin("first-come-late@example.com");
        assertThatThrownBy(() -> couponIssueService.claim(memberId("first-come-late@example.com"), campaignId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED);
    }

    @Test
    void 기존_발급_회원은_일시중지와_종료후에도_기존_쿠폰을_받는다() throws Exception {
        Long campaignId = activeCampaignWithLimit(1);
        signupAndLogin("issued-after-lifecycle@example.com");
        Long issuedMemberId = memberId("issued-after-lifecycle@example.com");
        Long couponId = couponIssueService.claim(issuedMemberId, campaignId).id();

        pause(campaignId);
        assertThat(couponIssueService.claim(issuedMemberId, campaignId).id()).isEqualTo(couponId);
        end(campaignId);
        assertThat(couponIssueService.claim(issuedMemberId, campaignId).id()).isEqualTo(couponId);
    }

    @Test
    void 예약_확정에_실패하면_예약을_반납해_다른_회원이_발급할_수_있다() throws Exception {
        Long campaignId = activeCampaignWithLimit(1);
        signupAndLogin("reservation-failure-first@example.com");
        signupAndLogin("reservation-failure-second@example.com");
        Long firstMemberId = memberId("reservation-failure-first@example.com");
        Long secondMemberId = memberId("reservation-failure-second@example.com");
        CouponIssuanceReservation reservation = new CouponIssuanceReservation(campaignId, firstMemberId, "release-token");
        doReturn(CouponIssuanceGateResult.reserved(reservation))
                .when(issuanceGate).reserve(eq(campaignId), eq(firstMemberId), anyInt(), anyInt(), any(), any());
        doNothing().when(issuanceGate).release(same(reservation), any());
        doThrow(new IllegalStateException("확정 실패"))
                .when(issueTransactionService).confirmLimitedIssue(anyLong(), anyLong(), any());

        assertThatThrownBy(() -> couponIssueService.claim(firstMemberId, campaignId))
                .isInstanceOf(IllegalStateException.class);
        verify(issuanceGate, times(1)).release(same(reservation), any());
        verify(issuanceGate, never()).complete(any(), any());

        reset(issueTransactionService);
        assertThat(couponIssueService.claim(secondMemberId, campaignId).campaignId()).isEqualTo(campaignId);
        assertThat(memberCouponCount(campaignId)).isEqualTo(1);
    }

    @Test
    void 예약_확정_성공시_같은_토큰을_한번만_완료한다() throws Exception {
        Long campaignId = activeCampaignWithLimit(1);
        signupAndLogin("reservation-complete@example.com");
        Long memberId = memberId("reservation-complete@example.com");
        CouponIssuanceReservation reservation = new CouponIssuanceReservation(campaignId, memberId, "complete-token");
        doReturn(CouponIssuanceGateResult.reserved(reservation))
                .when(issuanceGate).reserve(anyLong(), anyLong(), anyInt(), anyInt(), any(), any());
        doNothing().when(issuanceGate).complete(same(reservation), any());

        assertThat(couponIssueService.claim(memberId, campaignId).campaignId()).isEqualTo(campaignId);

        verify(issuanceGate, times(1)).complete(same(reservation), any());
        verify(issuanceGate, never()).release(any(), any());
    }

    @Test
    void 레디스_소진응답_사이에_비활성화된_캠페인은_수명주기_충돌을_반환한다() throws Exception {
        Long campaignId = activeCampaignWithLimit(1);
        signupAndLogin("sold-out-lifecycle@example.com");
        Long memberId = memberId("sold-out-lifecycle@example.com");
        doAnswer(invocation -> {
            pause(campaignId);
            return CouponIssuanceGateResult.of(ReservationType.SOLD_OUT);
        }).when(issuanceGate).reserve(eq(campaignId), eq(memberId), anyInt(), anyInt(), any(), any());

        assertThatThrownBy(() -> couponIssueService.claim(memberId, campaignId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.COUPON_LIFECYCLE_NOT_ALLOWED);
    }

    @Test
    void 진행중_예약은_쿠폰이_확정될_때까지_재시도해_기존_쿠폰을_반환한다() throws Exception {
        Long campaignId = activeCampaignWithLimit(1);
        signupAndLogin("in-progress-retry@example.com");
        Long memberId = memberId("in-progress-retry@example.com");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> durableIssue = executor.submit(() -> {
            Thread.sleep(100);
            issueTransactionService.issue(memberId, campaignId, java.time.Instant.now());
            return null;
        });
        doReturn(CouponIssuanceGateResult.of(ReservationType.IN_PROGRESS))
                .when(issuanceGate).reserve(eq(campaignId), eq(memberId), anyInt(), anyInt(), any(), any());

        try {
            assertThat(couponIssueService.claim(memberId, campaignId).campaignId()).isEqualTo(campaignId);
        } finally {
            durableIssue.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void 데이터베이스_확정후_레디스_완료가_실패해도_쿠폰을_반환한다() throws Exception {
        Long campaignId = activeCampaignWithLimit(1);
        signupAndLogin("complete-unavailable@example.com");
        Long memberId = memberId("complete-unavailable@example.com");
        doThrow(new CouponIssuanceGateUnavailableException(new IllegalStateException("Redis unavailable")))
                .when(issuanceGate).complete(any(), any());

        assertThat(couponIssueService.claim(memberId, campaignId).campaignId()).isEqualTo(campaignId);
        assertThat(memberCouponCount(campaignId)).isEqualTo(1);
    }

    @Test
    void 비관적락_폴백은_락후_기존_쿠폰을_재조회한다() throws Exception {
        Long campaignId = activeCampaignWithLimit(1);
        signupAndLogin("fallback-existing@example.com");
        Long memberId = memberId("fallback-existing@example.com");
        Long couponId = couponIssueService.claim(memberId, campaignId).id();
        clearInvocations(campaignRepository, memberCouponRepository);

        assertThat(issueTransactionService.issueWithPessimisticLock(memberId, campaignId, java.time.Instant.now()).getId())
                .isEqualTo(couponId);

        InOrder ordering = inOrder(campaignRepository, memberCouponRepository);
        ordering.verify(campaignRepository).findByIdForIssuance(campaignId);
        ordering.verify(memberCouponRepository).findByCampaignIdAndMemberId(campaignId, memberId);
    }

    @Test
    void 레디스_게이트를_사용할_수_없으면_데이터베이스_락으로_한도만_발급한다() throws Exception {
        Long campaignId = activeCampaignWithLimit(5);
        List<Long> memberIds = createMembers(20, "fallback-concurrent");
        doThrow(new CouponIssuanceGateUnavailableException(new IllegalStateException("Redis unavailable")))
                .when(issuanceGate).reserve(anyLong(), anyLong(), anyInt(), anyInt(), any(), any());

        List<ClaimResult> results = submitTogether(memberIds, memberId -> claimResult(memberId, campaignId))
                .stream().map(this::await).toList();

        assertThat(results).filteredOn(ClaimResult::success).hasSize(5);
        assertThat(issuedCount(campaignId)).isEqualTo(5);
        assertThat(memberCouponCount(campaignId)).isEqualTo(5);
    }

    private ResultActions claim(String token, Long campaignId) throws Exception {
        return mockMvc.perform(post("/api/coupon-campaigns/{campaignId}/claim", campaignId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Long activeCampaign() throws Exception { return activeCampaign("DAYS_FROM_ISSUANCE", 7, null); }
    private Long activeCampaign(String validityType, int validityDays) throws Exception {
        return activeCampaign(validityType, validityDays, null);
    }
    private Long activeCampaignWithLimit(int issueLimit) throws Exception {
        return activeCampaign("DAYS_FROM_ISSUANCE", 7, issueLimit);
    }
    private Long activeCampaign(String validityType, int validityDays, Integer issueLimit) throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0);
        Long id = campaign(now.minusDays(1), now.plusDays(2), validityType, validityDays, issueLimit);
        schedule(id);
        return id;
    }
    private Long campaign(LocalDateTime start, LocalDateTime end, String validityType, int validityDays) throws Exception {
        return campaign(start, end, validityType, validityDays, null);
    }
    private Long campaign(LocalDateTime start, LocalDateTime end, String validityType, int validityDays, Integer issueLimit) throws Exception {
        String adminToken = adminToken();
        String validity = "COMMON_EXPIRY".equals(validityType)
                ? "\"commonExpiresAt\": \"%s\",".formatted(end.plusDays(validityDays)) : "\"validityDays\": %d,".formatted(validityDays);
        String body = """
                { "scope": "ALL_PRODUCTS", "discountType": "FIXED_AMOUNT", "discountValue": 1000,
                  "minimumPurchaseAmount": 0, "stackable": true, "title": "발급 쿠폰", "label": "발급",
                  "issueStartsAt": "%s", "issueEndsAt": "%s", "validityType": "%s", %s "issueLimit": %s, "productIds": [] }
                """.formatted(start, end, validityType, validity, issueLimit == null ? "null" : issueLimit);
        String response = mockMvc.perform(post("/api/admin/coupon-campaigns").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }
    private void schedule(Long campaignId) throws Exception { mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/schedule", campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())).andExpect(status().isOk()); }
    private void pause(Long campaignId) throws Exception { mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/pause", campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())).andExpect(status().isOk()); }
    private void end(Long campaignId) throws Exception { mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/end", campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())).andExpect(status().isOk()); }
    private String adminToken() throws Exception { if (jdbcTemplate.queryForObject("select count(*) from members where email = ?", Integer.class, "coupon-admin@example.com") == 0) signupAndLogin("coupon-admin@example.com"); jdbcTemplate.update("update members set role = 'ADMIN' where email = ?", "coupon-admin@example.com"); return login("coupon-admin@example.com"); }
    private String signupAndLogin(String email) throws Exception { mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(new SignupRequest(email, "password123", "회원")))).andExpect(status().isCreated()); return login(email); }
    private String login(String email) throws Exception { return objectMapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(new LoginRequest(email, "password123")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("accessToken").asText(); }
    private Long memberId(String email) { return jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, email); }
    private int countMemberCoupons(Long campaignId, Long memberId) { return jdbcTemplate.queryForObject("select count(*) from member_coupons where coupon_campaign_id = ? and member_id = ?", Integer.class, campaignId, memberId); }
    private List<Long> createMembers(int count, String prefix) throws Exception {
        List<Long> memberIds = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String email = "%s-%d@example.com".formatted(prefix, index);
            signupAndLogin(email);
            memberIds.add(memberId(email));
        }
        return memberIds;
    }
    private List<Future<ClaimResult>> submitTogether(List<Long> memberIds, ClaimAction action) {
        CountDownLatch ready = new CountDownLatch(memberIds.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(memberIds.size());
        List<Future<ClaimResult>> claims = memberIds.stream().map(memberId -> executor.submit(() -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return action.claim(memberId);
        })).toList();
        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return claims;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } finally {
            executor.shutdown();
        }
    }
    private ClaimResult await(Future<ClaimResult> claim) {
        try {
            return claim.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
    private ClaimResult claimResult(Long memberId, Long campaignId) {
        try {
            couponIssueService.claim(memberId, campaignId);
            return new ClaimResult(true);
        } catch (BusinessException exception) {
            return new ClaimResult(false);
        }
    }
    private int issuedCount(Long campaignId) { return jdbcTemplate.queryForObject("select issued_count from coupon_campaigns where id = ?", Integer.class, campaignId); }
    private int memberCouponCount(Long campaignId) { return jdbcTemplate.queryForObject("select count(*) from member_coupons where coupon_campaign_id = ?", Integer.class, campaignId); }

    private record ClaimResult(boolean success) { }
    @FunctionalInterface
    private interface ClaimAction { ClaimResult claim(Long memberId) throws Exception; }
}
