// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { AdminOperationsDashboardPage } from './AdminOperationsDashboardPage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('AdminOperationsDashboardPage', () => {
  it('관리자_운영섹션을_순서대로_보이고_캠페인에_안전한_검사링크만_제공한다', async () => {
    const fetchMock = installApi();
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole('heading', { name: '플랫폼 운영 요약' });
    const text = document.body.textContent ?? '';
    expect(text.indexOf('플랫폼 운영 요약')).toBeLessThan(text.indexOf('캠페인·결과 상세'));
    expect(text.indexOf('캠페인·결과 상세')).toBeLessThan(text.indexOf('재고 압력'));
    expect(text.indexOf('재고 압력')).toBeLessThan(text.indexOf('캠페인 감사'));
    expect(text.indexOf('캠페인 감사')).toBeLessThan(text.indexOf('성능 측정'));
    expect(text.indexOf('성능 측정')).toBeLessThan(text.indexOf('프로젝션 상태와 복구'));

    const table = await screen.findByRole('table', { name: '관리자 캠페인 성과' });
    expect(within(table).getByRole('link', { name: '플랫폼 쿠폰 보기' }).getAttribute('href')).toBe('/admin/coupons');
    expect(within(table).getByRole('link', { name: '상점 캠페인 검사' }).getAttribute('href')).toContain('/admin/dashboard?storeId=7');
    expect(within(table).queryByRole('button', { name: /중지|종료|재개/ })).toBeNull();
    await user.click(within(table).getByRole('link', { name: '상점 캠페인 검사' }));
    await waitFor(() => expect(called(fetchMock, '/operations-dashboard?').some(([url]) => String(url).includes('storeId=7'))).toBe(true));
    expect(await screen.findByText('성능 측정 전')).toBeTruthy();
    expect(screen.getByText(/collect-m30-measurement\.ps1/)).toBeTruthy();
  });

  it('DEAD_재시도와_재구축은_명시적확인후에만_payload없이_요청한다', async () => {
    const fetchMock = installApi();
    const user = userEvent.setup();
    const confirmMock = vi.fn().mockReturnValueOnce(false).mockReturnValueOnce(true).mockReturnValueOnce(true);
    vi.stubGlobal('confirm', confirmMock);
    renderPage();

    const retryButton = await screen.findByRole('button', { name: '재시도' });
    await user.click(retryButton);
    expect(called(fetchMock, '/retry')).toHaveLength(0);
    await user.click(retryButton);
    await waitFor(() => expect(called(fetchMock, '/retry')).toHaveLength(1));
    expect((called(fetchMock, '/retry')[0][1] as RequestInit).body).toBeUndefined();

    await user.click(screen.getByRole('button', { name: '프로젝션 재구축' }));
    await waitFor(() => expect(called(fetchMock, '/operational-projections/rebuild')).toHaveLength(1));
    expect((called(fetchMock, '/operational-projections/rebuild')[0][1] as RequestInit).body).toBeUndefined();
    expect(confirmMock).toHaveBeenCalledTimes(3);
  });

  it('요약조회가_실패해도_DEAD_조회와_재시도와_재구축은_독립적으로_동작한다', async () => {
    const fetchMock = installApi({ overviewError: true });
    const user = userEvent.setup();
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
    renderPage();

    expect(await screen.findByText('플랫폼 운영 요약을 불러오지 못했습니다.')).toBeTruthy();
    expect(await screen.findByText('프로젝터 상태를 확인할 수 없습니다')).toBeTruthy();
    const retry = await screen.findByRole('button', { name: '재시도' });
    await user.click(retry);
    await waitFor(() => expect(called(fetchMock, '/retry')).toHaveLength(1));
    await user.click(screen.getByRole('button', { name: '프로젝션 재구축' }));
    await waitFor(() => expect(called(fetchMock, '/operational-projections/rebuild')).toHaveLength(1));
  });

  it('공유URL의_필터와_페이지를_복원하고_변경과_뒤로가기를_URL과_동기화한다', async () => {
    const fetchMock = installApi();
    const user = userEvent.setup();
    renderPage('/admin/dashboard?preset=LAST_7_DAYS&storeId=7&ownerType=STORE&campaignKind=COUPON&campaignStatus=ACTIVE&reason=PAYMENT_FAILED&productId=3&attentionOnly=false&campaignPage=1&outcomePage=2&inventoryPage=1&auditPage=2');

    await waitFor(() => expect(called(fetchMock, '/operations-dashboard/campaigns').some(([url]) => String(url).includes('page=1'))).toBe(true));
    expect((screen.getByLabelText('소유 유형') as HTMLSelectElement).value).toBe('STORE');
    expect((screen.getByLabelText('상품 ID') as HTMLInputElement).value).toBe('3');
    expect(screen.getByTestId('location-search').textContent).toContain('outcomePage=2');

    await user.selectOptions(screen.getByLabelText('캠페인 상태'), 'PAUSED');
    await waitFor(() => expect(screen.getByTestId('location-search').textContent).toContain('campaignStatus=PAUSED'));
    expect(screen.getByTestId('location-search').textContent).toContain('campaignPage=0');
    await user.click(screen.getByRole('button', { name: '뒤로' }));
    await waitFor(() => expect((screen.getByLabelText('캠페인 상태') as HTMLSelectElement).value).toBe('ACTIVE'));
    expect(screen.getByTestId('location-search').textContent).toContain('campaignPage=1');
  });

  it('잘못된_상품필터는_검증오류를_보이고_결과와_재고쿼리를_비활성화한다', async () => {
    const fetchMock = installApi();
    renderPage();
    await screen.findByRole('table', { name: '관리자 구매 발급 결과' });
    const outcomeCalls = called(fetchMock, '/operations-dashboard/outcomes').length;
    const inventoryCalls = called(fetchMock, '/operations-dashboard/inventory-pressure').length;

    for (const invalid of ['0', '-1', '1.5', 'abc']) {
      fireEvent.change(screen.getByLabelText('상품 ID'), { target: { value: invalid } });
      expect((await screen.findByRole('alert')).textContent).toContain('상품 ID는 1 이상의 정수여야 합니다.');
    }
    await new Promise((resolve) => setTimeout(resolve, 25));
    expect(called(fetchMock, '/operations-dashboard/outcomes')).toHaveLength(outcomeCalls);
    expect(called(fetchMock, '/operations-dashboard/inventory-pressure')).toHaveLength(inventoryCalls);
  });

  it('선택기간이_추적시작전이면_0이_아니라_측정전으로_표시하고_각_집계에_근거를_표시한다', async () => {
    installApi({ preTracking: true });
    renderPage('/admin/dashboard?from=2026-01-01&to=2026-01-31');

    expect((await screen.findAllByText('측정 전')).length).toBeGreaterThan(1);
    expect(screen.queryByText('10건')).toBeNull();
    expect(screen.getAllByText(/집계 기준.*2026-01-01.*2026-01-31.*생성/).length).toBeGreaterThanOrEqual(4);
  });

  it('재시도는_진행상태를_표시하고_성공후_운영과_DEAD_query를_무효화하며_충돌을_표시한다', async () => {
    let releaseRetry!: (response: Response) => void;
    const retryResponse = new Promise<Response>((resolve) => { releaseRetry = resolve; });
    const fetchMock = installApi({ retryResponse });
    const user = userEvent.setup();
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
    renderPage();

    await user.click(await screen.findByRole('button', { name: '재시도' }));
    expect((await screen.findByRole('button', { name: '재시도 중…' }) as HTMLButtonElement).disabled).toBe(true);
    releaseRetry(await jsonResponse(null));
    expect(await screen.findByText('DEAD event를 재시도 대기열로 이동했습니다.')).toBeTruthy();
    await waitFor(() => expect(called(fetchMock, '/operational-events/dead').length).toBeGreaterThan(1));
    await waitFor(() => expect(called(fetchMock, '/operations-dashboard?').length).toBeGreaterThan(1));

    cleanup();
    vi.unstubAllGlobals();
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
    installApi({ retryConflict: true });
    renderPage();
    await userEvent.setup().click(await screen.findByRole('button', { name: '재시도' }));
    expect(await screen.findByText('이미 처리 중인 event입니다.')).toBeTruthy();
  });

  it('DEAD와_성능_page와_선택run을_URL에서_복원하고_page변경을_URL에_기록한다', async () => {
    const fetchMock = installApi({ performanceAvailable: true });
    const user = userEvent.setup();
    renderPage('/admin/dashboard?deadPage=1&performancePage=1&performanceRun=33');

    await waitFor(() => expect(called(fetchMock, '/operational-events/dead').some(([url]) => String(url).includes('page=1'))).toBe(true));
    await waitFor(() => expect(called(fetchMock, '/performance-measurements?page=1').length).toBeGreaterThan(0));
    await waitFor(() => expect(called(fetchMock, '/performance-measurements/33').length).toBeGreaterThan(0));
    expect(screen.getByTestId('location-search').textContent).toContain('performanceRun=33');

    const deadNavigation = screen.getByRole('navigation', { name: 'DEAD event 페이지 이동' });
    await user.click(within(deadNavigation).getByRole('button', { name: '다음' }));
    await waitFor(() => expect(screen.getByTestId('location-search').textContent).toContain('deadPage=2'));
    const performanceNavigation = screen.getByRole('navigation', { name: '성능 측정 목록 페이지 이동' });
    await user.click(within(performanceNavigation).getByRole('button', { name: '다음' }));
    await waitFor(() => expect(screen.getByTestId('location-search').textContent).toContain('performancePage=2'));
  });

  it('overview가_확정되기전에는_상세query와_측정값해석을_보류하고_추적전이면_조회하지_않는다', async () => {
    let releaseOverview!: (response: Response) => void;
    const overviewResponse = new Promise<Response>((resolve) => { releaseOverview = resolve; });
    const fetchMock = installApi({ overviewResponse });
    renderPage('/admin/dashboard?from=2026-01-01&to=2026-01-31');

    expect((await screen.findAllByText('추적 범위를 확인하고 있습니다.')).length).toBeGreaterThan(1);
    expect(called(fetchMock, '/operations-dashboard/campaigns')).toHaveLength(0);
    expect(called(fetchMock, '/operations-dashboard/outcomes')).toHaveLength(0);
    releaseOverview(await jsonResponse({ ...dashboard, trackingStartedAt: '2026-02-01T00:00:00Z', period: { ...dashboard.period, from: '2026-01-01', to: '2026-01-31', fromInclusive: '2025-12-31T15:00:00Z', toExclusive: '2026-01-31T15:00:00Z' } }));

    expect((await screen.findAllByText('측정 전')).length).toBeGreaterThan(1);
    expect(called(fetchMock, '/operations-dashboard/campaigns')).toHaveLength(0);
    expect(called(fetchMock, '/operations-dashboard/outcomes')).toHaveLength(0);
  });

  it('재구축은_진행중_비활성화하고_성공후_query를_무효화하며_BUILDING충돌을_표시한다', async () => {
    let releaseRebuild!: (response: Response) => void;
    const rebuildResponse = new Promise<Response>((resolve) => { releaseRebuild = resolve; });
    const fetchMock = installApi({ rebuildResponse });
    const user = userEvent.setup();
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
    renderPage();

    await user.click(await screen.findByRole('button', { name: '프로젝션 재구축' }));
    expect((await screen.findByRole('button', { name: '재구축 중…' }) as HTMLButtonElement).disabled).toBe(true);
    releaseRebuild(await jsonResponse({ generationId: 3, status: 'ACTIVE', cutoff: '2026-07-17T00:00:00Z', activatedAt: '2026-07-17T00:01:00Z' }));
    expect(await screen.findByText('프로젝션 재구축 generation #3이 활성화되었습니다.')).toBeTruthy();
    await waitFor(() => expect(called(fetchMock, '/operations-dashboard?').length).toBeGreaterThan(1));
    await waitFor(() => expect(called(fetchMock, '/operational-events/dead').length).toBeGreaterThan(1));

    cleanup();
    vi.unstubAllGlobals();
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
    installApi({ rebuildConflict: true });
    renderPage();
    await userEvent.setup().click(await screen.findByRole('button', { name: '프로젝션 재구축' }));
    expect(await screen.findByText('이미 BUILDING generation이 있습니다.')).toBeTruthy();
  });
});

function renderPage(initialEntry = '/admin/dashboard') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}><MemoryRouter initialEntries={[initialEntry]}><Routes><Route path="/admin/dashboard" element={<><AdminOperationsDashboardPage /><LocationProbe /></>} /></Routes></MemoryRouter></QueryClientProvider>);
}

function LocationProbe() {
  const location = useLocation();
  const navigate = useNavigate();
  return <><output data-testid="location-search">{location.search}</output><button type="button" onClick={() => navigate(-1)}>뒤로</button></>;
}

function installApi(options: { overviewError?: boolean; overviewResponse?: Promise<Response>; preTracking?: boolean; retryResponse?: Promise<Response>; retryConflict?: boolean; rebuildResponse?: Promise<Response>; rebuildConflict?: boolean; performanceAvailable?: boolean } = {}) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes('/operations-dashboard/campaigns')) return json(page(campaigns, url));
    if (url.includes('/operations-dashboard/outcomes')) return json(page(outcomes, url));
    if (url.includes('/operations-dashboard/inventory-pressure')) return json(page(inventory, url));
    if (url.includes('/operations-dashboard/audits')) return json(page(audits, url));
    if (url.includes('/operations-dashboard')) {
      if (options.overviewResponse) return options.overviewResponse;
      if (options.overviewError) return new Response(JSON.stringify({ code: 'OVERVIEW_FAILED', message: '플랫폼 운영 요약을 불러오지 못했습니다.' }), { status: 500 });
      return json(options.preTracking ? { ...dashboard, trackingStartedAt: '2026-02-01T00:00:00Z', period: { ...dashboard.period, from: '2026-01-01', to: '2026-01-31', fromInclusive: '2025-12-31T15:00:00Z', toExclusive: '2026-01-31T15:00:00Z' } } : dashboard);
    }
    if (url.includes('/performance-measurements/')) return json(performanceMeasurement);
    if (url.includes('/performance-measurements')) return json(page(options.performanceAvailable ? [performanceMeasurement] : [], url));
    if (url.includes('/operational-events/dead')) return json(page(deadEvents));
    if (url.includes('/operational-events/') && url.endsWith('/retry')) {
      if (options.retryResponse) return options.retryResponse;
      if (options.retryConflict) return new Response(JSON.stringify({ code: 'CONFLICT', message: '이미 처리 중인 event입니다.' }), { status: 409 });
      return json(null);
    }
    if (url.includes('/operational-projections/rebuild')) {
      if (options.rebuildResponse) return options.rebuildResponse;
      if (options.rebuildConflict) return new Response(JSON.stringify({ code: 'CONFLICT', message: '이미 BUILDING generation이 있습니다.' }), { status: 409 });
      return json({ generationId: 2, status: 'ACTIVE', cutoff: '2026-07-17T00:00:00Z', activatedAt: '2026-07-17T00:01:00Z' });
    }
    throw new Error(`Unexpected request: ${url}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function called(fetchMock: ReturnType<typeof vi.fn>, fragment: string) {
  return fetchMock.mock.calls.filter(([url]) => String(url).includes(fragment));
}

function json(data: unknown) { return jsonResponse(data); }
function jsonResponse(data: unknown) { return Promise.resolve(new Response(JSON.stringify({ data }), { status: 200 })); }
function page<T>(content: T[], url?: string) { const number = url ? Number(new URL(url).searchParams.get('page') ?? 0) : 0; return { content, totalElements: content.length ? 60 : 0, totalPages: content.length ? 3 : 0, size: 20, number, first: number === 0, last: number === 2, empty: content.length === 0 }; }

const zeroDiscounts = { applied: 0, realized: 0, canceled: 0, refunded: 0 };
const health = { pendingCount: 1, retryCount: 0, deadCount: 1, oldestUnprocessedAt: '2026-07-17T00:00:00Z', projectionLagSeconds: 10, projectionUpdatedAt: '2026-07-17T00:05:00Z' };
const dashboard = { storeId: null, period: { from: '2026-07-01', to: '2026-07-17', fromInclusive: '2026-06-30T15:00:00Z', toExclusive: '2026-07-17T15:00:00Z', timezone: 'Asia/Seoul' }, generatedAt: '2026-07-17T00:05:10Z', projectionUpdatedAt: '2026-07-17T00:05:00Z', projectionLagSeconds: 10, trackingStartedAt: '2026-05-01T00:00:00Z', claimSuccessCount: 10, redemptionSuccessCount: 8, orderSuccessCount: 7, purchaseFailureCount: 1, promotionDiscounts: zeroDiscounts, couponDiscounts: zeroDiscounts, lowStockCount: 1, soldOutTransitionCount: 1, auditCount: 2, leadingFailureReasons: [], health };
const campaignBase = { latestBucketStart: '2026-07-17T00:00:00Z', campaignKind: 'COUPON', status: 'ACTIVE', claimSuccessCount: 1, claimFailureCount: 0, redemptionSuccessCount: 1, redemptionFailureCount: 0, orderSuccessCount: 1, purchaseFailureCount: 0, promotionDiscounts: zeroDiscounts, couponDiscounts: zeroDiscounts };
const campaigns = [{ ...campaignBase, id: 'platform', campaignId: 1, ownerType: 'PLATFORM', ownerStoreId: null }, { ...campaignBase, id: 'store', campaignId: 2, ownerType: 'STORE', ownerStoreId: 7 }];
const outcomes = [{ id: 'outcome', outcomeType: 'PURCHASE', latestBucketStart: '2026-07-17T00:00:00Z', storeId: 7, campaignKind: 'COUPON', campaignId: 2, ownerType: 'STORE', ownerStoreId: 7, productId: 3, reason: 'PAYMENT_FAILED', successCount: 1, failureCount: 1, reservationFailureCount: 0 }];
const inventory = [{ productId: 3, salesPolicy: 'STOCK_MANAGED', availableQuantity: 1, lowStock: true, lastSoldOutAt: null, reservationFailureCount: 1, lastReservationFailureAt: '2026-07-17T00:00:00Z', updatedAt: '2026-07-17T00:00:00Z' }];
const audits = [{ id: 1, eventId: '00000000-0000-0000-0000-000000000001', campaignKind: 'COUPON', campaignId: 2, ownerType: 'STORE', ownerStoreId: 7, actorMemberId: 1, command: 'PAUSE', occurredAt: '2026-07-17T00:00:00Z', aggregateVersion: 1, beforeSummary: '{}', afterSummary: '{}' }];
const deadEvents = [{ id: 1, eventId: '00000000-0000-0000-0000-000000000002', eventType: 'CAMPAIGN_CHANGED', schemaVersion: 1, aggregateType: 'COUPON_CAMPAIGN', aggregateId: 2, aggregateVersion: 1, storeId: 7, campaignId: 2, partitionKey: 'campaign-2', occurredAt: '2026-07-17T00:00:00Z', payload: { hidden: true }, deliveryState: 'DEAD', attemptCount: 5, nextAttemptAt: null, lastError: 'projection failed', createdAt: '2026-07-17T00:00:00Z' }];
const performanceMeasurement = { runId: 33, measurementId: '385b4525-21a2-4f4a-875f-364449f59957', payloadHash: 'abc', gitCommit: '0123456789abcdef0123456789abcdef01234567', dirtyWorktree: false, fixtureVersion: 'm30-v1', scenarioVersion: 'm30-v1', environmentName: 'local', hardwareDescription: 'test', artifactDirectory: 'performance/results/test', warmupSeconds: 60, measuredSeconds: 300, offStartedAt: '2026-07-17T00:00:00Z', offCompletedAt: '2026-07-17T00:05:00Z', onStartedAt: '2026-07-17T00:10:00Z', onCompletedAt: '2026-07-17T00:15:00Z', registeredBy: 1, registeredAt: '2026-07-17T00:20:00Z', valid: true, comparable: true, endpointMetrics: [], queryEvidence: [] };
