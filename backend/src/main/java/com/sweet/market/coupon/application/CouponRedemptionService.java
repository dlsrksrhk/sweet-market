package com.sweet.market.coupon.application;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.domain.CouponDiscountType;
import com.sweet.market.coupon.domain.CouponScope;
import com.sweet.market.coupon.domain.MemberCoupon;
import com.sweet.market.coupon.domain.MemberCouponStatus;
import com.sweet.market.coupon.repository.CouponReservationRepository;
import com.sweet.market.coupon.repository.MemberCouponRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.promotion.application.PromotionPrice;

@Service
public class CouponRedemptionService {
    private final MemberCouponRepository memberCouponRepository;
    private final CouponReservationRepository couponReservationRepository;

    public CouponRedemptionService(MemberCouponRepository memberCouponRepository,
                                   CouponReservationRepository couponReservationRepository) {
        this.memberCouponRepository = memberCouponRepository;
        this.couponReservationRepository = couponReservationRepository;
    }

    public CouponDiscountQuote quote(MemberCoupon coupon, Product product, PromotionPrice promotion, Instant now) {
        requireIssuedAndValid(coupon, now);
        requireTargetMatches(coupon, product.getId());
        requireStackable(coupon, promotion.promotionId());

        long base = promotion.effectivePrice();
        if (base < coupon.getMinimumPurchaseAmount()) {
            throw new BusinessException(ErrorCode.MEMBER_COUPON_MINIMUM_PURCHASE_NOT_MET);
        }
        long discount = discount(coupon, base);
        return new CouponDiscountQuote(coupon.getId(), discount, Math.max(0L, base - discount));
    }

    public CouponDiscountQuote quoteForReservation(Long memberId, Long memberCouponId, Product product,
                                                    PromotionPrice promotion, Instant now) {
        MemberCoupon coupon = memberCouponRepository.findRedemptionTargetByIdForUpdate(memberCouponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_COUPON_NOT_FOUND));
        if (!coupon.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_COUPON_ACCESS_DENIED);
        }
        if (couponReservationRepository.existsActiveByMemberCouponId(coupon.getId())) {
            throw new BusinessException(ErrorCode.MEMBER_COUPON_ALREADY_RESERVED);
        }
        return quote(coupon, product, promotion, now);
    }

    private void requireIssuedAndValid(MemberCoupon coupon, Instant now) {
        if (coupon.getStatus() != MemberCouponStatus.ISSUED) {
            throw new BusinessException(ErrorCode.MEMBER_COUPON_NOT_ISSUED);
        }
        if (!now.isBefore(coupon.getValidUntil())) {
            throw new BusinessException(ErrorCode.MEMBER_COUPON_EXPIRED);
        }
    }

    private void requireTargetMatches(MemberCoupon coupon, Long productId) {
        if (coupon.getScope() == CouponScope.SELECTED_PRODUCTS && !coupon.getTargetProductIds().contains(productId)) {
            throw new BusinessException(ErrorCode.MEMBER_COUPON_TARGET_MISMATCH);
        }
    }

    private void requireStackable(MemberCoupon coupon, Long promotionId) {
        if (promotionId != null && !coupon.isStackable()) {
            throw new BusinessException(ErrorCode.MEMBER_COUPON_PROMOTION_STACKING_NOT_ALLOWED);
        }
    }

    private long discount(MemberCoupon coupon, long base) {
        if (coupon.getDiscountType() == CouponDiscountType.FIXED_AMOUNT) {
            return Math.min(coupon.getDiscountValue(), base);
        }
        long percentage = percentageDiscount(base, coupon.getDiscountValue());
        long capped = coupon.getMaxDiscountAmount() == null ? percentage
                : Math.min(percentage, coupon.getMaxDiscountAmount());
        return Math.max(0L, Math.min(capped, base));
    }

    private long percentageDiscount(long base, long rate) {
        if (base <= 0 || rate <= 0) {
            return 0L;
        }
        if (rate >= 100) {
            return base;
        }
        return (base / 100) * rate + ((base % 100) * rate) / 100;
    }
}
