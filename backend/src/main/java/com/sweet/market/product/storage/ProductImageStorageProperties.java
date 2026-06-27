package com.sweet.market.product.storage;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "product.images")
public class ProductImageStorageProperties {

    private Path uploadRoot = Path.of("./.local/product-images");
    private String tempDir = "temp";
    private String publicDir = "public";
    private Duration tempExpiration = Duration.ofMinutes(60);
    private DataSize maxFileSize = DataSize.ofMegabytes(5);

    public Path getUploadRoot() {
        return uploadRoot;
    }

    public void setUploadRoot(Path uploadRoot) {
        if (uploadRoot != null) {
            this.uploadRoot = uploadRoot;
        }
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        if (StringUtils.hasText(tempDir)) {
            this.tempDir = tempDir;
        }
    }

    public String getPublicDir() {
        return publicDir;
    }

    public void setPublicDir(String publicDir) {
        if (StringUtils.hasText(publicDir)) {
            this.publicDir = publicDir;
        }
    }

    public Duration getTempExpiration() {
        return tempExpiration;
    }

    public void setTempExpiration(Duration tempExpiration) {
        if (tempExpiration != null) {
            this.tempExpiration = tempExpiration;
        }
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        if (maxFileSize != null) {
            this.maxFileSize = maxFileSize;
        }
    }

    public Path tempPath() {
        return uploadRoot.resolve(tempDir);
    }

    public Path publicPath() {
        return uploadRoot.resolve(publicDir);
    }
}
