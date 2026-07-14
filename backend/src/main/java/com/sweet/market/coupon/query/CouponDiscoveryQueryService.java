package com.sweet.market.coupon.query;

import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.coupon.api.AvailableCouponCampaignResponse;
import com.sweet.market.coupon.api.AvailableCouponCampaignSearchRequest;
import com.sweet.market.coupon.repository.CouponCampaignRepository;

@Service
public class CouponDiscoveryQueryService {
    private final CouponCampaignRepository campaignRepository;
    private final Clock clock;

    @Autowired
    public CouponDiscoveryQueryService(CouponCampaignRepository campaignRepository) {
        this(campaignRepository, Clock.systemUTC());
    }

    CouponDiscoveryQueryService(CouponCampaignRepository campaignRepository, Clock clock) {
        this.campaignRepository = campaignRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AvailableCouponCampaignResponse> findAvailable(Long memberId, AvailableCouponCampaignSearchRequest request) {
        Instant now = clock.instant();
        return campaignRepository.findAvailableForMember(memberId, now,
                PageRequest.of(request.resolvedPage(), request.resolvedSize()))
                .map(AvailableCouponCampaignResponse::from);
    }
}
