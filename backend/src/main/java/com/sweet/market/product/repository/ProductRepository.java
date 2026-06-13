package com.sweet.market.product.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.product.api.ProductSummaryResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"seller", "images"})
    Optional<Product> findWithSellerAndImagesById(Long id);

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
                (
                    select min(i.imageUrl)
                    from ProductImage i
                    where i.product = p
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
}
