package com.sweet.market.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.operations.OperableStoreResponse;
import com.sweet.market.store.operations.StoreMembershipResponse;

public interface StoreMembershipRepository extends JpaRepository<StoreMembership, Long> {

    boolean existsByStoreIdAndMemberIdAndActiveTrue(Long storeId, Long memberId);

    Optional<StoreMembership> findByStoreIdAndMemberId(Long storeId, Long memberId);

    @EntityGraph(attributePaths = "store")
    Optional<StoreMembership> findByStoreIdAndMemberIdAndActiveTrue(Long storeId, Long memberId);

    Optional<StoreMembership> findByIdAndStoreIdAndActiveTrue(Long id, Long storeId);

    @Query("""
            select new com.sweet.market.store.operations.StoreMembershipResponse(
                membership.id,
                member.id,
                member.nickname,
                membership.role,
                membership.createdAt
            )
            from StoreMembership membership
            join membership.member member
            where membership.store.id = :storeId
              and membership.active = true
            order by case membership.role
                when com.sweet.market.store.domain.StoreMemberRole.OWNER then 0
                when com.sweet.market.store.domain.StoreMemberRole.MANAGER then 1
                else 2
            end, membership.id
            """)
    List<StoreMembershipResponse> findActiveByStoreId(@Param("storeId") Long storeId);

    @Query("""
            select new com.sweet.market.store.operations.OperableStoreResponse(
                store.id,
                store.type,
                store.publicName,
                store.status,
                membership.role
            )
            from StoreMembership membership
            join membership.store store
            where membership.member.id = :memberId
              and membership.active = true
              and membership.role in (
                  com.sweet.market.store.domain.StoreMemberRole.OWNER,
                  com.sweet.market.store.domain.StoreMemberRole.MANAGER
              )
            order by case store.type
                when com.sweet.market.store.domain.StoreType.PERSONAL then 0
                when com.sweet.market.store.domain.StoreType.BUSINESS then 1
                else 2
            end, store.id
            """)
    List<OperableStoreResponse> findOperableStores(@Param("memberId") Long memberId);
}
