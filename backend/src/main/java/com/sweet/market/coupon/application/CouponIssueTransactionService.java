package com.sweet.market.coupon.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.coupon.domain.CouponDomainError;
import com.sweet.market.coupon.domain.MemberCoupon;
import com.sweet.market.coupon.repository.CouponCampaignRepository;
import com.sweet.market.coupon.repository.MemberCouponRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.operations.coupon.CouponOutcomeEventFactory;
import com.sweet.market.operations.event.OperationalEventRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CouponIssueTransactionService {
    private final CouponCampaignRepository campaignRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final MemberRepository memberRepository;
    private final OperationalEventRecorder operationalEventRecorder;
    private final CouponOutcomeEventFactory outcomeEventFactory;

    public CouponIssueTransactionService(
            CouponCampaignRepository campaignRepository,
            MemberCouponRepository memberCouponRepository,
            MemberRepository memberRepository,
            OperationalEventRecorder operationalEventRecorder,
            CouponOutcomeEventFactory outcomeEventFactory
    ) {
        this.campaignRepository = campaignRepository;
        this.memberCouponRepository = memberCouponRepository;
        this.memberRepository = memberRepository;
        this.operationalEventRecorder = operationalEventRecorder;
        this.outcomeEventFactory = outcomeEventFactory;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MemberCoupon issue(Long memberId, Long campaignId, Instant issuedAt) {
        CouponCampaign campaign = findCampaign(campaignId);
        requireClaimable(campaign, issuedAt);
        if (campaignRepository.incrementUnlimitedIssuedCount(campaignId, issuedAt) != 1) {
            throw unlimitedIssueFailure(campaignId, issuedAt);
        }
        campaign = findCampaign(campaignId);
        return saveIssuedCoupon(memberId, campaign, issuedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MemberCoupon confirmLimitedIssue(Long memberId, Long campaignId, Instant issuedAt) {
        CouponCampaign campaign = findCampaign(campaignId);
        requireClaimable(campaign, issuedAt);
        if (campaignRepository.incrementLimitedIssuedCount(campaignId, issuedAt) != 1) {
            throw capacityOrLifecycleFailure(campaignId, issuedAt);
        }
        campaign = findCampaign(campaignId);
        return saveIssuedCoupon(memberId, campaign, issuedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MemberCoupon issueWithPessimisticLock(Long memberId, Long campaignId, Instant issuedAt) {
        return issueWithPessimisticLockOutcome(memberId, campaignId, issuedAt).coupon();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PessimisticIssueResult issueWithPessimisticLockOutcome(
            Long memberId,
            Long campaignId,
            Instant issuedAt
    ) {
        CouponCampaign campaign = campaignRepository.findByIdForIssuance(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_CAMPAIGN_NOT_FOUND));
        MemberCoupon existing = memberCouponRepository.findByCampaignIdAndMemberId(campaignId, memberId).orElse(null);
        if (existing != null) return new PessimisticIssueResult(existing, false);
        requireClaimable(campaign, issuedAt);
        recordIssue(campaign);
        return new PessimisticIssueResult(saveIssuedCoupon(memberId, campaign, issuedAt), true);
    }

    private MemberCoupon saveIssuedCoupon(Long memberId, CouponCampaign campaign, Instant issuedAt) {
        MemberCoupon coupon = memberCouponRepository.saveAndFlush(
                MemberCoupon.issue(findMember(memberId), campaign, issuedAt));
        operationalEventRecorder.record(outcomeEventFactory.claimSucceeded(campaign, issuedAt));
        return coupon;
    }

    private void recordIssue(CouponCampaign campaign) {
        try {
            campaign.recordIssue();
        } catch (DomainException exception) {
            if (exception.error() == CouponDomainError.ISSUE_LIMIT_EXCEEDED) {
                throw new BusinessException(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED, exception);
            }
            throw exception;
        }
    }

    private CouponCampaign findCampaign(Long campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_CAMPAIGN_NOT_FOUND));
    }

    private void requireClaimable(CouponCampaign campaign, Instant issuedAt) {
        try {
            campaign.requireClaimable(issuedAt);
        } catch (DomainException exception) {
            CouponDomainError error = (CouponDomainError) exception.error();
            if (error == CouponDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED) {
                throw new BusinessException(ErrorCode.COUPON_LIFECYCLE_NOT_ALLOWED, exception);
            }
            throw exception;
        }
    }

    private BusinessException capacityOrLifecycleFailure(Long campaignId, Instant issuedAt) {
        CouponCampaign current = findCampaign(campaignId);
        requireClaimable(current, issuedAt);
        return new BusinessException(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED);
    }

    private BusinessException unlimitedIssueFailure(Long campaignId, Instant issuedAt) {
        CouponCampaign current = findCampaign(campaignId);
        requireClaimable(current, issuedAt);
        return new BusinessException(ErrorCode.COUPON_LIFECYCLE_NOT_ALLOWED);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public record PessimisticIssueResult(MemberCoupon coupon, boolean newlyIssued) {
    }
}
