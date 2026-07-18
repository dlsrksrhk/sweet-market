import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { OperationsPeriodControls } from './OperationsPeriodControls';
import { OperationsSummaryCards } from './OperationsSummaryCards';
import type { StoreOperationsDashboard } from './storeOperationsDashboardApi';
import { deriveTrackingCoverage } from './trackingCoverage';

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
    expect(html).toContain('>취소<');
    expect(html).toContain('>환불<');
    expect(html).not.toContain('취소·환불');
  });

  it('추적시작시각이_없으면_0이_아닌_미추적상태로_표시한다', () => {
    const html = renderToStaticMarkup(
      <OperationsSummaryCards dashboard={{ ...dashboard, trackingStartedAt: null }} />,
    );

    expect(html).toContain('추적을 시작하지 않았습니다');
    expect(html).not.toContain('측정된 0건');
  });

  it('선택기간이_추적시작전이면_0이_아닌_미측정상태로_표시한다', () => {
    const html = renderToStaticMarkup(
      <OperationsSummaryCards dashboard={{ ...dashboard, trackingStartedAt: '2026-08-01T00:00:00Z' }} />,
    );

    expect(html).toContain('선택 기간은 추적 시작 전');
    expect(html).not.toContain('측정된 0건');
  });

  it('선택기간이_추적시작을_포함하면_추적시작이후_집계임을_표시한다', () => {
    const html = renderToStaticMarkup(
      <OperationsSummaryCards dashboard={{ ...dashboard, trackingStartedAt: '2026-07-10T00:00:00Z' }} />,
    );

    expect(html).toContain('추적 시작 이후 측정된 0건');
    expect(html).toContain('추적 시작 이후 측정된 0원');
    expect(html).not.toContain('조회 기간 누적');
  });

  it('취소액과_환불액을_합치지_않고_각각_표시한다', () => {
    const html = renderToStaticMarkup(<OperationsSummaryCards dashboard={{
      ...dashboard,
      promotionDiscounts: { applied: 100, realized: 70, canceled: 20, refunded: 10 },
      couponDiscounts: { applied: 50, realized: 30, canceled: 15, refunded: 5 },
    }} />);

    expect(html).toContain('<dt>취소</dt><dd>20원</dd>');
    expect(html).toContain('<dt>환불</dt><dd>10원</dd>');
    expect(html).toContain('<dt>취소</dt><dd>15원</dd>');
    expect(html).toContain('<dt>환불</dt><dd>5원</dd>');
  });
});

describe('deriveTrackingCoverage', () => {
  it('종료경계가_추적시작과_같으면_UNTRACKED다', () => {
    expect(deriveTrackingCoverage(dashboard.period, dashboard.period.toExclusive))
      .toBe('UNTRACKED');
  });

  it('시작경계가_추적시작과_같으면_TRACKED다', () => {
    expect(deriveTrackingCoverage(dashboard.period, dashboard.period.fromInclusive))
      .toBe('TRACKED');
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
