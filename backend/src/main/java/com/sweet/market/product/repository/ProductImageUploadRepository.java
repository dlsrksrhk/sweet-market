package com.sweet.market.product.repository;

import com.sweet.market.product.domain.ProductImageUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductImageUploadRepository extends JpaRepository<ProductImageUpload, Long> {

    List<ProductImageUpload> findByExpiresAtLessThanEqual(LocalDateTime now);
}
