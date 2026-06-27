package com.sweet.market.product.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.product.domain.ProductImageUpload;

public interface ProductImageUploadRepository extends JpaRepository<ProductImageUpload, Long> {

    List<ProductImageUpload> findByExpiresAtBefore(LocalDateTime now);
}
