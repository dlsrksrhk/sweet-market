import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type ChangeEvent, useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthProvider';
import {
  createProduct,
  getProduct,
  toProductImageSrc,
  updateProduct,
  uploadProductImage,
  type ProductCreateImageInput,
  type ProductCreateInput,
  type ProductImage,
  type ProductUpdateImageInput,
  type ProductUpdateInput,
} from '../features/products/productApi';
import { type ApiError } from '../shared/api/http';
import { ErrorState } from '../shared/ui/ResourceStates';
import { parsePositiveIntegerParam } from '../shared/utils/parseId';

const MAX_PRODUCT_IMAGES = 10;
const MAX_PRODUCT_IMAGE_SIZE = 5 * 1024 * 1024;
const ACCEPTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

type ProductFormValues = {
  title: string;
  description: string;
  price: number;
};

type ManagedImage = {
  key: string;
  imageId?: number;
  uploadId?: number;
  previewUrl: string;
  originalFileName: string;
  sortOrder: number;
  representative: boolean;
};

export function ProductFormPage() {
  const navigate = useNavigate();
  const { productId } = useParams();
  const { member } = useAuth();
  const queryClient = useQueryClient();
  const parsedProductId = parsePositiveIntegerParam(productId);
  const isEditMode = productId !== undefined;
  const hasValidProductId = parsedProductId !== null;
  const [apiError, setApiError] = useState<string | null>(null);
  const [imageError, setImageError] = useState<string | null>(null);
  const [images, setImages] = useState<ManagedImage[]>([]);
  const [loadedProductId, setLoadedProductId] = useState<number | null>(null);
  const [isImageDirty, setIsImageDirty] = useState(false);

  const { data: product, error, isLoading } = useQuery({
    queryKey: ['products', parsedProductId],
    queryFn: () => getProduct(parsedProductId ?? 0),
    enabled: isEditMode && hasValidProductId,
  });

  const defaultValues = useMemo<ProductFormValues>(
    () => ({
      title: '',
      description: '',
      price: 0,
    }),
    [],
  );

  const {
    formState: { errors, isSubmitting },
    handleSubmit,
    register,
    reset,
  } = useForm<ProductFormValues>({ defaultValues });

  useEffect(() => {
    if (!product) {
      return;
    }

    const isDifferentProduct = loadedProductId !== product.id;
    const productImages = product.images.slice().sort(compareProductImages).map(toManagedImage);

    if (isDifferentProduct) {
      reset({
        title: product.title,
        description: product.description,
        price: product.price,
      });
      setImages(productImages);
      setLoadedProductId(product.id);
      setIsImageDirty(false);
      return;
    }

    if (!isImageDirty) {
      setImages(productImages);
    }
  }, [isImageDirty, loadedProductId, product, reset]);

  const createMutation = useMutation({
    mutationFn: (input: ProductCreateInput) => createProduct(input),
  });
  const updateMutation = useMutation({
    mutationFn: (input: ProductUpdateInput) => updateProduct(parsedProductId ?? 0, input),
  });
  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadProductImage(file),
  });

  const handleFilesSelected = async (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = Array.from(event.target.files ?? []);
    event.target.value = '';

    if (selectedFiles.length === 0) {
      return;
    }

    setApiError(null);
    setImageError(null);

    if (images.length + selectedFiles.length > MAX_PRODUCT_IMAGES) {
      setImageError('이미지는 최대 10개까지 등록할 수 있습니다.');
      return;
    }

    const invalidTypeFile = selectedFiles.find((file) => !ACCEPTED_IMAGE_TYPES.has(file.type));

    if (invalidTypeFile) {
      setImageError('JPEG, PNG, WebP 이미지만 등록할 수 있습니다.');
      return;
    }

    const tooLargeFile = selectedFiles.find((file) => file.size > MAX_PRODUCT_IMAGE_SIZE);

    if (tooLargeFile) {
      setImageError('이미지는 5MB 이하로 등록해주세요.');
      return;
    }

    let uploadedCount = 0;
    let uploadFailureMessage: string | null = null;

    for (const file of selectedFiles) {
      try {
        const uploadedImage = await uploadMutation.mutateAsync(file);
        const nextImage = {
          key: `upload-${uploadedImage.id}`,
          uploadId: uploadedImage.id,
          previewUrl: uploadedImage.previewUrl,
          originalFileName: uploadedImage.originalFileName,
          sortOrder: 0,
          representative: false,
        } satisfies ManagedImage;

        uploadedCount += 1;
        setImages((currentImages) => normalizeManagedImages([...currentImages, nextImage]));
        setIsImageDirty(true);
      } catch (caughtError) {
        const fileName = file.name || '선택한 이미지';
        uploadFailureMessage = `${fileName}: ${toErrorMessage(caughtError, '이미지 업로드에 실패했습니다.')}`;
        break;
      }
    }

    if (uploadFailureMessage) {
      setImageError(
        uploadedCount > 0
          ? `${uploadFailureMessage} ${uploadedCount}개 이미지는 업로드됐습니다.`
          : uploadFailureMessage,
      );
    }
  };

  const selectRepresentative = (key: string) => {
    setIsImageDirty(true);
    setImages((currentImages) =>
      currentImages.map((image, index) => ({
        ...image,
        sortOrder: index,
        representative: image.key === key,
      })),
    );
  };

  const moveImage = (key: string, direction: -1 | 1) => {
    setIsImageDirty(true);
    setImages((currentImages) => {
      const currentIndex = currentImages.findIndex((image) => image.key === key);
      const nextIndex = currentIndex + direction;

      if (currentIndex < 0 || nextIndex < 0 || nextIndex >= currentImages.length) {
        return currentImages;
      }

      const nextImages = currentImages.slice();
      const targetImage = nextImages[currentIndex];
      nextImages[currentIndex] = nextImages[nextIndex];
      nextImages[nextIndex] = targetImage;

      return normalizeManagedImages(nextImages);
    });
  };

  const removeImage = (key: string) => {
    setIsImageDirty(true);
    setImages((currentImages) => normalizeManagedImages(currentImages.filter((image) => image.key !== key)));
  };

  if (isEditMode && !hasValidProductId) {
    return <ErrorState message="상품 주소가 올바르지 않습니다." />;
  }

  if (isEditMode && isLoading) {
    return <p className="status-text">상품 정보를 불러오고 있습니다.</p>;
  }

  if (isEditMode && error) {
    return <ErrorState message="상품 정보를 불러오지 못했습니다." />;
  }

  if (isEditMode && product && member?.id !== product.sellerId) {
    return <ErrorState title="접근할 수 없습니다" message="본인이 등록한 상품만 수정할 수 있습니다." />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setApiError(null);
    setImageError(null);

    try {
      if (images.length === 0) {
        setImageError('이미지를 1개 이상 등록해주세요.');
        return;
      }

      const normalizedImages = normalizeManagedImages(images);
      setImages(normalizedImages);
      const payload = toPayload(values);
      const savedProduct = isEditMode
        ? await updateMutation.mutateAsync({ ...payload, images: toUpdateImages(normalizedImages) })
        : await createMutation.mutateAsync({ ...payload, images: toCreateImages(normalizedImages) });

      await queryClient.invalidateQueries({ queryKey: ['products'] });
      navigate(`/products/${savedProduct.id}`);
    } catch (caughtError) {
      setApiError(toErrorMessage(caughtError, '상품 저장에 실패했습니다.'));
    }
  });

  return (
    <section className="product-form-page">
      <h1>{isEditMode ? '상품 수정' : '상품 등록'}</h1>
      <form className="auth-form product-form" onSubmit={onSubmit}>
        <label>
          상품명
          <input
            type="text"
            {...register('title', {
              required: '상품명을 입력해주세요.',
              maxLength: { value: 100, message: '상품명은 100자 이하로 입력해주세요.' },
            })}
          />
          {errors.title ? <span className="error-text">{errors.title.message}</span> : null}
        </label>
        <label>
          설명
          <textarea
            rows={8}
            {...register('description', {
              required: '설명을 입력해주세요.',
              maxLength: { value: 2000, message: '설명은 2000자 이하로 입력해주세요.' },
            })}
          />
          {errors.description ? <span className="error-text">{errors.description.message}</span> : null}
        </label>
        <label>
          가격
          <input
            type="number"
            min="1"
            step="1"
            {...register('price', {
              required: '가격을 입력해주세요.',
              min: { value: 1, message: '가격은 1원 이상이어야 합니다.' },
              valueAsNumber: true,
            })}
          />
          {errors.price ? <span className="error-text">{errors.price.message}</span> : null}
        </label>
        <div className="product-image-manager">
          <div className="product-image-manager-header">
            <strong>상품 이미지</strong>
            <span className="status-text">
              {images.length}/{MAX_PRODUCT_IMAGES}
            </span>
          </div>
          <label>
            이미지 업로드
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              disabled={uploadMutation.isPending || images.length >= MAX_PRODUCT_IMAGES}
              onChange={handleFilesSelected}
            />
          </label>
          {imageError ? <p className="error-text">{imageError}</p> : null}
          {uploadMutation.isPending ? <p className="status-text">이미지를 업로드하고 있습니다.</p> : null}
          {images.length > 0 ? (
            <div className="product-image-list">
              {images.map((image, index) => {
                const imageLabel = `${index + 1}번째 ${image.originalFileName || '이미지'}`;

                return (
                  <div className="product-image-item" key={image.key}>
                    <img src={toProductImageSrc(image.previewUrl) ?? image.previewUrl} alt="" />
                    <div>
                      <strong>{image.originalFileName}</strong>
                      <span className="status-text">{image.representative ? '대표 이미지' : `${index + 1}번째 이미지`}</span>
                    </div>
                    <div className="product-image-actions">
                      <button
                        type="button"
                        className="text-button"
                        aria-label={`${imageLabel} 대표 이미지로 선택`}
                        disabled={image.representative}
                        onClick={() => selectRepresentative(image.key)}
                      >
                        대표
                      </button>
                      <button
                        type="button"
                        className="text-button"
                        aria-label={`${imageLabel} 위로 이동`}
                        disabled={index === 0}
                        onClick={() => moveImage(image.key, -1)}
                      >
                        위로
                      </button>
                      <button
                        type="button"
                        className="text-button"
                        aria-label={`${imageLabel} 아래로 이동`}
                        disabled={index === images.length - 1}
                        onClick={() => moveImage(image.key, 1)}
                      >
                        아래로
                      </button>
                      <button
                        type="button"
                        className="text-button danger-button"
                        aria-label={`${imageLabel} 삭제`}
                        onClick={() => removeImage(image.key)}
                      >
                        삭제
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <p className="status-text">선택된 이미지가 없습니다.</p>
          )}
        </div>
        {apiError ? <p className="error-text">{apiError}</p> : null}
        <button type="submit" disabled={isSubmitting || createMutation.isPending || updateMutation.isPending || uploadMutation.isPending}>
          {isEditMode ? '수정하기' : '등록하기'}
        </button>
      </form>
    </section>
  );
}

function toPayload(values: ProductFormValues) {
  return {
    title: values.title.trim(),
    description: values.description.trim(),
    price: values.price,
  };
}

function compareProductImages(firstImage: ProductImage, secondImage: ProductImage) {
  return firstImage.sortOrder - secondImage.sortOrder;
}

function toManagedImage(image: ProductImage, index: number): ManagedImage {
  return {
    key: `image-${image.id}`,
    imageId: image.id,
    previewUrl: image.imageUrl,
    originalFileName: toOriginalFileName(image.imageUrl, index),
    sortOrder: image.sortOrder,
    representative: image.representative,
  };
}

function toOriginalFileName(imageUrl: string, index: number) {
  return imageUrl.split('/').pop() || `상품 이미지 ${index + 1}`;
}

function normalizeManagedImages(images: ManagedImage[]) {
  const representativeIndex = images.findIndex((image) => image.representative);
  const nextRepresentativeIndex = representativeIndex >= 0 ? representativeIndex : images.length > 0 ? 0 : -1;

  return images.map((image, index) => ({
    ...image,
    sortOrder: index,
    representative: index === nextRepresentativeIndex,
  }));
}

function toCreateImages(images: ManagedImage[]): ProductCreateImageInput[] {
  return images.map((image) => {
    if (image.uploadId === undefined) {
      throw new Error('상품 이미지 업로드 정보가 없습니다.');
    }

    return {
      uploadId: image.uploadId,
      sortOrder: image.sortOrder,
      representative: image.representative,
    };
  });
}

function toUpdateImages(images: ManagedImage[]): ProductUpdateImageInput[] {
  return images.map((image) => {
    if (image.imageId !== undefined && image.uploadId !== undefined) {
      throw new Error('상품 이미지는 기존 이미지 또는 업로드 이미지 중 하나만 사용할 수 있습니다.');
    }

    if (image.imageId !== undefined) {
      return {
        imageId: image.imageId,
        sortOrder: image.sortOrder,
        representative: image.representative,
      };
    }

    if (image.uploadId !== undefined) {
      return {
        uploadId: image.uploadId,
        sortOrder: image.sortOrder,
        representative: image.representative,
      };
    }

    throw new Error('상품 이미지 정보가 없습니다.');
  });
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
