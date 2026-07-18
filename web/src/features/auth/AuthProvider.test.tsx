// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthProvider';

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.unstubAllGlobals();
});

describe('AuthProvider private query cleanup', () => {
  it('로그아웃하면_운영대시보드_cache를_제거한다', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(['store-operations-dashboard', 7, 'dashboard'], { secret: true });
    queryClient.setQueryData(['admin-operations-dashboard', 'dashboard'], { secret: true });
    localStorage.setItem('sweet-market-token', 'token');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(memberResponse()));
    renderAuth(queryClient);

    await userEvent.setup().click(await screen.findByRole('button', { name: '로그아웃' }));

    expect(queryClient.getQueryData(['store-operations-dashboard', 7, 'dashboard'])).toBeUndefined();
    expect(queryClient.getQueryData(['admin-operations-dashboard', 'dashboard'])).toBeUndefined();
  });

  it('다른계정으로_로그인할때도_이전_운영대시보드_cache를_제거한다', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(['store-operations-dashboard', 7, 'dashboard'], { secret: true });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { accessToken: 'next-token', tokenType: 'Bearer', expiresIn: 3600, member: { id: 2, email: 'next@example.com', nickname: '다음 사용자' } } })))
      .mockResolvedValueOnce(memberResponse());
    vi.stubGlobal('fetch', fetchMock);
    renderAuth(queryClient);

    await userEvent.setup().click(await screen.findByRole('button', { name: '로그인' }));
    await waitFor(() => expect(queryClient.getQueryData(['store-operations-dashboard', 7, 'dashboard'])).toBeUndefined());
  });
});

function renderAuth(queryClient: QueryClient) {
  return render(<QueryClientProvider client={queryClient}><AuthProvider><AuthProbe /></AuthProvider></QueryClientProvider>);
}

function AuthProbe() {
  const { loading, member, login, logout } = useAuth();
  if (loading) return <span>로딩</span>;
  if (member) return <button type="button" onClick={logout}>로그아웃</button>;
  return <button type="button" onClick={() => void login('next@example.com', 'password')}>로그인</button>;
}

function memberResponse() {
  return new Response(JSON.stringify({ data: { id: 2, email: 'next@example.com', nickname: '다음 사용자', role: 'MEMBER' } }));
}
