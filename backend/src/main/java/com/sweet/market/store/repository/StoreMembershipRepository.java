package com.sweet.market.store.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.store.domain.StoreMembership;

public interface StoreMembershipRepository extends JpaRepository<StoreMembership, Long> {

    boolean existsByStoreIdAndMemberIdAndActiveTrue(Long storeId, Long memberId);

    Optional<StoreMembership> findByStoreIdAndMemberId(Long storeId, Long memberId);

    Optional<StoreMembership> findByStoreIdAndMemberIdAndActiveTrue(Long storeId, Long memberId);
}
