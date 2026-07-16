package com.sweet.market.promotion.repository;

import com.sweet.market.promotion.domain.PromotionCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, Long> {

    @EntityGraph(attributePaths = {"store", "targets", "targets.product"})
    @Query("""
            select distinct campaign
            from PromotionCampaign campaign
            left join campaign.targets target
            where campaign.store.id = :storeId
              and campaign.lifecycleStatus in (
                    com.sweet.market.promotion.domain.PromotionLifecycleStatus.DRAFT,
                    com.sweet.market.promotion.domain.PromotionLifecycleStatus.SCHEDULED
              )
              and campaign.startAt <= :now
              and campaign.endAt > :now
              and campaign.store.type = com.sweet.market.store.domain.StoreType.BUSINESS
              and campaign.store.status = com.sweet.market.store.domain.StoreStatus.ACTIVE
              and (campaign.scope = com.sweet.market.promotion.domain.PromotionScope.STORE_WIDE
                   or target.product.id = :productId)
            """)
    List<PromotionCampaign> findActiveApplicableByProductId(
            @Param("storeId") Long storeId,
            @Param("productId") Long productId,
            @Param("now") Instant now
    );

    @EntityGraph(attributePaths = {"store", "targets", "targets.product"})
    @Query("""
            select distinct campaign
            from PromotionCampaign campaign
            left join campaign.targets target
            where campaign.store.id in (select product.store.id from Product product where product.id in :productIds)
              and campaign.lifecycleStatus in (
                    com.sweet.market.promotion.domain.PromotionLifecycleStatus.DRAFT,
                    com.sweet.market.promotion.domain.PromotionLifecycleStatus.SCHEDULED
              )
              and campaign.startAt <= :now
              and campaign.endAt > :now
              and campaign.store.type = com.sweet.market.store.domain.StoreType.BUSINESS
              and campaign.store.status = com.sweet.market.store.domain.StoreStatus.ACTIVE
              and (campaign.scope = com.sweet.market.promotion.domain.PromotionScope.STORE_WIDE
                   or target.product.id in :productIds)
            """)
    List<PromotionCampaign> findActiveApplicableByProductIds(
            @Param("productIds") Collection<Long> productIds,
            @Param("now") Instant now
    );

    @EntityGraph(attributePaths = {"targets", "targets.product"})
    Optional<PromotionCampaign> findByIdAndStoreId(Long id, Long storeId);

    @Query(value = """
            select campaign from PromotionCampaign campaign
            where campaign.store.id = :storeId
              and campaign.endAt >= :periodFrom
              and campaign.startAt <= :periodTo
              and (
                    :statusProvided = false
                    or (:status = 'SCHEDULED'
                        and campaign.lifecycleStatus in (
                            com.sweet.market.promotion.domain.PromotionLifecycleStatus.DRAFT,
                            com.sweet.market.promotion.domain.PromotionLifecycleStatus.SCHEDULED
                        )
                        and campaign.startAt > :now)
                    or (:status = 'ACTIVE'
                        and campaign.lifecycleStatus in (
                            com.sweet.market.promotion.domain.PromotionLifecycleStatus.DRAFT,
                            com.sweet.market.promotion.domain.PromotionLifecycleStatus.SCHEDULED
                        )
                        and campaign.startAt <= :now and campaign.endAt > :now)
                    or (:status = 'PAUSED'
                        and campaign.lifecycleStatus = com.sweet.market.promotion.domain.PromotionLifecycleStatus.PAUSED)
                    or (:status = 'ENDED'
                        and (campaign.lifecycleStatus = com.sweet.market.promotion.domain.PromotionLifecycleStatus.ENDED
                            or (campaign.lifecycleStatus in (
                                    com.sweet.market.promotion.domain.PromotionLifecycleStatus.DRAFT,
                                    com.sweet.market.promotion.domain.PromotionLifecycleStatus.SCHEDULED
                                ) and campaign.endAt <= :now)))
                  )
            order by campaign.id desc
            """,
            countQuery = """
                    select count(campaign) from PromotionCampaign campaign
                    where campaign.store.id = :storeId
                      and campaign.endAt >= :periodFrom
                      and campaign.startAt <= :periodTo
                      and (
                            :statusProvided = false
                            or (:status = 'SCHEDULED'
                                and campaign.lifecycleStatus in (
                                    com.sweet.market.promotion.domain.PromotionLifecycleStatus.DRAFT,
                                    com.sweet.market.promotion.domain.PromotionLifecycleStatus.SCHEDULED
                                )
                                and campaign.startAt > :now)
                            or (:status = 'ACTIVE'
                                and campaign.lifecycleStatus in (
                                    com.sweet.market.promotion.domain.PromotionLifecycleStatus.DRAFT,
                                    com.sweet.market.promotion.domain.PromotionLifecycleStatus.SCHEDULED
                                )
                                and campaign.startAt <= :now and campaign.endAt > :now)
                            or (:status = 'PAUSED'
                                and campaign.lifecycleStatus = com.sweet.market.promotion.domain.PromotionLifecycleStatus.PAUSED)
                            or (:status = 'ENDED'
                                and (campaign.lifecycleStatus = com.sweet.market.promotion.domain.PromotionLifecycleStatus.ENDED
                                    or (campaign.lifecycleStatus in (
                                            com.sweet.market.promotion.domain.PromotionLifecycleStatus.DRAFT,
                                            com.sweet.market.promotion.domain.PromotionLifecycleStatus.SCHEDULED
                                        ) and campaign.endAt <= :now)))
                          )
                    """)
    Page<PromotionCampaign> search(
            @Param("storeId") Long storeId,
            @Param("statusProvided") boolean statusProvided,
            @Param("status") String status,
            @Param("periodFrom") Instant periodFrom,
            @Param("periodTo") Instant periodTo,
            @Param("now") Instant now,
            Pageable pageable
    );
}
