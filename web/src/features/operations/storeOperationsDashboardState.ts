import type { OperableStore } from '../stores/storeOperationsApi';
import type { DashboardPeriodInput, DashboardPeriodPreset } from './storeOperationsDashboardApi';

export type DrilldownTab = 'campaigns' | 'coupon-outcomes' | 'inventory-pressure' | 'purchase-outcomes' | 'campaign-audits';

export type DashboardPages = Record<DrilldownTab, number>;

export type DashboardUrlState = {
  storeId: number | null;
  period: DashboardPeriodInput;
  tab: DrilldownTab;
  campaignKind: string;
  campaignStatus: string;
  couponReason: string;
  attentionOnly: boolean;
  purchaseReason: string;
  auditKind: string;
  auditCommand: string;
  pages: DashboardPages;
};

export const pageParamByTab: Record<DrilldownTab, string> = {
  campaigns: 'campaignsPage',
  'coupon-outcomes': 'couponPage',
  'inventory-pressure': 'inventoryPage',
  'purchase-outcomes': 'purchasePage',
  'campaign-audits': 'auditsPage',
};

const presets: DashboardPeriodPreset[] = ['TODAY', 'LAST_7_DAYS', 'LAST_30_DAYS', 'LAST_90_DAYS'];
const tabs: DrilldownTab[] = ['campaigns', 'coupon-outcomes', 'inventory-pressure', 'purchase-outcomes', 'campaign-audits'];
const kinds = ['', 'PROMOTION', 'COUPON'];
const campaignStatuses = ['DRAFT', 'SCHEDULED', 'ACTIVE', 'PAUSED', 'ENDED'];

export function deriveDashboardUrlState(searchParams: URLSearchParams, stores: OperableStore[]): DashboardUrlState {
  const requestedStoreId = positiveInteger(searchParams.get('storeId'));
  const storeId = stores.some((store) => store.storeId === requestedStoreId) ? requestedStoreId : stores[0]?.storeId ?? null;
  const rawPreset = searchParams.get('preset');
  const preset = presets.includes(rawPreset as DashboardPeriodPreset) ? rawPreset as DashboardPeriodPreset : null;
  const from = searchParams.get('from') ?? '';
  const to = searchParams.get('to') ?? '';
  const customPeriodValid = isIsoDate(from) && isIsoDate(to) && validInclusiveDays(from, to);
  const rawTab = searchParams.get('tab');
  const tab = tabs.includes(rawTab as DrilldownTab) ? rawTab as DrilldownTab : 'campaigns';

  return {
    storeId,
    period: preset ? { preset } : customPeriodValid ? { from, to } : { preset: 'LAST_30_DAYS' },
    tab,
    campaignKind: validOption(searchParams.get('campaignKind'), kinds, ''),
    campaignStatus: validOption(searchParams.get('campaignStatus'), campaignStatuses, 'ACTIVE'),
    couponReason: cleanText(searchParams.get('couponReason')),
    attentionOnly: searchParams.get('attentionOnly') === 'false' ? false : true,
    purchaseReason: cleanText(searchParams.get('purchaseReason')),
    auditKind: validOption(searchParams.get('auditKind'), kinds, ''),
    auditCommand: cleanText(searchParams.get('auditCommand')),
    pages: {
      campaigns: nonNegativeInteger(searchParams.get('campaignsPage')),
      'coupon-outcomes': nonNegativeInteger(searchParams.get('couponPage')),
      'inventory-pressure': nonNegativeInteger(searchParams.get('inventoryPage')),
      'purchase-outcomes': nonNegativeInteger(searchParams.get('purchasePage')),
      'campaign-audits': nonNegativeInteger(searchParams.get('auditsPage')),
    },
  };
}

export function toDashboardSearchParams(state: DashboardUrlState) {
  const params = new URLSearchParams();
  if (state.storeId !== null) params.set('storeId', String(state.storeId));
  if (state.period.preset) {
    params.set('preset', state.period.preset);
  } else if (state.period.from && state.period.to) {
    params.set('from', state.period.from);
    params.set('to', state.period.to);
  }
  params.set('tab', state.tab);
  if (state.campaignKind) params.set('campaignKind', state.campaignKind);
  params.set('campaignStatus', state.campaignStatus);
  if (state.couponReason) params.set('couponReason', state.couponReason);
  params.set('attentionOnly', String(state.attentionOnly));
  if (state.purchaseReason) params.set('purchaseReason', state.purchaseReason);
  if (state.auditKind) params.set('auditKind', state.auditKind);
  if (state.auditCommand) params.set('auditCommand', state.auditCommand);
  params.set('campaignsPage', String(state.pages.campaigns));
  params.set('couponPage', String(state.pages['coupon-outcomes']));
  params.set('inventoryPage', String(state.pages['inventory-pressure']));
  params.set('purchasePage', String(state.pages['purchase-outcomes']));
  params.set('auditsPage', String(state.pages['campaign-audits']));
  return params;
}

export function copyDashboardUrlState(state: DashboardUrlState): DashboardUrlState {
  return { ...state, period: { ...state.period }, pages: { ...state.pages } };
}

export function resetAllPages(state: DashboardUrlState) {
  tabs.forEach((tab) => { state.pages[tab] = 0; });
}

function positiveInteger(value: string | null) {
  const number = Number(value);
  return Number.isInteger(number) && number > 0 ? number : null;
}

function nonNegativeInteger(value: string | null) {
  const number = Number(value);
  return Number.isInteger(number) && number >= 0 ? number : 0;
}

function validOption(value: string | null, options: string[], fallback: string) {
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
