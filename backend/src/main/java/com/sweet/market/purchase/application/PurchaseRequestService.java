package com.sweet.market.purchase.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.purchase.domain.PurchaseRequest;
import com.sweet.market.purchase.domain.PurchaseRequestStatus;
import com.sweet.market.purchase.repository.PurchaseRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class PurchaseRequestService {

    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private static final Duration RESPONSE_RETENTION = Duration.ofHours(48);

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final MemberRepository memberRepository;

    public PurchaseRequestService(
            PurchaseRequestRepository purchaseRequestRepository,
            MemberRepository memberRepository
    ) {
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(Long buyerId, String key, String fingerprint, Instant now) {
        Member buyer = memberRepository.findByIdForUpdate(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        PurchaseRequest request = purchaseRequestRepository.findForUpdate(buyerId, key).orElse(null);
        if (request == null) {
            UUID executionToken = UUID.randomUUID();
            purchaseRequestRepository.save(PurchaseRequest.start(
                    buyer,
                    key,
                    fingerprint,
                    executionToken,
                    now.plus(LEASE_DURATION),
                    now.plus(RESPONSE_RETENTION)
            ));
            return new Claim.New(executionToken);
        }

        if (request.getStatus() == PurchaseRequestStatus.COMPLETED && request.hasExpiredAt(now)) {
            UUID executionToken = UUID.randomUUID();
            request.restart(fingerprint, executionToken, now.plus(LEASE_DURATION), now.plus(RESPONSE_RETENTION));
            return new Claim.New(executionToken);
        }
        if (!request.hasFingerprint(fingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (request.getStatus() == PurchaseRequestStatus.COMPLETED) {
            return new Claim.Replay(request.getResponseStatus(), request.getResponsePayload());
        }
        if (!request.hasExpiredLeaseAt(now)) {
            throw new BusinessException(ErrorCode.ORDER_REQUEST_IN_PROGRESS);
        }

        UUID executionToken = UUID.randomUUID();
        request.reclaim(executionToken, now.plus(LEASE_DURATION));
        return new Claim.New(executionToken);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeSuccess(
            Long buyerId,
            String key,
            UUID executionToken,
            int httpStatus,
            JsonNode payload,
            Instant now
    ) {
        complete(buyerId, key, executionToken, httpStatus, payload, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeBusinessFailure(
            Long buyerId,
            String key,
            UUID executionToken,
            int httpStatus,
            JsonNode payload,
            Instant now
    ) {
        complete(buyerId, key, executionToken, httpStatus, payload, now);
    }

    @Transactional
    public long purgeCompletedBefore(Instant cutoff) {
        return purchaseRequestRepository.deleteByStatusAndExpiresAtBefore(PurchaseRequestStatus.COMPLETED, cutoff);
    }

    private void complete(
            Long buyerId,
            String key,
            UUID executionToken,
            int httpStatus,
            JsonNode payload,
            Instant now
    ) {
        PurchaseRequest request = purchaseRequestRepository.findForUpdate(buyerId, key)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_REQUEST_IN_PROGRESS));
        if (!request.hasExecutionToken(executionToken) || request.getStatus() != PurchaseRequestStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.ORDER_REQUEST_IN_PROGRESS);
        }
        request.complete(httpStatus, payload, now, now.plus(RESPONSE_RETENTION));
    }

    public sealed interface Claim {

        record New(UUID executionToken) implements Claim {
        }

        record Processing() implements Claim {
        }

        record Replay(int httpStatus, JsonNode payload) implements Claim {
        }
    }
}
