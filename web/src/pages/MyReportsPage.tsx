import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthProvider';
import {
  getSellerDashboardReport,
  getSellerPeriodReport,
  type SellerDailySales,
  type SellerPeriodInput,
  type SellerProductRanking,
  type SellerRecentSale,
  type SellerRecentSettlement,
} from '../features/reports/sellerReportApi';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const MAX_PERIOD_DAYS = 180;
const MILLISECONDS_PER_DAY = 86_400_000;

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
  const [periodInput, setPeriodInput] = useState(() => getDefaultPeriod(30));
  const [draftPeriod, setDraftPeriod] = useState(() => getDefaultPeriod(30));
  const validationMessage = validatePeriod(draftPeriod);

  const dashboardQuery = useQuery({
    queryKey: ['seller-dashboard-report', memberId],
    queryFn: getSellerDashboardReport,
    enabled: memberId !== undefined,
  });
  const periodQuery = useQuery({
    queryKey: ['seller-period-report', memberId, periodInput.from, periodInput.to],
    queryFn: () => getSellerPeriodReport(periodInput),
    enabled: memberId !== undefined,
  });

  if (dashboardQuery.isLoading || periodQuery.isLoading) {
    return <p className="status-text">리포트를 불러오고 있습니다.</p>;
  }

  if (dashboardQuery.error) {
    return <ErrorState message="판매자 리포트를 불러오지 못했습니다." />;
  }

  if (periodQuery.error) {
    return <ErrorState message="선택 기간 리포트를 불러오지 못했습니다." />;
  }

  const dashboard = dashboardQuery.data;
  const period = periodQuery.data;

  if (!dashboard || !period) {
    return <EmptyState title="리포트 데이터가 없습니다" description="판매 활동이 생기면 이곳에 요약 지표가 표시됩니다." />;
  }

  const total = dashboard.summary.total;
  const recent = dashboard.summary.recent30Days;
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
      period.summary.orderedCount +
      period.summary.confirmedOrderCount +
      period.summary.confirmedSalesAmount +
      period.summary.completedSettlementAmount +
      period.summary.unsettledConfirmedAmount +
      sumCounts(dashboard.productStatusCounts) +
      sumCounts(dashboard.orderStatusCounts) >
    0;

  return (
    <section className="list-page seller-report-page">
      <div className="list-page-header report-page-header">
        <div>
          <h1>리포트</h1>
          <p>판매 상품, 주문, 정산 흐름을 기간별로 확인합니다.</p>
        </div>
        <p className="status-text">생성 시각 {formatDateTime(period.generatedAt)}</p>
      </div>

      <ReportFilter
        draftPeriod={draftPeriod}
        isFetching={periodQuery.isFetching}
        validationMessage={validationMessage}
        onChange={setDraftPeriod}
        onQuickRange={(days) => {
          const nextPeriod = getDefaultPeriod(days);
          setDraftPeriod(nextPeriod);
          setPeriodInput(nextPeriod);
        }}
        onSubmit={() => {
          if (!validationMessage) {
            setPeriodInput(draftPeriod);
          }
        }}
      />

      {!hasAnyData ? (
        <EmptyState title="아직 판매 데이터가 없습니다" description="상품을 등록하고 주문이 확정되면 리포트가 채워집니다." />
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
          <h2 id="report-recent-title">최근 {dashboard.period.recentDays}일</h2>
          <span className="muted-text">
            {formatDate(dashboard.period.recentFrom)} ~ {formatDate(dashboard.period.recentTo)}
          </span>
        </div>
        <div className="metric-grid">
          <MetricCard label="신규 주문" value={formatNumber(recent.orderedCount)} />
          <MetricCard label="확정 주문" value={formatNumber(recent.confirmedOrderCount)} />
          <MetricCard label="완료 정산액" value={`${formatCurrency(recent.completedSettlementAmount)}원`} />
          <MetricCard label="미정산 확정 금액" value={`${formatCurrency(recent.unsettledConfirmedAmount)}원`} />
        </div>
      </section>

      <section className="report-section" aria-labelledby="report-period-title">
        <div className="report-section-header">
          <h2 id="report-period-title">선택 기간</h2>
          <span className="muted-text">
            {formatDate(period.period.from)} ~ {formatDate(period.period.to)} · {formatNumber(period.period.days)}일
          </span>
        </div>
        <div className="metric-grid">
          <MetricCard label="신규 주문" value={formatNumber(period.summary.orderedCount)} />
          <MetricCard label="확정 주문" value={formatNumber(period.summary.confirmedOrderCount)} />
          <MetricCard label="확정 판매액" value={`${formatCurrency(period.summary.confirmedSalesAmount)}원`} />
          <MetricCard label="평균 주문액" value={`${formatCurrency(period.summary.averageConfirmedOrderAmount)}원`} />
          <MetricCard label="완료 정산액" value={`${formatCurrency(period.summary.completedSettlementAmount)}원`} />
          <MetricCard label="미정산 확정 금액" value={`${formatCurrency(period.summary.unsettledConfirmedAmount)}원`} />
        </div>
      </section>

      <DailySalesTrend rows={period.dailySales} />

      <ProductRanking rankings={period.productRankings} />

      <div className="report-table-grid">
        <RecentSales rows={period.recentSales} />
        <RecentSettlements rows={period.recentSettlements} />
      </div>

      <div className="report-distribution-grid">
        <StatusDistribution title="상품 상태" counts={dashboard.productStatusCounts} />
        <StatusDistribution title="주문 상태" counts={dashboard.orderStatusCounts} />
      </div>
    </section>
  );
}

function ReportFilter({
  draftPeriod,
  isFetching,
  validationMessage,
  onChange,
  onQuickRange,
  onSubmit,
}: {
  draftPeriod: SellerPeriodInput;
  isFetching: boolean;
  validationMessage: string | null;
  onChange: (period: SellerPeriodInput) => void;
  onQuickRange: (days: number) => void;
  onSubmit: () => void;
}) {
  return (
    <section className="report-filter" aria-label="리포트 기간 필터">
      <div className="report-filter-fields">
        <label>
          시작일
          <input
            type="date"
            value={draftPeriod.from}
            onChange={(event) => onChange({ ...draftPeriod, from: event.target.value })}
          />
        </label>
        <label>
          종료일
          <input
            type="date"
            value={draftPeriod.to}
            onChange={(event) => onChange({ ...draftPeriod, to: event.target.value })}
          />
        </label>
        <button className="text-button" type="button" disabled={Boolean(validationMessage) || isFetching} onClick={onSubmit}>
          {isFetching ? '조회 중' : '조회'}
        </button>
      </div>
      <div className="report-quick-ranges" aria-label="빠른 기간 선택">
        {[7, 30, 90].map((days) => (
          <button key={days} type="button" className="report-chip-button" onClick={() => onQuickRange(days)}>
            {days}일
          </button>
        ))}
      </div>
      {validationMessage ? <p className="error-text">{validationMessage}</p> : null}
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

function DailySalesTrend({ rows }: { rows: SellerDailySales[] }) {
  const maxAmount = Math.max(...rows.map((row) => row.confirmedSalesAmount), 0);

  return (
    <section className="report-section" aria-labelledby="daily-sales-title">
      <div className="report-section-header">
        <h2 id="daily-sales-title">일별 확정 판매</h2>
      </div>
      {rows.length === 0 ? (
        <EmptyState title="일별 판매 데이터가 없습니다" description="선택 기간에 표시할 판매 흐름이 없습니다." />
      ) : (
        <div className="daily-sales-list">
          {rows.map((row) => {
            const width = maxAmount === 0 ? 0 : Math.max(6, Math.round((row.confirmedSalesAmount / maxAmount) * 100));

            return (
              <div className="daily-sales-row" key={row.date}>
                <span>{formatDate(row.date)}</span>
                <div className="daily-sales-bar-track" aria-hidden="true">
                  <div className="daily-sales-bar" style={{ width: `${width}%` }} />
                </div>
                <strong>{formatCurrency(row.confirmedSalesAmount)}원</strong>
                <span>{formatNumber(row.confirmedOrderCount)}건</span>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

function ProductRanking({ rankings }: { rankings: SellerProductRanking[] }) {
  return (
    <section className="report-section" aria-labelledby="product-ranking-title">
      <div className="report-section-header">
        <h2 id="product-ranking-title">상품별 판매 랭킹</h2>
      </div>
      {rankings.length === 0 ? (
        <EmptyState title="랭킹 데이터가 없습니다" description="선택 기간에 확정된 판매가 없습니다." />
      ) : (
        <div className="product-ranking-list">
          {rankings.map((item, index) => (
            <div className="product-ranking-row" key={item.productId}>
              <span className="ranking-number">{index + 1}</span>
              {item.thumbnailUrl ? (
                <img className="ranking-thumb" src={item.thumbnailUrl} alt="" />
              ) : (
                <span className="ranking-thumb ranking-thumb-fallback">이미지 없음</span>
              )}
              <Link className="ranking-title" to={`/products/${item.productId}`}>
                {item.title}
              </Link>
              <span>{formatNumber(item.confirmedOrderCount)}건</span>
              <strong>{formatCurrency(item.confirmedSalesAmount)}원</strong>
              <span>{formatDateTime(item.lastConfirmedAt)}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function RecentSales({ rows }: { rows: SellerRecentSale[] }) {
  return (
    <section className="report-section" aria-label="최근 확정 판매">
      <div className="report-section-header">
        <h2>최근 확정 판매</h2>
      </div>
      {rows.length === 0 ? (
        <EmptyState title="최근 판매가 없습니다" description="선택 기간에 확정된 판매가 없습니다." />
      ) : (
        <div className="report-record-list">
          {rows.map((row) => (
            <div className="report-record-row" key={row.orderId}>
              <Link to={`/products/${row.productId}`}>{row.productTitle}</Link>
              <span>{row.buyerNickname}</span>
              <strong>{formatCurrency(row.amount)}원</strong>
              <SettlementStatusBadge status={row.settlementStatus} />
              <span>{formatDateTime(row.confirmedAt)}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function RecentSettlements({ rows }: { rows: SellerRecentSettlement[] }) {
  return (
    <section className="report-section" aria-label="최근 정산">
      <div className="report-section-header">
        <h2>최근 정산</h2>
      </div>
      {rows.length === 0 ? (
        <EmptyState title="최근 정산이 없습니다" description="선택 기간에 생성된 정산이 없습니다." />
      ) : (
        <div className="report-record-list">
          {rows.map((row) => (
            <div className="report-record-row" key={row.settlementId}>
              <Link to={`/products/${row.productId}`}>{row.productTitle}</Link>
              <span>주문 #{row.orderId}</span>
              <strong>{formatCurrency(row.amount)}원</strong>
              <StatusBadge status={row.status} />
              <span>{formatDateTime(row.settledAt)}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function SettlementStatusBadge({ status }: { status: SellerRecentSale['settlementStatus'] }) {
  if (status === 'NONE') {
    return <span className="status-badge status-badge-none">정산 없음</span>;
  }

  return <StatusBadge status={status} />;
}

function StatusDistribution({ title, counts }: { title: string; counts: StatusCount[] }) {
  return (
    <section className="report-section" aria-label={title}>
      <div className="report-section-header">
        <h2>{title}</h2>
      </div>
      {counts.length === 0 ? (
        <EmptyState title={`${title} 데이터가 없습니다`} description="집계할 항목이 없습니다." />
      ) : (
        <div className="status-count-list">
          {counts.map((item) => (
            <div className="status-count-row" key={item.status}>
              <StatusBadge status={item.status} />
              <strong>{formatNumber(item.count)}</strong>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function getDefaultPeriod(days: number): SellerPeriodInput {
  const to = new Date();
  const from = new Date(to);
  from.setDate(to.getDate() - days + 1);

  return {
    from: toDateInputValue(from),
    to: toDateInputValue(to),
  };
}

function validatePeriod(period: SellerPeriodInput) {
  if (!period.from || !period.to) {
    return '시작일과 종료일을 모두 선택해주세요.';
  }

  const from = parseDateInput(period.from);
  const to = parseDateInput(period.to);

  if (!from || !to) {
    return '날짜 형식이 올바르지 않습니다.';
  }

  const days = Math.floor((to.getTime() - from.getTime()) / MILLISECONDS_PER_DAY) + 1;

  if (days < 1) {
    return '시작일은 종료일보다 늦을 수 없습니다.';
  }

  if (days > MAX_PERIOD_DAYS) {
    return '리포트 기간은 최대 180일까지 조회할 수 있습니다.';
  }

  return null;
}

function parseDateInput(value: string) {
  const date = new Date(`${value}T00:00:00`);

  return Number.isNaN(date.getTime()) ? null : date;
}

function toDateInputValue(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
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
