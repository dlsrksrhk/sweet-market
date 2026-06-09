package com.sweet.market.product.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.api.ProductCreateRequest;
import com.sweet.market.product.api.ProductResponse;
import com.sweet.market.product.api.ProductUpdateRequest;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    public ProductService(ProductRepository productRepository, MemberRepository memberRepository) {
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public ProductResponse create(Long sellerId, ProductCreateRequest request) {
        Member seller = memberRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Product product = Product.create(seller, request.title(), request.description(), request.price());
        request.imageUrls().forEach(product::addImage);

        Product savedProduct = productRepository.save(product);
        return ProductResponse.from(savedProduct);
    }

    @Transactional
    public ProductResponse update(Long sellerId, Long productId, ProductUpdateRequest request) {
        Product product = findProductForOwner(sellerId, productId);
        product.update(request.title(), request.description(), request.price());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse hide(Long sellerId, Long productId) {
        Product product = findProductForOwner(sellerId, productId);
        product.hide();
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
}
