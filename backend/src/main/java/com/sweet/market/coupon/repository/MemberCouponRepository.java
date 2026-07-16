package com.sweet.market.coupon.repository;

import com.sweet.market.coupon.domain.MemberCoupon;
import com.sweet.market.coupon.query.MemberCouponWalletRow;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    @EntityGraph(attributePaths = "campaign")
    Optional<MemberCoupon> findByCampaignIdAndMemberId(Long campaignId, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"campaign", "targetProductIds"})
    @Query("""
            select coupon from MemberCoupon coupon
            where coupon.id = :couponId and coupon.member.id = :memberId
            """)
    Optional<MemberCoupon> findRedemptionTargetForUpdate(@Param("couponId") Long couponId, @Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"member", "campaign", "targetProductIds"})
    @Query("select coupon from MemberCoupon coupon where coupon.id = :couponId")
    Optional<MemberCoupon> findRedemptionTargetByIdForUpdate(@Param("couponId") Long couponId);

    @EntityGraph(attributePaths = {"campaign", "targetProductIds"})
    @Query("""
            select coupon from MemberCoupon coupon
            where coupon.member.id = :memberId
              and coupon.status = com.sweet.market.coupon.domain.MemberCouponStatus.ISSUED
              and coupon.validUntil > :now
              and not exists (
                  select reservation from CouponReservation reservation
                  where reservation.memberCoupon = coupon
                    and reservation.status = com.sweet.market.coupon.domain.CouponReservationStatus.RESERVED
              )
            order by coupon.validUntil asc, coupon.id asc
            """)
    List<MemberCoupon> findEligibleByMemberId(@Param("memberId") Long memberId, @Param("now") Instant now);

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
                        and coupon.validUntil > :now)
                    or (:status = 'UNAVAILABLE'
                        and coupon.status <> com.sweet.market.coupon.domain.MemberCouponStatus.USED
                        and coupon.validUntil > :now
                        and false)
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
                        and coupon.validUntil > :now)
                    or (:status = 'UNAVAILABLE'
                        and coupon.status <> com.sweet.market.coupon.domain.MemberCouponStatus.USED
                        and coupon.validUntil > :now
                        and false)
              )
            """)
    Page<MemberCouponWalletRow> findWalletByMemberId(
            @Param("memberId") Long memberId,
            @Param("status") String status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
