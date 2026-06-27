package com.sweet.market.product.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.api.ProductCreateImageRequest;
import com.sweet.market.product.api.ProductCreateRequest;
import com.sweet.market.product.api.ProductImageAddRequest;
import com.sweet.market.product.api.ProductResponse;
import com.sweet.market.product.api.ProductUpdateRequest;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final ProductImageUploadService productImageUploadService;

    public ProductService(
            ProductRepository productRepository,
            MemberRepository memberRepository,
            ProductImageUploadService productImageUploadService
    ) {
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.productImageUploadService = productImageUploadService;
    }

    @Transactional
    public ProductResponse create(Long sellerId, ProductCreateRequest request) {
        validateCreateImages(request.images());

        Member seller = memberRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Product product = Product.create(seller, request.title(), request.description(), request.price());
        List<ProductImage> images = request.images().stream()
                .map(image -> productImageUploadService.confirm(
                        sellerId,
                        image.uploadId(),
                        image.sortOrder(),
                        image.representative()
                ))
                .toList();
        try {
            product.replaceImages(images);
        } catch (IllegalArgumentException exception) {
            throw mapProductImageException(exception);
        }

        Product savedProduct = productRepository.save(product);
        return ProductResponse.from(savedProduct);
    }

    @Transactional
    public ProductResponse update(Long sellerId, Long productId, ProductUpdateRequest request) {
        Product product = findProductForOwner(sellerId, productId);
        try {
            product.update(request.title(), request.description(), request.price());
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_CHANGE_NOT_ALLOWED);
        }
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse hide(Long sellerId, Long productId) {
        Product product = findProductForOwner(sellerId, productId);
        try {
            product.hide();
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_CHANGE_NOT_ALLOWED);
        }
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse addImage(Long sellerId, Long productId, ProductImageAddRequest request) {
        Product product = findProductForOwner(sellerId, productId);
        try {
            product.addImage(request.imageUrl());
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_CHANGE_NOT_ALLOWED);
        }
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse removeImage(Long sellerId, Long productId, Long imageId) {
        Product product = findProductForOwner(sellerId, productId);
        try {
            product.removeImage(imageId);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_CHANGE_NOT_ALLOWED);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_NOT_FOUND);
        }
        return ProductResponse.from(product);
    }

    private Product findProductForOwner(Long sellerId, Long productId) {
        Product product = productRepository.findWithSellerAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(sellerId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
        return product;
    }

    private void validateCreateImages(List<ProductCreateImageRequest> images) {
        if (images.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_REQUIRED);
        }
        if (images.size() > 10) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED);
        }
        long representativeCount = images.stream()
                .filter(ProductCreateImageRequest::representative)
                .count();
        if (representativeCount != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<Integer> sortOrders = new HashSet<>();
        boolean hasDuplicateSortOrder = images.stream()
                .map(ProductCreateImageRequest::sortOrder)
                .anyMatch(sortOrder -> !sortOrders.add(sortOrder));
        if (hasDuplicateSortOrder) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private BusinessException mapProductImageException(IllegalArgumentException exception) {
        return switch (exception.getMessage()) {
            case "Product image is required" -> new BusinessException(ErrorCode.PRODUCT_IMAGE_REQUIRED);
            case "Product image limit exceeded" -> new BusinessException(ErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED);
            default -> new BusinessException(ErrorCode.VALIDATION_ERROR);
        };
    }
}
