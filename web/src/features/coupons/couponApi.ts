import { api } from '../../shared/api/http';
import type { Page } from '../products/productApi';

export type CouponScope = 'SELECTED_PRODUCTS' | 'ALL_PRODUCTS';
export type CouponDiscountType = 'FIXED_AMOUNT' | 'PERCENTAGE';
export type CouponValidityType = 'COMMON_EXPIRY' | 'DAYS_FROM_ISSUANCE';
export type CouponLifecycleStatus = 'DRAFT' | 'SCHEDULED' | 'PAUSED' | 'ENDED';
export type CouponEffectiveStatus = 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'ENDED';
export type MemberCouponStatus = 'ISSUED' | 'USED' | 'EXPIRED' | 'UNAVAILABLE';

export type CouponTarget = { productId: number; title: string; price: number };

export type CouponCampaign = {
  id: number;
  ownerType: 'PLATFORM' | 'STORE';
  store: { id: number; publicName: string } | null;
  scope: CouponScope;
  discountType: CouponDiscountType;
  discountValue: number;
  maxDiscountAmount: number | null;
  minimumPurchaseAmount: number;
  stackable: boolean;
  title: string;
  label: string | null;
  issueStartsAt: string;
  issueEndsAt: string;
  validityType: CouponValidityType;
  commonExpiresAt: string | null;
  validityDays: number | null;
  lifecycleStatus: CouponLifecycleStatus;
  effectiveStatus: CouponEffectiveStatus;
  targetCount: number;
  targets?: CouponTarget[];
};

export type CouponCampaignInput = {
  scope: CouponScope;
  discountType: CouponDiscountType;
  discountValue: number;
  maxDiscountAmount?: number;
  minimumPurchaseAmount: number;
  stackable: boolean;
  title: string;
  label?: string;
  issueStartsAt: string;
  issueEndsAt: string;
  validityType: CouponValidityType;
  commonExpiresAt?: string;
  validityDays?: number;
  productIds?: number[];
};

export type CouponCampaignSearchInput = { status?: CouponEffectiveStatus; periodFrom?: string; periodTo?: string; page: number; size: number };
export type AvailableCouponCampaign = {
  id: number; source: 'PLATFORM' | 'STORE'; store: { id: number; publicName: string } | null; scope: CouponScope; discountType: CouponDiscountType;
  discountValue: number; maxDiscountAmount: number | null; minimumPurchaseAmount: number; stackable: boolean;
  title: string; label: string | null; issueStartsAt: string; issueEndsAt: string; validityType: CouponValidityType;
  commonExpiresAt: string | null; validityDays: number | null; effectiveStatus: CouponEffectiveStatus; claimed: boolean;
};

export type MemberCoupon = {
  id: number; campaignId: number; title: string; label: string | null; source: 'PLATFORM' | 'STORE'; store: { id: number; publicName: string } | null; discountType: CouponDiscountType;
  discountValue: number; maxDiscountAmount: number | null; minimumPurchaseAmount: number; scope: CouponScope;
  stackable: boolean; issuedAt: string; validUntil: string; status: MemberCouponStatus;
  unavailabilityReason: CouponEffectiveStatus | null;
};

export type CouponListInput = { page: number; size: number; source?: 'PLATFORM' | 'STORE'; storeId?: number };

export const couponQueryKeys = {
  all: ['coupons'] as const,
  available: (input: CouponListInput) => [...couponQueryKeys.all, 'available', input.source ?? null, input.storeId ?? null, input.page, input.size] as const,
  wallet: (input: CouponListInput & { status?: MemberCouponStatus }) => [...couponQueryKeys.all, 'wallet', input.status ?? null, input.page, input.size] as const,
  store: (storeId: number) => [...couponQueryKeys.all, 'store', storeId] as const,
  storeList: (storeId: number, input: CouponCampaignSearchInput) => [...couponQueryKeys.store(storeId), 'list', input.status ?? null, input.periodFrom ?? null, input.periodTo ?? null, input.page, input.size] as const,
  storeDetail: (storeId: number, campaignId: number) => [...couponQueryKeys.store(storeId), 'detail', campaignId] as const,
  admin: () => [...couponQueryKeys.all, 'admin'] as const,
  adminList: (input: CouponCampaignSearchInput) => [...couponQueryKeys.admin(), 'list', input.status ?? null, input.periodFrom ?? null, input.periodTo ?? null, input.page, input.size] as const,
  adminDetail: (campaignId: number) => [...couponQueryKeys.admin(), 'detail', campaignId] as const,
};

function campaignQuery(input: CouponCampaignSearchInput) {
  const query = new URLSearchParams({ page: String(input.page), size: String(input.size) });
  if (input.status) query.set('status', input.status);
  if (input.periodFrom) query.set('periodFrom', input.periodFrom);
  if (input.periodTo) query.set('periodTo', input.periodTo);
  return query.toString();
}

function listQuery(input: CouponListInput & { status?: MemberCouponStatus }) { const query = new URLSearchParams({ page: String(input.page), size: String(input.size) }); if (input.status) query.set('status', input.status); if (input.source) query.set('source', input.source); if (input.storeId) query.set('storeId', String(input.storeId)); return query.toString(); }

export function getAvailableCouponCampaigns(input: CouponListInput) { return api<Page<AvailableCouponCampaign>>(`/api/coupon-campaigns/available?${listQuery(input)}`); }
export function claimCouponCampaign(campaignId: number) { return api<MemberCoupon>(`/api/coupon-campaigns/${campaignId}/claim`, { method: 'POST' }); }
export function getMyCoupons(input: CouponListInput & { status?: MemberCouponStatus }) { return api<Page<MemberCoupon>>(`/api/me/coupons?${listQuery(input)}`); }

export function getStoreCouponCampaigns(storeId: number, input: CouponCampaignSearchInput) { return api<Page<CouponCampaign>>(`/api/stores/${storeId}/coupon-campaigns?${campaignQuery(input)}`); }
export function getStoreCouponCampaign(storeId: number, campaignId: number) { return api<CouponCampaign>(`/api/stores/${storeId}/coupon-campaigns/${campaignId}`); }
export function createStoreCouponCampaign(storeId: number, input: CouponCampaignInput) { return api<CouponCampaign>(`/api/stores/${storeId}/coupon-campaigns`, { method: 'POST', body: JSON.stringify(input) }); }
export function updateStoreCouponCampaign(storeId: number, campaignId: number, input: CouponCampaignInput) { return api<CouponCampaign>(`/api/stores/${storeId}/coupon-campaigns/${campaignId}`, { method: 'PATCH', body: JSON.stringify(input) }); }
export function transitionStoreCouponCampaign(storeId: number, campaignId: number, action: 'schedule' | 'pause' | 'resume' | 'end') { return api<CouponCampaign>(`/api/stores/${storeId}/coupon-campaigns/${campaignId}/${action}`, { method: 'POST' }); }

export function getAdminCouponCampaigns(input: CouponCampaignSearchInput) { return api<Page<CouponCampaign>>(`/api/admin/coupon-campaigns?${campaignQuery(input)}`); }
export function getAdminCouponCampaign(campaignId: number) { return api<CouponCampaign>(`/api/admin/coupon-campaigns/${campaignId}`); }
export function createAdminCouponCampaign(input: CouponCampaignInput) { return api<CouponCampaign>('/api/admin/coupon-campaigns', { method: 'POST', body: JSON.stringify(input) }); }
export function updateAdminCouponCampaign(campaignId: number, input: CouponCampaignInput) { return api<CouponCampaign>(`/api/admin/coupon-campaigns/${campaignId}`, { method: 'PATCH', body: JSON.stringify(input) }); }
export function transitionAdminCouponCampaign(campaignId: number, action: 'schedule' | 'pause' | 'resume' | 'end') { return api<CouponCampaign>(`/api/admin/coupon-campaigns/${campaignId}/${action}`, { method: 'POST' }); }
