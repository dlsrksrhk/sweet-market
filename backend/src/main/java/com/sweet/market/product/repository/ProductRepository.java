package com.sweet.market.product.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"seller", "images"})
    Optional<Product> findWithSellerAndImagesById(Long id);

    @EntityGraph(attributePaths = {"seller", "images"})
    Optional<Product> findWithSellerAndImagesByIdAndStatus(Long id, ProductStatus status);

    @EntityGraph(attributePaths = "seller")
    Page<Product> findByStatusOrderByIdDesc(ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "seller")
    Page<Product> findBySellerIdOrderByIdDesc(Long sellerId, Pageable pageable);
}
