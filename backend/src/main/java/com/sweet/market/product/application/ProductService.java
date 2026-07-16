package com.sweet.market.product.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.discovery.cache.DiscoveryInvalidationEvent;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.product.api.*;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductDomainError;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.product.storage.ProductImageStorageService;
import com.sweet.market.store.application.StoreAccessService;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreType;
import com.sweet.market.wishlist.repository.WishlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StoreAccessService storeAccessService;
    private final ProductImageUploadService productImageUploadService;
    private final ProductImageStorageService productImageStorageService;
    private final WishlistItemRepository wishlistItemRepository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    public ProductService(
            ProductRepository productRepository,
            StoreAccessService storeAccessService,
            ProductImageUploadService productImageUploadService,
            ProductImageStorageService productImageStorageService,
            WishlistItemRepository wishlistItemRepository,
            InventoryService inventoryService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.productRepository = productRepository;
        this.storeAccessService = storeAccessService;
        this.productImageUploadService = productImageUploadService;
        this.productImageStorageService = productImageStorageService;
        this.wishlistItemRepository = wishlistItemRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProductResponse create(Long memberId, ProductCreateRequest request) {
        Store store = storeAccessService.requireCatalogOperator(memberId, request.storeId());
        validateSalesPolicy(store, request);
        Product product = createProduct(store, request);

        validateCreateImages(request.images());
        productImageUploadService.validateConfirmableUploads(
                memberId,
                request.images().stream()
                        .map(ProductCreateImageRequest::uploadId)
                        .toList()
        );

        List<ProductImage> images = request.images().stream()
                .map(image -> productImageUploadService.confirm(
                        memberId,
                        image.uploadId(),
                        image.sortOrder(),
                        image.representative()
                ))
                .toList();
        try {
            product.replaceImages(images);
        } catch (DomainException exception) {
            throw mapProductDomainException(exception);
        }

        Product savedProduct = productRepository.save(product);
        if (savedProduct.getSalesPolicy() == ProductSalesPolicy.STOCK_MANAGED) {
            inventoryService.initialize(savedProduct, request.initialTotalQuantity(), memberId);
        }
        ProductResponse response = sellerResponse(savedProduct);
        invalidateDiscovery();
        return response;
    }

    @Transactional
    public ProductResponse update(Long memberId, Long productId, ProductUpdateRequest request) {
        Product product = findProductForOwner(memberId, productId);
        if (request.salesPolicy() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        try {
            product.update(request.title(), request.description(), request.price(), request.category());
        } catch (DomainException exception) {
            throw mapProductDomainException(exception);
        }
        if (request.lowStockThreshold() != null) {
            try {
                product.changeLowStockThreshold(request.lowStockThreshold());
            } catch (DomainException exception) {
                throw mapProductDomainException(exception);
            }
        }
        validateUpdateImages(product, request.images());
        List<Long> uploadIds = request.images().stream()
                .filter(ProductUpdateImageRequest::referencesUpload)
                .map(ProductUpdateImageRequest::uploadId)
                .toList();
        productImageUploadService.validateConfirmableUploads(memberId, uploadIds);
        List<String> omittedLocalFileNames = omittedLocalFileNames(product, request.images());

        List<ProductImage> nextImages = request.images().stream()
                .map(image -> toProductImage(memberId, product, image))
                .toList();
        try {
            product.replaceImages(nextImages);
            deletePublicFilesAfterCommit(omittedLocalFileNames);
        } catch (DomainException exception) {
            throw mapProductDomainException(exception);
        }
        ProductResponse response = sellerResponse(product);
        invalidateDiscovery();
        return response;
    }

    @Transactional
    public ProductResponse hide(Long memberId, Long productId) {
        Product product = findProductForOwner(memberId, productId);
        try {
            product.hide();
        } catch (DomainException exception) {
            throw mapProductDomainException(exception);
        }
        ProductResponse response = sellerResponse(product);
        invalidateDiscovery();
        return response;
    }

    private ProductResponse sellerResponse(Product product) {
        return ProductResponse.from(
                product,
                wishlistItemRepository.countByProductId(product.getId()),
                false,
                productRepository.findBuyerAvailabilityByProductId(product.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND))
        );
    }

    private void validateSalesPolicy(Store store, ProductCreateRequest request) {
        if (request.salesPolicy() == ProductSalesPolicy.STOCK_MANAGED && store.getType() != StoreType.BUSINESS) {
            throw new BusinessException(ErrorCode.STORE_INVALID_TYPE);
        }
    }

    private Product createProduct(Store store, ProductCreateRequest request) {
        try {
            return Product.create(
                    store,
                    request.title(),
                    request.description(),
                    request.price(),
                    request.salesPolicy(),
                    request.lowStockThreshold(),
                    request.initialTotalQuantity(),
                    request.category()
            );
        } catch (DomainException exception) {
            throw mapProductDomainException(exception);
        }
    }

    private Product findProductForOwner(Long memberId, Long productId) {
        Product product = productRepository.findWithStoreAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        storeAccessService.requireCatalogOperator(memberId, product.getStore().getId());
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

    private BusinessException mapProductDomainException(DomainException exception) {
        ErrorCode errorCode = switch ((ProductDomainError) exception.error()) {
            case IMAGE_NOT_FOUND -> ErrorCode.PRODUCT_IMAGE_NOT_FOUND;
            case IMAGE_REQUIRED -> ErrorCode.PRODUCT_IMAGE_REQUIRED;
            case IMAGE_LIMIT_EXCEEDED -> ErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED;
            case CHANGE_NOT_ALLOWED, NOT_HIDDEN, NOT_ON_SALE, NOT_RESERVED -> ErrorCode.PRODUCT_CHANGE_NOT_ALLOWED;
            default -> ErrorCode.VALIDATION_ERROR;
        };
        return new BusinessException(errorCode, exception);
    }

    private void invalidateDiscovery() {
        eventPublisher.publishEvent(new DiscoveryInvalidationEvent());
    }
}
