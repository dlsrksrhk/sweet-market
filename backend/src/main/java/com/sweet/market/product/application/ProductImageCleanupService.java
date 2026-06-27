package com.sweet.market.product.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.product.domain.ProductImageUpload;
import com.sweet.market.product.repository.ProductImageUploadRepository;
import com.sweet.market.product.storage.ProductImageStorageService;

@Service
public class ProductImageCleanupService {

    private final ProductImageUploadRepository uploadRepository;
    private final ProductImageStorageService storageService;

    public ProductImageCleanupService(
            ProductImageUploadRepository uploadRepository,
            ProductImageStorageService storageService
    ) {
        this.uploadRepository = uploadRepository;
        this.storageService = storageService;
    }

    @Transactional
    public int cleanExpiredUploads(LocalDateTime now) {
        int deletedCount = 0;
        for (ProductImageUpload upload : uploadRepository.findByExpiresAtLessThanEqual(now)) {
            try {
                storageService.deleteTemporary(upload.getStoredFileName());
            } catch (BusinessException ignored) {
                // Best-effort file deletion should not block stale row cleanup.
            }
            uploadRepository.delete(upload);
            deletedCount++;
        }
        return deletedCount;
    }
}
