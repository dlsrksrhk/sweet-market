package com.sweet.market.product.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.api.ProductCreateImageRequest;
import com.sweet.market.product.api.ProductCreateRequest;
import com.sweet.market.product.api.ProductResponse;
import com.sweet.market.product.api.ProductUpdateImageRequest;
import com.sweet.market.product.api.ProductUpdateRequest;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.product.storage.ProductImageStorageService;
import com.sweet.market.wishlist.repository.WishlistItemRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final ProductImageUploadService productImageUploadService;
    private final ProductImageStorageService productImageStorageService;
    private final WishlistItemRepository wishlistItemRepository;

    public ProductService(
            ProductRepository productRepository,
            MemberRepository memberRepository,
            ProductImageUploadService productImageUploadService,
            ProductImageStorageService productImageStorageService,
            WishlistItemRepository wishlistItemRepository
    ) {
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.productImageUploadService = productImageUploadService;
        this.productImageStorageService = productImageStorageService;
        this.wishlistItemRepository = wishlistItemRepository;
    }

    @Transactional
    public ProductResponse create(Long sellerId, ProductCreateRequest request) {
        validateCreateImages(request.images());
        productImageUploadService.validateConfirmableUploads(
                sellerId,
                request.images().stream()
                        .map(ProductCreateImageRequest::uploadId)
                        .toList()
        );

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
        return sellerResponse(savedProduct);
    }

    @Transactional
    public ProductResponse update(Long sellerId, Long productId, ProductUpdateRequest request) {
        Product product = findProductForOwner(sellerId, productId);
        try {
            product.update(request.title(), request.description(), request.price());
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_CHANGE_NOT_ALLOWED);
        }
        validateUpdateImages(product, request.images());
        List<Long> uploadIds = request.images().stream()
                .filter(ProductUpdateImageRequest::referencesUpload)
                .map(ProductUpdateImageRequest::uploadId)
                .toList();
        productImageUploadService.validateConfirmableUploads(sellerId, uploadIds);
        List<String> omittedLocalFileNames = omittedLocalFileNames(product, request.images());

        List<ProductImage> nextImages = request.images().stream()
                .map(image -> toProductImage(sellerId, product, image))
                .toList();
        try {
            product.replaceImages(nextImages);
            deletePublicFilesAfterCommit(omittedLocalFileNames);
        } catch (IllegalArgumentException exception) {
            throw mapProductImageException(exception);
        }
        return sellerResponse(product);
    }

    @Transactional
    public ProductResponse hide(Long sellerId, Long productId) {
        Product product = findProductForOwner(sellerId, productId);
        try {
            product.hide();
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_CHANGE_NOT_ALLOWED);
        }
        return sellerResponse(product);
    }

    private ProductResponse sellerResponse(Product product) {
        return ProductResponse.from(product, wishlistItemRepository.countByProductId(product.getId()), false);
    }

    private Product findProductForOwner(Long sellerId, Long productId) {
        Product product = productRepository.findWithSellerAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(sellerId)) {
            throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
        return product;
    }

    private void validateUpdateImages(Product product, List<ProductUpdateImageRequest> images) {
        if (images.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_REQUIRED);
        }
        if (images.size() > 10) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED);
        }
        long representativeCount = images.stream()
                .filter(ProductUpdateImageRequest::representative)
                .count();
        if (representativeCount != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<Integer> sortOrders = new HashSet<>();
        boolean hasDuplicateSortOrder = images.stream()
                .map(ProductUpdateImageRequest::sortOrder)
                .anyMatch(sortOrder -> !sortOrders.add(sortOrder));
        if (hasDuplicateSortOrder) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        boolean hasInvalidReference = images.stream()
                .anyMatch(image -> !image.referencesExistingImage() && !image.referencesUpload());
        if (hasInvalidReference) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<Long> existingImageIds = new HashSet<>();
        boolean hasDuplicateExistingImageId = images.stream()
                .filter(ProductUpdateImageRequest::referencesExistingImage)
                .map(ProductUpdateImageRequest::imageId)
                .anyMatch(imageId -> !existingImageIds.add(imageId));
        if (hasDuplicateExistingImageId) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        boolean hasUnknownExistingImage = images.stream()
                .filter(ProductUpdateImageRequest::referencesExistingImage)
                .map(ProductUpdateImageRequest::imageId)
                .anyMatch(imageId -> product.getImages().stream()
                        .noneMatch(image -> imageId.equals(image.getId())));
        if (hasUnknownExistingImage) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_NOT_FOUND);
        }
    }

    private ProductImage toProductImage(Long sellerId, Product product, ProductUpdateImageRequest request) {
        if (request.referencesExistingImage()) {
            ProductImage image = product.getImages().stream()
                    .filter(existingImage -> request.imageId().equals(existingImage.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_IMAGE_NOT_FOUND));
            image.changeArrangement(request.sortOrder(), request.representative());
            return image;
        }
        return productImageUploadService.confirm(
                sellerId,
                request.uploadId(),
                request.sortOrder(),
                request.representative()
        );
    }

    private List<String> omittedLocalFileNames(Product product, List<ProductUpdateImageRequest> images) {
        Set<Long> retainedImageIds = images.stream()
                .filter(ProductUpdateImageRequest::referencesExistingImage)
                .map(ProductUpdateImageRequest::imageId)
                .collect(Collectors.toSet());
        return product.getImages().stream()
                .filter(ProductImage::isLocalFile)
                .filter(image -> !retainedImageIds.contains(image.getId()))
                .map(ProductImage::getStoredFileName)
                .toList();
    }

    private void deletePublicFilesAfterCommit(List<String> storedFileNames) {
        if (storedFileNames.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storedFileNames.forEach(this::deletePublicBestEffort);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storedFileNames.forEach(ProductService.this::deletePublicBestEffort);
            }
        });
    }

    private void deletePublicBestEffort(String storedFileName) {
        try {
            productImageStorageService.deletePublic(storedFileName);
        } catch (RuntimeException ignored) {
            // Best-effort cleanup after DB commit.
        }
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
