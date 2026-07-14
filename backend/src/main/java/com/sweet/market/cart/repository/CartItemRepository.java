package com.sweet.market.cart.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.cart.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    boolean existsByBuyerIdAndProductId(Long buyerId, Long productId);

    Optional<CartItem> findByBuyerIdAndProductId(Long buyerId, Long productId);

    long deleteByBuyerIdAndProductId(Long buyerId, Long productId);

    @Query("""
            select cartItem.product.id
            from CartItem cartItem
            where cartItem.buyer.id = :buyerId
              and cartItem.product.id in :productIds
            """)
    List<Long> findProductIdsByBuyerIdAndProductIdIn(
            @Param("buyerId") Long buyerId,
            @Param("productIds") Collection<Long> productIds
    );

    @Query(value = """
            select new com.sweet.market.cart.repository.CartItemReadRow(
                ci.id,
                p.id,
                seller.id,
                seller.nickname,
                p.title,
                p.price,
                case
                    when p.status = com.sweet.market.product.domain.ProductStatus.HIDDEN
                        then com.sweet.market.product.domain.ProductStatus.HIDDEN
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                         and inventory.totalQuantity - inventory.reservedQuantity > 0
                        then com.sweet.market.product.domain.ProductStatus.ON_SALE
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                        then com.sweet.market.product.domain.ProductStatus.SOLD_OUT
                    else p.status
                end,
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
                p.salesPolicy,
                inventory.totalQuantity - inventory.reservedQuantity,
                p.lowStockThreshold,
                case
                    when p.status <> com.sweet.market.product.domain.ProductStatus.HIDDEN
                     and (p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                          and inventory.totalQuantity - inventory.reservedQuantity > 0
                          or p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.SINGLE_ITEM
                          and p.status = com.sweet.market.product.domain.ProductStatus.ON_SALE)
                     and store.status = com.sweet.market.store.domain.StoreStatus.ACTIVE
                     and seller.id <> :buyerId then true
                    else false
                end,
                case
                    when seller.id = :buyerId then 'OWN_PRODUCT'
                    when p.status = com.sweet.market.product.domain.ProductStatus.HIDDEN then 'HIDDEN'
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                         and inventory.totalQuantity - inventory.reservedQuantity <= 0 then 'SOLD_OUT'
                    when p.status = com.sweet.market.product.domain.ProductStatus.RESERVED then 'RESERVED'
                    when p.status = com.sweet.market.product.domain.ProductStatus.SOLD_OUT then 'SOLD_OUT'
                    else null
                end
            )
            from CartItem ci
            join ci.product p
            join p.store store
            join store.ownerMember seller
            left join Inventory inventory on inventory.product = p
            where ci.buyer.id = :buyerId
            order by ci.createdAt desc, ci.id desc
            """,
            countQuery = """
            select count(ci)
            from CartItem ci
            where ci.buyer.id = :buyerId
            """)
    Page<CartItemReadRow> findPageByBuyerId(@Param("buyerId") Long buyerId, Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "product", "product.store", "product.store.ownerMember", "product.images"})
    List<CartItem> findAllWithBuyerProductSellerImagesByIdIn(List<Long> ids);
}
