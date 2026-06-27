package com.sweet.market.product.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.api.ProductImageUploadResponse;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.product.domain.ProductImageUpload;
import com.sweet.market.product.repository.ProductImageUploadRepository;
import com.sweet.market.product.storage.ProductImageStorageProperties;
import com.sweet.market.product.storage.ProductImageStorageService;
import com.sweet.market.product.storage.StoredProductImage;

@Service
public class ProductImageUploadService {

    private final ProductImageUploadRepository productImageUploadRepository;
    private final MemberRepository memberRepository;
    private final ProductImageStorageService storageService;
    private final ProductImageStorageProperties storageProperties;

    public ProductImageUploadService(
            ProductImageUploadRepository productImageUploadRepository,
            MemberRepository memberRepository,
            ProductImageStorageService storageService,
            ProductImageStorageProperties storageProperties
    ) {
        this.productImageUploadRepository = productImageUploadRepository;
        this.memberRepository = memberRepository;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
    }

    @Transactional
    public ProductImageUploadResponse upload(Long memberId, MultipartFile file) {
        Member uploader = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        StoredProductImage storedImage = storageService.storeTemporary(file);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(storageProperties.getTempExpiration());

        ProductImageUpload upload = ProductImageUpload.create(
                uploader,
                storedImage.storedFileName(),
                storedImage.originalFileName(),
                storedImage.contentType(),
                storedImage.size(),
                storedImage.url(),
                now,
                expiresAt
        );

        return ProductImageUploadResponse.from(productImageUploadRepository.save(upload));
    }

    @Transactional
    public ProductImage confirm(Long memberId, Long uploadId, int sortOrder, boolean representative) {
        ProductImageUpload upload = productImageUploadRepository.findById(uploadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_IMAGE_UPLOAD_NOT_FOUND));
        if (!upload.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
        if (upload.isExpired(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_UPLOAD_EXPIRED);
        }

        StoredProductImage storedImage = storageService.confirm(
                upload.getStoredFileName(),
                upload.getOriginalFileName(),
                upload.getContentType(),
                upload.getSize()
        );
        productImageUploadRepository.delete(upload);

        return ProductImage.local(
                storedImage.url(),
                storedImage.storedFileName(),
                storedImage.originalFileName(),
                storedImage.contentType(),
                storedImage.size(),
                sortOrder,
                representative
        );
    }
}
