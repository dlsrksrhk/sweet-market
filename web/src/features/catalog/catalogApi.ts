import { api } from '../../shared/api/http';
import type { BuyerAvailability, ProductCategory, ProductSalesPolicy } from '../products/productApi';
import type { StoreType } from '../stores/storeApi';
import { toSearchParams, type CatalogSearchInput } from './catalogQueryState';

export type { CatalogSearchInput } from './catalogQueryState';

export type CatalogSort = 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC';

export type CatalogAvailabilityFilter = 'IN_STOCK' | 'LOW_STOCK';

export type CatalogProductCard = {
  id: number;
  title: string;
  price: number;
  category: ProductCategory;
  representativeImageUrl: string | null;
  availability: BuyerAvailability;
  salesPolicy: ProductSalesPolicy;
  storeId: number;
  sellerId: number;
  storeName: string;
  storeType: StoreType;
  wishlisted: boolean;
  carted: boolean;
};

export type CatalogSearchResponse = {
  content: CatalogProductCard[];
  hasNext: boolean;
  nextCursor: string | null;
};

export type StoreCatalogSearchInput = Omit<CatalogSearchInput, 'storeId'>;

export function getCatalogProducts(input: CatalogSearchInput) {
  return api<CatalogSearchResponse>(`/api/catalog/products?${toSearchParams(input)}`);
}

export function getStoreCatalogProducts(storeId: number, input: StoreCatalogSearchInput) {
  const searchParams = toSearchParams({ ...input, storeId: undefined });

  return api<CatalogSearchResponse>(`/api/stores/${storeId}/catalog/products?${searchParams}`);
}
