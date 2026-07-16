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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    void 완료_응답은_48시간_동안만_재사용한다() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"orderId\":10}");
        PurchaseRequestService.Claim.New firstClaim = (PurchaseRequestService.Claim.New) service.claim(
                buyerId, "key-1", "direct:10:", NOW
        );
        service.completeSuccess(buyerId, "key-1", firstClaim.executionToken(), 201, payload, NOW);

        assertThat(service.claim(buyerId, "key-1", "direct:10:", NOW.plusSeconds(172_799)))
                .isInstanceOf(PurchaseRequestService.Claim.Replay.class);
        assertThat(service.claim(buyerId, "key-1", "direct:11:", NOW.plusSeconds(172_800)))
                .isInstanceOf(PurchaseRequestService.Claim.New.class);
    }

    @Test
    void 완료된_비즈니스_실패_응답을_재사용한다() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"code\":\"PRODUCT_SOLD_OUT\"}");
        PurchaseRequestService.Claim.New claim = (PurchaseRequestService.Claim.New) service.claim(
                buyerId, "key-1", "direct:10:", NOW
        );
        service.completeBusinessFailure(buyerId, "key-1", claim.executionToken(), 409, payload, NOW);

        PurchaseRequestService.Claim.Replay replay = (PurchaseRequestService.Claim.Replay) service.claim(
                buyerId, "key-1", "direct:10:", NOW.plusSeconds(1)
        );

        assertThat(replay.httpStatus()).isEqualTo(409);
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
    void 동시에_처음_점유하면_하나는_새_요청이고_다른_하나는_진행_중이다() throws Exception {
        createInsertDelayTrigger();
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ClaimAttempt> first = executorService.submit(() -> claimAtTheSameTime(ready, start));
            Future<ClaimAttempt> second = executorService.submit(() -> claimAtTheSameTime(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ClaimAttempt> attempts = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(attempts.stream()
                    .filter(attempt -> attempt.claim() instanceof PurchaseRequestService.Claim.New)
                    .count()).isEqualTo(1);
            assertThat(attempts.stream().map(ClaimAttempt::failure).filter(java.util.Objects::nonNull).toList())
                    .singleElement()
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).errorCode())
                    .isEqualTo(ErrorCode.ORDER_REQUEST_IN_PROGRESS);
        } finally {
            executorService.shutdownNow();
            dropInsertDelayTrigger();
        }
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

    private ClaimAttempt claimAtTheSameTime(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            return new ClaimAttempt(service.claim(buyerId, "key-1", "direct:10:", NOW), null);
        } catch (Throwable throwable) {
            return new ClaimAttempt(null, throwable);
        }
    }

    private void createInsertDelayTrigger() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION delay_purchase_request_insert()
                RETURNS trigger AS $$
                BEGIN
                    PERFORM pg_sleep(0.2);
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER purchase_request_insert_delay
                BEFORE INSERT ON purchase_requests
                FOR EACH ROW EXECUTE FUNCTION delay_purchase_request_insert()
                """);
    }

    private void dropInsertDelayTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS purchase_request_insert_delay ON purchase_requests");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS delay_purchase_request_insert()");
    }

    private record ClaimAttempt(PurchaseRequestService.Claim claim, Throwable failure) {
    }
}
