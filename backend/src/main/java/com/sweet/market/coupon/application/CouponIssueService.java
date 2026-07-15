package com.sweet.market.coupon.application;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.api.MemberCouponResponse;
import com.sweet.market.coupon.application.issuance.CouponIssuanceGate;
import com.sweet.market.coupon.application.issuance.CouponIssuanceGateResult;
import com.sweet.market.coupon.application.issuance.CouponIssuanceGateUnavailableException;
import com.sweet.market.coupon.application.issuance.CouponIssuanceReservation;
import com.sweet.market.coupon.application.issuance.ReservationType;
import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.coupon.domain.MemberCoupon;
import com.sweet.market.coupon.repository.CouponCampaignRepository;
import com.sweet.market.coupon.repository.MemberCouponRepository;

@Service
public class CouponIssueService {
    private static final String UNIQUE_CONSTRAINT = "uq_member_coupons_campaign_member";

    private final MemberCouponRepository memberCouponRepository;
    private final CouponIssueTransactionService issueTransactionService;
    private final CouponCampaignRepository campaignRepository;
    private final CouponIssuanceGate issuanceGate;
    private final Clock clock;

    @Autowired
    public CouponIssueService(
            MemberCouponRepository memberCouponRepository,
            CouponIssueTransactionService issueTransactionService,
            CouponCampaignRepository campaignRepository,
            CouponIssuanceGate issuanceGate
    ) {
        this(memberCouponRepository, issueTransactionService, campaignRepository, issuanceGate, Clock.systemUTC());
    }

    CouponIssueService(
            MemberCouponRepository memberCouponRepository,
            CouponIssueTransactionService issueTransactionService,
            CouponCampaignRepository campaignRepository,
            CouponIssuanceGate issuanceGate,
            Clock clock
    ) {
        this.memberCouponRepository = memberCouponRepository;
        this.issueTransactionService = issueTransactionService;
        this.campaignRepository = campaignRepository;
        this.issuanceGate = issuanceGate;
        this.clock = clock;
    }

    public MemberCouponResponse claim(Long memberId, Long campaignId) {
        Instant now = clock.instant();
        MemberCoupon existing = memberCouponRepository.findByCampaignIdAndMemberId(campaignId, memberId).orElse(null);
        if (existing != null) return MemberCouponResponse.from(existing, now);

        CouponCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_CAMPAIGN_NOT_FOUND));
        if (campaign.getIssueLimit() != null) {
            return claimLimited(memberId, campaign, now);
        }
        try {
            return MemberCouponResponse.from(issueTransactionService.issue(memberId, campaignId, now), now);
        } catch (DataIntegrityViolationException exception) {
            return MemberCouponResponse.from(findExistingDuplicate(campaignId, memberId, exception), now);
        }
    }

    private MemberCouponResponse claimLimited(Long memberId, CouponCampaign campaign, Instant now) {
        CouponIssuanceGateResult reservationResult;
        try {
            reservationResult = issuanceGate.reserve(campaign.getId(), memberId, campaign.getIssueLimit(),
                    campaign.getIssuedCount(), campaign.getIssueEndsAt(), now);
        } catch (CouponIssuanceGateUnavailableException exception) {
            return MemberCouponResponse.from(
                    issueTransactionService.issueWithPessimisticLock(memberId, campaign.getId(), now), now);
        }

        if (reservationResult.type() == ReservationType.SOLD_OUT) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED);
        }
        if (reservationResult.type() != ReservationType.RESERVED) {
            return MemberCouponResponse.from(findExistingReservedCoupon(campaign.getId(), memberId), now);
        }

        CouponIssuanceReservation reservation = reservationResult.reservation();
        MemberCoupon coupon;
        try {
            coupon = issueTransactionService.confirmLimitedIssue(memberId, campaign.getId(), now);
        } catch (DataIntegrityViolationException exception) {
            release(reservation, now, exception);
            return MemberCouponResponse.from(findExistingDuplicate(campaign.getId(), memberId, exception), now);
        } catch (RuntimeException exception) {
            release(reservation, now, exception);
            throw exception;
        }

        issuanceGate.complete(reservation, now);
        return MemberCouponResponse.from(coupon, now);
    }

    private MemberCoupon findExistingReservedCoupon(Long campaignId, Long memberId) {
        return memberCouponRepository.findByCampaignIdAndMemberId(campaignId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED));
    }

    private void release(CouponIssuanceReservation reservation, Instant now, RuntimeException failure) {
        try {
            issuanceGate.release(reservation, now);
        } catch (RuntimeException releaseFailure) {
            failure.addSuppressed(releaseFailure);
        }
    }

    private MemberCoupon findExistingDuplicate(Long campaignId, Long memberId, DataIntegrityViolationException exception) {
        if (!isCampaignMemberDuplicate(exception)) throw exception;
        return memberCouponRepository.findByCampaignIdAndMemberId(campaignId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_CAMPAIGN_NOT_FOUND, exception));
    }

    private boolean isCampaignMemberDuplicate(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) return true;
            if (current instanceof SQLException sqlException
                    && sqlException.getMessage() != null && sqlException.getMessage().contains(UNIQUE_CONSTRAINT)) return true;
            current = current.getCause();
        }
        return false;
    }
}
