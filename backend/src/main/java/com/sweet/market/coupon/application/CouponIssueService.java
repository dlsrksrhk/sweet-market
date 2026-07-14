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
import com.sweet.market.coupon.domain.MemberCoupon;
import com.sweet.market.coupon.repository.MemberCouponRepository;

@Service
public class CouponIssueService {
    private static final String UNIQUE_CONSTRAINT = "uq_member_coupons_campaign_member";

    private final MemberCouponRepository memberCouponRepository;
    private final CouponIssueTransactionService issueTransactionService;
    private final Clock clock;

    @Autowired
    public CouponIssueService(
            MemberCouponRepository memberCouponRepository,
            CouponIssueTransactionService issueTransactionService
    ) {
        this(memberCouponRepository, issueTransactionService, Clock.systemUTC());
    }

    CouponIssueService(
            MemberCouponRepository memberCouponRepository,
            CouponIssueTransactionService issueTransactionService,
            Clock clock
    ) {
        this.memberCouponRepository = memberCouponRepository;
        this.issueTransactionService = issueTransactionService;
        this.clock = clock;
    }

    public MemberCouponResponse claim(Long memberId, Long campaignId) {
        Instant now = clock.instant();
        MemberCoupon existing = memberCouponRepository.findByCampaignIdAndMemberId(campaignId, memberId).orElse(null);
        if (existing != null) return MemberCouponResponse.from(existing, now);
        try {
            return MemberCouponResponse.from(issueTransactionService.issue(memberId, campaignId, now), now);
        } catch (DataIntegrityViolationException exception) {
            return MemberCouponResponse.from(findExistingDuplicate(campaignId, memberId, exception), now);
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
