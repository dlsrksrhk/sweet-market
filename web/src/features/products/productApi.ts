import { api } from '../../shared/api/http';

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
};

export type ProductStatus = 'ON_SALE' | 'RESERVED' | 'SOLD_OUT' | 'HIDDEN';

export type ProductSummary = {
  id: number;
  sellerId: number;
  sellerNickname: string;
  title: string;
  price: number;
  status: ProductStatus;
  thumbnailUrl: string | null;
};

export type ProductImage = {
  id: number;
  imageUrl: string;
  sortOrder?: number;
};

export type Product = Omit<ProductSummary, 'thumbnailUrl'> & {
  description: string;
  images: ProductImage[];
};

export type ProductCreateInput = {
  title: string;
  description: string;
  price: number;
  imageUrls: string[];
};

export type ProductUpdateInput = {
  title: string;
  description: string;
  price: number;
};

export function getProducts() {
  return api<Page<ProductSummary>>('/api/products');
}

export function getMyProducts() {
  return api<Page<ProductSummary>>('/api/products/me');
}

export function getProduct(productId: number) {
  return api<Product>(`/api/products/${productId}`);
}

export function createProduct(input: ProductCreateInput) {
  return api<Product>('/api/products', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateProduct(productId: number, input: ProductUpdateInput) {
  return api<Product>(`/api/products/${productId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export function hideProduct(productId: number) {
  return api<Product>(`/api/products/${productId}`, {
    method: 'DELETE',
  });
}
