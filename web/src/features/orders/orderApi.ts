import { api } from '../../shared/api/http';
import { type Page } from '../products/productApi';

export type OrderStatus = 'CREATED' | 'PAID' | 'SHIPPING' | 'DELIVERED' | 'CONFIRMED' | 'CANCELED';

export type OrderSummary = {
  id: number;
  productId: number;
  productTitle: string;
  productPrice: number;
  sellerId: number;
  sellerNickname: string;
  status: OrderStatus;
  productStatus: string;
  orderedAt: string;
  reviewed: boolean;
};

export type Order = OrderSummary & {
  buyerId: number;
  buyerNickname: string;
  canceledAt: string | null;
};

export function createOrder(productId: number) {
  return api<Order>('/api/orders', {
    method: 'POST',
    body: JSON.stringify({ productId }),
  });
}

export function getMyOrders() {
  return api<Page<OrderSummary>>('/api/orders/me');
}

export function getOrder(orderId: number) {
  return api<Order>(`/api/orders/${orderId}`);
}

export function cancelOrder(orderId: number) {
  return api<Order>(`/api/orders/${orderId}/cancel`, {
    method: 'POST',
  });
}

export function confirmOrder(orderId: number) {
  return api<Order>(`/api/orders/${orderId}/confirm`, {
    method: 'POST',
  });
}
