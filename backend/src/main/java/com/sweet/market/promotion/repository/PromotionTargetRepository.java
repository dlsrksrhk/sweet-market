package com.sweet.market.promotion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.promotion.domain.PromotionTarget;

public interface PromotionTargetRepository extends JpaRepository<PromotionTarget, Long> {
}
