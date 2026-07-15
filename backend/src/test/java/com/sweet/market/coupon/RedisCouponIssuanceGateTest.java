package com.sweet.market.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sweet.market.coupon.application.issuance.CouponIssuanceGate;
import com.sweet.market.coupon.application.issuance.CouponIssuanceGateResult;
import com.sweet.market.coupon.application.issuance.CouponIssuanceReservation;
import com.sweet.market.coupon.application.issuance.ReservationType;
import com.sweet.market.support.IntegrationTestSupport;

class RedisCouponIssuanceGateTest extends IntegrationTestSupport {

    private static final long CAMPAIGN_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = NOW.plusSeconds(3_600);

    @Autowired
    private CouponIssuanceGate gate;

    @Test
    void 한도만큼만_원자적으로_예약한다() {
        List<CouponIssuanceGateResult> results = IntStream.range(0, 20).parallel()
                .mapToObj(memberId -> reserve(5, (long) memberId, NOW))
                .toList();

        assertThat(results).filteredOn(result -> result.type() == ReservationType.RESERVED).hasSize(5);
        assertThat(results).filteredOn(result -> result.type() == ReservationType.SOLD_OUT).hasSize(15);
    }

    @Test
    void 레디스_캐시가_비어도_영속_발급수를_기준으로_남은_수량만_예약한다() {
        List<CouponIssuanceGateResult> results = IntStream.range(0, 3)
                .mapToObj(memberId -> reserve(5, 3, (long) memberId, NOW))
                .toList();

        assertThat(results).filteredOn(result -> result.type() == ReservationType.RESERVED).hasSize(2);
        assertThat(results).filteredOn(result -> result.type() == ReservationType.SOLD_OUT).hasSize(1);
    }

    @Test
    void 같은_회원의_진행중_예약은_중복_슬롯을_차지하지_않는다() {
        CouponIssuanceGateResult first = reserve(1, 7L, NOW);
        CouponIssuanceGateResult retry = reserve(1, 7L, NOW);

        assertThat(first.type()).isEqualTo(ReservationType.RESERVED);
        assertThat(retry.type()).isEqualTo(ReservationType.IN_PROGRESS);
        assertThat(retry.reservation()).isNull();
    }

    @Test
    void 일치하지_않는_토큰은_예약을_해제할_수_없다() {
        CouponIssuanceReservation reservation = reserve(1, 7L, NOW).reservation();

        gate.release(new CouponIssuanceReservation(CAMPAIGN_ID, 7L, "different-token"), NOW);

        assertThat(reserve(1, 8L, NOW).type()).isEqualTo(ReservationType.SOLD_OUT);

        gate.release(reservation, NOW);

        assertThat(reserve(1, 8L, NOW).type()).isEqualTo(ReservationType.RESERVED);
    }

    @Test
    void 일치하는_토큰을_완료하면_이미_발급된_회원이_된다() {
        CouponIssuanceReservation reservation = reserve(1, 7L, NOW).reservation();

        gate.complete(reservation, NOW);

        CouponIssuanceGateResult retry = reserve(1, 7L, NOW);
        assertThat(retry.type()).isEqualTo(ReservationType.ALREADY_ISSUED);
        assertThat(retry.reservation()).isNull();
    }

    @Test
    void 만료된_예약은_다음_예약에서_회수된다() {
        reserve(1, 7L, NOW);

        CouponIssuanceGateResult result = reserve(1, 8L, NOW.plusSeconds(31));

        assertThat(result.type()).isEqualTo(ReservationType.RESERVED);
    }

    private CouponIssuanceGateResult reserve(int issueLimit, long memberId, Instant now) {
        return reserve(issueLimit, 0, memberId, now);
    }

    private CouponIssuanceGateResult reserve(int issueLimit, int issuedCount, long memberId, Instant now) {
        return gate.reserve(CAMPAIGN_ID, memberId, issueLimit, issuedCount, ISSUE_ENDS_AT, now);
    }
}
