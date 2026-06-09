package com.sweet.market.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.product.domain.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
}
