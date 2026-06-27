package com.sweet.market.product.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.product.application.ProductImageUploadService;

@RestController
@RequestMapping("/api/product-image-uploads")
public class ProductImageUploadController {

    private final ProductImageUploadService productImageUploadService;

    public ProductImageUploadController(ProductImageUploadService productImageUploadService) {
        this.productImageUploadService = productImageUploadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductImageUploadResponse> upload(
            Authentication authentication,
            @RequestPart MultipartFile file
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(productImageUploadService.upload(member.id(), file));
    }
}
