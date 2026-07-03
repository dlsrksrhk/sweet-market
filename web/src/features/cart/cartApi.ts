import { api } from '../../shared/api/http';
import { type OrderSummary } from '../orders/orderApi';
import { type Page, type ProductStatus } from '../products/productApi';

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
  status: ProductStatus;
  thumbnailUrl: string | null;
  cartedAt: string;
  checkoutAvailable: boolean;
  unavailableReason: string | null;
};

export type CartCheckoutResponse = {
  orders: OrderSummary[];
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

export function checkoutCart(cartItemIds: number[]) {
  return api<CartCheckoutResponse>('/api/me/cart/checkout', {
    method: 'POST',
    body: JSON.stringify({ cartItemIds }),
  });
}
