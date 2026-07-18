import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  adminOperationsDashboardQueryKeys,
  getAdminOperationsDashboard,
  getAdminOutcomeMetrics,
  getPerformanceMeasurement,
  getPerformanceMeasurements,
  rebuildOperationalProjections,
  retryOperationalEvent,
} from './adminOperationsDashboardApi';

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ data: null }), { status: 200 })));
  vi.stubGlobal('fetch', fetchMock);
  vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('adminOperationsDashboardApi', () => {
  it('관리자_운영요약은_기간과_상점필터를_전송한다', async () => {
    await getAdminOperationsDashboard({ preset: 'LAST_30_DAYS', storeId: 7 });

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/admin/operations-dashboard?preset=LAST_30_DAYS&storeId=7',
      expect.anything(),
    );
  });

  it('성과조회는_모든_필터와_page를_전송하고_query_key에_포함한다', async () => {
    const input = {
      preset: 'LAST_7_DAYS' as const,
      storeId: 7,
      ownerType: 'STORE',
      campaignKind: 'COUPON',
      productId: 31,
      reason: 'EXHAUSTED',
      page: 2,
      size: 20,
    };

    await getAdminOutcomeMetrics(input);

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/admin/operations-dashboard/outcomes?preset=LAST_7_DAYS&storeId=7&ownerType=STORE&campaignKind=COUPON&productId=31&reason=EXHAUSTED&page=2&size=20',
      expect.anything(),
    );
    expect(adminOperationsDashboardQueryKeys.outcomes(input)).toContain(31);
    expect(adminOperationsDashboardQueryKeys.outcomes(input)).toContain('EXHAUSTED');
    expect(adminOperationsDashboardQueryKeys.outcomes(input)).toContain(2);
  });

  it('성능측정_목록과_상세를_조회한다', async () => {
    await getPerformanceMeasurements(1, 20);
    await getPerformanceMeasurement(33);

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      'http://localhost:8080/api/admin/performance-measurements?page=1&size=20',
      expect.anything(),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      'http://localhost:8080/api/admin/performance-measurements/33',
      expect.anything(),
    );
  });

  it('DEAD_event_재시도는_payload를_전송하지_않는다', async () => {
    await retryOperationalEvent('4f8dbfd6-cb42-4a94-9fd3-e6b329f617dc');

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/admin/operational-events/4f8dbfd6-cb42-4a94-9fd3-e6b329f617dc/retry',
      expect.objectContaining({ method: 'POST', body: undefined }),
    );
  });

  it('프로젝션_재구축은_generation이나_payload를_전송하지_않는다', async () => {
    await rebuildOperationalProjections();

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/admin/operational-projections/rebuild',
      expect.objectContaining({ method: 'POST', body: undefined }),
    );
  });
});
