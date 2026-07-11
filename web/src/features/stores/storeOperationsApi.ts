import { api } from '../../shared/api/http';
import type { Page, ProductStatus } from '../products/productApi';
import type { StoreStatus, StoreType } from './storeApi';

export type StoreMemberRole = 'OWNER' | 'MANAGER';

export type OperableStore = {
  storeId: number;
  type: StoreType;
  publicName: string;
  status: StoreStatus;
  role: StoreMemberRole;
};

export type StoreCatalogSummary = {
  onSaleCount: number;
  reservedCount: number;
  soldOutCount: number;
  hiddenCount: number;
  catalogWritable: boolean;
};

export type StoreCatalogProduct = {
  productId: number;
  thumbnailUrl: string | null;
  title: string;
  price: number;
  status: ProductStatus;
};

export type StoreCatalogSort = 'NEWEST' | 'OLDEST';

export type StoreCatalogSearchInput = {
  status?: ProductStatus;
  keyword?: string;
  sort: StoreCatalogSort;
  page: number;
  size: number;
};

export type StoreMembership = {
  membershipId: number;
  memberId: number;
  memberNickname: string;
  role: StoreMemberRole;
  joinedAt: string;
};

function normalizeKeyword(keyword: string | undefined) {
  return keyword?.trim() || null;
}

export const storeOperationQueryKeys = {
  all: ['store-operations'] as const,
  stores: () => [...storeOperationQueryKeys.all, 'stores'] as const,
  store: (storeId: number) => [...storeOperationQueryKeys.all, 'store', storeId] as const,
  summary: (storeId: number) => [...storeOperationQueryKeys.store(storeId), 'summary'] as const,
  products: (storeId: number) => [...storeOperationQueryKeys.store(storeId), 'products'] as const,
  productList: (storeId: number, input: StoreCatalogSearchInput) =>
    [
      ...storeOperationQueryKeys.products(storeId),
      input.status ?? null,
      normalizeKeyword(input.keyword),
      input.sort,
      input.page,
      input.size,
    ] as const,
  memberships: (storeId: number) => [...storeOperationQueryKeys.store(storeId), 'memberships'] as const,
};

export function getOperableStores() {
  return api<OperableStore[]>('/api/store-operations');
}

export function getStoreCatalogSummary(storeId: number) {
  return api<StoreCatalogSummary>(`/api/store-operations/${storeId}/summary`);
}

export function getStoreCatalogProducts(storeId: number, input: StoreCatalogSearchInput) {
  const searchParams = new URLSearchParams();
  const keyword = normalizeKeyword(input.keyword);

  if (input.status) {
    searchParams.set('status', input.status);
  }
  if (keyword) {
    searchParams.set('keyword', keyword);
  }
  searchParams.set('sort', input.sort);
  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));

  return api<Page<StoreCatalogProduct>>(
    `/api/store-operations/${storeId}/products?${searchParams.toString()}`,
  );
}

export function hideStoreProducts(storeId: number, productIds: number[]) {
  return api<void>(`/api/store-operations/${storeId}/products/hide`, {
    method: 'POST',
    body: JSON.stringify({ productIds }),
  });
}

export function showStoreProducts(storeId: number, productIds: number[]) {
  return api<void>(`/api/store-operations/${storeId}/products/show`, {
    method: 'POST',
    body: JSON.stringify({ productIds }),
  });
}

export function getStoreMemberships(storeId: number) {
  return api<StoreMembership[]>(`/api/store-operations/${storeId}/memberships`);
}

export function removeStoreMembership(storeId: number, membershipId: number) {
  return api<void>(`/api/store-operations/${storeId}/memberships/${membershipId}`, {
    method: 'DELETE',
  });
}
