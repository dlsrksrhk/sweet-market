package com.sweet.market.product.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;

@Service
public class ProductImageStorageService {

    private static final String TEMP_URL_PREFIX = "/uploads/products/temp/";
    private static final String PUBLIC_URL_PREFIX = "/uploads/products/public/";
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final ProductImageStorageProperties properties;

    public ProductImageStorageService(ProductImageStorageProperties properties) {
        this.properties = properties;
    }

    public StoredProductImage storeTemp(MultipartFile file) {
        validate(file);

        String extension = EXTENSIONS.get(file.getContentType());
        String storedFileName = UUID.randomUUID() + extension;
        Path target = properties.tempPath().resolve(storedFileName).normalize();

        try {
            Files.createDirectories(properties.tempPath());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }

        return new StoredProductImage(
                storedFileName,
                originalFileName(file),
                file.getContentType(),
                file.getSize(),
                TEMP_URL_PREFIX + storedFileName
        );
    }

    public StoredProductImage confirm(String storedFileName, String originalFileName, String contentType, long size) {
        Path source = properties.tempPath().resolve(storedFileName).normalize();
        Path target = properties.publicPath().resolve(storedFileName).normalize();

        try {
            Files.createDirectories(properties.publicPath());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_UPLOAD_NOT_FOUND);
        }

        return new StoredProductImage(storedFileName, originalFileName, contentType, size, PUBLIC_URL_PREFIX + storedFileName);
    }

    public void deleteTemp(String storedFileName) {
        delete(properties.tempPath().resolve(storedFileName));
    }

    public void deletePublic(String storedFileName) {
        delete(properties.publicPath().resolve(storedFileName));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
        if (!EXTENSIONS.containsKey(file.getContentType())) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
    }

    private String originalFileName(MultipartFile file) {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        if (!StringUtils.hasText(originalFileName)) {
            return "product-image";
        }
        return originalFileName;
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path.normalize());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
    }
}
