import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthProvider';
import { getMyProducts, hideProduct, type ProductSummary } from '../features/products/productApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

export function MySalesPage() {
  const { member } = useAuth();
  const memberId = member?.id;
  const queryClient = useQueryClient();
  const pendingHideProductIdsRef = useRef(new Set<number>());
  const [pendingHideProductIds, setPendingHideProductIds] = useState(() => new Set<number>());
  const { data, error, isLoading } = useQuery({
    queryKey: ['my-products', memberId],
    queryFn: getMyProducts,
    enabled: memberId !== undefined,
  });

  const hideMutation = useMutation({
    mutationFn: (product: ProductSummary) => hideProduct(product.id),
    onSuccess: (_hiddenProduct, product) => invalidateProductResources(queryClient, product.id),
  });

  if (isLoading) {
    return <p className="status-text">판매 상품을 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="판매 상품 목록을 불러오지 못했습니다." />;
  }

  const products = data?.content ?? [];

  function setProductHidePending(productId: number, pending: boolean) {
    const nextPendingHideProductIds = new Set(pendingHideProductIdsRef.current);

    if (pending) {
      nextPendingHideProductIds.add(productId);
    } else {
      nextPendingHideProductIds.delete(productId);
    }

    pendingHideProductIdsRef.current = nextPendingHideProductIds;
    setPendingHideProductIds(nextPendingHideProductIds);
  }

  function hideProductOnce(product: ProductSummary) {
    if (pendingHideProductIdsRef.current.has(product.id)) {
      return;
    }

    setProductHidePending(product.id, true);
    void hideMutation
      .mutateAsync(product)
      .catch(() => undefined)
      .finally(() => setProductHidePending(product.id, false));
  }

  return (
    <section className="list-page">
      <div className="list-page-header">
        <h1>내 판매</h1>
        <p>등록한 상품의 상태를 확인하고 판매 글을 관리합니다.</p>
      </div>
      {hideMutation.isError ? <p className="error-text">{toErrorMessage(hideMutation.error)}</p> : null}
      {products.length === 0 ? (
        <EmptyState title="등록한 상품이 없습니다" description="판매할 상품을 등록하면 이곳에서 관리할 수 있습니다." />
      ) : (
        <div className="record-list" aria-label="내 판매 상품 목록">
          {products.map((product) => {
            const isHiding = pendingHideProductIds.has(product.id);

            return (
              <article className="record-card" key={product.id}>
                <div className="record-main">
                  <StatusBadge status={product.status} />
                  <h2>{product.title}</h2>
                  <strong>{currencyFormatter.format(product.price)}원</strong>
                </div>
                <dl className="record-meta">
                  <div>
                    <dt>판매자</dt>
                    <dd>{product.sellerNickname}</dd>
                  </div>
                  <div>
                    <dt>상품 번호</dt>
                    <dd>{product.id}</dd>
                  </div>
                </dl>
                <div className="record-actions">
                  <Link className="primary-link" to={`/products/${product.id}/edit`}>
                    수정
                  </Link>
                  {product.status === 'HIDDEN' ? (
                    <span className="muted-text">숨겨진 상품입니다.</span>
                  ) : (
                    <button
                      type="button"
                      className="text-button danger-button"
                      disabled={isHiding}
                      onClick={() => hideProductOnce(product)}
                    >
                      {isHiding ? '숨기는 중' : '숨기기'}
                    </button>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

async function invalidateProductResources(queryClient: QueryClient, productId: number) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['products'] }),
    queryClient.invalidateQueries({ queryKey: ['products', productId] }),
    queryClient.invalidateQueries({ queryKey: ['my-products'] }),
  ]);
}

function toErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? '상품을 숨기지 못했습니다.';
}
