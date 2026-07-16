package com.sweet.market.purchase.repository;

import com.sweet.market.purchase.domain.PurchaseRequest;
import com.sweet.market.purchase.domain.PurchaseRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from PurchaseRequest request
            where request.buyer.id = :buyerId
              and request.idempotencyKey = :key
            """)
    Optional<PurchaseRequest> findForUpdate(@Param("buyerId") Long buyerId, @Param("key") String key);

    long deleteByStatusAndExpiresAtBefore(PurchaseRequestStatus status, Instant cutoff);
}
