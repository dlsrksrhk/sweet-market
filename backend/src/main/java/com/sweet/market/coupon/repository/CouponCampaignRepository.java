package com.sweet.market.coupon.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.coupon.domain.CouponCampaignOwnerType;

public interface CouponCampaignRepository extends JpaRepository<CouponCampaign, Long> {

    Optional<CouponCampaign> findByIdAndStoreId(Long id, Long storeId);

    Optional<CouponCampaign> findByIdAndOwnerType(Long id, CouponCampaignOwnerType ownerType);
}
