package com.sweet.market.product.api;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.product.application.ProductImageUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
