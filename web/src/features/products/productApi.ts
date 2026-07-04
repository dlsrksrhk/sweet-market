import { api } from '../../shared/api/http';

const PRODUCT_IMAGE_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

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
  wishlistCount: number;
  wishlisted: boolean;
  carted: boolean;
};

export type ProductImage = {
  id: number;
  imageUrl: string;
  sortOrder: number;
  representative: boolean;
};

export type ProductImageUpload = {
  id: number;
  previewUrl: string;
  originalFileName: string;
  contentType: string;
  size: number;
  expiresAt: string;
};

export type ProductCreateImageInput = {
  uploadId: number;
  sortOrder: number;
  representative: boolean;
};

export type ProductUpdateImageInput =
  | {
      imageId: number;
      uploadId?: never;
      sortOrder: number;
      representative: boolean;
    }
  | {
      imageId?: never;
      uploadId: number;
      sortOrder: number;
      representative: boolean;
    };

export type Product = Omit<ProductSummary, 'thumbnailUrl'> & {
  description: string;
  images: ProductImage[];
  reviewCount: number;
  averageRating: number | null;
  sellerReviewCount: number;
  sellerAverageRating: number | null;
};

export type ProductCreateInput = {
  title: string;
  description: string;
  price: number;
  images: ProductCreateImageInput[];
};

export type ProductUpdateInput = {
  title: string;
  description: string;
  price: number;
  images: ProductUpdateImageInput[];
};

export type WishlistResponse = {
  productId: number;
  wishlisted: boolean;
  wishlistCount: number;
};

export type WishlistItem = {
  wishlistItemId: number;
  productId: number;
  sellerId: number;
  sellerNickname: string;
  title: string;
  price: number;
  status: ProductStatus;
  thumbnailUrl: string | null;
  wishlisted: boolean;
  wishlistCount: number;
  wishedAt: string;
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

export function addWishlist(productId: number) {
  return api<WishlistResponse>(`/api/products/${productId}/wishlist`, {
    method: 'POST',
  });
}

export function removeWishlist(productId: number) {
  return api<WishlistResponse>(`/api/products/${productId}/wishlist`, {
    method: 'DELETE',
  });
}

export function getMyWishlist() {
  return api<Page<WishlistItem>>('/api/me/wishlist');
}

export function uploadProductImage(file: File) {
  const formData = new FormData();
  formData.append('file', file);

  return api<ProductImageUpload>('/api/product-image-uploads', {
    method: 'POST',
    body: formData,
  });
}

export function toProductImageSrc(imageUrl: string | null) {
  if (!imageUrl) return null;
  if (imageUrl.startsWith('http://') || imageUrl.startsWith('https://') || imageUrl.startsWith('data:')) return imageUrl;
  return `${PRODUCT_IMAGE_BASE_URL}${imageUrl}`;
}
