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
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        StoredProductImage storedImage = storageService.storeTemporary(file);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(storageProperties.getTempExpiration());

        ProductImageUpload upload = ProductImageUpload.create(
                member,
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
}
