package com.sweet.market.product.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductDomainError;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class AdminProductService {

    private final ProductRepository productRepository;

    public AdminProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
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
        return AdminProductDetailResponse.from(product);
    }
}
