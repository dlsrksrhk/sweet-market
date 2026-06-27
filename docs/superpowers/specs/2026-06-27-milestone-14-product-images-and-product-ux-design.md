# Milestone 14 Product Images And Product UX Design

## Goal

Milestone 14 turns Sweet Market product images from URL text fields into a local file upload experience.

The feature should let sellers create and edit products with uploaded images, previews, representative image selection, and explicit ordering. Buyers should see realistic thumbnails and galleries. The milestone should deepen JPA learning around aggregate child lifecycle, `orphanRemoval`, ordering columns, representative-image constraints, temporary upload cleanup, scheduler behavior, and the boundary between database transactions and filesystem writes.

## Context

The product domain already has:

```text
Product
ProductImage
POST /api/products
PATCH /api/products/{productId}
POST /api/products/{productId}/images
DELETE /api/products/{productId}/images/{imageId}
```

The current image model stores `imageUrl` strings. The web product form accepts image URLs only on create, and the edit page does not provide a full image management experience. Product summaries and admin/reporting projections currently derive thumbnails from existing image URLs.

Milestone 14 should keep existing URL image rows readable so current demo data does not break, but new product create/edit UX should use local file upload only.

## Decisions

- New product create/edit flows use local file upload only.
- Existing URL image data remains readable.
- New products require at least one image.
- Each product can have at most 10 images.
- Each uploaded file can be at most 5MB.
- Allowed content types are JPEG, PNG, and WebP.
- Uploads are temporary until the seller saves a product create or edit form.
- Temporary uploads expire and are cleaned by a scheduler.
- The seller explicitly selects the representative image.
- The first uploaded image becomes representative by default in the web UI, but the seller can change it.
- Image ordering uses up/down controls, not drag and drop.
- Product summaries use representative image first.
- S3, CDN, image transformation, and thumbnail generation are out of scope.

## Non-Goals

- External object storage.
- CDN or production static asset hosting.
- Image resizing, compression, or generated thumbnails.
- Drag and drop image ordering.
- Multiple file upload batches with background retry queues.
- Full design system rewrite.
- Wishlist, cart, review, cancellation, or refund features.
- Migrating existing URL rows into local files.

## Backend Domain Design

Extend `ProductImage` instead of replacing it.

Recommended fields:

```text
id
product
imageUrl
sortOrder
representative
storedFileName nullable
originalFileName nullable
contentType nullable
size nullable
```

`imageUrl` remains the response-facing URL so existing consumers keep a simple shape. Existing URL-based rows can have null file metadata. New local-upload rows should store file metadata and a server-generated URL such as `/uploads/products/{storedFileName}`.

`Product` should own image invariants:

- At least one image is required when creating a new product.
- At most 10 images are allowed.
- Exactly one image is representative.
- Sort orders are unique and stable within a product.
- Reserved products still cannot be changed.
- Removing or replacing images should use the existing child collection and `orphanRemoval` behavior.

For edit requests, prefer applying the final image arrangement as one command:

```text
existing image ids to keep
new upload ids to confirm
sort order for every final image
representative marker for exactly one final image
```

This keeps the client and server aligned around the final desired state instead of many tiny image mutation calls.

## Temporary Upload Design

Add a temporary upload entity, for example `ProductImageUpload`.

Recommended fields:

```text
id
uploader member
storedFileName
originalFileName
contentType
size
previewUrl
expiresAt
createdAt
```

Temporary uploads are seller-owned. A seller cannot attach another member's upload to a product.

Temporary uploads are confirmed when a product create or edit request references them. Confirmation should:

1. Validate the upload exists, belongs to the seller, and is not expired.
2. Move or rename the file from the temporary directory to the product image directory.
3. Create a `ProductImage` row from the upload metadata.
4. Remove the temporary upload row.

If the database operation fails after a file move, the service should attempt best-effort cleanup of newly moved files. If file deletion fails, log it and leave the database in the correct state rather than rolling back a successful domain write only because local cleanup failed.

## Backend API Design

Add a temporary upload endpoint:

```text
POST /api/product-image-uploads
Content-Type: multipart/form-data
```

Request:

```text
file
```

Response:

```text
id
previewUrl
originalFileName
contentType
size
expiresAt
```

Update product create request:

```text
title
description
price
images: [
  {
    uploadId,
    sortOrder,
    representative
  }
]
```

Update product edit request:

```text
title
description
price
images: [
  {
    imageId nullable,
    uploadId nullable,
    sortOrder,
    representative
  }
]
```

Each edit image item must reference exactly one of `imageId` or `uploadId`.

Remove URL-based image writes from the product create/edit API surface in Milestone 14. The web app must not send `imageUrls`, and the existing URL add-image endpoint should be removed or replaced by the upload-confirmation flow. URL compatibility is storage/read compatibility for existing rows, not a continuing write feature.

## Local Storage And Static Resource Design

Add configuration for local product image storage.

Recommended settings:

```yaml
product:
  images:
    upload-root: ${PRODUCT_IMAGE_UPLOAD_ROOT:./.local/product-images}
    temp-dir: temp
    public-dir: public
    temp-expiration-minutes: ${PRODUCT_IMAGE_TEMP_EXPIRATION_MINUTES:60}
    cleanup-cron: ${PRODUCT_IMAGE_CLEANUP_CRON:'0 */10 * * * *'}
    max-file-size: 5MB
```

Static resource handling should expose confirmed and temporary preview images under a local URL path such as:

```text
/uploads/products/**
```

Use UUID-based stored file names and preserve file extensions only after validating the content type. Do not trust the original file name for storage paths.

## Validation And Errors

Validation cases:

- Missing file.
- Empty file.
- File size exceeds 5MB.
- Unsupported content type.
- Product create request contains zero images.
- Product request contains more than 10 images.
- Product request contains zero or multiple representative images.
- Duplicate or missing sort order.
- Upload id does not exist.
- Upload id belongs to another member.
- Upload id is expired.
- Existing image id does not belong to the product being edited.
- Reserved product is changed.

Recommended error additions:

```text
PRODUCT_IMAGE_REQUIRED
PRODUCT_IMAGE_LIMIT_EXCEEDED
PRODUCT_IMAGE_INVALID_FILE
PRODUCT_IMAGE_UPLOAD_NOT_FOUND
PRODUCT_IMAGE_UPLOAD_EXPIRED
```

Reuse existing error codes where they already communicate the condition clearly, such as `PRODUCT_IMAGE_NOT_FOUND`, `PRODUCT_ACCESS_DENIED`, and `PRODUCT_CHANGE_NOT_ALLOWED`.

## Scheduler And Cleanup Design

Add a cleanup service and scheduler for expired temporary uploads.

The cleanup service should:

- Query temporary uploads where `expiresAt` is before the current time.
- Delete the temporary file if it exists.
- Delete the temporary upload row.
- Continue processing other rows if one file deletion fails.
- Be directly testable without waiting for a real scheduler tick.

The scheduler should be thin and delegate to the cleanup service. Keep the first version local and simple; distributed locking is out of scope.

## Repository Query Design

Product list and report/admin projections should prefer representative images.

For summary thumbnails:

1. Use a representative image for the product if one exists.
2. Fall back to the lowest `sortOrder`.
3. Fall back to the existing URL-compatible rule for old rows if needed.
4. Return `null` when no image exists for old or exceptional data.

Avoid collection fetch joins for paged product lists. Use DTO projection or a subquery for thumbnails, matching the project's existing query optimization direction.

Product detail can load seller and images because it needs the full gallery.

## Web Design

Update `ProductFormPage`.

Create mode:

- Remove the image URL textarea.
- Add file selection.
- Upload selected files immediately through the temporary upload API.
- Show previews after upload succeeds.
- Require at least one image before submit.
- Prevent more than 10 images.
- Select the first uploaded image as representative by default.
- Allow representative selection.
- Allow up/down ordering.
- Allow deletion before submit.
- Submit title, description, price, and final image arrangement.

Edit mode:

- Show existing images and newly uploaded images in one list.
- Allow adding images up to the 10-image limit.
- Allow deleting images while keeping at least one final image.
- Allow representative change.
- Allow up/down ordering across existing and new images.
- Submit the final image arrangement in one request.

Product list and detail:

- Product cards show representative thumbnails.
- Product detail shows the representative image first, followed by other images in sort order.
- Existing fallback UI remains for products without images.

Keep the visual changes focused. The milestone should improve the product workflow without turning into a full frontend redesign.

## Frontend API Design

Extend:

```text
web/src/features/products/productApi.ts
```

Recommended additions:

```text
uploadProductImage(file)
ProductImageUpload
ProductImageInput
ProductCreateInput.images
ProductUpdateInput.images
```

The upload function should send `FormData` and not JSON.

The UI should show backend validation messages when available. Frontend file type, size, and count checks are useful for fast feedback, but backend validation remains authoritative.

## Testing Plan

Backend tests should cover:

- A seller can upload a valid JPEG, PNG, or WebP temporary image.
- Upload fails for unsupported content type.
- Upload fails for files over 5MB.
- Product create fails without images.
- Product create fails with more than 10 images.
- Product create confirms temporary uploads into product images.
- Product create rejects another member's upload.
- Product create rejects expired uploads.
- Product create stores exactly one representative image.
- Product update keeps existing images, adds new uploads, removes omitted images, changes representative image, and changes sort order.
- Product update rejects image ids that do not belong to the product.
- Reserved products cannot have images changed.
- Product list thumbnail prefers representative image.
- Product detail returns images in sort order with representative metadata.
- Cleanup service removes expired temporary upload rows and files.
- Cleanup service does not remove unexpired temporary uploads.
- `orphanRemoval` removes omitted product image rows.

New JUnit `@Test` method names must use Korean_with_underscores.

Frontend verification should cover:

- `npm run build` passes.
- Product create form cannot submit without images.
- Upload failure displays an error.
- Representative selection affects the submit payload.
- Up/down controls affect the submit payload.
- Delete controls keep the final image count valid.
- Edit mode can mix existing images and new uploads.

Full verification commands:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

```powershell
cd web
npm run build
```

```powershell
git diff --check
git status --short --branch --untracked-files=all
```

Do not stage or overwrite `backend/src/main/resources/application.yaml`; it has an existing local-only development change.

## Acceptance Criteria

- Seller can upload local product images from the product form.
- Seller can create a product only when at least one image is present.
- Seller can manage up to 10 product images.
- Seller can choose exactly one representative image.
- Seller can order images with up/down controls.
- Seller can edit existing product images and add new uploaded images.
- Expired temporary uploads are cleaned by a scheduler-backed service.
- Existing URL image rows still render in list/detail pages.
- Product list thumbnails prefer representative images.
- Product detail galleries render in the configured order.
- Backend tests pass with JDK 21 and `JWT_SECRET`.
- Web build passes.

## Self-Review

- The scope is one product-focused milestone.
- The design keeps URL image data readable without keeping URL input in the new UX.
- Temporary upload cleanup is included because the approved flow uses immediate uploads before product save.
- Representative image and ordering rules are explicit.
- The storage design is local only and avoids S3/CDN scope creep.
- The web work supports backend learning by exposing upload, confirmation, ordering, and deletion behavior.
- Wishlist, cart, review, cancellation, and refund work remain in later milestones.
