import { api } from '../../shared/api/http';

export type DashboardPeriodPreset = 'TODAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'LAST_90_DAYS';

export type DashboardPeriodInput = {
  preset?: DashboardPeriodPreset;
  from?: string;
  to?: string;
};

export type DashboardPeriod = {
  from: string;
  to: string;
  fromInclusive: string;
  toExclusive: string;
  timezone: 'Asia/Seoul';
};

export type DiscountAmountSummary = {
  applied: number;
  realized: number;
  canceled: number;
  refunded: number;
};

export type OutcomeReasonCount = {
  reason: string;
  count: number;
};

export type StoreOperationsDashboard = {
  storeId: number;
  storeName: string;
  period: DashboardPeriod;
  generatedAt: string;
  projectionUpdatedAt: string | null;
  projectionLagSeconds: number;
  trackingStartedAt: string | null;
  claimSuccessCount: number;
  redemptionSuccessCount: number;
  orderSuccessCount: number;
  purchaseFailureCount: number;
  promotionDiscounts: DiscountAmountSummary;
  couponDiscounts: DiscountAmountSummary;
  lowStockCount: number;
  soldOutTransitionCount: number;
  leadingFailureReasons: OutcomeReasonCount[];
};

export type StoreCampaignMetric = {
  id: string;
  latestBucketStart: string;
  campaignKind: string;
  campaignId: number;
  ownerType: string;
  ownerStoreId: number;
  status: string;
  claimSuccessCount: number;
  claimFailureCount: number;
  redemptionSuccessCount: number;
  redemptionFailureCount: number;
  orderSuccessCount: number;
  purchaseFailureCount: number;
  promotionDiscounts: DiscountAmountSummary;
  couponDiscounts: DiscountAmountSummary;
};

export type StoreCouponOutcome = {
  id: string;
  latestBucketStart: string;
  campaignId: number;
  ownerType: string;
  ownerStoreId: number;
  reason: string;
  claimSuccessCount: number;
  claimFailureCount: number;
  redemptionSuccessCount: number;
  redemptionFailureCount: number;
  discounts: DiscountAmountSummary;
};

export type StoreInventoryPressure = {
  productId: number;
  salesPolicy: string;
  availableQuantity: number | null;
  lowStock: boolean;
  lastSoldOutAt: string | null;
  reservationFailureCount: number;
  lastReservationFailureAt: string | null;
  updatedAt: string;
};

export type StorePurchaseOutcome = {
  id: string;
  latestBucketStart: string;
  reason: string;
  orderSuccessCount: number;
  purchaseFailureCount: number;
  reservationFailureCount: number;
};

export type StoreCampaignAudit = {
  id: number;
  eventId: string;
  campaignKind: string;
  campaignId: number;
  ownerType: string;
  ownerStoreId: number;
  actorMemberId: number | null;
  command: string;
  occurredAt: string;
  aggregateVersion: number | null;
  beforeSummary: string | null;
  afterSummary: string | null;
};

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

export type CampaignMetricInput = DashboardPeriodInput & {
  campaignKind?: string;
  status?: string;
  page: number;
  size: number;
};

export type CouponOutcomeInput = DashboardPeriodInput & {
  reason?: string;
  page: number;
  size: number;
};

export type InventoryPressureInput = DashboardPeriodInput & {
  attentionOnly: boolean;
  page: number;
  size: number;
};

export type PurchaseOutcomeInput = DashboardPeriodInput & {
  reason?: string;
  page: number;
  size: number;
};

export type CampaignAuditInput = DashboardPeriodInput & {
  campaignKind?: string;
  command?: string;
  page: number;
  size: number;
};

function normalized(value: string | undefined) {
  return value?.trim() || null;
}

function periodKey(input: DashboardPeriodInput) {
  return [input.preset ?? null, normalized(input.from), normalized(input.to)] as const;
}

export const storeOperationsDashboardQueryKeys = {
  all: ['store-operations-dashboard'] as const,
  store: (storeId: number) => [...storeOperationsDashboardQueryKeys.all, storeId] as const,
  dashboard: (storeId: number, input: DashboardPeriodInput) =>
    [...storeOperationsDashboardQueryKeys.store(storeId), 'dashboard', ...periodKey(input)] as const,
  campaigns: (storeId: number, input: CampaignMetricInput) =>
    [...storeOperationsDashboardQueryKeys.store(storeId), 'campaigns', ...periodKey(input), normalized(input.campaignKind), normalized(input.status), input.page, input.size] as const,
  couponOutcomes: (storeId: number, input: CouponOutcomeInput) =>
    [...storeOperationsDashboardQueryKeys.store(storeId), 'coupon-outcomes', ...periodKey(input), normalized(input.reason), input.page, input.size] as const,
  inventoryPressure: (storeId: number, input: InventoryPressureInput) =>
    [...storeOperationsDashboardQueryKeys.store(storeId), 'inventory-pressure', ...periodKey(input), input.attentionOnly, input.page, input.size] as const,
  purchaseOutcomes: (storeId: number, input: PurchaseOutcomeInput) =>
    [...storeOperationsDashboardQueryKeys.store(storeId), 'purchase-outcomes', ...periodKey(input), normalized(input.reason), input.page, input.size] as const,
  campaignAudits: (storeId: number, input: CampaignAuditInput) =>
    [...storeOperationsDashboardQueryKeys.store(storeId), 'campaign-audits', ...periodKey(input), normalized(input.campaignKind), normalized(input.command), input.page, input.size] as const,
};

function addPeriod(query: URLSearchParams, input: DashboardPeriodInput) {
  if (input.preset) query.set('preset', input.preset);
  if (normalized(input.from)) query.set('from', input.from!.trim());
  if (normalized(input.to)) query.set('to', input.to!.trim());
}

function addOptional(query: URLSearchParams, name: string, value: string | undefined) {
  const cleanValue = normalized(value);
  if (cleanValue) query.set(name, cleanValue);
}

function pageQuery(input: DashboardPeriodInput & { page: number; size: number }) {
  const query = new URLSearchParams();
  addPeriod(query, input);
  return query;
}

function finishPageQuery(query: URLSearchParams, input: { page: number; size: number }) {
  query.set('page', String(input.page));
  query.set('size', String(input.size));
  return query.toString();
}

export function getStoreOperationsDashboard(storeId: number, input: DashboardPeriodInput) {
  const query = new URLSearchParams();
  addPeriod(query, input);
  const suffix = query.size > 0 ? `?${query.toString()}` : '';
  return api<StoreOperationsDashboard>(`/api/stores/${storeId}/operations/dashboard${suffix}`);
}

export function getStoreCampaignMetrics(storeId: number, input: CampaignMetricInput) {
  const query = pageQuery(input);
  addOptional(query, 'campaignKind', input.campaignKind);
  addOptional(query, 'status', input.status);
  return api<Page<StoreCampaignMetric>>(`/api/stores/${storeId}/operations/campaigns?${finishPageQuery(query, input)}`);
}

export function getStoreCouponOutcomes(storeId: number, input: CouponOutcomeInput) {
  const query = pageQuery(input);
  addOptional(query, 'reason', input.reason);
  return api<Page<StoreCouponOutcome>>(`/api/stores/${storeId}/operations/coupon-outcomes?${finishPageQuery(query, input)}`);
}

export function getStoreInventoryPressure(storeId: number, input: InventoryPressureInput) {
  const query = pageQuery(input);
  query.set('attentionOnly', String(input.attentionOnly));
  return api<Page<StoreInventoryPressure>>(`/api/stores/${storeId}/operations/inventory-pressure?${finishPageQuery(query, input)}`);
}

export function getStorePurchaseOutcomes(storeId: number, input: PurchaseOutcomeInput) {
  const query = pageQuery(input);
  addOptional(query, 'reason', input.reason);
  return api<Page<StorePurchaseOutcome>>(`/api/stores/${storeId}/operations/purchase-outcomes?${finishPageQuery(query, input)}`);
}

export function getStoreCampaignAudits(storeId: number, input: CampaignAuditInput) {
  const query = pageQuery(input);
  addOptional(query, 'campaignKind', input.campaignKind);
  addOptional(query, 'command', input.command);
  return api<Page<StoreCampaignAudit>>(`/api/stores/${storeId}/operations/campaign-audits?${finishPageQuery(query, input)}`);
}
