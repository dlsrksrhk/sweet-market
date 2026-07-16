package com.sweet.market.product.storage;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

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

    public StoredProductImage storeTemporary(MultipartFile file) {
        validate(file);

        String extension = EXTENSIONS.get(file.getContentType());
        String storedFileName = UUID.randomUUID() + extension;
        Path target = resolveUnder(properties.tempPath(), storedFileName);

        try {
            Files.createDirectories(normalizedRoot(properties.tempPath()));
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
        Path source = resolveUnder(properties.tempPath(), storedFileName);
        Path target = resolveUnder(properties.publicPath(), storedFileName);

        try {
            Files.createDirectories(normalizedRoot(properties.publicPath()));
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_UPLOAD_NOT_FOUND);
        }

        return new StoredProductImage(storedFileName, originalFileName, contentType, size, PUBLIC_URL_PREFIX + storedFileName);
    }

    public void restoreTemporary(String storedFileName) {
        Path source = resolveUnder(properties.publicPath(), storedFileName);
        Path target = resolveUnder(properties.tempPath(), storedFileName);

        try {
            Files.createDirectories(normalizedRoot(properties.tempPath()));
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
    }

    public void deleteTemporary(String storedFileName) {
        delete(resolveUnder(properties.tempPath(), storedFileName));
    }

    public void deletePublic(String storedFileName) {
        delete(resolveUnder(properties.publicPath(), storedFileName));
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
        if (!hasValidSignature(file)) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
    }

    private boolean hasValidSignature(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(12);
            return switch (file.getContentType()) {
                case "image/jpeg" -> startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
                case "image/png" -> startsWith(header, new byte[]{
                        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
                });
                case "image/webp" -> startsWith(header, new byte[]{0x52, 0x49, 0x46, 0x46})
                        && startsAt(header, 8, new byte[]{0x57, 0x45, 0x42, 0x50});
                default -> false;
            };
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsAt(byte[] source, int offset, byte[] prefix) {
        if (source.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
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
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
    }

    private Path resolveUnder(Path root, String storedFileName) {
        if (!StringUtils.hasText(storedFileName)
                || storedFileName.contains("..")
                || storedFileName.contains("/")
                || storedFileName.contains("\\")) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }

        try {
            Path normalizedRoot = normalizedRoot(root);
            Path target = normalizedRoot.resolve(storedFileName).normalize();
            if (!target.startsWith(normalizedRoot)) {
                throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
            }
            return target;
        } catch (InvalidPathException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
    }

    private Path normalizedRoot(Path root) {
        return root.toAbsolutePath().normalize();
    }
}
