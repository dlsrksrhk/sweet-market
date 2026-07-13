import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { CatalogPanel } from '../features/catalog/CatalogPanel';
import { getPublicStore, storeQueryKeys, type PublicStore } from '../features/stores/storeApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';
import { parsePositiveIntegerParam } from '../shared/utils/parseId';

const numberFormatter = new Intl.NumberFormat('ko-KR');

export function StoreProfilePage() {
  const { storeId } = useParams();
  const parsedStoreId = parsePositiveIntegerParam(storeId);
  const hasValidStoreId = parsedStoreId !== null;
  const headerQuery = useQuery({
    queryKey: storeQueryKeys.public(parsedStoreId ?? 0),
    queryFn: () => getPublicStore(parsedStoreId ?? 0),
    enabled: hasValidStoreId,
  });

  if (!hasValidStoreId) {
    return <ErrorState message="상점 주소가 올바르지 않습니다." />;
  }

  if (headerQuery.isLoading) {
    return <p className="status-text">상점 정보를 불러오고 있습니다.</p>;
  }

  if (headerQuery.error) {
    return <ErrorState message={toErrorMessage(headerQuery.error, '상점 정보를 불러오지 못했습니다.')} />;
  }

  if (!headerQuery.data) {
    return <ErrorState message="상점 정보를 불러오지 못했습니다." />;
  }

  const store = headerQuery.data;

  return (
    <main className="storefront-page">
      <StorefrontHeader store={store} />
      {store.operatingStatus === 'SUSPENDED' ? (
        <EmptyState title="현재 운영이 중지된 상점입니다" />
      ) : (
        <section className="storefront-catalog" aria-labelledby="storefront-catalog-title">
          <div className="storefront-catalog-heading">
            <div>
              <p className="eyebrow">CATALOG</p>
              <h2 id="storefront-catalog-title">상점 상품</h2>
            </div>
            <span>{numberFormatter.format(store.publicProductCount)}개 공개 상품</span>
          </div>
          <CatalogPanel storeId={parsedStoreId} />
        </section>
      )}
    </main>
  );
}

type StorefrontHeaderProps = {
  store: PublicStore;
};

function StorefrontHeader({ store }: StorefrontHeaderProps) {
  return (
    <section className="storefront-header">
      <div className="storefront-identity">
        <p className="eyebrow">STORE</p>
        <div className="storefront-badges">
          <StatusBadge status={store.type} />
          <StatusBadge status={store.operatingStatus} />
        </div>
        <h1>{store.publicName}</h1>
        <p>{store.introduction}</p>
      </div>
      {store.operatingStatus === 'ACTIVE' && (
        <dl className="storefront-stats">
          <div>
            <dt>평점</dt>
            <dd>{store.averageRating === null ? '아직 리뷰 없음' : store.averageRating.toFixed(1)}</dd>
          </div>
          <div>
            <dt>리뷰</dt>
            <dd>{numberFormatter.format(store.reviewCount)}개</dd>
          </div>
          <div>
            <dt>상품</dt>
            <dd>{numberFormatter.format(store.publicProductCount)}개</dd>
          </div>
        </dl>
      )}
    </section>
  );
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
