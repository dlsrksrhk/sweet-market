package com.sweet.market.product.storage;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
        this.uploadRoot = uploadRoot;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public String getPublicDir() {
        return publicDir;
    }

    public void setPublicDir(String publicDir) {
        this.publicDir = publicDir;
    }

    public Duration getTempExpiration() {
        return tempExpiration;
    }

    public void setTempExpiration(Duration tempExpiration) {
        this.tempExpiration = tempExpiration;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public Path tempPath() {
        return uploadRoot.resolve(tempDir);
    }

    public Path publicPath() {
        return uploadRoot.resolve(publicDir);
    }
}
