import { api } from '../../shared/api/http';
import { type Page } from '../products/productApi';

export type OrderStatus = 'CREATED' | 'PAID' | 'SHIPPING' | 'DELIVERED' | 'CONFIRMED' | 'CANCELED' | 'REFUND_REQUESTED' | 'REFUNDED';

export type RefundRequestStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED';

export type RefundRequest = {
  id: number;
  orderId: number;
  productId: number;
  productTitle: string;
  buyerId: number;
  reason: string;
  status: RefundRequestStatus;
  requestedAt: string;
  handledById: number | null;
  handledAt: string | null;
  rejectReason: string | null;
};

export type OrderSummary = {
  id: number;
  productId: number;
  productTitle: string;
  productPrice: number;
  listPrice: number;
  promotionCampaignId: number | null;
  promotionDiscountAmount: number;
  memberCouponId: number | null;
  couponDiscountAmount: number;
  finalPrice: number;
  sellerId: number;
  sellerNickname: string;
  status: OrderStatus;
  productStatus: string;
  orderedAt: string;
  reviewed: boolean;
  refundStatus: RefundRequestStatus | null;
  refundRequestedAt: string | null;
  refundHandledAt: string | null;
  refundRejectReason: string | null;
};

export type Order = OrderSummary & {
  buyerId: number;
  buyerNickname: string;
  canceledAt: string | null;
};

export function createOrder(productId: number, memberCouponId: number | null = null) {
  return api<Order>('/api/orders', {
    method: 'POST',
    body: JSON.stringify({ productId, memberCouponId }),
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

export function createRefundRequest(orderId: number, reason: string) {
  return api<RefundRequest>(`/api/orders/${orderId}/refund-requests`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}
