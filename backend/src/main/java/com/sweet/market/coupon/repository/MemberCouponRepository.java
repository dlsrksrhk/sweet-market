package com.sweet.market.coupon.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.coupon.domain.MemberCoupon;
import com.sweet.market.coupon.query.MemberCouponWalletRow;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    @EntityGraph(attributePaths = "campaign")
    Optional<MemberCoupon> findByCampaignIdAndMemberId(Long campaignId, Long memberId);

    @Query(value = """
            select new com.sweet.market.coupon.query.MemberCouponWalletRow(
                coupon.id, campaign.id, campaign.title, campaign.label, campaign.ownerType, store.id, store.publicName,
                coupon.discountType, coupon.discountValue, coupon.maxDiscountAmount,
                coupon.minimumPurchaseAmount, coupon.scope, coupon.stackable,
                coupon.issuedAt, coupon.validUntil, coupon.status,
                campaign.lifecycleStatus, campaign.issueStartsAt, campaign.issueEndsAt)
            from MemberCoupon coupon join coupon.campaign campaign left join campaign.store store
            where coupon.member.id = :memberId
              and (
                    :status is null
                    or (:status = 'USED'
                        and coupon.status = com.sweet.market.coupon.domain.MemberCouponStatus.USED)
                    or (:status = 'EXPIRED'
                        and coupon.status <> com.sweet.market.coupon.domain.MemberCouponStatus.USED
                        and coupon.validUntil <= :now)
                    or (:status = 'ISSUED'
                        and coupon.status <> com.sweet.market.coupon.domain.MemberCouponStatus.USED
                        and coupon.validUntil > :now
                        and campaign.lifecycleStatus <> com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED
                        and campaign.lifecycleStatus <> com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED
                        and campaign.issueStartsAt <= :now
                        and campaign.issueEndsAt > :now)
                    or (:status = 'UNAVAILABLE'
                        and coupon.status <> com.sweet.market.coupon.domain.MemberCouponStatus.USED
                        and coupon.validUntil > :now
                        and (campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED
                            or campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED
                            or campaign.issueStartsAt > :now
                            or campaign.issueEndsAt <= :now))
              )
            """, countQuery = """
            select count(coupon) from MemberCoupon coupon join coupon.campaign campaign left join campaign.store store
            where coupon.member.id = :memberId
              and (
                    :status is null
                    or (:status = 'USED'
                        and coupon.status = com.sweet.market.coupon.domain.MemberCouponStatus.USED)
                    or (:status = 'EXPIRED'
                        and coupon.status <> com.sweet.market.coupon.domain.MemberCouponStatus.USED
                        and coupon.validUntil <= :now)
                    or (:status = 'ISSUED'
                        and coupon.status <> com.sweet.market.coupon.domain.MemberCouponStatus.USED
                        and coupon.validUntil > :now
                        and campaign.lifecycleStatus <> com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED
                        and campaign.lifecycleStatus <> com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED
                        and campaign.issueStartsAt <= :now
                        and campaign.issueEndsAt > :now)
                    or (:status = 'UNAVAILABLE'
                        and coupon.status <> com.sweet.market.coupon.domain.MemberCouponStatus.USED
                        and coupon.validUntil > :now
                        and (campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED
                            or campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED
                            or campaign.issueStartsAt > :now
                            or campaign.issueEndsAt <= :now))
              )
            """)
    Page<MemberCouponWalletRow> findWalletByMemberId(
            @Param("memberId") Long memberId,
            @Param("status") String status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
