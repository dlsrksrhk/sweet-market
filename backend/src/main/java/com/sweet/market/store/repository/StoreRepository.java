package com.sweet.market.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreType;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByOwnerMemberIdAndType(Long ownerMemberId, StoreType type);

    List<Store> findAllByOwnerMemberIdAndType(Long ownerMemberId, StoreType type);

    @Query(value = """
            SELECT *
            FROM stores
            WHERE owner_member_id = :ownerMemberId
            ORDER BY CASE type
                WHEN 'PERSONAL' THEN 0
                WHEN 'BUSINESS' THEN 1
                ELSE 2
            END, id
            """, nativeQuery = true)
    List<Store> findAllOwnedByOwnerMemberIdInMyStoreOrder(Long ownerMemberId);

    default Optional<Store> findPersonalByOwnerMemberId(Long ownerMemberId) {
        return findByOwnerMemberIdAndType(ownerMemberId, StoreType.PERSONAL);
    }

    default List<Store> findBusinessByOwnerMemberId(Long ownerMemberId) {
        return findAllByOwnerMemberIdAndType(ownerMemberId, StoreType.BUSINESS);
    }

    Page<Store> findAllByType(StoreType type, Pageable pageable);
}
