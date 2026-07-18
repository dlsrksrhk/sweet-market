import type { DiscountAmountSummary, StoreOperationsDashboard } from './storeOperationsDashboardApi';
import { deriveTrackingCoverage, type TrackingCoverage } from './trackingCoverage';

type OperationsSummaryCardsProps = {
  dashboard: StoreOperationsDashboard;
};

const numberFormatter = new Intl.NumberFormat('ko-KR');

export function OperationsSummaryCards({ dashboard }: OperationsSummaryCardsProps) {
  const coverage = deriveTrackingCoverage(dashboard.period, dashboard.trackingStartedAt);

  return (
    <>
      <dl className="operations-summary-grid" aria-label="주요 운영 성과">
        <CountCard label="쿠폰 발급 성공" value={dashboard.claimSuccessCount} coverage={coverage} trackingStartedAt={dashboard.trackingStartedAt} />
        <CountCard label="쿠폰 사용 성공" value={dashboard.redemptionSuccessCount} coverage={coverage} trackingStartedAt={dashboard.trackingStartedAt} />
        <CountCard label="주문 성공" value={dashboard.orderSuccessCount} coverage={coverage} trackingStartedAt={dashboard.trackingStartedAt} />
        <CountCard label="구매 실패" value={dashboard.purchaseFailureCount} coverage={coverage} trackingStartedAt={dashboard.trackingStartedAt} tone="warning" />
        <CountCard label="재고 부족 상품" value={dashboard.lowStockCount} coverage={coverage} trackingStartedAt={dashboard.trackingStartedAt} tone="warning" />
        <CountCard label="품절 전환" value={dashboard.soldOutTransitionCount} coverage={coverage} trackingStartedAt={dashboard.trackingStartedAt} tone="warning" />
      </dl>
      <div className="operations-amount-sections">
        <AmountSummary title="프로모션 할인" summary={dashboard.promotionDiscounts} coverage={coverage} trackingStartedAt={dashboard.trackingStartedAt} />
        <AmountSummary title="쿠폰 할인" summary={dashboard.couponDiscounts} coverage={coverage} trackingStartedAt={dashboard.trackingStartedAt} />
      </div>
    </>
  );
}

function CountCard({ label, value, coverage, trackingStartedAt, tone }: { label: string; value: number; coverage: TrackingCoverage; trackingStartedAt: string | null; tone?: 'warning' }) {
  return (
    <div className={tone ? 'operations-summary-card is-warning' : 'operations-summary-card'}>
      <dt>{label}</dt>
      <dd>{coverage !== 'UNTRACKED' ? `${numberFormatter.format(value)}건` : '—'}</dd>
      <small>{countProvenance(value, coverage, trackingStartedAt)}</small>
    </div>
  );
}

function AmountSummary({ title, summary, coverage, trackingStartedAt }: { title: string; summary: DiscountAmountSummary; coverage: TrackingCoverage; trackingStartedAt: string | null }) {
  return (
    <section className="operations-amount-panel" aria-label={title}>
      <h3>{title}</h3>
      <dl>
        <AmountCard label="적용" value={summary.applied} coverage={coverage} trackingStartedAt={trackingStartedAt} />
        <AmountCard label="실현" value={summary.realized} coverage={coverage} trackingStartedAt={trackingStartedAt} />
        <AmountCard label="취소" value={summary.canceled} coverage={coverage} trackingStartedAt={trackingStartedAt} />
        <AmountCard label="환불" value={summary.refunded} coverage={coverage} trackingStartedAt={trackingStartedAt} />
      </dl>
    </section>
  );
}

function AmountCard({ label, value, coverage, trackingStartedAt }: { label: string; value: number; coverage: TrackingCoverage; trackingStartedAt: string | null }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{coverage !== 'UNTRACKED' ? `${numberFormatter.format(value)}원` : '—'}</dd>
      <small>{amountProvenance(value, coverage, trackingStartedAt)}</small>
    </div>
  );
}

function countProvenance(value: number, coverage: TrackingCoverage, trackingStartedAt: string | null) {
  if (coverage === 'UNTRACKED') return trackingStartedAt === null ? '추적을 시작하지 않았습니다' : '선택 기간은 추적 시작 전';
  if (coverage === 'PARTIAL') return value === 0 ? '추적 시작 이후 측정된 0건' : '추적 시작 이후 누적';
  return value === 0 ? '측정된 0건' : '조회 기간 누적';
}

function amountProvenance(value: number, coverage: TrackingCoverage, trackingStartedAt: string | null) {
  if (coverage === 'UNTRACKED') return trackingStartedAt === null ? '추적을 시작하지 않았습니다' : '선택 기간은 추적 시작 전';
  if (coverage === 'PARTIAL') return value === 0 ? '추적 시작 이후 측정된 0원' : '추적 시작 이후 누적';
  return value === 0 ? '측정된 0원' : '조회 기간 누적';
}
