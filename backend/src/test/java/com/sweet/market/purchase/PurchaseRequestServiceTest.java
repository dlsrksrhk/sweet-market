package com.sweet.market.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.purchase.application.PurchaseRequestService;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseRequestServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    @Autowired
    private PurchaseRequestService service;

    @Autowired
    private MemberRepository memberRepository;

    private Long buyerId;

    @BeforeEach
    void setUp() {
        buyerId = memberRepository.save(Member.create("buyer@example.com", "encoded-password", "buyer")).getId();
    }

    @Test
    void 동일한_키와_요청은_완료_응답을_재사용한다() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"orderId\":10}");
        PurchaseRequestService.Claim.New claim = (PurchaseRequestService.Claim.New) service.claim(
                buyerId, "key-1", "direct:10:", NOW
        );
        service.completeSuccess(buyerId, "key-1", claim.executionToken(), 201, payload, NOW);

        PurchaseRequestService.Claim.Replay replay = (PurchaseRequestService.Claim.Replay) service.claim(
                buyerId, "key-1", "direct:10:", NOW.plusSeconds(1)
        );

        assertThat(replay.httpStatus()).isEqualTo(201);
        assertThat(replay.payload()).isEqualTo(payload);
    }

    @Test
    void 다른_요청으로_같은_키를_재사용하면_거부한다() {
        service.claim(buyerId, "key-1", "direct:10:", NOW);

        assertThatThrownBy(() -> service.claim(buyerId, "key-1", "direct:11:", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void 처리_중인_동일_요청은_진행_중_오류를_반환한다() {
        service.claim(buyerId, "key-1", "direct:10:", NOW);

        assertThatThrownBy(() -> service.claim(buyerId, "key-1", "direct:10:", NOW.plusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.ORDER_REQUEST_IN_PROGRESS);
    }

    @Test
    void 만료된_처리_요청은_새_실행_토큰으로_다시_점유한다() {
        PurchaseRequestService.Claim.New firstClaim = (PurchaseRequestService.Claim.New) service.claim(
                buyerId, "key-1", "direct:10:", NOW
        );

        PurchaseRequestService.Claim.New recoveredClaim = (PurchaseRequestService.Claim.New) service.claim(
                buyerId, "key-1", "direct:10:", NOW.plusSeconds(301)
        );

        assertThat(recoveredClaim.executionToken()).isNotEqualTo(firstClaim.executionToken());
    }

    @Test
    void 현재_실행_토큰이_아니면_완료할_수_없다() {
        service.claim(buyerId, "key-1", "direct:10:", NOW);
        JsonNode payload = objectMapper.createObjectNode().put("orderId", 10L);

        assertThatThrownBy(() -> service.completeBusinessFailure(
                buyerId, "key-1", UUID.randomUUID(), 409, payload, NOW
        )).isInstanceOf(BusinessException.class);
    }
}
