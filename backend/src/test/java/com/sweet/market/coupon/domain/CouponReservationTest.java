package com.sweet.market.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.member.domain.Member;

class CouponReservationTest {

    private static final Instant RESERVED_AT = Instant.parse("2026-07-15T00:00:00Z");
    private static final Instant EXPIRES_AT = RESERVED_AT.plusSeconds(1_800);

    @Test
    void 예약은_소비하면_소비시각을_보존한다() {
        CouponReservation reservation = CouponReservation.reserve(null, null, RESERVED_AT, EXPIRES_AT);
        Instant consumedAt = RESERVED_AT.plusSeconds(60);

        reservation.consume(consumedAt);

        assertThat(reservation.getStatus()).isEqualTo(CouponReservationStatus.CONSUMED);
        assertThat(reservation.getConsumedAt()).isEqualTo(consumedAt);
        assertThat(reservation.getReleasedAt()).isNull();
    }

    @Test
    void 예약은_만료시각과_같거나_지난_시각에는_소비할_수_없다() {
        CouponReservation reservation = CouponReservation.reserve(null, null, RESERVED_AT, EXPIRES_AT);

        assertThatThrownBy(() -> reservation.consume(EXPIRES_AT))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.RESERVATION_EXPIRED);
        assertThat(reservation.getStatus()).isEqualTo(CouponReservationStatus.RESERVED);
    }

    @Test
    void 예약은_해제하거나_만료할_수_있고_종료된_예약은_다시_전이할_수_없다() {
        CouponReservation released = CouponReservation.reserve(null, null, RESERVED_AT, EXPIRES_AT);
        CouponReservation expired = CouponReservation.reserve(null, null, RESERVED_AT, EXPIRES_AT);

        released.release(RESERVED_AT.plusSeconds(10));
        expired.expire(EXPIRES_AT);

        assertThat(released.getStatus()).isEqualTo(CouponReservationStatus.RELEASED);
        assertThat(released.getReleasedAt()).isEqualTo(RESERVED_AT.plusSeconds(10));
        assertThat(expired.getStatus()).isEqualTo(CouponReservationStatus.EXPIRED);
        assertThat(expired.getReleasedAt()).isEqualTo(EXPIRES_AT);
        assertThatThrownBy(() -> released.expire(EXPIRES_AT))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.RESERVATION_TRANSITION_NOT_ALLOWED);
    }

    @Test
    void 예약은_만료시각_전에는_만료_처리할_수_없다() {
        CouponReservation reservation = CouponReservation.reserve(null, null, RESERVED_AT, EXPIRES_AT);

        assertThatThrownBy(() -> reservation.expire(EXPIRES_AT.minusSeconds(1)))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.RESERVATION_NOT_EXPIRED);
        assertThat(reservation.getStatus()).isEqualTo(CouponReservationStatus.RESERVED);
    }

    @Test
    void 예약은_만료시각에_만료_처리할_수_있다() {
        CouponReservation reservation = CouponReservation.reserve(null, null, RESERVED_AT, EXPIRES_AT);

        reservation.expire(EXPIRES_AT);

        assertThat(reservation.getStatus()).isEqualTo(CouponReservationStatus.EXPIRED);
        assertThat(reservation.getReleasedAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void 발급_쿠폰은_한번만_사용_처리할_수_있다() {
        MemberCoupon coupon = MemberCoupon.issue(
                Member.create("member@example.com", "encoded-password", "회원"),
                CouponCampaign.create(
                        CouponCampaignOwnerType.PLATFORM, null, CouponScope.ALL_PRODUCTS,
                        CouponDiscountType.FIXED_AMOUNT, 1_000L, null, 0L, false,
                        "쿠폰", null, RESERVED_AT, EXPIRES_AT,
                        CouponValidityType.COMMON_EXPIRY, EXPIRES_AT, null, java.util.List.of()),
                RESERVED_AT);

        coupon.markUsed();

        assertThat(coupon.getStatus()).isEqualTo(MemberCouponStatus.USED);
        assertThatThrownBy(coupon::markUsed)
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.MEMBER_COUPON_USE_NOT_ALLOWED);
    }
}
