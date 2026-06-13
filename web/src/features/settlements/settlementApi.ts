import { api } from '../../shared/api/http';

export type SettlementStatus = 'READY' | 'COMPLETED' | 'FAILED';

export type Settlement = {
  id: number;
  orderId: number;
  sellerId: number;
  sellerNickname: string;
  productId: number;
  productTitle: string;
  amount: number;
  status: SettlementStatus;
  settledAt: string | null;
};

export function createSettlement(orderId: number) {
  return api<Settlement>(`/api/settlements/orders/${orderId}`, {
    method: 'POST',
  });
}

export function getMySettlements() {
  return api<Settlement[]>('/api/settlements/me');
}
