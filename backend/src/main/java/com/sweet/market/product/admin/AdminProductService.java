package com.sweet.market.product.admin;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.discovery.cache.DiscoveryInvalidationEvent;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductDomainError;
import com.sweet.market.product.repository.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminProductService(ProductRepository productRepository, ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AdminProductDetailResponse hide(Long productId) {
        Product product = productRepository.findWithStoreAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        try {
            product.hide();
        } catch (DomainException exception) {
            if (exception.error() == ProductDomainError.CHANGE_NOT_ALLOWED) {
                throw new BusinessException(ErrorCode.PRODUCT_CHANGE_NOT_ALLOWED, exception);
            }
            throw exception;
        }
        eventPublisher.publishEvent(new DiscoveryInvalidationEvent());
        return AdminProductDetailResponse.from(product);
    }
}
