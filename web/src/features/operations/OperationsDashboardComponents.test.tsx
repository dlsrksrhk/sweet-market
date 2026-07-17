import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { OperationsPeriodControls } from './OperationsPeriodControls';
import { OperationsSummaryCards } from './OperationsSummaryCards';
import type { StoreOperationsDashboard } from './storeOperationsDashboardApi';

const dashboard: StoreOperationsDashboard = {
  storeId: 7,
  storeName: '달콤 상점',
  period: {
    from: '2026-07-01',
    to: '2026-07-17',
    fromInclusive: '2026-06-30T15:00:00Z',
    toExclusive: '2026-07-17T15:00:00Z',
    timezone: 'Asia/Seoul',
  },
  generatedAt: '2026-07-17T00:05:10Z',
  projectionUpdatedAt: '2026-07-17T00:05:00Z',
  projectionLagSeconds: 10,
  trackingStartedAt: '2026-05-01T00:00:00Z',
  claimSuccessCount: 0,
  redemptionSuccessCount: 0,
  orderSuccessCount: 0,
  purchaseFailureCount: 0,
  promotionDiscounts: { applied: 0, realized: 0, canceled: 0, refunded: 0 },
  couponDiscounts: { applied: 0, realized: 0, canceled: 0, refunded: 0 },
  lowStockCount: 0,
  soldOutTransitionCount: 0,
  leadingFailureReasons: [],
};

describe('OperationsSummaryCards', () => {
  it('추적중인_0건과_0원은_측정된_0으로_표시한다', () => {
    const html = renderToStaticMarkup(<OperationsSummaryCards dashboard={dashboard} />);

    expect(html).toContain('측정된 0건');
    expect(html).toContain('측정된 0원');
    expect(html).not.toContain('추적을 시작하지 않았습니다');
  });

  it('추적시작시각이_없으면_0이_아닌_미추적상태로_표시한다', () => {
    const html = renderToStaticMarkup(
      <OperationsSummaryCards dashboard={{ ...dashboard, trackingStartedAt: null }} />,
    );

    expect(html).toContain('추적을 시작하지 않았습니다');
    expect(html).not.toContain('측정된 0건');
  });
});

describe('OperationsPeriodControls', () => {
  it('KST_프리셋과_사용자지정_날짜입력을_제공한다', () => {
    const html = renderToStaticMarkup(
      <OperationsPeriodControls
        period={{ preset: 'LAST_30_DAYS' }}
        customFrom="2026-07-01"
        customTo="2026-07-17"
        validationMessage={null}
        onPresetChange={vi.fn()}
        onCustomFromChange={vi.fn()}
        onCustomToChange={vi.fn()}
        onCustomApply={vi.fn()}
      />,
    );

    expect(html).toContain('최근 30일');
    expect(html).toContain('KST 기준');
    expect(html).toContain('type="date"');
    expect(html).toContain('aria-pressed="true"');
  });
});
