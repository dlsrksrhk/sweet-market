# Milestone 14 Product Images And Product UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace URL-based product image writes with local file upload, temporary upload confirmation, representative image selection, image ordering, and product image UX.

**Architecture:** Add a seller-owned temporary upload model and local storage service. Confirm temporary uploads into `ProductImage` children during product create/update, while preserving read compatibility for existing URL rows. The web product form uploads files immediately, then submits a final image arrangement with representative and sort order metadata.

**Tech Stack:** Spring Boot 3.5, Spring MVC multipart upload, Spring Data JPA, PostgreSQL/Testcontainers, Java 21, React, TypeScript, React Query, React Hook Form, Vite.

---

## File Structure

Backend files to create:

- `backend/src/main/java/com/sweet/market/product/api/ProductImageUploadController.java` - multipart temporary upload endpoint.
- `backend/src/main/java/com/sweet/market/product/api/ProductImageUploadResponse.java` - upload API response.
- `backend/src/main/java/com/sweet/market/product/api/ProductCreateImageRequest.java` - create image arrangement item.
- `backend/src/main/java/com/sweet/market/product/api/ProductUpdateImageRequest.java` - update image arrangement item.
- `backend/src/main/java/com/sweet/market/product/domain/ProductImageUpload.java` - temporary upload entity.
- `backend/src/main/java/com/sweet/market/product/repository/ProductImageUploadRepository.java` - temporary upload repository.
- `backend/src/main/java/com/sweet/market/product/storage/ProductImageStorageProperties.java` - local storage configuration.
- `backend/src/main/java/com/sweet/market/product/storage/ProductImageStorageService.java` - file validation, write, move, and delete operations.
- `backend/src/main/java/com/sweet/market/product/storage/StoredProductImage.java` - immutable file metadata returned by storage.
- `backend/src/main/java/com/sweet/market/product/config/ProductImageWebConfig.java` - static resource handler for local upload URLs.
- `backend/src/main/java/com/sweet/market/product/application/ProductImageUploadService.java` - upload and confirmation use cases.
- `backend/src/main/java/com/sweet/market/product/application/ProductImageCleanupService.java` - expired upload cleanup.
- `backend/src/main/java/com/sweet/market/product/application/ProductImageCleanupScheduler.java` - thin scheduled cleanup entry point.
- `backend/src/test/java/com/sweet/market/product/ProductImageUploadApiTest.java` - multipart upload API tests.
- `backend/src/test/java/com/sweet/market/product/ProductImageCleanupServiceTest.java` - cleanup service tests.

Backend files to modify:

- `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java` - add image upload/product image validation errors.
- `backend/src/main/java/com/sweet/market/product/api/ProductCreateRequest.java` - replace `imageUrls` with upload-backed image requests.
- `backend/src/main/java/com/sweet/market/product/api/ProductUpdateRequest.java` - include final image arrangement.
- `backend/src/main/java/com/sweet/market/product/api/ProductImageResponse.java` - include `sortOrder` and `representative`.
- `backend/src/main/java/com/sweet/market/product/api/ProductResponse.java` - return ordered image metadata.
- `backend/src/main/java/com/sweet/market/product/api/ProductController.java` - remove URL image write endpoints.
- `backend/src/main/java/com/sweet/market/product/api/ProductImageAddRequest.java` - delete after URL write endpoints are removed.
- `backend/src/main/java/com/sweet/market/product/application/ProductService.java` - create/update products from final image arrangements.
- `backend/src/main/java/com/sweet/market/product/domain/Product.java` - enforce image invariants and final arrangement changes.
- `backend/src/main/java/com/sweet/market/product/domain/ProductImage.java` - add metadata, sort order, representative flag, legacy factory.
- `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java` - representative thumbnail projections and ordered detail loading.
- `backend/src/main/resources/application.yaml` - do not edit in the main checkout while local-only changes exist; add config in an implementation worktree only.
- `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java` - truncate temporary upload table and set test storage root.
- `backend/src/test/java/com/sweet/market/product/ProductApiTest.java` - migrate product create/update tests to upload-backed images.
- `backend/src/test/java/com/sweet/market/product/ProductSellerApiTest.java` - migrate helper to upload-backed create requests.
- `backend/src/test/java/com/sweet/market/product/domain/ProductTest.java` - cover invariants, representative image, ordering, and legacy URL compatibility.
- `backend/src/test/java/com/sweet/market/jpalab/ProductQueryOptimizationTest.java` - keep list image collection unloaded.

Frontend files to modify:

- `web/src/features/products/productApi.ts` - add upload API and image arrangement types.
- `web/src/pages/ProductFormPage.tsx` - replace URL textarea with upload preview, representative, ordering, and deletion UX.
- `web/src/pages/ProductDetailPage.tsx` - render representative image first and metadata-aware gallery.
- `web/src/pages/HomePage.tsx` - resolve local backend image URLs for product cards.
- `web/src/pages/MyReportsPage.tsx` - resolve local backend image URLs for ranking thumbnails.
- `web/src/shared/styles.css` - add focused image manager styles.

Operational files:

- `docs/superpowers/handoffs/2026-06-27-milestone-14-product-images-and-product-ux-handoff.md` - create after implementation.

## Task 0: Isolate Worktree And Baseline

**Files:**
- Read: `docs/superpowers/specs/2026-06-27-milestone-14-product-images-and-product-ux-design.md`
- Read: `docs/superpowers/plans/2026-06-27-milestone-14-product-images-and-product-ux.md`

- [ ] **Step 1: Create an isolated worktree**

Run from `C:\dev\study\sweet-market`:

```powershell
git status --short --branch --untracked-files=all
git worktree add .worktrees/milestone-14-product-images -b codex/milestone-14-product-images main
cd .worktrees/milestone-14-product-images
git status --short --branch --untracked-files=all
```

Expected:

```text
## codex/milestone-14-product-images
```

The pre-existing main-checkout `backend/src/main/resources/application.yaml` change must not appear inside the new clean worktree.

- [ ] **Step 2: Run baseline backend tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run baseline web build**

Run:

```powershell
cd ..\web
npm run build
```

Expected: Vite build succeeds.

## Task 1: Add Upload Storage Configuration And Temporary Upload API

**Files:**
- Create: `backend/src/main/java/com/sweet/market/product/storage/ProductImageStorageProperties.java`
- Create: `backend/src/main/java/com/sweet/market/product/storage/StoredProductImage.java`
- Create: `backend/src/main/java/com/sweet/market/product/storage/ProductImageStorageService.java`
- Create: `backend/src/main/java/com/sweet/market/product/config/ProductImageWebConfig.java`
- Create: `backend/src/main/java/com/sweet/market/product/domain/ProductImageUpload.java`
- Create: `backend/src/main/java/com/sweet/market/product/repository/ProductImageUploadRepository.java`
- Create: `backend/src/main/java/com/sweet/market/product/application/ProductImageUploadService.java`
- Create: `backend/src/main/java/com/sweet/market/product/api/ProductImageUploadController.java`
- Create: `backend/src/main/java/com/sweet/market/product/api/ProductImageUploadResponse.java`
- Create: `backend/src/test/java/com/sweet/market/product/ProductImageUploadApiTest.java`
- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Modify: `backend/src/main/resources/application.yaml`
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`

- [ ] **Step 1: Write failing upload API tests**

Create `backend/src/test/java/com/sweet/market/product/ProductImageUploadApiTest.java`:

```java
package com.sweet.market.product;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class ProductImageUploadApiTest extends IntegrationTestSupport {

    @Test
    void 상품_이미지_임시_업로드에_성공한다() throws Exception {
        String accessToken = signupAndLogin("seller-upload@example.com", "password123", "seller");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        mockMvc.perform(multipart("/api/product-image-uploads")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.previewUrl", endsWith(".jpg")))
                .andExpect(jsonPath("$.data.originalFileName").value("product.jpg"))
                .andExpect(jsonPath("$.data.contentType").value(MediaType.IMAGE_JPEG_VALUE))
                .andExpect(jsonPath("$.data.size").value(4))
                .andExpect(jsonPath("$.data.expiresAt", not(blankOrNullString())));
    }

    @Test
    void 상품_이미지_임시_업로드는_JWT가_필요하다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        mockMvc.perform(multipart("/api/product-image-uploads").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 지원하지_않는_상품_이미지_형식은_업로드할_수_없다() throws Exception {
        String accessToken = signupAndLogin("seller-upload-invalid@example.com", "password123", "seller");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/product-image-uploads")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_INVALID_FILE"));
    }

    private String signupAndLogin(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, password, nickname))))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }
}
```

- [ ] **Step 2: Run upload API test to verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.ProductImageUploadApiTest"
```

Expected: compile or test failure because `/api/product-image-uploads` and upload classes do not exist.

- [ ] **Step 3: Add error codes**

Modify `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java` by adding these constants after `PRODUCT_IMAGE_NOT_FOUND`:

```java
PRODUCT_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "상품 이미지는 최소 1개 이상 필요합니다."),
PRODUCT_IMAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "상품 이미지는 최대 10개까지 등록할 수 있습니다."),
PRODUCT_IMAGE_INVALID_FILE(HttpStatus.BAD_REQUEST, "상품 이미지 파일이 올바르지 않습니다."),
PRODUCT_IMAGE_UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "임시 업로드 상품 이미지를 찾을 수 없습니다."),
PRODUCT_IMAGE_UPLOAD_EXPIRED(HttpStatus.BAD_REQUEST, "임시 업로드 상품 이미지가 만료되었습니다."),
```

- [ ] **Step 4: Add storage properties**

Create `backend/src/main/java/com/sweet/market/product/storage/ProductImageStorageProperties.java`:

```java
package com.sweet.market.product.storage;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "product.images")
public record ProductImageStorageProperties(
        Path uploadRoot,
        String tempDir,
        String publicDir,
        Duration tempExpiration,
        DataSize maxFileSize
) {
    public ProductImageStorageProperties {
        if (uploadRoot == null) {
            uploadRoot = Path.of(".local/product-images");
        }
        if (tempDir == null || tempDir.isBlank()) {
            tempDir = "temp";
        }
        if (publicDir == null || publicDir.isBlank()) {
            publicDir = "public";
        }
        if (tempExpiration == null) {
            tempExpiration = Duration.ofMinutes(60);
        }
        if (maxFileSize == null) {
            maxFileSize = DataSize.ofMegabytes(5);
        }
    }

    public Path tempPath() {
        return uploadRoot.resolve(tempDir).normalize();
    }

    public Path publicPath() {
        return uploadRoot.resolve(publicDir).normalize();
    }
}
```

- [ ] **Step 5: Add stored file metadata**

Create `backend/src/main/java/com/sweet/market/product/storage/StoredProductImage.java`:

```java
package com.sweet.market.product.storage;

public record StoredProductImage(
        String storedFileName,
        String originalFileName,
        String contentType,
        long size,
        String url
) {
}
```

- [ ] **Step 6: Add local storage service**

Create `backend/src/main/java/com/sweet/market/product/storage/ProductImageStorageService.java`:

```java
package com.sweet.market.product.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;

@Service
public class ProductImageStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
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
        String contentType = file.getContentType();
        String storedFileName = UUID.randomUUID() + EXTENSIONS.get(contentType);
        Path target = properties.tempPath().resolve(storedFileName).normalize();
        write(file, target);
        return new StoredProductImage(
                storedFileName,
                file.getOriginalFilename(),
                contentType,
                file.getSize(),
                "/uploads/products/temp/" + storedFileName
        );
    }

    public StoredProductImage confirm(String storedFileName, String originalFileName, String contentType, long size) {
        Path source = properties.tempPath().resolve(storedFileName).normalize();
        Path target = properties.publicPath().resolve(storedFileName).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredProductImage(
                    storedFileName,
                    originalFileName,
                    contentType,
                    size,
                    "/uploads/products/public/" + storedFileName
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to confirm product image file", exception);
        }
    }

    public void deleteTemporary(String storedFileName) {
        deleteIfExists(properties.tempPath().resolve(storedFileName).normalize());
    }

    public void deletePublic(String storedFileName) {
        deleteIfExists(properties.publicPath().resolve(storedFileName).normalize());
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
        }
    }

    private void write(MultipartFile file, Path target) {
        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store product image file", exception);
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete product image file", exception);
        }
    }
}
```

- [ ] **Step 7: Add temporary upload entity and repository**

Create `backend/src/main/java/com/sweet/market/product/domain/ProductImageUpload.java`:

```java
package com.sweet.market.product.domain;

import java.time.LocalDateTime;

import com.sweet.market.member.domain.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product_image_uploads")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImageUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private Member uploader;

    @Column(nullable = false, length = 255)
    private String storedFileName;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, length = 500)
    private String previewUrl;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private ProductImageUpload(
            Member uploader,
            String storedFileName,
            String originalFileName,
            String contentType,
            long size,
            String previewUrl,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        this.uploader = uploader;
        this.storedFileName = storedFileName;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.size = size;
        this.previewUrl = previewUrl;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static ProductImageUpload create(
            Member uploader,
            String storedFileName,
            String originalFileName,
            String contentType,
            long size,
            String previewUrl,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        return new ProductImageUpload(
                uploader,
                storedFileName,
                originalFileName,
                contentType,
                size,
                previewUrl,
                expiresAt,
                createdAt
        );
    }

    public boolean isOwnedBy(Long memberId) {
        return uploader.getId().equals(memberId);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
```

Create `backend/src/main/java/com/sweet/market/product/repository/ProductImageUploadRepository.java`:

```java
package com.sweet.market.product.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.product.domain.ProductImageUpload;

public interface ProductImageUploadRepository extends JpaRepository<ProductImageUpload, Long> {

    List<ProductImageUpload> findByExpiresAtBefore(LocalDateTime now);
}
```

- [ ] **Step 8: Add upload response, service, and controller**

Create `backend/src/main/java/com/sweet/market/product/api/ProductImageUploadResponse.java`:

```java
package com.sweet.market.product.api;

import java.time.LocalDateTime;

import com.sweet.market.product.domain.ProductImageUpload;

public record ProductImageUploadResponse(
        Long id,
        String previewUrl,
        String originalFileName,
        String contentType,
        long size,
        LocalDateTime expiresAt
) {
    public static ProductImageUploadResponse from(ProductImageUpload upload) {
        return new ProductImageUploadResponse(
                upload.getId(),
                upload.getPreviewUrl(),
                upload.getOriginalFileName(),
                upload.getContentType(),
                upload.getSize(),
                upload.getExpiresAt()
        );
    }
}
```

Create `backend/src/main/java/com/sweet/market/product/application/ProductImageUploadService.java`:

```java
package com.sweet.market.product.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.api.ProductImageUploadResponse;
import com.sweet.market.product.domain.ProductImageUpload;
import com.sweet.market.product.repository.ProductImageUploadRepository;
import com.sweet.market.product.storage.ProductImageStorageProperties;
import com.sweet.market.product.storage.ProductImageStorageService;
import com.sweet.market.product.storage.StoredProductImage;

@Service
public class ProductImageUploadService {

    private final ProductImageUploadRepository uploadRepository;
    private final MemberRepository memberRepository;
    private final ProductImageStorageService storageService;
    private final ProductImageStorageProperties properties;

    public ProductImageUploadService(
            ProductImageUploadRepository uploadRepository,
            MemberRepository memberRepository,
            ProductImageStorageService storageService,
            ProductImageStorageProperties properties
    ) {
        this.uploadRepository = uploadRepository;
        this.memberRepository = memberRepository;
        this.storageService = storageService;
        this.properties = properties;
    }

    @Transactional
    public ProductImageUploadResponse upload(Long uploaderId, MultipartFile file) {
        Member uploader = memberRepository.findById(uploaderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        StoredProductImage storedImage = storageService.storeTemporary(file);
        LocalDateTime now = LocalDateTime.now();
        ProductImageUpload upload = ProductImageUpload.create(
                uploader,
                storedImage.storedFileName(),
                storedImage.originalFileName(),
                storedImage.contentType(),
                storedImage.size(),
                storedImage.url(),
                now.plus(properties.tempExpiration()),
                now
        );
        return ProductImageUploadResponse.from(uploadRepository.save(upload));
    }
}
```

Create `backend/src/main/java/com/sweet/market/product/api/ProductImageUploadController.java`:

```java
package com.sweet.market.product.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.product.application.ProductImageUploadService;

@RestController
@RequestMapping("/api/product-image-uploads")
public class ProductImageUploadController {

    private final ProductImageUploadService uploadService;

    public ProductImageUploadController(ProductImageUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductImageUploadResponse> upload(
            Authentication authentication,
            @RequestParam MultipartFile file
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(uploadService.upload(member.id(), file));
    }
}
```

- [ ] **Step 9: Add static resource config**

Create `backend/src/main/java/com/sweet/market/product/config/ProductImageWebConfig.java`:

```java
package com.sweet.market.product.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sweet.market.product.storage.ProductImageStorageProperties;

@Configuration
@EnableConfigurationProperties(ProductImageStorageProperties.class)
public class ProductImageWebConfig implements WebMvcConfigurer {

    private final ProductImageStorageProperties properties;

    public ProductImageWebConfig(ProductImageStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/products/temp/**")
                .addResourceLocations(properties.tempPath().toUri().toString());
        registry.addResourceHandler("/uploads/products/public/**")
                .addResourceLocations(properties.publicPath().toUri().toString());
    }
}
```

- [ ] **Step 10: Add configuration values**

In the implementation worktree only, add these values to `backend/src/main/resources/application.yaml`:

```yaml
product:
  images:
    upload-root: ${PRODUCT_IMAGE_UPLOAD_ROOT:./.local/product-images}
    temp-dir: temp
    public-dir: public
    temp-expiration: ${PRODUCT_IMAGE_TEMP_EXPIRATION:60m}
    max-file-size: 5MB
```

Also set Spring multipart size limits under `spring.servlet.multipart`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB
```

- [ ] **Step 11: Update integration test support**

Modify `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`:

```java
registry.add("product.images.upload-root", () -> "build/test-product-images");
registry.add("product.images.temp-expiration", () -> "60m");
registry.add("spring.servlet.multipart.max-file-size", () -> "5MB");
registry.add("spring.servlet.multipart.max-request-size", () -> "6MB");
```

Change cleanup SQL to include `product_image_uploads`:

```java
jdbcTemplate.execute("TRUNCATE TABLE settlements, deliveries, payments, orders, product_image_uploads, product_images, products, members RESTART IDENTITY CASCADE");
```

- [ ] **Step 12: Run upload tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.ProductImageUploadApiTest"
```

Expected: tests pass.

- [ ] **Step 13: Commit upload API foundation**

Run:

```powershell
git add backend/src/main/java/com/sweet/market/common/error/ErrorCode.java `
  backend/src/main/java/com/sweet/market/product/storage `
  backend/src/main/java/com/sweet/market/product/config/ProductImageWebConfig.java `
  backend/src/main/java/com/sweet/market/product/domain/ProductImageUpload.java `
  backend/src/main/java/com/sweet/market/product/repository/ProductImageUploadRepository.java `
  backend/src/main/java/com/sweet/market/product/application/ProductImageUploadService.java `
  backend/src/main/java/com/sweet/market/product/api/ProductImageUploadController.java `
  backend/src/main/java/com/sweet/market/product/api/ProductImageUploadResponse.java `
  backend/src/main/resources/application.yaml `
  backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java `
  backend/src/test/java/com/sweet/market/product/ProductImageUploadApiTest.java
git commit -m "feat: add product image upload API"
```

Expected: commit succeeds.

## Task 2: Extend Product Image Domain Invariants

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/product/domain/ProductImage.java`
- Modify: `backend/src/main/java/com/sweet/market/product/domain/Product.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductImageResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductResponse.java`
- Modify: `backend/src/test/java/com/sweet/market/product/domain/ProductTest.java`

- [ ] **Step 1: Add failing domain tests**

Append these tests to `backend/src/test/java/com/sweet/market/product/domain/ProductTest.java`:

```java
@Test
void 상품_이미지는_대표_이미지와_순서를_가진다() {
    Member seller = Member.create("seller@example.com", "encoded-password", "seller");
    Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

    product.replaceImages(List.of(
            ProductImage.local("/uploads/products/public/a.jpg", "a.jpg", "a.jpg", "image/jpeg", 100L, 1, false),
            ProductImage.local("/uploads/products/public/b.jpg", "b.jpg", "b.jpg", "image/jpeg", 100L, 0, true)
    ));

    assertThat(product.getImages()).extracting(ProductImage::getImageUrl)
            .containsExactly("/uploads/products/public/b.jpg", "/uploads/products/public/a.jpg");
    assertThat(product.getImages()).extracting(ProductImage::isRepresentative)
            .containsExactly(true, false);
}

@Test
void 상품_이미지는_최소_한_개가_필요하다() {
    Member seller = Member.create("seller@example.com", "encoded-password", "seller");
    Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

    assertThatThrownBy(() -> product.replaceImages(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Product image is required");
}

@Test
void 상품_대표_이미지는_정확히_한_개여야_한다() {
    Member seller = Member.create("seller@example.com", "encoded-password", "seller");
    Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

    assertThatThrownBy(() -> product.replaceImages(List.of(
            ProductImage.local("/uploads/products/public/a.jpg", "a.jpg", "a.jpg", "image/jpeg", 100L, 0, false)
    )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Product representative image must be exactly one");
}
```

Add `import java.util.List;` to the test file.

- [ ] **Step 2: Run domain tests to verify failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests "com.sweet.market.product.domain.ProductTest"
```

Expected: compile failure because `replaceImages`, `ProductImage.local`, and `isRepresentative` do not exist.

- [ ] **Step 3: Replace ProductImage implementation**

Modify `backend/src/main/java/com/sweet/market/product/domain/ProductImage.java`:

```java
package com.sweet.market.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int sortOrder;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean representative;

    @Column(length = 255)
    private String storedFileName;

    @Column(length = 255)
    private String originalFileName;

    @Column(length = 100)
    private String contentType;

    private Long size;

    private ProductImage(
            String imageUrl,
            int sortOrder,
            boolean representative,
            String storedFileName,
            String originalFileName,
            String contentType,
            Long size
    ) {
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.representative = representative;
        this.storedFileName = storedFileName;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.size = size;
    }

    public static ProductImage legacyUrl(String imageUrl, int sortOrder, boolean representative) {
        return new ProductImage(imageUrl, sortOrder, representative, null, null, null, null);
    }

    public static ProductImage local(
            String imageUrl,
            String storedFileName,
            String originalFileName,
            String contentType,
            long size,
            int sortOrder,
            boolean representative
    ) {
        return new ProductImage(
                imageUrl,
                sortOrder,
                representative,
                storedFileName,
                originalFileName,
                contentType,
                size
        );
    }

    void assignProduct(Product product) {
        this.product = product;
    }

    public void changeArrangement(int sortOrder, boolean representative) {
        this.sortOrder = sortOrder;
        this.representative = representative;
    }

    public boolean isLocalFile() {
        return storedFileName != null && !storedFileName.isBlank();
    }
}
```

- [ ] **Step 4: Add Product image invariant methods**

Modify `backend/src/main/java/com/sweet/market/product/domain/Product.java`.

Replace `addImage` with a legacy helper used only by tests/demo compatibility:

```java
public ProductImage addLegacyImage(String imageUrl) {
    validateNotReserved();
    ProductImage image = ProductImage.legacyUrl(imageUrl, images.size(), images.isEmpty());
    image.assignProduct(this);
    images.add(image);
    sortImages();
    return image;
}
```

Add final replacement method:

```java
public void replaceImages(List<ProductImage> nextImages) {
    validateNotReserved();
    validateImages(nextImages);
    images.clear();
    nextImages.forEach(image -> {
        image.assignProduct(this);
        images.add(image);
    });
    sortImages();
}
```

Add validation helpers:

```java
private void validateImages(List<ProductImage> nextImages) {
    if (nextImages.isEmpty()) {
        throw new IllegalArgumentException("Product image is required");
    }
    if (nextImages.size() > 10) {
        throw new IllegalArgumentException("Product image limit exceeded");
    }
    long representativeCount = nextImages.stream()
            .filter(ProductImage::isRepresentative)
            .count();
    if (representativeCount != 1) {
        throw new IllegalArgumentException("Product representative image must be exactly one");
    }
    long distinctSortOrderCount = nextImages.stream()
            .map(ProductImage::getSortOrder)
            .distinct()
            .count();
    if (distinctSortOrderCount != nextImages.size()) {
        throw new IllegalArgumentException("Product image sort order must be unique");
    }
}

private void sortImages() {
    images.sort((left, right) -> Integer.compare(left.getSortOrder(), right.getSortOrder()));
}
```

Update `getImages()`:

```java
public List<ProductImage> getImages() {
    sortImages();
    return Collections.unmodifiableList(images);
}
```

Keep `removeImage(Long imageId)` until Task 4 removes URL-style image endpoints. Task 4 stops calling it from controllers.

- [ ] **Step 5: Update image responses**

Modify `backend/src/main/java/com/sweet/market/product/api/ProductImageResponse.java`:

```java
package com.sweet.market.product.api;

import com.sweet.market.product.domain.ProductImage;

public record ProductImageResponse(
        Long id,
        String imageUrl,
        int sortOrder,
        boolean representative
) {

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getImageUrl(),
                image.getSortOrder(),
                image.isRepresentative()
        );
    }
}
```

- [ ] **Step 6: Run domain tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests "com.sweet.market.product.domain.ProductTest"
```

Expected: domain tests pass.

- [ ] **Step 7: Commit domain image invariants**

Run:

```powershell
git add backend/src/main/java/com/sweet/market/product/domain/Product.java `
  backend/src/main/java/com/sweet/market/product/domain/ProductImage.java `
  backend/src/main/java/com/sweet/market/product/api/ProductImageResponse.java `
  backend/src/test/java/com/sweet/market/product/domain/ProductTest.java
git commit -m "feat: add product image ordering invariants"
```

Expected: commit succeeds.

## Task 3: Confirm Temporary Uploads During Product Create

**Files:**
- Create: `backend/src/main/java/com/sweet/market/product/api/ProductCreateImageRequest.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductCreateRequest.java`
- Modify: `backend/src/main/java/com/sweet/market/product/application/ProductImageUploadService.java`
- Modify: `backend/src/main/java/com/sweet/market/product/application/ProductService.java`
- Modify: `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/product/ProductSellerApiTest.java`

- [ ] **Step 1: Update ProductApiTest with upload-backed create**

In `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`, replace `상품_등록에_성공한다` with:

```java
@Test
void 상품_등록에_성공한다() throws Exception {
    String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
    Long uploadId = uploadImage(accessToken, "macbook-1.jpg");

    mockMvc.perform(post("/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "MacBook Pro",
                              "description": "M3 laptop",
                              "price": 2000000,
                              "images": [
                                {
                                  "uploadId": %d,
                                  "sortOrder": 0,
                                  "representative": true
                                }
                              ]
                            }
                            """.formatted(uploadId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.sellerId").isNumber())
            .andExpect(jsonPath("$.data.sellerNickname").value("seller"))
            .andExpect(jsonPath("$.data.title").value("MacBook Pro"))
            .andExpect(jsonPath("$.data.description").value("M3 laptop"))
            .andExpect(jsonPath("$.data.price").value(2000000))
            .andExpect(jsonPath("$.data.status").value("ON_SALE"))
            .andExpect(jsonPath("$.data.images", hasSize(1)))
            .andExpect(jsonPath("$.data.images[0].imageUrl").value(org.hamcrest.Matchers.startsWith("/uploads/products/public/")))
            .andExpect(jsonPath("$.data.images[0].sortOrder").value(0))
            .andExpect(jsonPath("$.data.images[0].representative").value(true));
}
```

Add helper:

```java
private Long uploadImage(String accessToken, String fileName) throws Exception {
    org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file",
            fileName,
            MediaType.IMAGE_JPEG_VALUE,
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
    );

    String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/product-image-uploads")
                    .file(file)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("id").asLong();
}
```

Update `createProduct(String accessToken)` to upload one image and send the `images` array.

- [ ] **Step 2: Add create validation tests**

Add tests to `ProductApiTest`:

```java
@Test
void 상품_등록은_이미지가_필요하다() throws Exception {
    String accessToken = signupAndLogin("seller-required@example.com", "password123", "seller");

    mockMvc.perform(post("/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "MacBook Pro",
                              "description": "M3 laptop",
                              "price": 2000000,
                              "images": []
                            }
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_REQUIRED"));
}

@Test
void 다른_회원의_임시_업로드로_상품을_등록할_수_없다() throws Exception {
    String sellerToken = signupAndLogin("seller-owner@example.com", "password123", "seller");
    String otherToken = signupAndLogin("other-owner@example.com", "password123", "other");
    Long otherUploadId = uploadImage(otherToken, "other.jpg");

    mockMvc.perform(post("/api/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "MacBook Pro",
                              "description": "M3 laptop",
                              "price": 2000000,
                              "images": [
                                {
                                  "uploadId": %d,
                                  "sortOrder": 0,
                                  "representative": true
                                }
                              ]
                            }
                            """.formatted(otherUploadId)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));
}
```

- [ ] **Step 3: Run product API tests to verify failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.ProductApiTest"
```

Expected: compile or test failure because create request and service still use `imageUrls`.

- [ ] **Step 4: Add create image request**

Create `backend/src/main/java/com/sweet/market/product/api/ProductCreateImageRequest.java`:

```java
package com.sweet.market.product.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductCreateImageRequest(
        @NotNull
        Long uploadId,

        @PositiveOrZero
        int sortOrder,

        boolean representative
) {
}
```

Replace `backend/src/main/java/com/sweet/market/product/api/ProductCreateRequest.java` with:

```java
package com.sweet.market.product.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotBlank
        @Size(max = 100)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @Positive
        long price,

        @NotNull
        @Size(max = 10)
        List<@Valid ProductCreateImageRequest> images
) {
}
```

- [ ] **Step 5: Add confirmation method to upload service**

Add this method to `ProductImageUploadService`:

```java
@Transactional
public ProductImage confirm(Long memberId, Long uploadId, int sortOrder, boolean representative) {
    ProductImageUpload upload = uploadRepository.findById(uploadId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_IMAGE_UPLOAD_NOT_FOUND));
    if (!upload.isOwnedBy(memberId)) {
        throw new BusinessException(ErrorCode.PRODUCT_ACCESS_DENIED);
    }
    if (upload.isExpired(LocalDateTime.now())) {
        throw new BusinessException(ErrorCode.PRODUCT_IMAGE_UPLOAD_EXPIRED);
    }

    StoredProductImage storedImage = storageService.confirm(
            upload.getStoredFileName(),
            upload.getOriginalFileName(),
            upload.getContentType(),
            upload.getSize()
    );
    uploadRepository.delete(upload);
    return ProductImage.local(
            storedImage.url(),
            storedImage.storedFileName(),
            storedImage.originalFileName(),
            storedImage.contentType(),
            storedImage.size(),
            sortOrder,
            representative
    );
}
```

Add import:

```java
import com.sweet.market.product.domain.ProductImage;
```

- [ ] **Step 6: Update ProductService create**

Modify constructor to inject `ProductImageUploadService`.

Replace image handling in `create`:

```java
if (request.images().isEmpty()) {
    throw new BusinessException(ErrorCode.PRODUCT_IMAGE_REQUIRED);
}

Product product = Product.create(seller, request.title(), request.description(), request.price());
try {
    product.replaceImages(request.images().stream()
            .map(image -> imageUploadService.confirm(
                    sellerId,
                    image.uploadId(),
                    image.sortOrder(),
                    image.representative()
            ))
            .toList());
} catch (IllegalArgumentException exception) {
    throw toImageBusinessException(exception);
}
```

Add helper:

```java
private BusinessException toImageBusinessException(IllegalArgumentException exception) {
    return switch (exception.getMessage()) {
        case "Product image is required" -> new BusinessException(ErrorCode.PRODUCT_IMAGE_REQUIRED);
        case "Product image limit exceeded" -> new BusinessException(ErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED);
        default -> new BusinessException(ErrorCode.VALIDATION_ERROR);
    };
}
```

- [ ] **Step 7: Run migrated product API tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.ProductApiTest" --tests "com.sweet.market.product.ProductSellerApiTest"
```

Expected: product tests pass after helpers are migrated to upload-backed create requests.

- [ ] **Step 8: Commit create confirmation flow**

Run:

```powershell
git add backend/src/main/java/com/sweet/market/product/api/ProductCreateImageRequest.java `
  backend/src/main/java/com/sweet/market/product/api/ProductCreateRequest.java `
  backend/src/main/java/com/sweet/market/product/application/ProductImageUploadService.java `
  backend/src/main/java/com/sweet/market/product/application/ProductService.java `
  backend/src/test/java/com/sweet/market/product/ProductApiTest.java `
  backend/src/test/java/com/sweet/market/product/ProductSellerApiTest.java
git commit -m "feat: confirm uploads when creating products"
```

Expected: commit succeeds.

## Task 4: Implement Product Image Edit Arrangement

**Files:**
- Create: `backend/src/main/java/com/sweet/market/product/api/ProductUpdateImageRequest.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductUpdateRequest.java`
- Modify: `backend/src/main/java/com/sweet/market/product/application/ProductService.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductController.java`
- Delete: `backend/src/main/java/com/sweet/market/product/api/ProductImageAddRequest.java`
- Modify: `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`

- [ ] **Step 1: Add failing edit arrangement tests**

Add to `ProductApiTest`:

```java
@Test
void 소유자는_상품_이미지_구성을_수정할_수_있다() throws Exception {
    String accessToken = signupAndLogin("seller-edit-images@example.com", "password123", "seller");
    Long productId = createProduct(accessToken);
    Long existingImageId = getFirstImageId(productId);
    Long uploadId = uploadImage(accessToken, "new-image.jpg");

    mockMvc.perform(patch("/api/products/{productId}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "iPhone 15 Pro",
                              "description": "Natural titanium",
                              "price": 1200000,
                              "images": [
                                {
                                  "imageId": %d,
                                  "sortOrder": 1,
                                  "representative": false
                                },
                                {
                                  "uploadId": %d,
                                  "sortOrder": 0,
                                  "representative": true
                                }
                              ]
                            }
                            """.formatted(existingImageId, uploadId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.images", hasSize(2)))
            .andExpect(jsonPath("$.data.images[0].sortOrder").value(0))
            .andExpect(jsonPath("$.data.images[0].representative").value(true))
            .andExpect(jsonPath("$.data.images[1].id").value(existingImageId))
            .andExpect(jsonPath("$.data.images[1].sortOrder").value(1));
}

@Test
void 상품_수정은_최소_한_개_이미지를_유지해야_한다() throws Exception {
    String accessToken = signupAndLogin("seller-edit-required@example.com", "password123", "seller");
    Long productId = createProduct(accessToken);

    mockMvc.perform(patch("/api/products/{productId}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "iPhone 15 Pro",
                              "description": "Natural titanium",
                              "price": 1200000,
                              "images": []
                            }
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_REQUIRED"));
}
```

- [ ] **Step 2: Run edit tests to verify failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.ProductApiTest"
```

Expected: failure because update request has no `images`.

- [ ] **Step 3: Add update image request**

Create `backend/src/main/java/com/sweet/market/product/api/ProductUpdateImageRequest.java`:

```java
package com.sweet.market.product.api;

import jakarta.validation.constraints.PositiveOrZero;

public record ProductUpdateImageRequest(
        Long imageId,
        Long uploadId,

        @PositiveOrZero
        int sortOrder,

        boolean representative
) {
    public boolean referencesExistingImage() {
        return imageId != null && uploadId == null;
    }

    public boolean referencesUpload() {
        return imageId == null && uploadId != null;
    }
}
```

Replace `backend/src/main/java/com/sweet/market/product/api/ProductUpdateRequest.java`:

```java
package com.sweet.market.product.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @Positive
        long price,

        @NotNull
        @Size(max = 10)
        List<@Valid ProductUpdateImageRequest> images
) {
}
```

- [ ] **Step 4: Update ProductService update**

After `product.update(...)`, build final images:

```java
List<ProductImage> nextImages = request.images().stream()
        .map(image -> toProductImage(sellerId, product, image))
        .toList();
product.replaceImages(nextImages);
```

Add helper:

```java
private ProductImage toProductImage(Long sellerId, Product product, ProductUpdateImageRequest image) {
    if (image.referencesUpload()) {
        return imageUploadService.confirm(
                sellerId,
                image.uploadId(),
                image.sortOrder(),
                image.representative()
        );
    }
    if (!image.referencesExistingImage()) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    return product.getImages().stream()
            .filter(existingImage -> image.imageId().equals(existingImage.getId()))
            .findFirst()
            .map(existingImage -> {
                existingImage.changeArrangement(image.sortOrder(), image.representative());
                return existingImage;
            })
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_IMAGE_NOT_FOUND));
}
```

Catch `IllegalArgumentException` with `toImageBusinessException`.

- [ ] **Step 5: Remove URL image write endpoints**

Delete these methods from `ProductController`:

```java
@PostMapping("/{productId}/images")
@DeleteMapping("/{productId}/images/{imageId}")
```

Remove unused imports:

```java
import org.springframework.web.bind.annotation.DeleteMapping;
```

Keep `DeleteMapping` imported because product hide still uses it. Delete `backend/src/main/java/com/sweet/market/product/api/ProductImageAddRequest.java`.

- [ ] **Step 6: Run product API tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.ProductApiTest"
```

Expected: tests pass after URL add/delete tests are removed or rewritten for final arrangement update.

- [ ] **Step 7: Commit edit arrangement flow**

Run:

```powershell
git add backend/src/main/java/com/sweet/market/product/api/ProductUpdateImageRequest.java `
  backend/src/main/java/com/sweet/market/product/api/ProductUpdateRequest.java `
  backend/src/main/java/com/sweet/market/product/application/ProductService.java `
  backend/src/main/java/com/sweet/market/product/api/ProductController.java `
  backend/src/test/java/com/sweet/market/product/ProductApiTest.java
git add -u backend/src/main/java/com/sweet/market/product/api/ProductImageAddRequest.java
git commit -m "feat: update product images as final arrangement"
```

Expected: commit succeeds.

## Task 5: Prefer Representative Image In Queries

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/product/api/ProductSummaryResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/product/admin/AdminProductDetailResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/product/admin/AdminProductSummaryResponse.java`
- Modify: `backend/src/test/java/com/sweet/market/product/ProductSellerApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/jpalab/ProductQueryOptimizationTest.java`

- [ ] **Step 1: Add failing representative thumbnail assertion**

In `ProductSellerApiTest`, create a product with two images where the second image is representative. Assert `/api/products/me` returns the representative image as `thumbnailUrl`.

Use request content:

```json
{
  "title": "Seller Product",
  "description": "Seller Product description",
  "price": 10000,
  "images": [
    {
      "uploadId": 1,
      "sortOrder": 0,
      "representative": false
    },
    {
      "uploadId": 2,
      "sortOrder": 1,
      "representative": true
    }
  ]
}
```

Use uploaded ids from the test helper instead of hard-coded ids.

- [ ] **Step 2: Run seller product tests to verify failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.ProductSellerApiTest"
```

Expected: failure if query still uses `min(imageUrl)` or first image by incidental order.

- [ ] **Step 3: Update ProductSummaryResponse**

Keep constructor unchanged. Update `from(Product product)`:

```java
String thumbnailUrl = product.getImages().stream()
        .filter(ProductImage::isRepresentative)
        .findFirst()
        .or(() -> product.getImages().stream().findFirst())
        .map(ProductImage::getImageUrl)
        .orElse(null);
```

Add import:

```java
import com.sweet.market.product.domain.ProductImage;
```

- [ ] **Step 4: Update repository thumbnail subqueries**

In `ProductRepository`, replace thumbnail subquery expressions with representative-first fallback using `coalesce`.

Use this expression in DTO projection queries:

```jpql
coalesce(
    (
        select min(representativeImage.imageUrl)
        from ProductImage representativeImage
        where representativeImage.product = p
          and representativeImage.representative = true
    ),
    (
        select min(orderedImage.imageUrl)
        from ProductImage orderedImage
        where orderedImage.product = p
          and orderedImage.sortOrder = (
              select min(firstImage.sortOrder)
              from ProductImage firstImage
              where firstImage.product = p
          )
    )
)
```

This avoids collection fetch joins for paged queries and keeps legacy URL rows readable because old rows get `sortOrder = 0` and `representative = false` from the column defaults.

- [ ] **Step 5: Run query optimization tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.jpalab.ProductQueryOptimizationTest" --tests "com.sweet.market.product.ProductSellerApiTest"
```

Expected: tests pass and `상품_목록_최적화_조회는_images를_로딩하지_않는다` remains green.

- [ ] **Step 6: Commit representative thumbnail queries**

Run:

```powershell
git add backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java `
  backend/src/main/java/com/sweet/market/product/api/ProductSummaryResponse.java `
  backend/src/main/java/com/sweet/market/product/admin `
  backend/src/test/java/com/sweet/market/product/ProductSellerApiTest.java `
  backend/src/test/java/com/sweet/market/jpalab/ProductQueryOptimizationTest.java
git commit -m "feat: prefer representative product thumbnails"
```

Expected: commit succeeds.

## Task 6: Add Expired Temporary Upload Cleanup

**Files:**
- Create: `backend/src/main/java/com/sweet/market/product/application/ProductImageCleanupService.java`
- Create: `backend/src/main/java/com/sweet/market/product/application/ProductImageCleanupScheduler.java`
- Create: `backend/src/test/java/com/sweet/market/product/ProductImageCleanupServiceTest.java`
- Modify: `backend/src/main/resources/application.yaml`

- [ ] **Step 1: Write failing cleanup service test**

Create `backend/src/test/java/com/sweet/market/product/ProductImageCleanupServiceTest.java`:

```java
package com.sweet.market.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.application.ProductImageCleanupService;
import com.sweet.market.product.domain.ProductImageUpload;
import com.sweet.market.product.repository.ProductImageUploadRepository;
import com.sweet.market.product.storage.ProductImageStorageProperties;
import com.sweet.market.support.IntegrationTestSupport;

class ProductImageCleanupServiceTest extends IntegrationTestSupport {

    @Autowired
    private ProductImageCleanupService cleanupService;

    @Autowired
    private ProductImageUploadRepository uploadRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductImageStorageProperties properties;

    @Test
    void 만료된_임시_업로드와_파일을_정리한다() throws Exception {
        Member seller = memberRepository.save(Member.create("seller-cleanup@example.com", "encoded-password", "seller"));
        Files.createDirectories(properties.tempPath());
        Path expiredFile = properties.tempPath().resolve("expired.jpg");
        Files.write(expiredFile, new byte[]{1, 2, 3});
        uploadRepository.save(ProductImageUpload.create(
                seller,
                "expired.jpg",
                "expired.jpg",
                "image/jpeg",
                3L,
                "/uploads/products/temp/expired.jpg",
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().minusHours(1)
        ));

        int deletedCount = cleanupService.cleanExpiredUploads(LocalDateTime.now());

        assertThat(deletedCount).isEqualTo(1);
        assertThat(uploadRepository.findAll()).isEmpty();
        assertThat(Files.exists(expiredFile)).isFalse();
    }
}
```

- [ ] **Step 2: Run cleanup test to verify failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.ProductImageCleanupServiceTest"
```

Expected: compile failure because cleanup service does not exist.

- [ ] **Step 3: Implement cleanup service**

Create `backend/src/main/java/com/sweet/market/product/application/ProductImageCleanupService.java`:

```java
package com.sweet.market.product.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.product.domain.ProductImageUpload;
import com.sweet.market.product.repository.ProductImageUploadRepository;
import com.sweet.market.product.storage.ProductImageStorageService;

@Service
public class ProductImageCleanupService {

    private final ProductImageUploadRepository uploadRepository;
    private final ProductImageStorageService storageService;

    public ProductImageCleanupService(
            ProductImageUploadRepository uploadRepository,
            ProductImageStorageService storageService
    ) {
        this.uploadRepository = uploadRepository;
        this.storageService = storageService;
    }

    @Transactional
    public int cleanExpiredUploads(LocalDateTime now) {
        int deletedCount = 0;
        for (ProductImageUpload upload : uploadRepository.findByExpiresAtBefore(now)) {
            try {
                storageService.deleteTemporary(upload.getStoredFileName());
            } catch (IllegalStateException exception) {
                // Keep cleanup moving; this local learning app has no dead-letter table.
            }
            uploadRepository.delete(upload);
            deletedCount++;
        }
        return deletedCount;
    }
}
```

- [ ] **Step 4: Implement scheduler**

Create `backend/src/main/java/com/sweet/market/product/application/ProductImageCleanupScheduler.java`:

```java
package com.sweet.market.product.application;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProductImageCleanupScheduler {

    private final ProductImageCleanupService cleanupService;

    public ProductImageCleanupScheduler(ProductImageCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${product.images.cleanup-cron:0 */10 * * * *}")
    public void cleanExpiredUploads() {
        cleanupService.cleanExpiredUploads(LocalDateTime.now());
    }
}
```

Add `@EnableScheduling` to `backend/src/main/java/com/sweet/market/MarketApplication.java`:

```java
package com.sweet.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class MarketApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketApplication.class, args);
    }

}
```

- [ ] **Step 5: Add cleanup cron config**

In `backend/src/main/resources/application.yaml`, under `product.images`, add:

```yaml
cleanup-cron: ${PRODUCT_IMAGE_CLEANUP_CRON:0 */10 * * * *}
```

Use the quoted cron default to avoid YAML parsing ambiguity:

```yaml
cleanup-cron: ${PRODUCT_IMAGE_CLEANUP_CRON:'0 */10 * * * *'}
```

- [ ] **Step 6: Run cleanup tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.ProductImageCleanupServiceTest"
```

Expected: tests pass.

- [ ] **Step 7: Commit cleanup scheduler**

Run:

```powershell
git add backend/src/main/java/com/sweet/market/product/application/ProductImageCleanupService.java `
  backend/src/main/java/com/sweet/market/product/application/ProductImageCleanupScheduler.java `
  backend/src/main/java/com/sweet/market/MarketApplication.java `
  backend/src/main/resources/application.yaml `
  backend/src/test/java/com/sweet/market/product/ProductImageCleanupServiceTest.java
git commit -m "feat: clean expired product image uploads"
```

Expected: commit succeeds.

## Task 7: Update Product Web UX

**Files:**
- Modify: `web/src/features/products/productApi.ts`
- Modify: `web/src/pages/ProductFormPage.tsx`
- Modify: `web/src/pages/ProductDetailPage.tsx`
- Modify: `web/src/pages/HomePage.tsx`
- Modify: `web/src/pages/MyReportsPage.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Update product API types**

Modify `web/src/features/products/productApi.ts`.

Set `ProductImage`:

```ts
export type ProductImage = {
  id: number;
  imageUrl: string;
  sortOrder: number;
  representative: boolean;
};
```

Add upload and image input types:

```ts
export type ProductImageUpload = {
  id: number;
  previewUrl: string;
  originalFileName: string;
  contentType: string;
  size: number;
  expiresAt: string;
};

export type ProductCreateImageInput = {
  uploadId: number;
  sortOrder: number;
  representative: boolean;
};

export type ProductUpdateImageInput = {
  imageId?: number;
  uploadId?: number;
  sortOrder: number;
  representative: boolean;
};
```

Update create/update inputs:

```ts
export type ProductCreateInput = {
  title: string;
  description: string;
  price: number;
  images: ProductCreateImageInput[];
};

export type ProductUpdateInput = {
  title: string;
  description: string;
  price: number;
  images: ProductUpdateImageInput[];
};
```

Add upload function:

```ts
const PRODUCT_IMAGE_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export function uploadProductImage(file: File) {
  const formData = new FormData();
  formData.append('file', file);

  return api<ProductImageUpload>('/api/product-image-uploads', {
    method: 'POST',
    body: formData,
    skipJsonContentType: true,
  });
}
```

Modify `web/src/shared/api/http.ts` so it omits the JSON `Content-Type` header when body is `FormData`.

Replace:

```ts
if (init.body && !headers.has('Content-Type')) {
  headers.set('Content-Type', 'application/json');
}
```

with:

```ts
if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
  headers.set('Content-Type', 'application/json');
}
```

Then use this upload function without `skipJsonContentType`:

```ts
export function uploadProductImage(file: File) {
  const formData = new FormData();
  formData.append('file', file);

  return api<ProductImageUpload>('/api/product-image-uploads', {
    method: 'POST',
    body: formData,
  });
}

export function toProductImageSrc(imageUrl: string | null) {
  if (!imageUrl) {
    return null;
  }
  if (imageUrl.startsWith('http://') || imageUrl.startsWith('https://') || imageUrl.startsWith('data:')) {
    return imageUrl;
  }
  return `${PRODUCT_IMAGE_BASE_URL}${imageUrl}`;
}
```

- [ ] **Step 2: Replace ProductFormPage image URL state**

In `web/src/pages/ProductFormPage.tsx`, remove `imageUrls` from `ProductFormValues`.

Add local image state:

```ts
type ManagedImage = {
  key: string;
  imageId?: number;
  uploadId?: number;
  previewUrl: string;
  originalFileName: string;
  sortOrder: number;
  representative: boolean;
};

const [images, setImages] = useState<ManagedImage[]>([]);
const [imageError, setImageError] = useState<string | null>(null);
```

On edit load:

```ts
setImages(
  product.images
    .slice()
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map((image) => ({
      key: `existing-${image.id}`,
      imageId: image.id,
      previewUrl: image.imageUrl,
      originalFileName: `image-${image.id}`,
      sortOrder: image.sortOrder,
      representative: image.representative,
    })),
);
```

- [ ] **Step 3: Add upload handler**

Add:

```ts
const uploadMutation = useMutation({
  mutationFn: (file: File) => uploadProductImage(file),
});

async function handleFilesSelected(files: FileList | null) {
  setImageError(null);
  if (!files || files.length === 0) {
    return;
  }
  const selectedFiles = Array.from(files);
  if (images.length + selectedFiles.length > 10) {
    setImageError('이미지는 최대 10개까지 등록할 수 있습니다.');
    return;
  }
  for (const file of selectedFiles) {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      setImageError('JPEG, PNG, WebP 이미지만 업로드할 수 있습니다.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setImageError('이미지 파일은 5MB 이하로 업로드해주세요.');
      return;
    }
  }
  const uploadedImages = await Promise.all(selectedFiles.map((file) => uploadMutation.mutateAsync(file)));
  setImages((current) => {
    const next = [
      ...current,
      ...uploadedImages.map((image) => ({
        key: `upload-${image.id}`,
        uploadId: image.id,
        previewUrl: image.previewUrl,
        originalFileName: image.originalFileName,
        sortOrder: 0,
        representative: false,
      })),
    ];
    return normalizeImages(next);
  });
}
```

Add helper:

```ts
function normalizeImages(input: ManagedImage[]) {
  const representativeExists = input.some((image) => image.representative);
  return input.map((image, index) => ({
    ...image,
    sortOrder: index,
    representative: representativeExists ? image.representative : index === 0,
  }));
}
```

- [ ] **Step 4: Add representative, move, and delete handlers**

Add:

```ts
function selectRepresentative(key: string) {
  setImages((current) =>
    current.map((image) => ({
      ...image,
      representative: image.key === key,
    })),
  );
}

function moveImage(key: string, direction: -1 | 1) {
  setImages((current) => {
    const index = current.findIndex((image) => image.key === key);
    const targetIndex = index + direction;
    if (index < 0 || targetIndex < 0 || targetIndex >= current.length) {
      return current;
    }
    const next = [...current];
    const [image] = next.splice(index, 1);
    next.splice(targetIndex, 0, image);
    return normalizeImages(next);
  });
}

function removeImage(key: string) {
  setImages((current) => {
    if (current.length <= 1) {
      setImageError('상품 이미지는 최소 1개 이상 필요합니다.');
      return current;
    }
    return normalizeImages(current.filter((image) => image.key !== key));
  });
}
```

- [ ] **Step 5: Build submit payload from image state**

In `onSubmit`, before mutation:

```ts
if (images.length === 0) {
  setImageError('상품 이미지는 최소 1개 이상 필요합니다.');
  return;
}
```

Create payload helpers:

```ts
function toCreateImages(images: ManagedImage[]) {
  return images.map((image, index) => ({
    uploadId: image.uploadId ?? 0,
    sortOrder: index,
    representative: image.representative,
  }));
}

function toUpdateImages(images: ManagedImage[]) {
  return images.map((image, index) => ({
    imageId: image.imageId,
    uploadId: image.uploadId,
    sortOrder: index,
    representative: image.representative,
  }));
}
```

Use:

```ts
const savedProduct = isEditMode
  ? await updateMutation.mutateAsync({ ...payload, images: toUpdateImages(images) })
  : await createMutation.mutateAsync({ ...payload, images: toCreateImages(images) });
```

- [ ] **Step 6: Render image manager**

Replace the URL textarea section with:

```tsx
<section className="product-image-manager" aria-label="상품 이미지">
  <div className="product-image-manager-header">
    <strong>상품 이미지</strong>
    <span>{images.length}/10</span>
  </div>
  <input
    type="file"
    accept="image/jpeg,image/png,image/webp"
    multiple
    onChange={(event) => {
      void handleFilesSelected(event.target.files);
      event.target.value = '';
    }}
  />
  {imageError ? <span className="error-text">{imageError}</span> : null}
  <div className="product-image-list">
    {images.map((image, index) => (
      <div className="product-image-item" key={image.key}>
        <img src={toProductImageSrc(image.previewUrl) ?? image.previewUrl} alt="" />
        <div>
          <span>{image.originalFileName}</span>
          <button type="button" onClick={() => selectRepresentative(image.key)}>
            {image.representative ? '대표 이미지' : '대표로 선택'}
          </button>
        </div>
        <div className="product-image-actions">
          <button type="button" disabled={index === 0} onClick={() => moveImage(image.key, -1)}>
            위
          </button>
          <button type="button" disabled={index === images.length - 1} onClick={() => moveImage(image.key, 1)}>
            아래
          </button>
          <button type="button" onClick={() => removeImage(image.key)}>
            삭제
          </button>
        </div>
      </div>
    ))}
  </div>
</section>
```

- [ ] **Step 7: Update detail gallery ordering**

In `ProductDetailPage.tsx`, import `toProductImageSrc` and sort images before rendering:

```ts
const orderedImages = product.images
  .slice()
  .sort((left, right) => {
    if (left.representative !== right.representative) {
      return left.representative ? -1 : 1;
    }
    return left.sortOrder - right.sortOrder;
  });
```

Render `orderedImages` instead of `product.images`, and use:

```tsx
<img key={image.id} src={toProductImageSrc(image.imageUrl) ?? image.imageUrl} alt="" />
```

- [ ] **Step 8: Resolve image URLs in thumbnail consumers**

In `HomePage.tsx`, import `toProductImageSrc` and update `ProductThumb`:

```tsx
const imageSrc = toProductImageSrc(product.thumbnailUrl);
if (imageSrc) {
  return <img className="product-thumb" src={imageSrc} alt="" />;
}

return <div className="product-thumb product-thumb-fallback">Sweet Market</div>;
```

In `MyReportsPage.tsx`, import `toProductImageSrc` and update the ranking thumbnail render:

```tsx
const imageSrc = toProductImageSrc(item.thumbnailUrl);
return imageSrc ? (
  <img className="ranking-thumb" src={imageSrc} alt="" />
) : (
  <span className="ranking-thumb ranking-thumb-fallback">이미지 없음</span>
);
```

- [ ] **Step 9: Add focused styles**

In `web/src/shared/styles.css`, add:

```css
.product-image-manager {
  display: grid;
  gap: 12px;
}

.product-image-manager-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.product-image-list {
  display: grid;
  gap: 10px;
}

.product-image-item {
  display: grid;
  grid-template-columns: 84px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 10px;
  border: 1px solid #d7dde8;
  border-radius: 8px;
}

.product-image-item img {
  width: 84px;
  height: 84px;
  object-fit: cover;
  border-radius: 6px;
  background: #f3f6fb;
}

.product-image-actions {
  display: flex;
  gap: 6px;
}
```

Use the literal colors shown above for the first implementation. Do not introduce new color variables in this milestone.

- [ ] **Step 10: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: build succeeds.

- [ ] **Step 11: Commit web product image UX**

Run:

```powershell
git add web/src/features/products/productApi.ts `
  web/src/pages/ProductFormPage.tsx `
  web/src/pages/ProductDetailPage.tsx `
  web/src/pages/HomePage.tsx `
  web/src/pages/MyReportsPage.tsx `
  web/src/shared/styles.css `
  web/src/shared/api/http.ts
git commit -m "feat: add product image upload UX"
```

Expected: commit succeeds.

## Task 8: Final Verification And Handoff

**Files:**
- Create: `docs/superpowers/handoffs/2026-06-27-milestone-14-product-images-and-product-ux-handoff.md`

- [ ] **Step 1: Run full backend test suite**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full web build**

Run:

```powershell
cd ..\web
npm run build
```

Expected: Vite build succeeds.

- [ ] **Step 3: Run whitespace and status checks**

Run:

```powershell
cd ..
git diff --check
git status --short --branch --untracked-files=all
```

Expected: `git diff --check` has no output. Status shows only intentional Milestone 14 files if the handoff has not been committed yet.

- [ ] **Step 4: Create handoff document**

Create `docs/superpowers/handoffs/2026-06-27-milestone-14-product-images-and-product-ux-handoff.md`:

```markdown
# Milestone 14 Product Images And Product UX Handoff

## Completed

- Added local product image temporary upload API.
- Added local storage configuration and static resource handling.
- Added temporary upload confirmation during product create and update.
- Replaced URL-based image writes with upload-backed image arrangements.
- Added representative image and image ordering metadata.
- Added expired temporary upload cleanup service and scheduler.
- Updated product list/detail thumbnail behavior to prefer representative images.
- Updated product create/edit web UX for upload, preview, representative selection, ordering, and deletion.

## Verification

- Backend tests: `.\gradlew.bat test`
- Web build: `npm run build`
- Diff check: `git diff --check`

## Local Notes

- Existing URL image rows remain readable, but new product writes use local uploads only.
- Product images are stored under the configured local upload root.
- S3, CDN, image resizing, and drag-and-drop ordering remain out of scope.

## Follow-Up Candidates

- Add image resizing or thumbnail generation.
- Add S3-compatible storage abstraction.
- Add frontend regression tests if a browser test framework is introduced.
- Start Milestone 15 Wishlist after Milestone 14 is merged.
```

- [ ] **Step 5: Commit handoff**

Run:

```powershell
git add docs/superpowers/handoffs/2026-06-27-milestone-14-product-images-and-product-ux-handoff.md
git commit -m "docs: add milestone 14 handoff"
```

Expected: commit succeeds.

- [ ] **Step 6: Final branch status**

Run:

```powershell
git status --short --branch --untracked-files=all
git log --oneline --decorate -n 12
```

Expected: branch is ahead by Milestone 14 commits and has no unexpected uncommitted files.

## Self-Review

- Spec coverage: the plan covers local uploads, temporary records, scheduler cleanup, representative selection, ordering, create/edit UX, list/detail thumbnails, legacy URL read compatibility, backend tests, web build, and handoff.
- Scope control: S3, CDN, image resizing, drag-and-drop ordering, wishlist, cart, reviews, cancellation, and refund are excluded.
- Test naming: all new JUnit test method examples use Korean_with_underscores.
- Worktree safety: implementation starts in an isolated worktree and keeps the main checkout's local `application.yaml` change untouched.
- Verification: backend test, web build, `git diff --check`, and git status are explicit.
