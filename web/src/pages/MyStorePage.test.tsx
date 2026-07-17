// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { MyStorePage } from './MyStorePage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it('선택한_상점_ID를_운영대시보드_링크에_포함한다', async () => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith('/api/store-operations')) return json([
      { storeId: 1, type: 'BUSINESS', publicName: '첫 상점', status: 'ACTIVE', role: 'OWNER' },
      { storeId: 2, type: 'BUSINESS', publicName: '둘째 상점', status: 'ACTIVE', role: 'OWNER' },
    ]);
    if (url.includes('/summary')) return json({ onSaleCount: 0, reservedCount: 0, soldOutCount: 0, hiddenCount: 0, catalogWritable: true });
    if (url.includes('/products?')) return json({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0, first: true, last: true, empty: true });
    throw new Error(`Unexpected URL: ${url}`);
  }));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={queryClient}><MemoryRouter><MyStorePage /></MemoryRouter></QueryClientProvider>);

  expect((await screen.findByRole('link', { name: '운영 대시보드' })).getAttribute('href')).toBe('/me/store/dashboard?storeId=1');
  await userEvent.setup().selectOptions(screen.getByLabelText('운영할 상점'), '2');
  expect(screen.getByRole('link', { name: '운영 대시보드' }).getAttribute('href')).toBe('/me/store/dashboard?storeId=2');
});

function json(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({ data })));
}
