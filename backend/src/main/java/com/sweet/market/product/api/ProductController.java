package com.sweet.market.product.api;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.product.application.ProductService;
import com.sweet.market.product.query.ProductQueryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ProductQueryService productQueryService;

    public ProductController(ProductService productService, ProductQueryService productQueryService) {
        this.productService = productService;
        this.productQueryService = productQueryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(
            Authentication authentication,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productService.create(member.id(), request));
    }

    @GetMapping
    public ApiResponse<Page<ProductSummaryResponse>> list(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(productQueryService.findOnSaleProducts(pageable));
    }

    @GetMapping("/me")
    public ApiResponse<Page<ProductSummaryResponse>> listMine(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productQueryService.findMine(member.id(), pageable));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> get(@PathVariable Long productId) {
        return ApiResponse.ok(productQueryService.findOnSaleProduct(productId));
    }

    @PatchMapping("/{productId}")
    public ApiResponse<ProductResponse> update(
            Authentication authentication,
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productService.update(member.id(), productId, request));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<ProductResponse> hide(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productService.hide(member.id(), productId));
    }

}
