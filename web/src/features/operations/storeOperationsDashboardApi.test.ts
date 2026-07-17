import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getStoreCampaignAudits,
  getStoreCampaignMetrics,
  getStoreCouponOutcomes,
  getStoreInventoryPressure,
  getStoreOperationsDashboard,
  getStorePurchaseOutcomes,
  storeOperationsDashboardQueryKeys,
} from './storeOperationsDashboardApi';

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ data: { content: [] } }))));
  vi.stubGlobal('fetch', fetchMock);
  vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

describe('storeOperationsDashboardApi', () => {
  it('상점과_기간을_포함해_운영요약을_조회한다', async () => {
    await getStoreOperationsDashboard(7, { preset: 'LAST_30_DAYS' });

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/stores/7/operations/dashboard?preset=LAST_30_DAYS',
      expect.anything(),
    );
  });

  it('드릴다운은_상점과_필터와_page를_보존한다', async () => {
    await getStoreInventoryPressure(7, {
      preset: 'LAST_7_DAYS', attentionOnly: true, page: 2, size: 20,
    });

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/stores/7/operations/inventory-pressure?preset=LAST_7_DAYS&attentionOnly=true&page=2&size=20',
      expect.anything(),
    );
  });

  it('사용자지정_KST_기간과_각_드릴다운_필터를_전달한다', async () => {
    const period = { from: '2026-07-01', to: '2026-07-17' } as const;

    await getStoreCampaignMetrics(3, { ...period, campaignKind: 'COUPON', status: 'PAUSED', page: 0, size: 10 });
    await getStoreCouponOutcomes(3, { ...period, reason: 'EXHAUSTED', page: 1, size: 10 });
    await getStorePurchaseOutcomes(3, { ...period, reason: 'PAYMENT_FAILED', page: 2, size: 10 });
    await getStoreCampaignAudits(3, { ...period, campaignKind: 'PROMOTION', command: 'END', page: 3, size: 10 });

    expect(fetchMock).toHaveBeenNthCalledWith(1, 'http://localhost:8080/api/stores/3/operations/campaigns?from=2026-07-01&to=2026-07-17&campaignKind=COUPON&status=PAUSED&page=0&size=10', expect.anything());
    expect(fetchMock).toHaveBeenNthCalledWith(2, 'http://localhost:8080/api/stores/3/operations/coupon-outcomes?from=2026-07-01&to=2026-07-17&reason=EXHAUSTED&page=1&size=10', expect.anything());
    expect(fetchMock).toHaveBeenNthCalledWith(3, 'http://localhost:8080/api/stores/3/operations/purchase-outcomes?from=2026-07-01&to=2026-07-17&reason=PAYMENT_FAILED&page=2&size=10', expect.anything());
    expect(fetchMock).toHaveBeenNthCalledWith(4, 'http://localhost:8080/api/stores/3/operations/campaign-audits?from=2026-07-01&to=2026-07-17&campaignKind=PROMOTION&command=END&page=3&size=10', expect.anything());
  });

  it('비어있는_선택필터는_query_string과_query_key에서_제외한다', async () => {
    const input = {
      preset: 'TODAY' as const,
      campaignKind: undefined,
      status: '',
      page: 0,
      size: 20,
    };

    await getStoreCampaignMetrics(9, input);

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/stores/9/operations/campaigns?preset=TODAY&page=0&size=20',
      expect.anything(),
    );
    expect(storeOperationsDashboardQueryKeys.campaigns(9, input)).toEqual([
      'store-operations-dashboard', 9, 'campaigns', 'TODAY', null, null, null, null, 0, 20,
    ]);
  });

  it('query_key는_상점_기간_필터_page_size를_모두_구분한다', () => {
    const first = storeOperationsDashboardQueryKeys.inventoryPressure(1, {
      from: '2026-07-01', to: '2026-07-17', attentionOnly: true, page: 0, size: 20,
    });
    const second = storeOperationsDashboardQueryKeys.inventoryPressure(2, {
      from: '2026-07-02', to: '2026-07-17', attentionOnly: false, page: 1, size: 50,
    });

    expect(first).not.toEqual(second);
    expect(first).toEqual([
      'store-operations-dashboard', 1, 'inventory-pressure', null, '2026-07-01', '2026-07-17', true, 0, 20,
    ]);
  });
});
