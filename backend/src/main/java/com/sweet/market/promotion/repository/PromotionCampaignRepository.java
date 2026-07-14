package com.sweet.market.promotion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.promotion.domain.PromotionCampaign;

public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, Long> {
}
