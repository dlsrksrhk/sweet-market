package com.sweet.market.coupon.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.domain.*;
import com.sweet.market.coupon.repository.CouponReservationRepository;
import com.sweet.market.coupon.repository.MemberCouponRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.operations.coupon.CouponOutcomeEventFactory;
import com.sweet.market.operations.coupon.CouponOutcomeReason;
import com.sweet.market.operations.event.OperationalEventRecorder;
import com.sweet.market.operations.event.OperationalFailureRecorder;
import com.sweet.market.product.domain.Product;
import com.sweet.market.promotion.application.PromotionPrice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CouponRedemptionService {
    private final MemberCouponRepository memberCouponRepository;
    private final CouponReservationRepository couponReservationRepository;
    private final CouponReservationExpiryTransactionService couponReservationExpiryTransactionService;
    private final OperationalEventRecorder operationalEventRecorder;
    private final OperationalFailureRecorder operationalFailureRecorder;
    private final CouponOutcomeEventFactory outcomeEventFactory;

    public CouponRedemptionService(MemberCouponRepository memberCouponRepository,
                                   CouponReservationRepository couponReservationRepository,
                                   CouponReservationExpiryTransactionService couponReservationExpiryTransactionService,
                                   OperationalEventRecorder operationalEventRecorder,
                                   OperationalFailureRecorder operationalFailureRecorder,
                                   CouponOutcomeEventFactory outcomeEventFactory) {
        this.memberCouponRepository = memberCouponRepository;
        this.couponReservationRepository = couponReservationRepository;
        this.couponReservationExpiryTransactionService = couponReservationExpiryTransactionService;
        this.operationalEventRecorder = operationalEventRecorder;
        this.operationalFailureRecorder = operationalFailureRecorder;
        this.outcomeEventFactory = outcomeEventFactory;
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

    public CouponReservationQuote quoteForReservation(Long memberId, Long memberCouponId, Product product,
                                                      PromotionPrice promotion, Instant now) {
        MemberCoupon coupon = memberCouponRepository.findRedemptionTargetByIdForUpdate(memberCouponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_COUPON_NOT_FOUND));
        try {
            if (!coupon.getMember().getId().equals(memberId)) {
                throw new BusinessException(ErrorCode.MEMBER_COUPON_ACCESS_DENIED);
            }
            if (couponReservationRepository.existsActiveByMemberCouponId(coupon.getId())) {
                throw new BusinessException(ErrorCode.MEMBER_COUPON_ALREADY_RESERVED);
            }
            return new CouponReservationQuote(coupon, quote(coupon, product, promotion, now));
        } catch (BusinessException exception) {
            CouponOutcomeReason reason = redemptionReason(exception.errorCode());
            if (reason != null) {
                recordRedemptionFailure(coupon, product, null, reason, now);
            }
            throw exception;
        }
    }

    public void reserve(CouponReservationQuote reservationQuote, Order order, Instant now) {
        try {
            couponReservationRepository.saveAndFlush(CouponReservation.reserve(
                    reservationQuote.memberCoupon(), order, now, now.plusSeconds(1_800)
            ));
        } catch (DataIntegrityViolationException exception) {
            if (isActiveReservationConstraintViolation(exception)) {
                recordRedemptionFailure(
                        reservationQuote.memberCoupon(), order.getProduct(), order.getId(),
                        CouponOutcomeReason.RESERVATION_CONFLICT, now);
                throw new BusinessException(ErrorCode.MEMBER_COUPON_ALREADY_RESERVED, exception);
            }
            throw exception;
        }
    }

    public CouponReservation prepareForPayment(Order order, Instant now) {
        if (order.getMemberCouponId() == null) {
            return null;
        }
        CouponReservation reservation = couponReservationRepository.findActiveByOrderIdForUpdate(order.getId())
                .orElseThrow(() -> new DomainException(CouponDomainError.RESERVATION_TRANSITION_NOT_ALLOWED));
        MemberCoupon memberCoupon = memberCouponRepository.findRedemptionTargetByIdForUpdate(reservation.getMemberCoupon().getId())
                .orElseThrow(() -> new DomainException(CouponDomainError.MEMBER_COUPON_USE_NOT_ALLOWED));
        try {
            if (memberCoupon.getStatus() != MemberCouponStatus.ISSUED) {
                throw new DomainException(CouponDomainError.MEMBER_COUPON_USE_NOT_ALLOWED);
            }
            if (!now.isBefore(reservation.getExpiresAt())) {
                throw new DomainException(CouponDomainError.RESERVATION_EXPIRED);
            }
            return reservation;
        } catch (DomainException exception) {
            CouponOutcomeReason reason = redemptionReason((CouponDomainError) exception.error());
            if (reason != null) {
                recordRedemptionFailure(memberCoupon, order.getProduct(), order.getId(), reason, now);
            }
            throw exception;
        }
    }

    public void consumeAfterPaymentApproval(CouponReservation reservation, Instant now) {
        if (reservation == null) {
            return;
        }
        reservation.consume(now);
        reservation.getMemberCoupon().markUsed();
        Order order = reservation.getOrder();
        operationalEventRecorder.record(outcomeEventFactory.redemptionSucceeded(
                reservation.getMemberCoupon().getCampaign(),
                order.getProduct().getStore().getId(),
                order.getId(),
                order.getCouponDiscountAmount(),
                now));
    }

    public void releaseForCanceledOrder(Order order, Instant now) {
        if (order.getMemberCouponId() == null) {
            return;
        }
        couponReservationRepository.findActiveByOrderIdForUpdate(order.getId())
                .ifPresent(reservation -> reservation.release(now));
    }

    public void expireReservations(Instant now) {
        couponReservationRepository.findExpiredReservationIds(now)
                .forEach(reservationId -> couponReservationExpiryTransactionService.expireReservation(reservationId, now));
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

    private void recordRedemptionFailure(
            MemberCoupon coupon,
            Product product,
            Long orderId,
            CouponOutcomeReason reason,
            Instant occurredAt
    ) {
        operationalFailureRecorder.recordSafely(outcomeEventFactory.redemptionFailed(
                coupon.getCampaign(), product.getStore().getId(), orderId, reason, occurredAt));
    }

    private CouponOutcomeReason redemptionReason(ErrorCode errorCode) {
        return switch (errorCode) {
            case MEMBER_COUPON_ACCESS_DENIED,
                 MEMBER_COUPON_MINIMUM_PURCHASE_NOT_MET -> CouponOutcomeReason.INELIGIBLE;
            case MEMBER_COUPON_NOT_ISSUED -> CouponOutcomeReason.UNAVAILABLE;
            case MEMBER_COUPON_EXPIRED -> CouponOutcomeReason.EXPIRED;
            case MEMBER_COUPON_TARGET_MISMATCH -> CouponOutcomeReason.SCOPE_MISMATCH;
            case MEMBER_COUPON_PROMOTION_STACKING_NOT_ALLOWED ->
                    CouponOutcomeReason.COMBINATION_NOT_ALLOWED;
            case MEMBER_COUPON_ALREADY_RESERVED -> CouponOutcomeReason.RESERVATION_CONFLICT;
            default -> null;
        };
    }

    private CouponOutcomeReason redemptionReason(CouponDomainError error) {
        return switch (error) {
            case RESERVATION_EXPIRED -> CouponOutcomeReason.EXPIRED;
            case RESERVATION_TRANSITION_NOT_ALLOWED -> CouponOutcomeReason.RESERVATION_CONFLICT;
            case MEMBER_COUPON_USE_NOT_ALLOWED -> CouponOutcomeReason.UNAVAILABLE;
            default -> null;
        };
    }

    private boolean isActiveReservationConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintViolation
                    && "uq_coupon_reservations_active_member_coupon".equals(constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
