package com.sweet.market.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.product.admin.AdminProductSummaryResponse;
import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.api.ProductSummaryResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.seller.report.SellerProductStatusCountProjection;
import com.sweet.market.store.storefront.StorefrontProductResponse;
import com.sweet.market.store.operations.StoreCatalogProductResponse;
import com.sweet.market.store.operations.StoreCatalogSummaryResponse;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByStoreIdAndIdIn(Long storeId, List<Long> ids);

    @EntityGraph(attributePaths = {"store", "store.ownerMember", "images"})
    Optional<Product> findWithStoreAndImagesById(Long id);

    @EntityGraph(attributePaths = {"store", "store.ownerMember"})
    Optional<Product> findWithStoreById(Long id);

    @EntityGraph(attributePaths = {"store", "store.ownerMember", "images"})
    Optional<Product> findWithStoreAndImagesByIdAndStatus(Long id, ProductStatus status);

    @EntityGraph(attributePaths = {"store", "store.ownerMember", "images"})
    @Query("""
            select p
            from Product p
            where p.id = :id
              and p.status in (
                  com.sweet.market.product.domain.ProductStatus.ON_SALE,
                  com.sweet.market.product.domain.ProductStatus.RESERVED,
                  com.sweet.market.product.domain.ProductStatus.SOLD_OUT
              )
            """)
    Optional<Product> findBuyerVisibleDetailById(@Param("id") Long id);

    @Query("""
            select new com.sweet.market.inventory.api.BuyerAvailabilityResponse(
                p.salesPolicy,
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
                inventory.totalQuantity - inventory.reservedQuantity,
                p.lowStockThreshold
            )
            from Product p
            left join Inventory inventory on inventory.product = p
            where p.id = :productId
            """)
    Optional<BuyerAvailabilityResponse> findBuyerAvailabilityByProductId(@Param("productId") Long productId);

    @EntityGraph(attributePaths = {"store", "store.ownerMember"})
    Page<Product> findByStatusOrderByIdDesc(ProductStatus status, Pageable pageable);

    @Query(value = """
            select new com.sweet.market.product.api.ProductSummaryResponse(
                p.id,
                store.id,
                store.publicName,
                store.type,
                owner.id,
                owner.nickname,
                p.title,
                p.price,
                case
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
                (
                    select count(allItem)
                    from WishlistItem allItem
                    where allItem.product = p
                ),
                case
                    when :viewerId is null then false
                    when (
                        select count(viewerItem)
                        from WishlistItem viewerItem
                        where viewerItem.product = p
                          and viewerItem.buyer.id = :viewerId
                    ) > 0 then true
                    else false
                end,
                case
                    when :viewerId is null then false
                    when (
                        select count(cartItem)
                        from CartItem cartItem
                        where cartItem.product = p
                          and cartItem.buyer.id = :viewerId
                    ) > 0 then true
                    else false
                end,
                p.salesPolicy,
                inventory.totalQuantity - inventory.reservedQuantity,
                p.lowStockThreshold
            )
            from Product p
            join p.store store
            join store.ownerMember owner
            left join Inventory inventory on inventory.product = p
            where p.status <> com.sweet.market.product.domain.ProductStatus.HIDDEN
              and case
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                         and inventory.totalQuantity - inventory.reservedQuantity > 0
                        then com.sweet.market.product.domain.ProductStatus.ON_SALE
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                        then com.sweet.market.product.domain.ProductStatus.SOLD_OUT
                    else p.status
                  end = :status
              and (store.type <> com.sweet.market.store.domain.StoreType.BUSINESS
                   or store.status = com.sweet.market.store.domain.StoreStatus.ACTIVE)
            order by p.id desc
            """,
            countQuery = """
            select count(p)
            from Product p
            join p.store store
            left join Inventory inventory on inventory.product = p
            where p.status <> com.sweet.market.product.domain.ProductStatus.HIDDEN
              and case
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                         and inventory.totalQuantity - inventory.reservedQuantity > 0
                        then com.sweet.market.product.domain.ProductStatus.ON_SALE
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                        then com.sweet.market.product.domain.ProductStatus.SOLD_OUT
                    else p.status
                  end = :status
              and (store.type <> com.sweet.market.store.domain.StoreType.BUSINESS
                   or store.status = com.sweet.market.store.domain.StoreStatus.ACTIVE)
            """)
    Page<ProductSummaryResponse> findPublicSummariesByStatusOrderByIdDesc(
            @Param("status") ProductStatus status,
            @Param("viewerId") Long viewerId,
            Pageable pageable
    );

    @Query(value = """
            select new com.sweet.market.store.storefront.StorefrontProductResponse(
                p.id,
                store.id,
                store.publicName,
                store.type,
                owner.id,
                owner.nickname,
                p.title,
                p.price,
                case
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
                (
                    select count(allItem)
                    from WishlistItem allItem
                    where allItem.product = p
                ),
                case
                    when :viewerId is null then false
                    when (
                        select count(viewerItem)
                        from WishlistItem viewerItem
                        where viewerItem.product = p
                          and viewerItem.buyer.id = :viewerId
                    ) > 0 then true
                    else false
                end,
                case
                    when :viewerId is null then false
                    when (
                        select count(cartItem)
                        from CartItem cartItem
                        where cartItem.product = p
                          and cartItem.buyer.id = :viewerId
                    ) > 0 then true
                    else false
                end,
                p.salesPolicy,
                inventory.totalQuantity - inventory.reservedQuantity,
                p.lowStockThreshold
            )
            from Product p
            join p.store store
            join store.ownerMember owner
            left join Inventory inventory on inventory.product = p
            where store.id = :storeId
              and store.status = com.sweet.market.store.domain.StoreStatus.ACTIVE
              and p.status <> com.sweet.market.product.domain.ProductStatus.HIDDEN
              and case
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                         and inventory.totalQuantity - inventory.reservedQuantity > 0
                        then com.sweet.market.product.domain.ProductStatus.ON_SALE
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                        then com.sweet.market.product.domain.ProductStatus.SOLD_OUT
                    else p.status
                  end = :status
            """,
            countQuery = """
            select count(p)
            from Product p
            join p.store store
            left join Inventory inventory on inventory.product = p
            where store.id = :storeId
              and store.status = com.sweet.market.store.domain.StoreStatus.ACTIVE
              and p.status <> com.sweet.market.product.domain.ProductStatus.HIDDEN
              and case
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                         and inventory.totalQuantity - inventory.reservedQuantity > 0
                        then com.sweet.market.product.domain.ProductStatus.ON_SALE
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                        then com.sweet.market.product.domain.ProductStatus.SOLD_OUT
                    else p.status
                  end = :status
            """)
    Page<StorefrontProductResponse> findStorefrontProducts(
            @Param("storeId") Long storeId,
            @Param("status") ProductStatus status,
            @Param("viewerId") Long viewerId,
            Pageable pageable
    );

    @Query("""
            select new com.sweet.market.store.operations.StoreCatalogSummaryResponse(
                coalesce(sum(case when p.status <> com.sweet.market.product.domain.ProductStatus.HIDDEN
                    and (p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                        and inventory.totalQuantity - inventory.reservedQuantity > 0
                        or p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.SINGLE_ITEM
                        and p.status = com.sweet.market.product.domain.ProductStatus.ON_SALE) then 1 else 0 end), 0),
                coalesce(sum(case when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.SINGLE_ITEM
                    and p.status = com.sweet.market.product.domain.ProductStatus.RESERVED then 1 else 0 end), 0),
                coalesce(sum(case when p.status <> com.sweet.market.product.domain.ProductStatus.HIDDEN
                    and (p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                        and inventory.totalQuantity - inventory.reservedQuantity <= 0
                        or p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.SINGLE_ITEM
                        and p.status = com.sweet.market.product.domain.ProductStatus.SOLD_OUT) then 1 else 0 end), 0),
                coalesce(sum(case when p.status = com.sweet.market.product.domain.ProductStatus.HIDDEN then 1 else 0 end), 0)
            )
            from Product p
            left join Inventory inventory on inventory.product = p
            where p.store.id = :storeId
            """)
    StoreCatalogSummaryResponse summarizeStoreCatalog(@Param("storeId") Long storeId);

    @Query(value = """
            select new com.sweet.market.store.operations.StoreCatalogProductResponse(
                p.id,
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
                p.salesPolicy,
                inventory.totalQuantity,
                inventory.reservedQuantity,
                inventory.totalQuantity - inventory.reservedQuantity,
                p.lowStockThreshold
            )
            from Product p
            left join Inventory inventory on inventory.product = p
            where p.store.id = :storeId
              and (:status is null or case
                    when p.status = com.sweet.market.product.domain.ProductStatus.HIDDEN
                        then com.sweet.market.product.domain.ProductStatus.HIDDEN
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                         and inventory.totalQuantity - inventory.reservedQuantity > 0
                        then com.sweet.market.product.domain.ProductStatus.ON_SALE
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                        then com.sweet.market.product.domain.ProductStatus.SOLD_OUT
                    else p.status
                  end = :status)
              and (coalesce(:keyword, '') = '' or lower(p.title) like lower(concat('%', coalesce(:keyword, ''), '%')))
            """,
            countQuery = """
            select count(p)
            from Product p
            left join Inventory inventory on inventory.product = p
            where p.store.id = :storeId
              and (:status is null or case
                    when p.status = com.sweet.market.product.domain.ProductStatus.HIDDEN
                        then com.sweet.market.product.domain.ProductStatus.HIDDEN
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                         and inventory.totalQuantity - inventory.reservedQuantity > 0
                        then com.sweet.market.product.domain.ProductStatus.ON_SALE
                    when p.salesPolicy = com.sweet.market.product.domain.ProductSalesPolicy.STOCK_MANAGED
                        then com.sweet.market.product.domain.ProductStatus.SOLD_OUT
                    else p.status
                  end = :status)
              and (coalesce(:keyword, '') = '' or lower(p.title) like lower(concat('%', coalesce(:keyword, ''), '%')))
            """)
    Page<StoreCatalogProductResponse> searchStoreCatalog(
            @Param("storeId") Long storeId,
            @Param("status") ProductStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            select new com.sweet.market.product.api.ProductSummaryResponse(
                p.id,
                store.id,
                store.publicName,
                store.type,
                owner.id,
                owner.nickname,
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
                )
            )
            from Product p
            join p.store store
            join store.ownerMember owner
            where store.ownerMember.id = :sellerId
            order by p.id desc
            """,
            countQuery = """
            select count(p)
            from Product p
            join p.store store
            where store.ownerMember.id = :sellerId
            """)
    Page<ProductSummaryResponse> findSummariesBySellerIdOrderByIdDesc(@Param("sellerId") Long sellerId, Pageable pageable);

    @Query(value = """
            select new com.sweet.market.product.admin.AdminProductSummaryResponse(
                p.id,
                s.id,
                s.nickname,
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
                )
            )
            from Product p
            join p.store store
            join store.ownerMember s
            where (:sellerId is null or s.id = :sellerId)
              and (:status is null or p.status = :status)
              and (coalesce(:keyword, '') = '' or lower(p.title) like lower(concat('%', coalesce(:keyword, ''), '%')))
            """,
            countQuery = """
            select count(p)
            from Product p
            join p.store store
            join store.ownerMember s
            where (:sellerId is null or s.id = :sellerId)
              and (:status is null or p.status = :status)
              and (coalesce(:keyword, '') = '' or lower(p.title) like lower(concat('%', coalesce(:keyword, ''), '%')))
            """)
    Page<AdminProductSummaryResponse> searchAdminProducts(
            @Param("sellerId") Long sellerId,
            @Param("status") ProductStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("select count(p) from Product p where p.store.ownerMember.id = :sellerId")
    long countBySellerId(@Param("sellerId") Long sellerId);

    @Query("select count(p) from Product p where p.store.ownerMember.id = :sellerId and p.status = :status")
    long countBySellerIdAndStatus(@Param("sellerId") Long sellerId, @Param("status") ProductStatus status);

    @Query("""
            select p.status as status, count(p) as count
            from Product p
            where p.store.ownerMember.id = :sellerId
            group by p.status
            """)
    List<SellerProductStatusCountProjection> countProductStatusesBySellerId(@Param("sellerId") Long sellerId);
}
