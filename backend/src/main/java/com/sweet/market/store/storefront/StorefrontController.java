package com.sweet.market.store.storefront;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;

@RestController
@RequestMapping("/api/stores")
public class StorefrontController {

    private final StorefrontQueryService storefrontQueryService;

    public StorefrontController(StorefrontQueryService storefrontQueryService) {
        this.storefrontQueryService = storefrontQueryService;
    }

    @GetMapping("/{storeId}")
    public ApiResponse<StorefrontResponse> storefront(@PathVariable long storeId) {
        return ApiResponse.ok(storefrontQueryService.findStorefront(storeId));
    }
}
