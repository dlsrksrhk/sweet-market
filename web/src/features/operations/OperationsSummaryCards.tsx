import type { DiscountAmountSummary, StoreOperationsDashboard } from './storeOperationsDashboardApi';

type OperationsSummaryCardsProps = {
  dashboard: StoreOperationsDashboard;
};

const numberFormatter = new Intl.NumberFormat('ko-KR');

export function OperationsSummaryCards({ dashboard }: OperationsSummaryCardsProps) {
  const trackingStarted = dashboard.trackingStartedAt !== null;

  return (
    <>
      <dl className="operations-summary-grid" aria-label="주요 운영 성과">
        <CountCard label="쿠폰 발급 성공" value={dashboard.claimSuccessCount} trackingStarted={trackingStarted} />
        <CountCard label="쿠폰 사용 성공" value={dashboard.redemptionSuccessCount} trackingStarted={trackingStarted} />
        <CountCard label="주문 성공" value={dashboard.orderSuccessCount} trackingStarted={trackingStarted} />
        <CountCard label="구매 실패" value={dashboard.purchaseFailureCount} trackingStarted={trackingStarted} tone="warning" />
        <CountCard label="재고 부족 상품" value={dashboard.lowStockCount} trackingStarted={trackingStarted} tone="warning" />
        <CountCard label="품절 전환" value={dashboard.soldOutTransitionCount} trackingStarted={trackingStarted} tone="warning" />
      </dl>
      <div className="operations-amount-sections">
        <AmountSummary title="프로모션 할인" summary={dashboard.promotionDiscounts} trackingStarted={trackingStarted} />
        <AmountSummary title="쿠폰 할인" summary={dashboard.couponDiscounts} trackingStarted={trackingStarted} />
      </div>
    </>
  );
}

function CountCard({ label, value, trackingStarted, tone }: { label: string; value: number; trackingStarted: boolean; tone?: 'warning' }) {
  return (
    <div className={tone ? 'operations-summary-card is-warning' : 'operations-summary-card'}>
      <dt>{label}</dt>
      <dd>{trackingStarted ? `${numberFormatter.format(value)}건` : '—'}</dd>
      <small>{trackingStarted ? (value === 0 ? '측정된 0건' : '조회 기간 누적') : '추적을 시작하지 않았습니다'}</small>
    </div>
  );
}

function AmountSummary({ title, summary, trackingStarted }: { title: string; summary: DiscountAmountSummary; trackingStarted: boolean }) {
  const reversal = summary.canceled + summary.refunded;
  return (
    <section className="operations-amount-panel" aria-label={title}>
      <h3>{title}</h3>
      <dl>
        <AmountCard label="적용" value={summary.applied} trackingStarted={trackingStarted} />
        <AmountCard label="실현" value={summary.realized} trackingStarted={trackingStarted} />
        <AmountCard label="취소·환불" value={reversal} trackingStarted={trackingStarted} />
      </dl>
    </section>
  );
}

function AmountCard({ label, value, trackingStarted }: { label: string; value: number; trackingStarted: boolean }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{trackingStarted ? `${numberFormatter.format(value)}원` : '—'}</dd>
      <small>{trackingStarted ? (value === 0 ? '측정된 0원' : '조회 기간 누적') : '추적을 시작하지 않았습니다'}</small>
    </div>
  );
}
