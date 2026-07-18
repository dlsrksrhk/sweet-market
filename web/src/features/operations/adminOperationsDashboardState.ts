import type { DashboardPeriodInput, DashboardPeriodPreset } from './storeOperationsDashboardApi';

export type AdminDashboardPages = {
  campaigns: number;
  outcomes: number;
  inventory: number;
  audits: number;
};

export type AdminDashboardUrlState = {
  period: DashboardPeriodInput;
  storeId: number | null;
  ownerType: string;
  campaignKind: string;
  campaignStatus: string;
  outcomeReason: string;
  productId: number | null;
  attentionOnly: boolean;
  pages: AdminDashboardPages;
};

const presets: DashboardPeriodPreset[] = ['TODAY', 'LAST_7_DAYS', 'LAST_30_DAYS', 'LAST_90_DAYS'];
const ownerTypes = ['', 'PLATFORM', 'STORE'];
const campaignKinds = ['', 'PROMOTION', 'COUPON'];
const campaignStatuses = ['', 'DRAFT', 'SCHEDULED', 'ACTIVE', 'PAUSED', 'ENDED'];

export function deriveAdminDashboardUrlState(params: URLSearchParams): AdminDashboardUrlState {
  const rawPreset = params.get('preset');
  const preset = presets.includes(rawPreset as DashboardPeriodPreset) ? rawPreset as DashboardPeriodPreset : null;
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  const customPeriodValid = isIsoDate(from) && isIsoDate(to) && validInclusiveDays(from, to);
  return {
    period: preset ? { preset } : customPeriodValid ? { from, to } : { preset: 'LAST_30_DAYS' },
    storeId: positiveInteger(params.get('storeId')),
    ownerType: validOption(params.get('ownerType'), ownerTypes, ''),
    campaignKind: validOption(params.get('campaignKind'), campaignKinds, ''),
    campaignStatus: validOption(params.get('campaignStatus'), campaignStatuses, ''),
    outcomeReason: cleanText(params.get('reason')),
    productId: positiveInteger(params.get('productId')),
    attentionOnly: params.get('attentionOnly') === 'false' ? false : true,
    pages: {
      campaigns: nonNegativeInteger(params.get('campaignPage')),
      outcomes: nonNegativeInteger(params.get('outcomePage')),
      inventory: nonNegativeInteger(params.get('inventoryPage')),
      audits: nonNegativeInteger(params.get('auditPage')),
    },
  };
}

export function toAdminDashboardSearchParams(state: AdminDashboardUrlState) {
  const params = new URLSearchParams();
  if (state.period.preset) params.set('preset', state.period.preset);
  else if (state.period.from && state.period.to) {
    params.set('from', state.period.from);
    params.set('to', state.period.to);
  }
  if (state.storeId !== null) params.set('storeId', String(state.storeId));
  if (state.ownerType) params.set('ownerType', state.ownerType);
  if (state.campaignKind) params.set('campaignKind', state.campaignKind);
  if (state.campaignStatus) params.set('campaignStatus', state.campaignStatus);
  if (state.outcomeReason) params.set('reason', state.outcomeReason);
  if (state.productId !== null) params.set('productId', String(state.productId));
  params.set('attentionOnly', String(state.attentionOnly));
  params.set('campaignPage', String(state.pages.campaigns));
  params.set('outcomePage', String(state.pages.outcomes));
  params.set('inventoryPage', String(state.pages.inventory));
  params.set('auditPage', String(state.pages.audits));
  return params;
}

export function copyAdminDashboardUrlState(state: AdminDashboardUrlState): AdminDashboardUrlState {
  return { ...state, period: { ...state.period }, pages: { ...state.pages } };
}

export function resetAdminDashboardPages(state: AdminDashboardUrlState) {
  state.pages = { campaigns: 0, outcomes: 0, inventory: 0, audits: 0 };
}

function positiveInteger(value: string | null) {
  if (value === null || !/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 1 ? parsed : null;
}

function nonNegativeInteger(value: string | null) {
  if (value === null || !/^\d+$/.test(value)) return 0;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0;
}

function validOption(value: string | null, options: readonly string[], fallback: string) {
  return value !== null && options.includes(value) ? value : fallback;
}

function cleanText(value: string | null) {
  return value?.trim().slice(0, 80) ?? '';
}

function isIsoDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function validInclusiveDays(from: string, to: string) {
  const days = Math.floor((Date.parse(to) - Date.parse(from)) / 86_400_000) + 1;
  return days >= 1 && days <= 90;
}
