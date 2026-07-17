package com.sweet.market.coupon.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.api.MemberCouponResponse;
import com.sweet.market.coupon.application.issuance.*;
import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.coupon.domain.CouponDomainError;
import com.sweet.market.coupon.domain.MemberCoupon;
import com.sweet.market.coupon.repository.CouponCampaignRepository;
import com.sweet.market.coupon.repository.MemberCouponRepository;
import com.sweet.market.operations.coupon.CouponOutcomeEventFactory;
import com.sweet.market.operations.coupon.CouponOutcomeReason;
import com.sweet.market.operations.event.OperationalFailureRecorder;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class CouponIssueService {
    private static final String UNIQUE_CONSTRAINT = "uq_member_coupons_campaign_member";
    private static final Duration IN_PROGRESS_RETRY_INTERVAL = Duration.ofMillis(50);

    private final MemberCouponRepository memberCouponRepository;
    private final CouponIssueTransactionService issueTransactionService;
    private final CouponCampaignRepository campaignRepository;
    private final CouponIssuanceGate issuanceGate;
    private final OperationalFailureRecorder operationalFailureRecorder;
    private final CouponOutcomeEventFactory outcomeEventFactory;
    private final Clock clock;

    @Autowired
    public CouponIssueService(
            MemberCouponRepository memberCouponRepository,
            CouponIssueTransactionService issueTransactionService,
            CouponCampaignRepository campaignRepository,
            CouponIssuanceGate issuanceGate,
            OperationalFailureRecorder operationalFailureRecorder,
            CouponOutcomeEventFactory outcomeEventFactory
    ) {
        this(memberCouponRepository, issueTransactionService, campaignRepository, issuanceGate,
                operationalFailureRecorder, outcomeEventFactory, Clock.systemUTC());
    }

    CouponIssueService(
            MemberCouponRepository memberCouponRepository,
            CouponIssueTransactionService issueTransactionService,
            CouponCampaignRepository campaignRepository,
            CouponIssuanceGate issuanceGate,
            OperationalFailureRecorder operationalFailureRecorder,
            CouponOutcomeEventFactory outcomeEventFactory,
            Clock clock
    ) {
        this.memberCouponRepository = memberCouponRepository;
        this.issueTransactionService = issueTransactionService;
        this.campaignRepository = campaignRepository;
        this.issuanceGate = issuanceGate;
        this.operationalFailureRecorder = operationalFailureRecorder;
        this.outcomeEventFactory = outcomeEventFactory;
        this.clock = clock;
    }

    public MemberCouponResponse claim(Long memberId, Long campaignId) {
        Instant now = clock.instant();
        MemberCoupon existing = memberCouponRepository.findByCampaignIdAndMemberId(campaignId, memberId).orElse(null);
        if (existing != null) return alreadyClaimed(existing, now);

        CouponCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_CAMPAIGN_NOT_FOUND));
        try {
            if (campaign.getIssueLimit() != null) {
                return claimLimited(memberId, campaign, now);
            }
            try {
                return MemberCouponResponse.from(issueTransactionService.issue(memberId, campaignId, now), now);
            } catch (DataIntegrityViolationException exception) {
                return alreadyClaimed(findExistingDuplicate(campaignId, memberId, exception), now);
            }
        } catch (BusinessException exception) {
            CouponOutcomeReason reason = claimReason(exception.errorCode());
            if (reason != null) {
                recordClaimFailure(campaign, reason, clock.instant());
            }
            throw exception;
        } catch (CouponIssuanceGateUnavailableException exception) {
            recordClaimFailure(campaign, CouponOutcomeReason.UNAVAILABLE, clock.instant());
            throw exception;
        }
    }

    private MemberCouponResponse claimLimited(Long memberId, CouponCampaign campaign, Instant now) {
        ClaimReservation claimReservation = reserve(campaign, memberId, now);
        CouponIssuanceGateResult reservationResult = claimReservation.gateResult();
        Instant attemptNow = now;
        Instant retryDeadline = now.plus(CouponIssuanceGate.RESERVATION_DURATION);
        while (reservationResult.type() == ReservationType.IN_PROGRESS) {
            MemberCoupon existing = memberCouponRepository.findByCampaignIdAndMemberId(campaign.getId(), memberId).orElse(null);
            if (existing != null) return alreadyClaimed(existing, attemptNow);
            if (!attemptNow.isBefore(retryDeadline)) break;
            waitForInProgressRetry();
            attemptNow = clock.instant();
            campaign = findCampaign(campaign.getId());
            claimReservation = reserve(campaign, memberId, attemptNow);
            reservationResult = claimReservation.gateResult();
        }

        if (reservationResult.type() == ReservationType.SOLD_OUT) {
            requireClaimable(findCampaign(campaign.getId()), attemptNow);
            throw new BusinessException(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED);
        }
        if (reservationResult.type() != ReservationType.RESERVED) {
            if (claimReservation.fallbackResult() != null) {
                MemberCoupon fallbackCoupon = claimReservation.fallbackResult().coupon();
                return claimReservation.fallbackResult().newlyIssued()
                        ? MemberCouponResponse.from(fallbackCoupon, attemptNow)
                        : alreadyClaimed(fallbackCoupon, attemptNow);
            }
            return alreadyClaimed(findExistingReservedCoupon(campaign.getId(), memberId), attemptNow);
        }

        CouponIssuanceReservation reservation = reservationResult.reservation();
        MemberCoupon coupon;
        try {
            coupon = issueTransactionService.confirmLimitedIssue(memberId, campaign.getId(), attemptNow);
        } catch (DataIntegrityViolationException exception) {
            release(reservation, attemptNow, exception);
            return alreadyClaimed(findExistingDuplicate(campaign.getId(), memberId, exception), attemptNow);
        } catch (RuntimeException exception) {
            release(reservation, attemptNow, exception);
            throw exception;
        }

        try {
            issuanceGate.complete(reservation, attemptNow);
        } catch (CouponIssuanceGateUnavailableException ignored) {
            // The coupon transaction is already committed; a later claim reads that durable result first.
        }
        return MemberCouponResponse.from(coupon, attemptNow);
    }

    private MemberCouponResponse alreadyClaimed(MemberCoupon coupon, Instant occurredAt) {
        recordClaimFailure(coupon.getCampaign(), CouponOutcomeReason.ALREADY_CLAIMED, occurredAt);
        return MemberCouponResponse.from(coupon, occurredAt);
    }

    private void recordClaimFailure(
            CouponCampaign campaign,
            CouponOutcomeReason reason,
            Instant occurredAt
    ) {
        operationalFailureRecorder.recordSafely(
                outcomeEventFactory.claimFailed(campaign, reason, occurredAt));
    }

    private CouponOutcomeReason claimReason(ErrorCode errorCode) {
        return switch (errorCode) {
            case COUPON_ISSUE_LIMIT_EXCEEDED -> CouponOutcomeReason.EXHAUSTED;
            case COUPON_LIFECYCLE_NOT_ALLOWED -> CouponOutcomeReason.INACTIVE;
            case MEMBER_NOT_FOUND -> CouponOutcomeReason.INELIGIBLE;
            default -> null;
        };
    }

    private ClaimReservation reserve(CouponCampaign campaign, Long memberId, Instant now) {
        try {
            return new ClaimReservation(issuanceGate.reserve(
                    campaign.getId(), memberId, campaign.getIssueLimit(), campaign.getIssuedCount(),
                    campaign.getIssueEndsAt(), now), null);
        } catch (CouponIssuanceGateUnavailableException exception) {
            CouponIssueTransactionService.PessimisticIssueResult fallbackResult =
                    issueTransactionService.issueWithPessimisticLockOutcome(
                            memberId, campaign.getId(), now);
            return new ClaimReservation(
                    CouponIssuanceGateResult.of(ReservationType.ALREADY_ISSUED), fallbackResult);
        }
    }

    private CouponCampaign findCampaign(Long campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_CAMPAIGN_NOT_FOUND));
    }

    private void requireClaimable(CouponCampaign campaign, Instant now) {
        try {
            campaign.requireClaimable(now);
        } catch (DomainException exception) {
            if (exception.error() == CouponDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED) {
                throw new BusinessException(ErrorCode.COUPON_LIFECYCLE_NOT_ALLOWED, exception);
            }
            throw exception;
        }
    }

    private void waitForInProgressRetry() {
        try {
            Thread.sleep(IN_PROGRESS_RETRY_INTERVAL);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CouponIssuanceGateUnavailableException(exception);
        }
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
                    && sqlException.getMessage() != null && sqlException.getMessage().contains(UNIQUE_CONSTRAINT))
                return true;
            current = current.getCause();
        }
        return false;
    }

    private record ClaimReservation(
            CouponIssuanceGateResult gateResult,
            CouponIssueTransactionService.PessimisticIssueResult fallbackResult
    ) {
    }
}
