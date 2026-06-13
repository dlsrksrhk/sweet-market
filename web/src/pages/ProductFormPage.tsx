import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import {
  createProduct,
  getProduct,
  updateProduct,
  type ProductCreateInput,
  type ProductUpdateInput,
} from '../features/products/productApi';
import { type ApiError } from '../shared/api/http';
import { ErrorState } from '../shared/ui/ResourceStates';

type ProductFormValues = {
  title: string;
  description: string;
  price: number;
  imageUrls: string;
};

export function ProductFormPage() {
  const navigate = useNavigate();
  const { productId } = useParams();
  const queryClient = useQueryClient();
  const parsedProductId = Number(productId);
  const isEditMode = productId !== undefined;
  const hasValidProductId = Number.isInteger(parsedProductId) && parsedProductId > 0;
  const [apiError, setApiError] = useState<string | null>(null);

  const { data: product, error, isLoading } = useQuery({
    queryKey: ['products', parsedProductId],
    queryFn: () => getProduct(parsedProductId),
    enabled: isEditMode && hasValidProductId,
  });

  const defaultValues = useMemo<ProductFormValues>(
    () => ({
      title: '',
      description: '',
      price: 0,
      imageUrls: '',
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

    reset({
      title: product.title,
      description: product.description,
      price: product.price,
      imageUrls: product.images.map((image) => image.imageUrl).join('\n'),
    });
  }, [product, reset]);

  const createMutation = useMutation({
    mutationFn: (input: ProductCreateInput) => createProduct(input),
  });
  const updateMutation = useMutation({
    mutationFn: (input: ProductUpdateInput) => updateProduct(parsedProductId, input),
  });

  if (isEditMode && !hasValidProductId) {
    return <ErrorState message="상품 주소가 올바르지 않습니다." />;
  }

  if (isEditMode && isLoading) {
    return <p className="status-text">상품 정보를 불러오고 있습니다.</p>;
  }

  if (isEditMode && error) {
    return <ErrorState message="상품 정보를 불러오지 못했습니다." />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setApiError(null);

    try {
      const payload = toPayload(values);
      const savedProduct = isEditMode
        ? await updateMutation.mutateAsync(payload)
        : await createMutation.mutateAsync({ ...payload, imageUrls: parseImageUrls(values.imageUrls) });

      await queryClient.invalidateQueries({ queryKey: ['products'] });
      navigate(`/products/${savedProduct.id}`);
    } catch (caughtError) {
      setApiError(toErrorMessage(caughtError));
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
        {!isEditMode ? (
          <label>
            이미지 URL
            <textarea
              rows={5}
              placeholder="줄바꿈 또는 쉼표로 여러 URL을 입력하세요."
              {...register('imageUrls', {
                validate: (value) => parseImageUrls(value).length <= 10 || '이미지는 최대 10개까지 등록할 수 있습니다.',
              })}
            />
            {errors.imageUrls ? <span className="error-text">{errors.imageUrls.message}</span> : null}
          </label>
        ) : null}
        {apiError ? <p className="error-text">{apiError}</p> : null}
        <button type="submit" disabled={isSubmitting || createMutation.isPending || updateMutation.isPending}>
          {isEditMode ? '수정하기' : '등록하기'}
        </button>
      </form>
    </section>
  );
}

function toPayload(values: ProductFormValues): ProductUpdateInput {
  return {
    title: values.title.trim(),
    description: values.description.trim(),
    price: values.price,
  };
}

function parseImageUrls(value: string) {
  return value
    .split(/[\n,]/)
    .map((url) => url.trim())
    .filter(Boolean);
}

function toErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? '상품 저장에 실패했습니다.';
}
