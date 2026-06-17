import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../features/auth/AuthProvider';
import { getSellerDashboardReport } from '../features/reports/sellerReportApi';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');
const numberFormatter = new Intl.NumberFormat('ko-KR');
const dateOnlyFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
});
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

type StatusCount = {
  status: string;
  count: number;
};

export function MyReportsPage() {
  const { member } = useAuth();
  const memberId = member?.id;
  const { data, error, isLoading } = useQuery({
    queryKey: ['seller-dashboard-report', memberId],
    queryFn: getSellerDashboardReport,
    enabled: memberId !== undefined,
  });

  if (isLoading) {
    return <p className="status-text">리포트를 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="판매자 리포트를 불러오지 못했습니다." />;
  }

  if (!data) {
    return <EmptyState title="리포트 데이터가 없습니다" description="판매 활동이 생기면 이곳에 요약 지표가 표시됩니다." />;
  }

  const total = data.summary.total;
  const recent = data.summary.recent30Days;
  const hasAnyData =
    total.activeProductCount +
      total.soldOutProductCount +
      total.confirmedOrderCount +
      total.completedSettlementAmount +
      total.unsettledConfirmedAmount +
      recent.orderedCount +
      recent.confirmedOrderCount +
      recent.completedSettlementAmount +
      recent.unsettledConfirmedAmount +
      sumCounts(data.productStatusCounts) +
      sumCounts(data.orderStatusCounts) >
    0;

  return (
    <section className="list-page seller-report-page">
      <div className="list-page-header">
        <div>
          <h1>리포트</h1>
          <p>판매 상품, 주문, 정산 상태를 한눈에 확인합니다.</p>
        </div>
        <p className="status-text">생성 시각 {formatDateTime(data.generatedAt)}</p>
      </div>

      {!hasAnyData ? (
        <EmptyState title="아직 판매 데이터가 없습니다" description="상품을 등록하고 주문이 확정되면 요약 지표가 채워집니다." />
      ) : null}

      <section className="report-section" aria-labelledby="report-total-title">
        <div className="report-section-header">
          <h2 id="report-total-title">전체 누적</h2>
          <span className="muted-text">현재까지의 판매 활동</span>
        </div>
        <div className="metric-grid">
          <MetricCard label="판매중 상품" value={formatNumber(total.activeProductCount)} />
          <MetricCard label="판매완료 상품" value={formatNumber(total.soldOutProductCount)} />
          <MetricCard label="확정 주문" value={formatNumber(total.confirmedOrderCount)} />
          <MetricCard label="완료 정산액" value={`${formatCurrency(total.completedSettlementAmount)}원`} />
          <MetricCard label="미정산 확정 금액" value={`${formatCurrency(total.unsettledConfirmedAmount)}원`} />
        </div>
      </section>

      <section className="report-section" aria-labelledby="report-recent-title">
        <div className="report-section-header">
          <h2 id="report-recent-title">최근 {data.period.recentDays}일</h2>
          <span className="muted-text">
            {formatDate(data.period.recentFrom)} ~ {formatDate(data.period.recentTo)}
          </span>
        </div>
        <div className="metric-grid">
          <MetricCard label="신규 주문" value={formatNumber(recent.orderedCount)} />
          <MetricCard label="확정 주문" value={formatNumber(recent.confirmedOrderCount)} />
          <MetricCard label="완료 정산액" value={`${formatCurrency(recent.completedSettlementAmount)}원`} />
          <MetricCard label="미정산 확정 금액" value={`${formatCurrency(recent.unsettledConfirmedAmount)}원`} />
        </div>
      </section>

      <div className="report-distribution-grid">
        <StatusDistribution title="상품 상태" counts={data.productStatusCounts} />
        <StatusDistribution title="주문 상태" counts={data.orderStatusCounts} />
      </div>
    </section>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="metric-card">
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function StatusDistribution({ title, counts }: { title: string; counts: StatusCount[] }) {
  return (
    <section className="report-section" aria-label={title}>
      <div className="report-section-header">
        <h2>{title}</h2>
      </div>
      <div className="status-count-list">
        {counts.map((item) => (
          <div className="status-count-row" key={item.status}>
            <StatusBadge status={item.status} />
            <strong>{formatNumber(item.count)}</strong>
          </div>
        ))}
      </div>
    </section>
  );
}

function formatNumber(value: number) {
  return numberFormatter.format(value);
}

function formatCurrency(value: number) {
  return currencyFormatter.format(value);
}

function formatDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);

  return dateOnlyFormatter.format(new Date(year, month - 1, day));
}

function formatDateTime(value: string) {
  return dateFormatter.format(new Date(value));
}

function sumCounts(counts: StatusCount[]) {
  return counts.reduce((sum, item) => sum + item.count, 0);
}
