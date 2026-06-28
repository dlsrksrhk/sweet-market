package com.sweet.market.wishlist.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.wishlist.api.WishlistItemResponse;
import com.sweet.market.wishlist.domain.WishlistItem;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    boolean existsByBuyerIdAndProductId(Long buyerId, Long productId);

    Optional<WishlistItem> findByBuyerIdAndProductId(Long buyerId, Long productId);

    long countByProductId(Long productId);

    long deleteByBuyerIdAndProductId(Long buyerId, Long productId);

    @Query(value = """
            select new com.sweet.market.wishlist.api.WishlistItemResponse(
                wi.id,
                p.id,
                seller.id,
                seller.nickname,
                p.title,
                p.price,
                p.status,
                coalesce(
                    (
                        select min(representativeImage.imageUrl)
                        from ProductImage representativeImage
                        where representativeImage.product = p
                          and representativeImage.representative = true
                          and representativeImage.sortOrder = (
                              select min(firstRepresentativeImage.sortOrder)
                              from ProductImage firstRepresentativeImage
                              where firstRepresentativeImage.product = p
                                and firstRepresentativeImage.representative = true
                          )
                    ),
                    (
                        select min(orderedImage.imageUrl)
                        from ProductImage orderedImage
                        where orderedImage.product = p
                          and orderedImage.sortOrder = (
                              select min(firstImage.sortOrder)
                              from ProductImage firstImage
                              where firstImage.product = p
                          )
                    )
                ),
                true,
                (
                    select count(allItem)
                    from WishlistItem allItem
                    where allItem.product = p
                ),
                wi.createdAt
            )
            from WishlistItem wi
            join wi.product p
            join p.seller seller
            where wi.buyer.id = :buyerId
              and p.status in :visibleStatuses
            order by wi.createdAt desc, wi.id desc
            """,
            countQuery = """
            select count(wi)
            from WishlistItem wi
            join wi.product p
            where wi.buyer.id = :buyerId
              and p.status in :visibleStatuses
            """)
    Page<WishlistItemResponse> findPageByBuyerIdAndProductStatusIn(
            @Param("buyerId") Long buyerId,
            @Param("visibleStatuses") List<ProductStatus> visibleStatuses,
            Pageable pageable
    );
}
