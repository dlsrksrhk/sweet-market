import { api } from '../../shared/api/http';
import type { Page } from '../products/productApi';

export type PromotionScope = 'SELECTED_PRODUCTS' | 'STORE_WIDE';
export type PromotionDiscountType = 'FIXED_AMOUNT' | 'PERCENTAGE';
export type PromotionLifecycleStatus = 'DRAFT' | 'SCHEDULED' | 'PAUSED' | 'ENDED';
export type PromotionEffectiveStatus = 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'ENDED';

export type PromotionTarget = {
  productId: number;
  title: string;
  price: number;
};

export type PromotionCampaign = {
  id: number;
  scope: PromotionScope;
  discountType: PromotionDiscountType;
  discountValue: number;
  priority: number;
  title: string;
  label: string | null;
  buyerText: string;
  discountText: string;
  startsAt: string;
  endsAt: string;
  lifecycleStatus: PromotionLifecycleStatus;
  effectiveStatus: PromotionEffectiveStatus;
  targetCount: number;
  targets?: PromotionTarget[];
};

export type PromotionCampaignInput = {
  scope: PromotionScope;
  discountType: PromotionDiscountType;
  discountValue: number;
  priority: number;
  title: string;
  label?: string;
  startsAt: string;
  endsAt: string;
  productIds?: number[];
};

export type PromotionSearchInput = {
  status?: PromotionEffectiveStatus;
  periodFrom?: string;
  periodTo?: string;
  page: number;
  size: number;
};

export const promotionQueryKeys = {
  all: ['promotions'] as const,
  store: (storeId: number) => [...promotionQueryKeys.all, 'store', storeId] as const,
  list: (storeId: number, input: PromotionSearchInput) => [
    ...promotionQueryKeys.store(storeId),
    'list',
    input.status ?? null,
    input.periodFrom ?? null,
    input.periodTo ?? null,
    input.page,
    input.size,
  ] as const,
  detail: (storeId: number, promotionId: number) => [...promotionQueryKeys.store(storeId), 'detail', promotionId] as const,
};

export function getPromotions(storeId: number, input: PromotionSearchInput) {
  const query = new URLSearchParams({ page: String(input.page), size: String(input.size) });
  if (input.status) query.set('status', input.status);
  if (input.periodFrom) query.set('periodFrom', input.periodFrom);
  if (input.periodTo) query.set('periodTo', input.periodTo);
  return api<Page<PromotionCampaign>>(`/api/stores/${storeId}/promotions?${query.toString()}`);
}

export function getPromotion(storeId: number, promotionId: number) {
  return api<PromotionCampaign>(`/api/stores/${storeId}/promotions/${promotionId}`);
}

export function createPromotion(storeId: number, input: PromotionCampaignInput) {
  return api<PromotionCampaign>(`/api/stores/${storeId}/promotions`, { method: 'POST', body: JSON.stringify(input) });
}

export function updatePromotion(storeId: number, promotionId: number, input: PromotionCampaignInput) {
  return api<PromotionCampaign>(`/api/stores/${storeId}/promotions/${promotionId}`, { method: 'PATCH', body: JSON.stringify(input) });
}

export function transitionPromotion(storeId: number, promotionId: number, action: 'schedule' | 'pause' | 'resume' | 'end') {
  return api<PromotionCampaign>(`/api/stores/${storeId}/promotions/${promotionId}/${action}`, { method: 'POST' });
}
