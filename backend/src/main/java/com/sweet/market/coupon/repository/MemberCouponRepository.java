package com.sweet.market.coupon.repository;

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
                coupon.id, campaign.id, campaign.title, campaign.label,
                coupon.discountType, coupon.discountValue, coupon.maxDiscountAmount,
                coupon.minimumPurchaseAmount, coupon.scope, coupon.stackable,
                coupon.issuedAt, coupon.validUntil, coupon.status,
                campaign.lifecycleStatus, campaign.issueStartsAt, campaign.issueEndsAt)
            from MemberCoupon coupon join coupon.campaign campaign
            where coupon.member.id = :memberId
            """, countQuery = """
            select count(coupon) from MemberCoupon coupon where coupon.member.id = :memberId
            """)
    Page<MemberCouponWalletRow> findWalletByMemberId(@Param("memberId") Long memberId, Pageable pageable);
}
