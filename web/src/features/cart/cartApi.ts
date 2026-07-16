import { api } from '../../shared/api/http';
import { type OrderSummary } from '../orders/orderApi';
import { type BuyerAvailability, type Page, type ProductStatus } from '../products/productApi';

export type CartResponse = {
  productId: number;
  carted: boolean;
};

export type CartItem = {
  cartItemId: number;
  productId: number;
  sellerId: number;
  sellerNickname: string;
  title: string;
  price: number;
  listPrice: number;
  promotionId: number | null;
  promotionTitle: string | null;
  promotionDiscountAmount: number;
  effectivePrice: number;
  status: ProductStatus;
  thumbnailUrl: string | null;
  cartedAt: string;
  checkoutAvailable: boolean;
  unavailableReason: string | null;
  availability: BuyerAvailability;
};

export type CartCheckoutResponse = {
  orders: OrderSummary[];
};

export type CartCheckoutFailureItem = {
  cartItemId: number;
  productId: number;
  productTitle: string;
  reason: 'SOLD_OUT' | 'UNAVAILABLE';
};

export function addCart(productId: number) {
  return api<CartResponse>(`/api/products/${productId}/cart`, {
    method: 'POST',
  });
}

export function removeCart(productId: number) {
  return api<CartResponse>(`/api/products/${productId}/cart`, {
    method: 'DELETE',
  });
}

export function getMyCart() {
  return api<Page<CartItem>>('/api/me/cart');
}

export function checkoutCart(cartItemIds: number[], idempotencyKey: string) {
  return api<CartCheckoutResponse>('/api/me/cart/checkout', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ cartItemIds }),
  });
}
