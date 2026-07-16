package com.sweet.market.purchase.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "purchase_requests",
        uniqueConstraints = @UniqueConstraint(name = "uq_purchase_requests_buyer_key", columnNames = {"buyer_id", "idempotency_key"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 128)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseRequestStatus status;

    @Column(name = "execution_token", nullable = false)
    private UUID executionToken;

    @Column(name = "lease_expires_at", nullable = false)
    private Instant leaseExpiresAt;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private JsonNode responsePayload;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private PurchaseRequest(
            Member buyer,
            String idempotencyKey,
            String requestFingerprint,
            UUID executionToken,
            Instant leaseExpiresAt,
            Instant expiresAt
    ) {
        this.buyer = buyer;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.status = PurchaseRequestStatus.PROCESSING;
        this.executionToken = executionToken;
        this.leaseExpiresAt = leaseExpiresAt;
        this.expiresAt = expiresAt;
    }

    public static PurchaseRequest start(
            Member buyer,
            String idempotencyKey,
            String requestFingerprint,
            UUID executionToken,
            Instant leaseExpiresAt,
            Instant expiresAt
    ) {
        return new PurchaseRequest(buyer, idempotencyKey, requestFingerprint, executionToken, leaseExpiresAt, expiresAt);
    }

    public boolean hasFingerprint(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }

    public boolean hasExpiredLeaseAt(Instant now) {
        return !leaseExpiresAt.isAfter(now);
    }

    public boolean hasExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void reclaim(UUID nextExecutionToken, Instant nextLeaseExpiresAt) {
        this.executionToken = nextExecutionToken;
        this.leaseExpiresAt = nextLeaseExpiresAt;
    }

    public void restart(
            String nextRequestFingerprint,
            UUID nextExecutionToken,
            Instant nextLeaseExpiresAt,
            Instant nextExpiresAt
    ) {
        this.requestFingerprint = nextRequestFingerprint;
        this.status = PurchaseRequestStatus.PROCESSING;
        this.executionToken = nextExecutionToken;
        this.leaseExpiresAt = nextLeaseExpiresAt;
        this.responseStatus = null;
        this.responsePayload = null;
        this.completedAt = null;
        this.expiresAt = nextExpiresAt;
    }

    public boolean hasExecutionToken(UUID token) {
        return executionToken.equals(token);
    }

    public void complete(int responseStatus, JsonNode responsePayload, Instant now, Instant expiresAt) {
        this.status = PurchaseRequestStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.responsePayload = responsePayload;
        this.completedAt = now;
        this.expiresAt = expiresAt;
    }
}
