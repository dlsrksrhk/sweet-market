import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getMyProducts, hideProduct, type ProductSummary } from '../features/products/productApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

export function MySalesPage() {
  const queryClient = useQueryClient();
  const { data, error, isLoading } = useQuery({
    queryKey: ['my-products'],
    queryFn: getMyProducts,
  });
  const hideMutation = useMutation({
    mutationFn: (productId: number) => hideProduct(productId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['my-products'] }),
      ]);
    },
  });

  if (isLoading) {
    return <p className="status-text">판매 상품을 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="판매 상품을 불러오지 못했습니다." />;
  }

  const products = data?.content ?? [];

  return (
    <section className="list-page">
      <header className="list-page-header">
        <h1>내 판매</h1>
        <p>등록한 상품 상태를 확인하고 판매 중인 상품을 숨길 수 있습니다.</p>
      </header>
      {products.length === 0 ? (
        <EmptyState title="등록한 상품이 없습니다" description="판매할 상품을 등록하면 이곳에서 관리할 수 있습니다." />
      ) : (
        <div className="record-list" aria-label="내 판매 상품 목록">
          {products.map((product) => (
            <SaleRecord
              disabled={hideMutation.isPending}
              key={product.id}
              product={product}
              onHide={() => hideMutation.mutate(product.id)}
            />
          ))}
        </div>
      )}
      {hideMutation.isError ? <p className="error-text">{toErrorMessage(hideMutation.error)}</p> : null}
    </section>
  );
}

type SaleRecordProps = {
  disabled: boolean;
  product: ProductSummary;
  onHide: () => void;
};

function SaleRecord({ disabled, product, onHide }: SaleRecordProps) {
  return (
    <article className="record-card">
      <div className="record-main">
        <StatusBadge status={product.status} />
        <h2>
          <Link to={`/products/${product.id}`}>{product.title}</Link>
        </h2>
        <strong>{currencyFormatter.format(product.price)}원</strong>
      </div>
      <div className="record-actions">
        <Link className="primary-link" to={`/products/${product.id}/edit`}>
          수정
        </Link>
        {product.status !== 'HIDDEN' ? (
          <button type="button" className="text-button danger-button" disabled={disabled} onClick={onHide}>
            숨기기
          </button>
        ) : null}
      </div>
    </article>
  );
}

function toErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;

  return apiError.message ?? '상품을 숨기지 못했습니다.';
}
