package com.sweet.market.store.repository;

import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.domain.StoreType;
import com.sweet.market.store.storefront.StorefrontResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select store from Store store where store.id in :storeIds order by store.id asc")
    List<Store> findAllByIdInForUpdateOrderByIdAsc(@Param("storeIds") List<Long> storeIds);

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

    @Query("""
            select new com.sweet.market.store.storefront.StorefrontResponse(
                store.id,
                store.type,
                store.publicName,
                store.introduction,
                store.status,
                case when store.status = :suspendedStatus then null else (
                    select avg(review.rating)
                    from Review review
                    where review.product.store.id = store.id
                ) end,
                case when store.status = :suspendedStatus then 0 else (
                    select count(review)
                    from Review review
                    where review.product.store.id = store.id
                ) end,
                case when store.status = :suspendedStatus then 0 else (
                    select count(product)
                    from Product product
                    where product.store.id = store.id
                      and product.status in :publicProductStatuses
                ) end
            )
            from Store store
            where store.id = :storeId
              and store.status in :publicStoreStatuses
            """)
    Optional<StorefrontResponse> findStorefrontHeader(
            @Param("storeId") Long storeId,
            @Param("publicStoreStatuses") List<StoreStatus> publicStoreStatuses,
            @Param("suspendedStatus") StoreStatus suspendedStatus,
            @Param("publicProductStatuses") List<ProductStatus> publicProductStatuses
    );

    default Optional<Store> findPersonalByOwnerMemberId(Long ownerMemberId) {
        return findByOwnerMemberIdAndType(ownerMemberId, StoreType.PERSONAL);
    }

    default List<Store> findBusinessByOwnerMemberId(Long ownerMemberId) {
        return findAllByOwnerMemberIdAndType(ownerMemberId, StoreType.BUSINESS);
    }

    Page<Store> findAllByType(StoreType type, Pageable pageable);

    Page<Store> findAllByTypeAndStatus(StoreType type, StoreStatus status, Pageable pageable);

    boolean existsByIdAndStatus(Long id, StoreStatus status);
}
