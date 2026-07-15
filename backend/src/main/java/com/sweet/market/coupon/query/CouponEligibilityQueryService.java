package com.sweet.market.coupon.query;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.api.EligibleMemberCouponResponse;
import com.sweet.market.coupon.application.CouponRedemptionService;
import com.sweet.market.coupon.domain.MemberCoupon;
import com.sweet.market.coupon.repository.MemberCouponRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.promotion.application.PromotionPrice;
import com.sweet.market.promotion.application.PromotionPricingService;

@Service
public class CouponEligibilityQueryService {
    private final MemberCouponRepository memberCouponRepository;
    private final ProductRepository productRepository;
    private final PromotionPricingService promotionPricingService;
    private final CouponRedemptionService couponRedemptionService;
    private final Clock clock;

    @Autowired
    public CouponEligibilityQueryService(MemberCouponRepository memberCouponRepository, ProductRepository productRepository,
                                         PromotionPricingService promotionPricingService, CouponRedemptionService couponRedemptionService) {
        this(memberCouponRepository, productRepository, promotionPricingService, couponRedemptionService, Clock.systemUTC());
    }

    CouponEligibilityQueryService(MemberCouponRepository memberCouponRepository, ProductRepository productRepository,
                                  PromotionPricingService promotionPricingService, CouponRedemptionService couponRedemptionService,
                                  Clock clock) {
        this.memberCouponRepository = memberCouponRepository;
        this.productRepository = productRepository;
        this.promotionPricingService = promotionPricingService;
        this.couponRedemptionService = couponRedemptionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EligibleMemberCouponResponse> findEligible(Long memberId, Long productId) {
        Product product = productRepository.findWithStoreById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        PromotionPrice promotion = promotionPricingService.quote(product);
        Instant now = clock.instant();
        return memberCouponRepository.findEligibleByMemberId(memberId, now).stream()
                .map(coupon -> eligible(coupon, product, promotion, now))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<EligibleMemberCouponResponse> eligible(MemberCoupon coupon, Product product, PromotionPrice promotion, Instant now) {
        try {
            return Optional.of(EligibleMemberCouponResponse.from(coupon, couponRedemptionService.quote(coupon, product, promotion, now)));
        } catch (BusinessException exception) {
            if (isIneligibleForProduct(exception.errorCode())) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private boolean isIneligibleForProduct(ErrorCode errorCode) {
        return errorCode == ErrorCode.MEMBER_COUPON_TARGET_MISMATCH
                || errorCode == ErrorCode.MEMBER_COUPON_MINIMUM_PURCHASE_NOT_MET
                || errorCode == ErrorCode.MEMBER_COUPON_PROMOTION_STACKING_NOT_ALLOWED;
    }
}
