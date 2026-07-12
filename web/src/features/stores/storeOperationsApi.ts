import { api } from '../../shared/api/http';
import type { Page, ProductSalesPolicy, ProductStatus } from '../products/productApi';
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
  salesPolicy: ProductSalesPolicy;
  totalQuantity: number | null;
  reservedQuantity: number | null;
  availableQuantity: number | null;
  lowStockThreshold: number | null;
};

export type InventoryAdjustmentReason = 'RESTOCK' | 'STOCKTAKE' | 'DAMAGE_OR_DISPOSAL' | 'RETURN_RESTOCK' | 'OTHER';

export type InventoryAdjustmentInput = {
  totalQuantity: number;
  reason: InventoryAdjustmentReason;
  referenceNote?: string;
};

export type InventoryAdjustment = {
  adjustmentId: number;
  productId: number;
  changeType: 'INITIALIZATION' | 'MANUAL_ADJUSTMENT' | 'RESERVATION' | 'RELEASE' | 'SHIPMENT_COMMITMENT';
  reason: InventoryAdjustmentReason | null;
  referenceNote: string | null;
  beforeTotalQuantity: number;
  afterTotalQuantity: number;
  beforeReservedQuantity: number;
  afterReservedQuantity: number;
  actorMemberId: number | null;
  actorNickname: string | null;
  occurredAt: string;
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
  inventory: (storeId: number, productId: number) =>
    [...storeOperationQueryKeys.products(storeId), productId, 'inventory'] as const,
  inventoryHistory: (storeId: number, productId: number, page: number, size: number) =>
    [...storeOperationQueryKeys.inventory(storeId, productId), 'history', page, size] as const,
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

export function adjustProductInventory(storeId: number, productId: number, input: InventoryAdjustmentInput) {
  return api<InventoryAdjustment>(`/api/store-operations/${storeId}/products/${productId}/inventory`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export function getProductInventoryHistory(storeId: number, productId: number, page: number, size: number) {
  const searchParams = new URLSearchParams({ page: String(page), size: String(size) });
  return api<Page<InventoryAdjustment>>(
    `/api/store-operations/${storeId}/products/${productId}/inventory/history?${searchParams.toString()}`,
  );
}

export function getStoreMemberships(storeId: number) {
  return api<StoreMembership[]>(`/api/store-operations/${storeId}/memberships`);
}

export function removeStoreMembership(storeId: number, membershipId: number) {
  return api<void>(`/api/store-operations/${storeId}/memberships/${membershipId}`, {
    method: 'DELETE',
  });
}
