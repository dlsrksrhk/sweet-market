import { describe, expect, it } from 'vitest';
import {
  deriveAdminDashboardUrlState,
  resetAdminDashboardPages,
  toAdminDashboardSearchParams,
} from './adminOperationsDashboardState';

describe('adminOperationsDashboardState', () => {
  it('공유URL의_기간_필터와_네페이지를_복원하고_정규화한다', () => {
    const state = deriveAdminDashboardUrlState(new URLSearchParams(
      'from=2026-05-01&to=2026-05-30&storeId=7&ownerType=STORE&campaignKind=COUPON&campaignStatus=ACTIVE&reason=PAYMENT_FAILED&productId=31&attentionOnly=false&campaignPage=1&outcomePage=2&inventoryPage=3&auditPage=4',
    ));

    expect(state).toMatchObject({
      period: { from: '2026-05-01', to: '2026-05-30' }, storeId: 7,
      ownerType: 'STORE', campaignKind: 'COUPON', campaignStatus: 'ACTIVE',
      outcomeReason: 'PAYMENT_FAILED', productId: 31, attentionOnly: false,
      pages: { campaigns: 1, outcomes: 2, inventory: 3, audits: 4 },
    });
    expect(toAdminDashboardSearchParams(state).toString()).toBe(
      'from=2026-05-01&to=2026-05-30&storeId=7&ownerType=STORE&campaignKind=COUPON&campaignStatus=ACTIVE&reason=PAYMENT_FAILED&productId=31&attentionOnly=false&campaignPage=1&outcomePage=2&inventoryPage=3&auditPage=4',
    );
  });

  it('잘못된_URL값은_안전한_기본값으로_정규화한다', () => {
    const state = deriveAdminDashboardUrlState(new URLSearchParams(
      'preset=FOREVER&from=bad&to=2026-99-99&storeId=-1&ownerType=HACK&campaignKind=SALE&campaignStatus=UNKNOWN&productId=1.5&attentionOnly=yes&campaignPage=-1&outcomePage=NaN',
    ));

    expect(state).toEqual({
      period: { preset: 'LAST_30_DAYS' }, storeId: null, ownerType: '', campaignKind: '',
      campaignStatus: '', outcomeReason: '', productId: null, attentionOnly: true,
      pages: { campaigns: 0, outcomes: 0, inventory: 0, audits: 0 },
    });
    expect(toAdminDashboardSearchParams(state).toString()).toBe(
      'preset=LAST_30_DAYS&attentionOnly=true&campaignPage=0&outcomePage=0&inventoryPage=0&auditPage=0',
    );
  });

  it('공통의존조건이_바뀌면_네페이지를_동시에_초기화한다', () => {
    const state = deriveAdminDashboardUrlState(new URLSearchParams(
      'preset=LAST_7_DAYS&campaignPage=1&outcomePage=2&inventoryPage=3&auditPage=4',
    ));

    resetAdminDashboardPages(state);

    expect(state.pages).toEqual({ campaigns: 0, outcomes: 0, inventory: 0, audits: 0 });
  });
});
