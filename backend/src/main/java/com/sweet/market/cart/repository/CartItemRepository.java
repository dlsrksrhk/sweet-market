package com.sweet.market.cart.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.cart.api.CartItemResponse;
import com.sweet.market.cart.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    boolean existsByBuyerIdAndProductId(Long buyerId, Long productId);

    Optional<CartItem> findByBuyerIdAndProductId(Long buyerId, Long productId);

    long deleteByBuyerIdAndProductId(Long buyerId, Long productId);

    @Query(value = """
            select new com.sweet.market.cart.api.CartItemResponse(
                ci.id,
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
                ci.createdAt,
                case
                    when p.status = com.sweet.market.product.domain.ProductStatus.ON_SALE
                     and seller.id <> :buyerId then true
                    else false
                end,
                case
                    when seller.id = :buyerId then 'OWN_PRODUCT'
                    when p.status = com.sweet.market.product.domain.ProductStatus.RESERVED then 'RESERVED'
                    when p.status = com.sweet.market.product.domain.ProductStatus.SOLD_OUT then 'SOLD_OUT'
                    when p.status = com.sweet.market.product.domain.ProductStatus.HIDDEN then 'HIDDEN'
                    else null
                end
            )
            from CartItem ci
            join ci.product p
            join p.seller seller
            where ci.buyer.id = :buyerId
            order by ci.createdAt desc, ci.id desc
            """,
            countQuery = """
            select count(ci)
            from CartItem ci
            where ci.buyer.id = :buyerId
            """)
    Page<CartItemResponse> findPageByBuyerId(@Param("buyerId") Long buyerId, Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "product", "product.seller", "product.images"})
    List<CartItem> findAllWithBuyerProductSellerImagesByIdIn(List<Long> ids);
}
