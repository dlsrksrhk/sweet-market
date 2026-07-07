import { api } from '../../shared/api/http';
import { type OrderStatus } from '../orders/orderApi';

export type PaymentStatus = 'READY' | 'APPROVED' | 'CANCELED' | 'FAILED' | 'REFUNDED';

export type Payment = {
  id: number;
  orderId: number;
  externalPaymentId: string;
  status: PaymentStatus;
  orderStatus: OrderStatus;
  approvedAt: string | null;
  canceledAt: string | null;
};

export function approvePayment(orderId: number) {
  return api<Payment>(`/api/payments/${orderId}/approve`, {
    method: 'POST',
  });
}

export function cancelPayment(orderId: number) {
  return api<Payment>(`/api/payments/${orderId}/cancel`, {
    method: 'POST',
  });
}
