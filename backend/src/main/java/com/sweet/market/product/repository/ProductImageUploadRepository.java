package com.sweet.market.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.product.domain.ProductImageUpload;

public interface ProductImageUploadRepository extends JpaRepository<ProductImageUpload, Long> {
}
