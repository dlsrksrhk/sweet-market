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
import com.sweet.market.product.api.ProductSummaryResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.seller.report.SellerProductStatusCountProjection;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"seller", "images"})
    Optional<Product> findWithSellerAndImagesById(Long id);

    @EntityGraph(attributePaths = "seller")
    Optional<Product> findWithSellerById(Long id);

    @EntityGraph(attributePaths = {"seller", "images"})
    Optional<Product> findWithSellerAndImagesByIdAndStatus(Long id, ProductStatus status);

    @EntityGraph(attributePaths = "seller")
    Page<Product> findByStatusOrderByIdDesc(ProductStatus status, Pageable pageable);

    @Query(value = """
            select new com.sweet.market.product.api.ProductSummaryResponse(
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
            join p.seller s
            where s.id = :sellerId
            order by p.id desc
            """,
            countQuery = """
            select count(p)
            from Product p
            where p.seller.id = :sellerId
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
            join p.seller s
            where (:sellerId is null or s.id = :sellerId)
              and (:status is null or p.status = :status)
              and (coalesce(:keyword, '') = '' or lower(p.title) like lower(concat('%', coalesce(:keyword, ''), '%')))
            """,
            countQuery = """
            select count(p)
            from Product p
            join p.seller s
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

    long countBySellerId(Long sellerId);

    long countBySellerIdAndStatus(Long sellerId, ProductStatus status);

    @Query("""
            select p.status as status, count(p) as count
            from Product p
            where p.seller.id = :sellerId
            group by p.status
            """)
    List<SellerProductStatusCountProjection> countProductStatusesBySellerId(@Param("sellerId") Long sellerId);
}
