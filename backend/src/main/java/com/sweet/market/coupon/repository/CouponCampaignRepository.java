package com.sweet.market.coupon.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.coupon.domain.CouponCampaignOwnerType;

public interface CouponCampaignRepository extends JpaRepository<CouponCampaign, Long> {

    Optional<CouponCampaign> findByIdAndStoreId(Long id, Long storeId);

    Optional<CouponCampaign> findByIdAndOwnerType(Long id, CouponCampaignOwnerType ownerType);

    @EntityGraph(attributePaths = {"store", "targets", "targets.product"})
    Optional<CouponCampaign> findWithDetailsByIdAndStoreId(Long id, Long storeId);

    @EntityGraph(attributePaths = {"store", "targets", "targets.product"})
    Optional<CouponCampaign> findWithDetailsByIdAndOwnerType(Long id, CouponCampaignOwnerType ownerType);

    @Query(value = """
            select campaign from CouponCampaign campaign
            where campaign.ownerType = :ownerType and (:storeId is null or campaign.store.id = :storeId)
              and campaign.issueEndsAt >= :periodFrom and campaign.issueStartsAt <= :periodTo
              and (:statusProvided = false
                   or (:status = 'PAUSED' and campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED)
                   or (:status = 'ENDED' and (campaign.lifecycleStatus = com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED or (campaign.lifecycleStatus not in (com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED, com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED) and campaign.issueEndsAt <= :now)))
                   or (:status = 'SCHEDULED' and campaign.lifecycleStatus not in (com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED, com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED) and campaign.issueStartsAt > :now)
                   or (:status = 'ACTIVE' and campaign.lifecycleStatus not in (com.sweet.market.coupon.domain.CouponLifecycleStatus.PAUSED, com.sweet.market.coupon.domain.CouponLifecycleStatus.ENDED) and campaign.issueStartsAt <= :now and campaign.issueEndsAt > :now))
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
    Page<CouponCampaign> search(
            @Param("ownerType") CouponCampaignOwnerType ownerType, @Param("storeId") Long storeId,
            @Param("statusProvided") boolean statusProvided, @Param("status") String status,
            @Param("periodFrom") Instant periodFrom, @Param("periodTo") Instant periodTo, @Param("now") Instant now,
            Pageable pageable
    );
}
