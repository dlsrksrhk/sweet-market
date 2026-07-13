import type { ProductCategory, ProductSalesPolicy } from '../products/productApi';
import type { StoreType } from '../stores/storeApi';
import type { CatalogAvailabilityFilter, CatalogSort } from './catalogApi';

export type CatalogSearchInput = {
  keyword?: string;
  category?: ProductCategory;
  minPrice?: number;
  maxPrice?: number;
  availability?: CatalogAvailabilityFilter;
  salesPolicy?: ProductSalesPolicy;
  storeType?: StoreType;
  storeId?: number;
  sort?: CatalogSort;
  cursor?: string;
  size?: number;
};

export type CatalogQueryState = Omit<CatalogSearchInput, 'sort' | 'size'> & {
  sort: CatalogSort;
  size: number;
};

export const DEFAULT_CATALOG_SORT: CatalogSort = 'NEWEST';
export const DEFAULT_CATALOG_SIZE = 12;
export const MAX_CATALOG_SIZE = 40;

const CATALOG_SORTS = ['NEWEST', 'PRICE_ASC', 'PRICE_DESC'] as const;
const CATALOG_AVAILABILITY_FILTERS = ['IN_STOCK', 'LOW_STOCK'] as const;
const PRODUCT_CATEGORIES = ['COMPUTERS', 'MOBILE', 'HOME_APPLIANCES', 'VEHICLES', 'LIVING_HOBBY', 'OTHER'] as const;
const PRODUCT_SALES_POLICIES = ['SINGLE_ITEM', 'STOCK_MANAGED'] as const;
const STORE_TYPES = ['PERSONAL', 'BUSINESS'] as const;
const CURSOR_RESET_FIELDS: (keyof Pick<
  CatalogSearchInput,
  'keyword' | 'category' | 'minPrice' | 'maxPrice' | 'availability' | 'salesPolicy' | 'storeType' | 'storeId' | 'sort'
>)[] = [
  'keyword',
  'category',
  'minPrice',
  'maxPrice',
  'availability',
  'salesPolicy',
  'storeType',
  'storeId',
  'sort',
];

export function readCatalogQueryState(searchParams: URLSearchParams): CatalogQueryState {
  return normalizeCatalogSearchInput({
    keyword: searchParams.get('keyword') ?? undefined,
    category: knownValue(searchParams.get('category'), PRODUCT_CATEGORIES),
    minPrice: nonNegativeSafeInteger(searchParams.get('minPrice')),
    maxPrice: nonNegativeSafeInteger(searchParams.get('maxPrice')),
    availability: knownValue(searchParams.get('availability'), CATALOG_AVAILABILITY_FILTERS),
    salesPolicy: knownValue(searchParams.get('salesPolicy'), PRODUCT_SALES_POLICIES),
    storeType: knownValue(searchParams.get('storeType'), STORE_TYPES),
    storeId: positiveSafeInteger(searchParams.get('storeId')),
    sort: knownValue(searchParams.get('sort'), CATALOG_SORTS),
    cursor: searchParams.get('cursor') ?? undefined,
    size: size(searchParams.get('size')),
  });
}

export const fromSearchParams = readCatalogQueryState;

export function normalizeCatalogSearchInput(input: CatalogSearchInput): CatalogQueryState {
  const normalized: CatalogQueryState = {
    sort: knownValue(input.sort, CATALOG_SORTS) ?? DEFAULT_CATALOG_SORT,
    size: size(input.size),
  };
  const keyword = nonBlank(input.keyword);
  const cursor = nonBlank(input.cursor);
  const category = knownValue(input.category, PRODUCT_CATEGORIES);
  const minPrice = nonNegativeSafeInteger(input.minPrice);
  const maxPrice = nonNegativeSafeInteger(input.maxPrice);
  const availability = knownValue(input.availability, CATALOG_AVAILABILITY_FILTERS);
  const salesPolicy = knownValue(input.salesPolicy, PRODUCT_SALES_POLICIES);
  const storeType = knownValue(input.storeType, STORE_TYPES);
  const storeId = positiveSafeInteger(input.storeId);

  if (keyword !== undefined) normalized.keyword = keyword;
  if (category !== undefined) normalized.category = category;
  if (minPrice !== undefined) normalized.minPrice = minPrice;
  if (maxPrice !== undefined) normalized.maxPrice = maxPrice;
  if (availability !== undefined) normalized.availability = availability;
  if (salesPolicy !== undefined) normalized.salesPolicy = salesPolicy;
  if (storeType !== undefined) normalized.storeType = storeType;
  if (storeId !== undefined) normalized.storeId = storeId;
  if (cursor !== undefined) normalized.cursor = cursor;

  return normalized;
}

export function updateCatalogQueryState(
  current: CatalogSearchInput,
  changes: Partial<CatalogSearchInput>,
): CatalogQueryState {
  const normalizedCurrent = normalizeCatalogSearchInput(current);
  const next = normalizeCatalogSearchInput({ ...normalizedCurrent, ...changes });

  if (CURSOR_RESET_FIELDS.some((field) => normalizedCurrent[field] !== next[field])) {
    delete next.cursor;
  }

  return next;
}

export function toSearchParams(input: CatalogSearchInput): URLSearchParams {
  const normalized = normalizeCatalogSearchInput(input);
  const searchParams = new URLSearchParams({
    sort: normalized.sort,
    size: String(normalized.size),
  });

  setSearchParam(searchParams, 'keyword', normalized.keyword);
  setSearchParam(searchParams, 'category', normalized.category);
  setSearchParam(searchParams, 'minPrice', normalized.minPrice);
  setSearchParam(searchParams, 'maxPrice', normalized.maxPrice);
  setSearchParam(searchParams, 'availability', normalized.availability);
  setSearchParam(searchParams, 'salesPolicy', normalized.salesPolicy);
  setSearchParam(searchParams, 'storeType', normalized.storeType);
  setSearchParam(searchParams, 'storeId', normalized.storeId);
  setSearchParam(searchParams, 'cursor', normalized.cursor);

  return searchParams;
}

function setSearchParam(searchParams: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined) {
    searchParams.set(key, String(value));
  }
}

function knownValue<T extends string>(value: unknown, options: readonly T[]): T | undefined {
  return typeof value === 'string' && options.includes(value as T) ? (value as T) : undefined;
}

function nonBlank(value: unknown) {
  if (typeof value !== 'string') return undefined;

  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
}

function nonNegativeSafeInteger(value: unknown) {
  const numberValue = toSafeInteger(value);
  return numberValue !== undefined && numberValue >= 0 ? numberValue : undefined;
}

function positiveSafeInteger(value: unknown) {
  const numberValue = toSafeInteger(value);
  return numberValue !== undefined && numberValue > 0 ? numberValue : undefined;
}

function size(value: unknown) {
  const numberValue = toSafeInteger(value);
  return numberValue !== undefined && numberValue >= 1 && numberValue <= MAX_CATALOG_SIZE
    ? numberValue
    : DEFAULT_CATALOG_SIZE;
}

function toSafeInteger(value: unknown) {
  if (typeof value === 'string' && value.trim() === '') return undefined;

  const numberValue = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : NaN;
  return Number.isSafeInteger(numberValue) ? numberValue : undefined;
}
