package com.sweet.market.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreType;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByOwnerMemberIdAndType(Long ownerMemberId, StoreType type);

    List<Store> findAllByOwnerMemberIdAndType(Long ownerMemberId, StoreType type);

    List<Store> findAllByOwnerMemberId(Long ownerMemberId);

    default Optional<Store> findPersonalByOwnerMemberId(Long ownerMemberId) {
        return findByOwnerMemberIdAndType(ownerMemberId, StoreType.PERSONAL);
    }

    default List<Store> findBusinessByOwnerMemberId(Long ownerMemberId) {
        return findAllByOwnerMemberIdAndType(ownerMemberId, StoreType.BUSINESS);
    }

    Page<Store> findAllByType(StoreType type, Pageable pageable);
}
