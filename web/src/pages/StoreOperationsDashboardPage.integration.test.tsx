// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { StoreOperationsDashboardPage } from './StoreOperationsDashboardPage';

type FetchMode = 'normal' | 'no-stores' | 'overview-error' | 'personal-empty';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('StoreOperationsDashboardPage mounted experience', () => {
  it('공유_URL의_상점_기간_탭_필터_page를_복원하고_잘못된_값은_기본값으로_정규화한다', async () => {
    const fetchMock = installApi('normal');
    renderPage('/me/store/dashboard?storeId=2&preset=LAST_7_DAYS&tab=inventory-pressure&attentionOnly=false&inventoryPage=2');

    expect((await screen.findByLabelText('운영 상점') as HTMLSelectElement).value).toBe('2');
    expect(screen.getByRole('button', { name: '재고 주의' }).getAttribute('aria-current')).toBe('page');
    await waitFor(() => expect(calledUrl(fetchMock, '/inventory-pressure')).toContain('preset=LAST_7_DAYS&attentionOnly=false&page=2&size=20'));

    cleanup();
    vi.unstubAllGlobals();
    const fallbackFetch = installApi('normal');
    renderPage('/me/store/dashboard?storeId=999&preset=BAD&tab=unknown&campaignsPage=-4&campaignStatus=NOPE');

    expect((await screen.findByLabelText('운영 상점') as HTMLSelectElement).value).toBe('1');
    await waitFor(() => expect(screen.getByTestId('location-search').textContent).toContain('storeId=1'));
    expect(screen.getByTestId('location-search').textContent).toContain('preset=LAST_30_DAYS');
    expect(screen.getByTestId('location-search').textContent).toContain('tab=campaigns');
    await waitFor(() => expect(calledUrl(fallbackFetch, '/campaigns')).toContain('status=ACTIVE&page=0&size=20'));
  });

  it('드릴다운별_page를_URL에_독립적으로_보존하고_뒤로가기로_이전상태를_복원한다', async () => {
    const fetchMock = installApi('normal');
    const user = userEvent.setup();
    renderPage('/me/store/dashboard?storeId=1&preset=LAST_30_DAYS&tab=campaigns&campaignsPage=2&inventoryPage=1');

    await waitFor(() => expect(calledUrl(fetchMock, '/campaigns')).toContain('page=2&size=20'));
    await user.click(screen.getByRole('button', { name: '재고 주의' }));
    await waitFor(() => expect(calledUrl(fetchMock, '/inventory-pressure')).toContain('page=1&size=20'));
    await user.click(screen.getByRole('button', { name: '다음' }));

    await waitFor(() => {
      const search = screen.getByTestId('location-search').textContent ?? '';
      expect(search).toContain('campaignsPage=2');
      expect(search).toContain('inventoryPage=2');
    });

    await user.click(screen.getByRole('button', { name: '뒤로' }));
    await waitFor(() => expect(screen.getByTestId('location-search').textContent).toContain('inventoryPage=1'));
  });

  it('상점_기간_필터_변경을_URL에_남기고_90일은_허용하지만_91일은_거부한다', async () => {
    const fetchMock = installApi('normal');
    const user = userEvent.setup();
    renderPage('/me/store/dashboard?storeId=1&preset=LAST_30_DAYS&tab=campaigns');

    await user.selectOptions(await screen.findByLabelText('운영 상점'), '2');
    await waitFor(() => expect(screen.getByTestId('location-search').textContent).toContain('storeId=2'));

    fireEvent.change(screen.getByLabelText('시작일 (KST)'), { target: { value: '2026-01-01' } });
    fireEvent.change(screen.getByLabelText('종료일 (KST)'), { target: { value: '2026-03-31' } });
    await user.click(screen.getByRole('button', { name: '직접 설정 적용' }));
    await waitFor(() => expect(calledUrl(fetchMock, '/dashboard')).toContain('from=2026-01-01&to=2026-03-31'));
    expect(screen.getByTestId('location-search').textContent).toContain('from=2026-01-01');

    fireEvent.change(screen.getByLabelText('종료일 (KST)'), { target: { value: '2026-04-01' } });
    await user.click(screen.getByRole('button', { name: '직접 설정 적용' }));
    expect((await screen.findByRole('alert')).textContent).toContain('조회 기간은 1일 이상 90일 이하여야 합니다.');
    expect(screen.getByTestId('location-search').textContent).not.toContain('to=2026-04-01');
  });

  it('플랫폼과_상점_소유를_표시하고_현재상점_소유행에만_관리링크를_제공한다', async () => {
    installApi('normal');
    const user = userEvent.setup();
    renderPage('/me/store/dashboard?storeId=1&preset=LAST_30_DAYS&tab=campaigns');

    const table = await screen.findByRole('table', { name: '캠페인 성과' });
    expect(within(table).getByText('플랫폼')).toBeTruthy();
    expect(within(table).getByText('달콤 상점 소유')).toBeTruthy();
    expect(within(table).getAllByRole('link', { name: '관리' })).toHaveLength(1);
    expect(table.tagName).toBe('TABLE');
    expect(table.querySelector('thead')).toBeTruthy();
    expect(within(table).getAllByRole('columnheader')).toHaveLength(7);

    await user.click(screen.getByRole('button', { name: '쿠폰 결과' }));
    const couponTable = await screen.findByRole('table', { name: '쿠폰 결과' });
    expect(within(couponTable).getByText('플랫폼')).toBeTruthy();
    expect(within(couponTable).queryAllByRole('link', { name: '관리' })).toHaveLength(0);
    await user.click(screen.getByRole('button', { name: '캠페인 변경' }));
    const auditTable = await screen.findByRole('table', { name: '캠페인 변경 이력' });
    expect(within(auditTable).getByText('플랫폼')).toBeTruthy();
    expect(within(auditTable).queryAllByRole('link', { name: '관리' })).toHaveLength(1);
  });

  it('접근불가_요청실패_개인상점_빈상태를_각각_구분한다', async () => {
    installApi('no-stores');
    renderPage('/me/store/dashboard');
    expect(await screen.findByText('운영 대시보드를 볼 수 있는 상점이 없습니다')).toBeTruthy();

    cleanup();
    vi.unstubAllGlobals();
    installApi('overview-error');
    renderPage('/me/store/dashboard?storeId=1');
    expect(await screen.findByText('운영 요약을 불러오지 못했습니다.')).toBeTruthy();
    expect(await screen.findByRole('table', { name: '캠페인 성과' })).toBeTruthy();

    cleanup();
    vi.unstubAllGlobals();
    installApi('personal-empty');
    renderPage('/me/store/dashboard?storeId=2&tab=campaigns');
    expect(await screen.findByText('개인 상점에는 운영할 상점 캠페인이 없습니다')).toBeTruthy();
    expect((screen.getByLabelText('상태') as HTMLSelectElement).value).toBe('ACTIVE');
  });
});

function renderPage(initialEntry: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/me/store/dashboard" element={<><StoreOperationsDashboardPage /><LocationProbe /></>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function LocationProbe() {
  const location = useLocation();
  const navigate = useNavigate();
  return <><output data-testid="location-search">{location.search}</output><button type="button" onClick={() => navigate(-1)}>뒤로</button></>;
}

function installApi(mode: FetchMode) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith('/api/store-operations')) {
      if (mode === 'no-stores') return json([]);
      return json(stores);
    }
    if (url.includes('/operations/dashboard')) {
      if (mode === 'overview-error') return new Response(JSON.stringify({ code: 'DASHBOARD_FAILED', message: '운영 요약을 불러오지 못했습니다.' }), { status: 500 });
      const storeId = url.includes('/stores/2/') ? 2 : 1;
      return json({ ...dashboard, storeId, storeName: storeId === 2 ? '개인 상점' : '달콤 상점' });
    }
    if (url.includes('/operations/campaigns')) return json(page(mode === 'personal-empty' || url.includes('/stores/2/') ? [] : campaignRows, url));
    if (url.includes('/operations/coupon-outcomes')) return json(page(couponRows, url));
    if (url.includes('/operations/inventory-pressure')) return json(page(inventoryRows, url));
    if (url.includes('/operations/purchase-outcomes')) return json(page(purchaseRows, url));
    if (url.includes('/operations/campaign-audits')) return json(page(auditRows, url));
    throw new Error(`Unexpected request: ${url}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function calledUrl(fetchMock: ReturnType<typeof vi.fn>, route: string) {
  const call = [...fetchMock.mock.calls].reverse().find(([url]) => String(url).includes(route));
  return call ? String(call[0]) : '';
}

function json(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({ data }), { status: 200 }));
}

function page<T>(content: T[], url: string) {
  const number = Number(new URL(url).searchParams.get('page') ?? 0);
  return { content, totalElements: content.length ? 60 : 0, totalPages: content.length ? 3 : 0, size: 20, number, first: number === 0, last: number === 2, empty: content.length === 0 };
}

const stores = [
  { storeId: 1, type: 'BUSINESS', publicName: '달콤 상점', status: 'ACTIVE', role: 'OWNER' },
  { storeId: 2, type: 'PERSONAL', publicName: '개인 상점', status: 'ACTIVE', role: 'OWNER' },
];

const zeroDiscounts = { applied: 0, realized: 0, canceled: 0, refunded: 0 };
const dashboard = {
  storeId: 1, storeName: '달콤 상점',
  period: { from: '2026-07-01', to: '2026-07-17', fromInclusive: '2026-06-30T15:00:00Z', toExclusive: '2026-07-17T15:00:00Z', timezone: 'Asia/Seoul' },
  generatedAt: '2026-07-17T00:05:10Z', projectionUpdatedAt: '2026-07-17T00:05:00Z', projectionLagSeconds: 10, trackingStartedAt: '2026-05-01T00:00:00Z',
  claimSuccessCount: 1, redemptionSuccessCount: 1, orderSuccessCount: 1, purchaseFailureCount: 0,
  promotionDiscounts: zeroDiscounts, couponDiscounts: zeroDiscounts, lowStockCount: 0, soldOutTransitionCount: 0, leadingFailureReasons: [],
};

const campaignBase = {
  latestBucketStart: '2026-07-17T00:00:00Z', campaignKind: 'COUPON', status: 'ACTIVE',
  claimSuccessCount: 1, claimFailureCount: 0, redemptionSuccessCount: 1, redemptionFailureCount: 0,
  orderSuccessCount: 1, purchaseFailureCount: 0, promotionDiscounts: zeroDiscounts, couponDiscounts: zeroDiscounts,
};
const campaignRows = [
  { ...campaignBase, id: 'platform', campaignId: 11, ownerType: 'PLATFORM', ownerStoreId: null },
  { ...campaignBase, id: 'owned', campaignId: 12, ownerType: 'STORE', ownerStoreId: 1 },
  { ...campaignBase, id: 'foreign', campaignId: 13, ownerType: 'STORE', ownerStoreId: 999 },
];
const couponRows = [{ id: 'coupon-platform', latestBucketStart: '2026-07-17T00:00:00Z', campaignId: 11, ownerType: 'PLATFORM', ownerStoreId: null, reason: 'NONE', claimSuccessCount: 1, claimFailureCount: 0, redemptionSuccessCount: 1, redemptionFailureCount: 0, discounts: zeroDiscounts }];
const inventoryRows = [{ productId: 1, salesPolicy: 'STOCK_MANAGED', availableQuantity: 2, lowStock: true, lastSoldOutAt: null, reservationFailureCount: 1, lastReservationFailureAt: '2026-07-17T00:00:00Z', updatedAt: '2026-07-17T00:00:00Z' }];
const purchaseRows = [{ id: 'purchase', latestBucketStart: '2026-07-17T00:00:00Z', reason: 'NONE', orderSuccessCount: 1, purchaseFailureCount: 0, reservationFailureCount: 0 }];
const auditBase = { id: 1, eventId: '00000000-0000-0000-0000-000000000001', campaignKind: 'COUPON', campaignId: 11, actorMemberId: 1, command: 'CREATE', occurredAt: '2026-07-17T00:00:00Z', aggregateVersion: 1, beforeSummary: null, afterSummary: '{}' };
const auditRows = [
  { ...auditBase, ownerType: 'PLATFORM', ownerStoreId: null },
  { ...auditBase, id: 2, eventId: '00000000-0000-0000-0000-000000000002', campaignId: 12, ownerType: 'STORE', ownerStoreId: 1 },
];
