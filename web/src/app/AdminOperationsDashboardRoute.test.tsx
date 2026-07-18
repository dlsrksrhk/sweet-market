// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../features/auth/AuthProvider';
import { AppRouter } from './router';

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.unstubAllGlobals();
});

describe('/admin/dashboard route', () => {
  it('ADMIN에게_운영대시보드_navigation과_page를_제공한다', async () => {
    localStorage.setItem('sweet-market-token', 'admin-token');
    installApi('ADMIN');
    renderRouter();

    expect(await screen.findByRole('link', { name: '운영 대시보드' })).toBeTruthy();
    expect(await screen.findByRole('heading', { name: '관리자 운영 대시보드' })).toBeTruthy();
  });

  it('일반회원에게_ADMIN_dashboard를_노출하지_않는다', async () => {
    localStorage.setItem('sweet-market-token', 'member-token');
    installApi('MEMBER');
    renderRouter();

    expect(await screen.findByText('접근 권한이 없습니다.')).toBeTruthy();
    expect(screen.queryByRole('link', { name: '운영 대시보드' })).toBeNull();
  });
});

function renderRouter() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}><MemoryRouter initialEntries={['/admin/dashboard']}><AuthProvider><AppRouter /></AuthProvider></MemoryRouter></QueryClientProvider>);
}

function installApi(role: 'ADMIN' | 'MEMBER') {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith('/api/members/me')) return json({ id: 1, email: 'admin@example.com', nickname: '관리자', role });
    if (url.includes('/operations-dashboard') && !url.match(/\/(campaigns|outcomes|inventory-pressure|audits)/)) return json(dashboard);
    if (url.includes('/operations-dashboard/') || url.includes('/performance-measurements') || url.includes('/operational-events/dead')) return json(page([]));
    throw new Error(`Unexpected request: ${url}`);
  }));
}

function json(data: unknown) { return Promise.resolve(new Response(JSON.stringify({ data }), { status: 200 })); }
function page<T>(content: T[]) { return { content, totalElements: 0, totalPages: 0, size: 20, number: 0, first: true, last: true, empty: true }; }
const zeroDiscounts = { applied: 0, realized: 0, canceled: 0, refunded: 0 };
const dashboard = { storeId: null, period: { from: '2026-07-01', to: '2026-07-17', fromInclusive: '2026-06-30T15:00:00Z', toExclusive: '2026-07-17T15:00:00Z', timezone: 'Asia/Seoul' }, generatedAt: '2026-07-17T00:05:10Z', projectionUpdatedAt: '2026-07-17T00:05:00Z', projectionLagSeconds: 10, trackingStartedAt: '2026-05-01T00:00:00Z', claimSuccessCount: 0, redemptionSuccessCount: 0, orderSuccessCount: 0, purchaseFailureCount: 0, promotionDiscounts: zeroDiscounts, couponDiscounts: zeroDiscounts, lowStockCount: 0, soldOutTransitionCount: 0, auditCount: 0, leadingFailureReasons: [], health: { pendingCount: 0, retryCount: 0, deadCount: 0, oldestUnprocessedAt: null, projectionLagSeconds: 10, projectionUpdatedAt: '2026-07-17T00:05:00Z' } };
