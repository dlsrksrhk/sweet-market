import { api } from '../../shared/api/http';
import { type OrderStatus } from '../orders/orderApi';

export type DeliveryStatus = 'READY' | 'SHIPPING' | 'DELIVERED';

export type Delivery = {
  id: number;
  orderId: number;
  trackingNumber: string;
  status: DeliveryStatus;
  orderStatus: OrderStatus;
  startedAt: string | null;
  completedAt: string | null;
};

export function startDelivery(orderId: number) {
  return api<Delivery>(`/api/deliveries/${orderId}/start`, {
    method: 'POST',
  });
}

export function completeDelivery(orderId: number) {
  return api<Delivery>(`/api/deliveries/${orderId}/complete`, {
    method: 'POST',
  });
}
