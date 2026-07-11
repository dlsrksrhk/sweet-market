import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useParams, useSearchParams } from 'react-router-dom';
import { ProductCard } from '../features/products/ProductCard';
import { useAuth } from '../features/auth/AuthProvider';
import {
  getPublicStore,
  getStorefrontProducts,
  storeQueryKeys,
  type PublicStore,
  type StorefrontProductSort,
  type StorefrontProductStatus,
} from '../features/stores/storeApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';
import { parsePositiveIntegerParam } from '../shared/utils/parseId';

const PAGE_SIZE = 12;
const JAVA_INT_MAX = 2_147_483_647;
const productStatuses: { value: StorefrontProductStatus; label: string }[] = [
  { value: 'ON_SALE', label: '판매중' },
  { value: 'RESERVED', label: '예약중' },
  { value: 'SOLD_OUT', label: '판매완료' },
];
const productSorts: { value: StorefrontProductSort; label: string }[] = [
  { value: 'NEWEST', label: '최신순' },
  { value: 'PRICE_ASC', label: '낮은 가격순' },
  { value: 'PRICE_DESC', label: '높은 가격순' },
];
const numberFormatter = new Intl.NumberFormat('ko-KR');

export function StoreProfilePage() {
  const { member, loading: authLoading } = useAuth();
  const { storeId } = useParams();
  const parsedStoreId = parsePositiveIntegerParam(storeId);
  const hasValidStoreId = parsedStoreId !== null;
  const [searchParams, setSearchParams] = useSearchParams();
  const status = normalizeStatus(searchParams.get('status'));
  const sort = normalizeSort(searchParams.get('sort'));
  const page = normalizePage(searchParams.get('page'));

  useEffect(() => {
    if (
      searchParams.get('status') === status &&
      searchParams.get('sort') === sort &&
      searchParams.get('page') === String(page)
    ) {
      return;
    }

    const normalizedParams = new URLSearchParams(searchParams);
    normalizedParams.set('status', status);
    normalizedParams.set('sort', sort);
    normalizedParams.set('page', String(page));
    setSearchParams(normalizedParams, { replace: true });
  }, [page, searchParams, setSearchParams, sort, status]);

  const headerQuery = useQuery({
    queryKey: storeQueryKeys.public(parsedStoreId ?? 0),
    queryFn: () => getPublicStore(parsedStoreId ?? 0),
    enabled: hasValidStoreId,
  });
  const hasSettledActiveStore =
    headerQuery.isSuccess && !headerQuery.isFetching && headerQuery.data.operatingStatus === 'ACTIVE';
  const catalogInput = { status, sort, page, size: PAGE_SIZE };
  const viewerKey = member?.id ?? 'anonymous';
  const catalogQuery = useQuery({
    queryKey: storeQueryKeys.publicProductList(parsedStoreId ?? 0, viewerKey, catalogInput),
    queryFn: () => getStorefrontProducts(parsedStoreId ?? 0, catalogInput),
    enabled: hasValidStoreId && hasSettledActiveStore && !authLoading,
  });

  useEffect(() => {
    const totalPages = catalogQuery.data?.totalPages;
    if (!hasSettledActiveStore || catalogQuery.isFetching || totalPages === undefined) {
      return;
    }

    const normalizedPage = totalPages === 0 ? 0 : Math.min(page, totalPages - 1);
    if (page === normalizedPage) {
      return;
    }

    const normalizedParams = new URLSearchParams(searchParams);
    normalizedParams.set('page', String(normalizedPage));
    setSearchParams(normalizedParams, { replace: true });
  }, [catalogQuery.data?.totalPages, catalogQuery.isFetching, hasSettledActiveStore, page, searchParams, setSearchParams]);

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
          <div className="storefront-controls">
            <div className="storefront-status-filter">
              <span className="storefront-control-label" id="storefront-status-label">
                상품 상태
              </span>
              <div role="group" aria-labelledby="storefront-status-label">
                {productStatuses.map((option) => (
                  <button
                    className="storefront-filter-button"
                    type="button"
                    key={option.value}
                    aria-pressed={status === option.value}
                    onClick={() => updateSearchParams({ status: option.value, page: 0 })}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
            <label className="storefront-sort-control">
              <span className="storefront-control-label">정렬</span>
              <select value={sort} onChange={(event) => updateSearchParams({ sort: event.target.value as StorefrontProductSort, page: 0 })}>
                {productSorts.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <StorefrontCatalog query={catalogQuery} page={page} onMovePage={(nextPage) => updateSearchParams({ page: nextPage })} />
        </section>
      )}
    </main>
  );

  function updateSearchParams(next: Partial<{ status: StorefrontProductStatus; sort: StorefrontProductSort; page: number }>) {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('status', next.status ?? status);
    nextParams.set('sort', next.sort ?? sort);
    nextParams.set('page', String(next.page ?? page));
    setSearchParams(nextParams);
  }
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

type StorefrontCatalogProps = {
  query: ReturnType<typeof useQuery<Awaited<ReturnType<typeof getStorefrontProducts>>, Error>>;
  page: number;
  onMovePage: (page: number) => void;
};

function StorefrontCatalog({ query, page, onMovePage }: StorefrontCatalogProps) {
  if (query.isLoading) {
    return <p className="status-text">상품을 불러오고 있습니다.</p>;
  }

  if (query.error) {
    return <ErrorState message={toErrorMessage(query.error, '상품 목록을 불러오지 못했습니다.')} />;
  }

  if (!query.data || query.data.content.length === 0) {
    return <EmptyState title="이 상태의 상품이 없습니다" description="다른 상품 상태를 선택해보세요." />;
  }

  return (
    <>
      <div className="product-grid" aria-label="상점 상품 목록">
        {query.data.content.map((product) => (
          <ProductCard key={product.id} product={product} />
        ))}
      </div>
      <nav className="storefront-pagination" aria-label="상품 페이지 이동">
        <button className="text-button" type="button" disabled={page === 0 || query.isFetching} onClick={() => onMovePage(page - 1)}>
          이전 페이지
        </button>
        <span aria-live="polite">
          {page + 1} / {Math.max(query.data.totalPages, 1)}
        </span>
        <button
          className="text-button"
          type="button"
          disabled={page + 1 >= query.data.totalPages || query.isFetching}
          onClick={() => onMovePage(page + 1)}
        >
          다음 페이지
        </button>
      </nav>
    </>
  );
}

function normalizeStatus(value: string | null): StorefrontProductStatus {
  return productStatuses.some((option) => option.value === value) ? (value as StorefrontProductStatus) : 'ON_SALE';
}

function normalizeSort(value: string | null): StorefrontProductSort {
  return productSorts.some((option) => option.value === value) ? (value as StorefrontProductSort) : 'NEWEST';
}

function normalizePage(value: string | null) {
  if (value === null || !/^\d+$/.test(value)) {
    return 0;
  }

  const parsedPage = Number(value);
  return Number.isSafeInteger(parsedPage) && parsedPage <= JAVA_INT_MAX ? parsedPage : 0;
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
