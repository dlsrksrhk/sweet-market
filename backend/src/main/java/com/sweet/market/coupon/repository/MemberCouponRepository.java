package com.sweet.market.coupon.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.coupon.domain.MemberCoupon;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    Optional<MemberCoupon> findByCampaignIdAndMemberId(Long campaignId, Long memberId);
}
