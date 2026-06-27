package com.sweet.market.product.api;

import jakarta.validation.constraints.PositiveOrZero;

public record ProductUpdateImageRequest(
        Long imageId,
        Long uploadId,
        @PositiveOrZero int sortOrder,
        boolean representative
) {

    public boolean referencesExistingImage() {
        return imageId != null && uploadId == null;
    }

    public boolean referencesUpload() {
        return imageId == null && uploadId != null;
    }
}
