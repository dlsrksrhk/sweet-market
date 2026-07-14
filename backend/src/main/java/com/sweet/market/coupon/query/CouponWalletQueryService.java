package com.sweet.market.coupon.query;

import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.coupon.api.MemberCouponResponse;
import com.sweet.market.coupon.api.MemberCouponSearchRequest;
import com.sweet.market.coupon.repository.MemberCouponRepository;

@Service
public class CouponWalletQueryService {
    private final MemberCouponRepository memberCouponRepository;
    private final Clock clock;

    @Autowired
    public CouponWalletQueryService(MemberCouponRepository memberCouponRepository) {
        this(memberCouponRepository, Clock.systemUTC());
    }

    CouponWalletQueryService(MemberCouponRepository memberCouponRepository, Clock clock) {
        this.memberCouponRepository = memberCouponRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<MemberCouponResponse> findMine(Long memberId, MemberCouponSearchRequest request) {
        Instant now = clock.instant();
        PageRequest page = PageRequest.of(request.resolvedPage(), request.resolvedSize(),
                Sort.by(Sort.Order.desc("issuedAt"), Sort.Order.desc("id")));
        String status = request.status() == null ? null : request.status().name();
        return memberCouponRepository.findWalletByMemberId(memberId, status, now, page)
                .map(row -> MemberCouponResponse.from(row, now));
    }
}
