import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import type { ProductCategory, ProductSalesPolicy } from '../products/productApi';
import type { StoreType } from '../stores/storeApi';
import { EmptyState, ErrorState } from '../../shared/ui/ResourceStates';
import { CatalogProductCard } from './CatalogProductCard';
import {
  getCatalogProducts,
  getStoreCatalogProducts,
  type CatalogAvailabilityFilter,
  type CatalogSearchInput,
  type CatalogSort,
} from './catalogApi';
import { fromSearchParams, toSearchParams } from './catalogQueryState';

const categories: { value: ProductCategory; label: string }[] = [
  { value: 'COMPUTERS', label: '컴퓨터' },
  { value: 'MOBILE', label: '모바일' },
  { value: 'HOME_APPLIANCES', label: '생활가전' },
  { value: 'VEHICLES', label: '차량' },
  { value: 'LIVING_HOBBY', label: '생활·취미' },
  { value: 'OTHER', label: '기타' },
];
const availabilityFilters: { value: CatalogAvailabilityFilter; label: string }[] = [
  { value: 'IN_STOCK', label: '재고 있음' },
  { value: 'LOW_STOCK', label: '재고 적음' },
];
const salesPolicies: { value: ProductSalesPolicy; label: string }[] = [
  { value: 'SINGLE_ITEM', label: '단일 상품' },
  { value: 'STOCK_MANAGED', label: '재고 관리' },
];
const storeTypes: { value: StoreType; label: string }[] = [
  { value: 'PERSONAL', label: '개인 상점' },
  { value: 'BUSINESS', label: '사업자 상점' },
];
const sorts: { value: CatalogSort; label: string }[] = [
  { value: 'NEWEST', label: '최신순' },
  { value: 'PRICE_ASC', label: '낮은 가격순' },
  { value: 'PRICE_DESC', label: '높은 가격순' },
];

type CatalogPanelProps = {
  storeId?: number;
};

export function updateCatalogQuery(
  current: CatalogSearchInput,
  change: Partial<CatalogSearchInput>,
): CatalogSearchInput {
  const next = { ...current, ...change };
  return Object.keys(change).length === 1 && change.cursor !== undefined
    ? next
    : { ...next, cursor: undefined };
}

export function CatalogPanel({ storeId }: CatalogPanelProps) {
  const { member, loading: authLoading } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [filtersOpen, setFiltersOpen] = useState(false);
  const parsedInput = fromSearchParams(searchParams);
  const input = storeId === undefined ? parsedInput : { ...parsedInput, storeId: undefined };
  const normalizedSearchParams = toSearchParams(input);
  const isStoreCatalog = storeId !== undefined;
  const viewerKey = member?.id ?? 'anonymous';
  const query = useQuery({
    queryKey: ['catalog', storeId ?? 'global', viewerKey, input],
    queryFn: () => (storeId === undefined ? getCatalogProducts(input) : getStoreCatalogProducts(storeId, input)),
    enabled: !authLoading,
  });

  useEffect(() => {
    if (searchParams.toString() !== normalizedSearchParams.toString()) {
      setSearchParams(normalizedSearchParams, { replace: true });
    }
  }, [normalizedSearchParams, searchParams, setSearchParams]);

  function updateQuery(change: Partial<CatalogSearchInput>) {
    const next = updateCatalogQuery(input, change);
    if (isStoreCatalog) {
      delete next.storeId;
    }
    setSearchParams(toSearchParams(next));
  }

  function loadNext() {
    if (query.data?.nextCursor) {
      updateQuery({ cursor: query.data.nextCursor });
    }
  }

  return (
    <section className="catalog-panel" aria-label="상품 카탈로그">
      <button
        className="catalog-filter-toggle"
        type="button"
        aria-controls="catalog-filters"
        aria-expanded={filtersOpen}
        onClick={() => setFiltersOpen((open) => !open)}
      >
        필터 {filtersOpen ? '닫기' : '열기'}
      </button>
      <aside id="catalog-filters" className={filtersOpen ? 'catalog-filters catalog-filters-open' : 'catalog-filters'} aria-label="상품 필터">
        <label>
          <span>검색어</span>
          <input
            type="search"
            value={input.keyword ?? ''}
            onChange={(event) => updateQuery({ keyword: event.target.value || undefined })}
            placeholder="상품명을 검색하세요"
          />
        </label>
        <label>
          <span>카테고리</span>
          <select value={input.category ?? ''} onChange={(event) => updateQuery({ category: event.target.value as ProductCategory || undefined })}>
            <option value="">전체</option>
            {categories.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <div className="catalog-price-fields">
          <label>
            <span>최소 가격</span>
            <input type="number" min="0" inputMode="numeric" value={input.minPrice ?? ''} onChange={(event) => updateQuery({ minPrice: numberValue(event.target.value) })} />
          </label>
          <label>
            <span>최대 가격</span>
            <input type="number" min="0" inputMode="numeric" value={input.maxPrice ?? ''} onChange={(event) => updateQuery({ maxPrice: numberValue(event.target.value) })} />
          </label>
        </div>
        <label>
          <span>재고 상태</span>
          <select value={input.availability ?? ''} onChange={(event) => updateQuery({ availability: event.target.value as CatalogAvailabilityFilter || undefined })}>
            <option value="">전체</option>
            {availabilityFilters.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <label>
          <span>판매 방식</span>
          <select value={input.salesPolicy ?? ''} onChange={(event) => updateQuery({ salesPolicy: event.target.value as ProductSalesPolicy || undefined })}>
            <option value="">전체</option>
            {salesPolicies.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        {!isStoreCatalog && (
          <label>
            <span>상점 유형</span>
            <select value={input.storeType ?? ''} onChange={(event) => updateQuery({ storeType: event.target.value as StoreType || undefined })}>
              <option value="">전체</option>
              {storeTypes.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
        )}
        <label>
          <span>정렬</span>
          <select value={input.sort} onChange={(event) => updateQuery({ sort: event.target.value as CatalogSort })}>
            {sorts.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
      </aside>
      <section className="catalog-results" aria-live="polite">
        {query.isLoading && <p className="status-text">상품을 불러오고 있습니다.</p>}
        {query.error && (
          <div className="catalog-error">
            <ErrorState message="상품 목록을 불러오지 못했습니다." />
            <button className="text-button" type="button" onClick={() => updateQuery({ cursor: undefined })}>첫 페이지로 다시 시작</button>
          </div>
        )}
        {!query.isLoading && !query.error && query.data?.content.length === 0 && (
          <EmptyState title="조건에 맞는 상품이 없습니다" description="필터를 바꿔 다시 찾아보세요." />
        )}
        {!query.isLoading && !query.error && query.data && query.data.content.length > 0 && (
          <>
            <div className="catalog-product-grid" aria-label="상품 목록">
              {query.data.content.map((product) => <CatalogProductCard key={product.id} product={product} />)}
            </div>
            <button className="text-button catalog-load-more" type="button" onClick={loadNext} disabled={!query.data.hasNext || query.isFetching}>
              더 보기
            </button>
          </>
        )}
      </section>
    </section>
  );
}

function numberValue(value: string) {
  return value === '' ? undefined : Number(value);
}
