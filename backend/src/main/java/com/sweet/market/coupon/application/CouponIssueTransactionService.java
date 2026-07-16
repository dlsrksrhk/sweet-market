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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CouponIssueTransactionService {
    private final CouponCampaignRepository campaignRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final MemberRepository memberRepository;

    public CouponIssueTransactionService(
            CouponCampaignRepository campaignRepository,
            MemberCouponRepository memberCouponRepository,
            MemberRepository memberRepository
    ) {
        this.campaignRepository = campaignRepository;
        this.memberCouponRepository = memberCouponRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MemberCoupon issue(Long memberId, Long campaignId, Instant issuedAt) {
        CouponCampaign campaign = findCampaign(campaignId);
        requireClaimable(campaign, issuedAt);
        return memberCouponRepository.saveAndFlush(MemberCoupon.issue(findMember(memberId), campaign, issuedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MemberCoupon confirmLimitedIssue(Long memberId, Long campaignId, Instant issuedAt) {
        CouponCampaign campaign = findCampaign(campaignId);
        requireClaimable(campaign, issuedAt);
        if (campaignRepository.incrementLimitedIssuedCount(campaignId, issuedAt) != 1) {
            throw capacityOrLifecycleFailure(campaignId, issuedAt);
        }
        campaign = findCampaign(campaignId);
        return memberCouponRepository.saveAndFlush(MemberCoupon.issue(findMember(memberId), campaign, issuedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MemberCoupon issueWithPessimisticLock(Long memberId, Long campaignId, Instant issuedAt) {
        CouponCampaign campaign = campaignRepository.findByIdForIssuance(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_CAMPAIGN_NOT_FOUND));
        MemberCoupon existing = memberCouponRepository.findByCampaignIdAndMemberId(campaignId, memberId).orElse(null);
        if (existing != null) return existing;
        requireClaimable(campaign, issuedAt);
        try {
            campaign.recordIssue();
        } catch (DomainException exception) {
            if (exception.error() == CouponDomainError.ISSUE_LIMIT_EXCEEDED) {
                throw new BusinessException(ErrorCode.COUPON_ISSUE_LIMIT_EXCEEDED, exception);
            }
            throw exception;
        }
        return memberCouponRepository.saveAndFlush(MemberCoupon.issue(findMember(memberId), campaign, issuedAt));
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

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
