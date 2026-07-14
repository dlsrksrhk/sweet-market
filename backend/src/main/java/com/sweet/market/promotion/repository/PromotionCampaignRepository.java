package com.sweet.market.promotion.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.promotion.domain.PromotionCampaign;

public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, Long> {

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
