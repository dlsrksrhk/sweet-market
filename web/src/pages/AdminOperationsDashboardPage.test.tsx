// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
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
});

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}><MemoryRouter><AdminOperationsDashboardPage /></MemoryRouter></QueryClientProvider>);
}

function installApi() {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes('/operations-dashboard/campaigns')) return json(page(campaigns));
    if (url.includes('/operations-dashboard/outcomes')) return json(page(outcomes));
    if (url.includes('/operations-dashboard/inventory-pressure')) return json(page(inventory));
    if (url.includes('/operations-dashboard/audits')) return json(page(audits));
    if (url.includes('/operations-dashboard')) return json(dashboard);
    if (url.includes('/performance-measurements')) return json(page([]));
    if (url.includes('/operational-events/dead')) return json(page(deadEvents));
    if (url.includes('/operational-events/') && url.endsWith('/retry')) return json(null);
    if (url.includes('/operational-projections/rebuild')) return json({ generationId: 2, status: 'ACTIVE', cutoff: '2026-07-17T00:00:00Z', activatedAt: '2026-07-17T00:01:00Z' });
    throw new Error(`Unexpected request: ${url}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function called(fetchMock: ReturnType<typeof vi.fn>, fragment: string) {
  return fetchMock.mock.calls.filter(([url]) => String(url).includes(fragment));
}

function json(data: unknown) { return Promise.resolve(new Response(JSON.stringify({ data }), { status: 200 })); }
function page<T>(content: T[]) { return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, size: 20, number: 0, first: true, last: true, empty: content.length === 0 }; }

const zeroDiscounts = { applied: 0, realized: 0, canceled: 0, refunded: 0 };
const health = { pendingCount: 1, retryCount: 0, deadCount: 1, oldestUnprocessedAt: '2026-07-17T00:00:00Z', projectionLagSeconds: 10, projectionUpdatedAt: '2026-07-17T00:05:00Z' };
const dashboard = { storeId: null, period: { from: '2026-07-01', to: '2026-07-17', fromInclusive: '2026-06-30T15:00:00Z', toExclusive: '2026-07-17T15:00:00Z', timezone: 'Asia/Seoul' }, generatedAt: '2026-07-17T00:05:10Z', projectionUpdatedAt: '2026-07-17T00:05:00Z', projectionLagSeconds: 10, trackingStartedAt: '2026-05-01T00:00:00Z', claimSuccessCount: 10, redemptionSuccessCount: 8, orderSuccessCount: 7, purchaseFailureCount: 1, promotionDiscounts: zeroDiscounts, couponDiscounts: zeroDiscounts, lowStockCount: 1, soldOutTransitionCount: 1, auditCount: 2, leadingFailureReasons: [], health };
const campaignBase = { latestBucketStart: '2026-07-17T00:00:00Z', campaignKind: 'COUPON', status: 'ACTIVE', claimSuccessCount: 1, claimFailureCount: 0, redemptionSuccessCount: 1, redemptionFailureCount: 0, orderSuccessCount: 1, purchaseFailureCount: 0, promotionDiscounts: zeroDiscounts, couponDiscounts: zeroDiscounts };
const campaigns = [{ ...campaignBase, id: 'platform', campaignId: 1, ownerType: 'PLATFORM', ownerStoreId: null }, { ...campaignBase, id: 'store', campaignId: 2, ownerType: 'STORE', ownerStoreId: 7 }];
const outcomes = [{ id: 'outcome', outcomeType: 'PURCHASE', latestBucketStart: '2026-07-17T00:00:00Z', storeId: 7, campaignKind: 'COUPON', campaignId: 2, ownerType: 'STORE', ownerStoreId: 7, productId: 3, reason: 'PAYMENT_FAILED', successCount: 1, failureCount: 1, reservationFailureCount: 0 }];
const inventory = [{ productId: 3, salesPolicy: 'STOCK_MANAGED', availableQuantity: 1, lowStock: true, lastSoldOutAt: null, reservationFailureCount: 1, lastReservationFailureAt: '2026-07-17T00:00:00Z', updatedAt: '2026-07-17T00:00:00Z' }];
const audits = [{ id: 1, eventId: '00000000-0000-0000-0000-000000000001', campaignKind: 'COUPON', campaignId: 2, ownerType: 'STORE', ownerStoreId: 7, actorMemberId: 1, command: 'PAUSE', occurredAt: '2026-07-17T00:00:00Z', aggregateVersion: 1, beforeSummary: '{}', afterSummary: '{}' }];
const deadEvents = [{ id: 1, eventId: '00000000-0000-0000-0000-000000000002', eventType: 'CAMPAIGN_CHANGED', schemaVersion: 1, aggregateType: 'COUPON_CAMPAIGN', aggregateId: 2, aggregateVersion: 1, storeId: 7, campaignId: 2, partitionKey: 'campaign-2', occurredAt: '2026-07-17T00:00:00Z', payload: { hidden: true }, deliveryState: 'DEAD', attemptCount: 5, nextAttemptAt: null, lastError: 'projection failed', createdAt: '2026-07-17T00:00:00Z' }];
