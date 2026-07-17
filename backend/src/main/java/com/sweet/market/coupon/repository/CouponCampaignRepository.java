package com.sweet.market.coupon.repository;

import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.coupon.domain.CouponCampaignOwnerType;
import com.sweet.market.coupon.query.AvailableCouponCampaignRow;
import com.sweet.market.coupon.query.CouponCampaignSummaryRow;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CouponCampaignRepository extends JpaRepository<CouponCampaign, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CouponCampaign campaign
               set campaign.issuedCount = campaign.issuedCount + 1,
                   campaign.version = campaign.version + 1
             where campaign.id = :campaignId
               and campaign.issueLimit is null
               and campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.SCHEDULED
               and campaign.issueStartsAt <= :now and campaign.issueEndsAt > :now
            """)
    int incrementUnlimitedIssuedCount(@Param("campaignId") Long campaignId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CouponCampaign campaign
               set campaign.issuedCount = campaign.issuedCount + 1,
                   campaign.version = campaign.version + 1
             where campaign.id = :campaignId
               and campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.SCHEDULED
               and campaign.issueStartsAt <= :now and campaign.issueEndsAt > :now
               and campaign.issuedCount < campaign.issueLimit
            """)
    int incrementLimitedIssuedCount(@Param("campaignId") Long campaignId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select campaign from CouponCampaign campaign where campaign.id = :campaignId")
    Optional<CouponCampaign> findByIdForIssuance(@Param("campaignId") Long campaignId);

    Optional<CouponCampaign> findByIdAndStoreId(Long id, Long storeId);

    Optional<CouponCampaign> findByIdAndOwnerType(Long id, CouponCampaignOwnerType ownerType);

    @EntityGraph(attributePaths = {"store", "targets", "targets.product"})
    Optional<CouponCampaign> findWithDetailsByIdAndStoreId(Long id, Long storeId);

    @EntityGraph(attributePaths = {"store", "targets", "targets.product"})
    Optional<CouponCampaign> findWithDetailsByIdAndOwnerType(Long id, CouponCampaignOwnerType ownerType);

    @Query(value = """
            select new com.sweet.market.coupon.query.CouponCampaignSummaryRow(
                campaign.id, campaign.ownerType, store.id, store.publicName, campaign.scope, campaign.discountType,
                campaign.discountValue, campaign.maxDiscountAmount, campaign.minimumPurchaseAmount, campaign.stackable,
                campaign.title, campaign.label, campaign.issueStartsAt, campaign.issueEndsAt, campaign.validityType,
                campaign.commonExpiresAt, campaign.validityDays, campaign.issueLimit, campaign.issuedCount,
                campaign.lifecycleStatus, count(target.id))
            from CouponCampaign campaign left join campaign.store store left join campaign.targets target
            where campaign.ownerType = :ownerType and (:storeId is null or campaign.store.id = :storeId)
              and campaign.issueEndsAt >= :periodFrom and campaign.issueStartsAt <= :periodTo
              and (:statusProvided = false
                   or (:status = 'PAUSED' and campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED)
                   or (:status = 'ENDED' and (campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED or (campaign.lifecycleStatus not in (com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED, com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED) and campaign.issueEndsAt <= :now)))
                   or (:status = 'SCHEDULED' and campaign.lifecycleStatus not in (com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED, com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED) and campaign.issueStartsAt > :now)
                   or (:status = 'ACTIVE' and campaign.lifecycleStatus not in (com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED, com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED) and campaign.issueStartsAt <= :now and campaign.issueEndsAt > :now))
            group by campaign.id, campaign.ownerType, store.id, store.publicName, campaign.scope, campaign.discountType,
                campaign.discountValue, campaign.maxDiscountAmount, campaign.minimumPurchaseAmount, campaign.stackable,
                campaign.title, campaign.label, campaign.issueStartsAt, campaign.issueEndsAt, campaign.validityType,
                campaign.commonExpiresAt, campaign.validityDays, campaign.issueLimit, campaign.issuedCount,
                campaign.lifecycleStatus
            order by campaign.id desc
            """, countQuery = """
            select count(campaign) from CouponCampaign campaign
            where campaign.ownerType = :ownerType and (:storeId is null or campaign.store.id = :storeId)
              and campaign.issueEndsAt >= :periodFrom and campaign.issueStartsAt <= :periodTo
              and (:statusProvided = false
                   or (:status = 'PAUSED' and campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED)
                   or (:status = 'ENDED' and (campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED or (campaign.lifecycleStatus not in (com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED, com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED) and campaign.issueEndsAt <= :now)))
                   or (:status = 'SCHEDULED' and campaign.lifecycleStatus not in (com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED, com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED) and campaign.issueStartsAt > :now)
                   or (:status = 'ACTIVE' and campaign.lifecycleStatus not in (com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED, com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED) and campaign.issueStartsAt <= :now and campaign.issueEndsAt > :now))
            """)
    Page<CouponCampaignSummaryRow> search(
            @Param("ownerType") CouponCampaignOwnerType ownerType, @Param("storeId") Long storeId,
            @Param("statusProvided") boolean statusProvided, @Param("status") String status,
            @Param("periodFrom") Instant periodFrom, @Param("periodTo") Instant periodTo, @Param("now") Instant now,
            Pageable pageable
    );

    @Query(value = """
            select new com.sweet.market.coupon.query.AvailableCouponCampaignRow(
                campaign.id, campaign.ownerType, campaign.scope, campaign.discountType,
                campaign.discountValue, campaign.maxDiscountAmount, campaign.minimumPurchaseAmount,
                campaign.stackable, campaign.title, campaign.label, campaign.issueStartsAt,
                campaign.issueEndsAt, campaign.validityType, campaign.commonExpiresAt,
                campaign.validityDays, campaign.issueLimit, campaign.issuedCount, campaign.lifecycleStatus, campaign.store.id, store.publicName,
                case when exists (select 1 from MemberCoupon coupon
                    where coupon.campaign.id = campaign.id and coupon.member.id = :memberId)
                    then true else false end)
            from CouponCampaign campaign left join campaign.store store
            where campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.SCHEDULED
              and campaign.issueStartsAt <= :now and campaign.issueEndsAt > :now
              and (:source is null or campaign.ownerType = :source)
              and (:storeId is null or campaign.store.id = :storeId)
            order by campaign.id desc
            """, countQuery = """
            select count(campaign) from CouponCampaign campaign
            where campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.SCHEDULED
              and campaign.issueStartsAt <= :now and campaign.issueEndsAt > :now
              and (:source is null or campaign.ownerType = :source)
              and (:storeId is null or campaign.store.id = :storeId)
            """)
    Page<AvailableCouponCampaignRow> findAvailableForMember(
            @Param("memberId") Long memberId, @Param("now") Instant now,
            @Param("source") CouponCampaignOwnerType source, @Param("storeId") Long storeId, Pageable pageable
    );

    @Query("""
            select new com.sweet.market.coupon.query.AvailableCouponCampaignRow(
                campaign.id, campaign.ownerType, campaign.scope, campaign.discountType,
                campaign.discountValue, campaign.maxDiscountAmount, campaign.minimumPurchaseAmount,
                campaign.stackable, campaign.title, campaign.label, campaign.issueStartsAt,
                campaign.issueEndsAt, campaign.validityType, campaign.commonExpiresAt,
                campaign.validityDays, campaign.issueLimit, campaign.issuedCount, campaign.lifecycleStatus, campaign.store.id, store.publicName,
                case when exists (select 1 from MemberCoupon coupon
                    where coupon.campaign.id = campaign.id and coupon.member.id = :memberId)
                    then true else false end)
            from CouponCampaign campaign left join campaign.store store
            where campaign.id = :campaignId
              and campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.SCHEDULED
              and campaign.issueStartsAt <= :now and campaign.issueEndsAt > :now
            """)
    Optional<AvailableCouponCampaignRow> findAvailableByIdForMember(
            @Param("memberId") Long memberId, @Param("campaignId") Long campaignId, @Param("now") Instant now
    );
}
