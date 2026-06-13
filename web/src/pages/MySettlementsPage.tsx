import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../features/auth/AuthProvider';
import { getMySettlements } from '../features/settlements/settlementApi';
import { EmptyState, ErrorState } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

export function MySettlementsPage() {
  const { member } = useAuth();
  const memberId = member?.id;
  const { data: settlements = [], error, isLoading } = useQuery({
    queryKey: ['my-settlements', memberId],
    queryFn: getMySettlements,
    enabled: memberId !== undefined,
  });

  if (isLoading) {
    return <p className="status-text">정산 내역을 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="정산 내역을 불러오지 못했습니다." />;
  }

  return (
    <section className="list-page">
      <div className="list-page-header">
        <h1>정산</h1>
        <p>판매 확정 후 생성된 정산 내역을 확인합니다.</p>
      </div>
      {settlements.length === 0 ? (
        <EmptyState title="정산 내역이 없습니다" description="구매 확정된 판매 건의 정산이 생성되면 이곳에 표시됩니다." />
      ) : (
        <div className="record-list" aria-label="내 정산 목록">
          {settlements.map((settlement) => (
            <article className="record-card" key={settlement.id}>
              <div className="record-main">
                <StatusPill status={settlement.status} />
                <h2>{settlement.productTitle}</h2>
                <strong>{currencyFormatter.format(settlement.amount)}원</strong>
              </div>
              <dl className="record-meta">
                <div>
                  <dt>주문 번호</dt>
                  <dd>{settlement.orderId}</dd>
                </div>
                <div>
                  <dt>정산일시</dt>
                  <dd>{formatDate(settlement.settledAt)}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function StatusPill({ status }: { status: string }) {
  return <span className={`status-badge status-badge-${status.toLowerCase()}`}>{formatStatus(status)}</span>;
}

function formatStatus(status: string) {
  switch (status) {
    case 'READY':
      return '대기';
    case 'COMPLETED':
      return '완료';
    case 'FAILED':
      return '실패';
    default:
      return status;
  }
}

function formatDate(value: string | null) {
  if (!value) {
    return '-';
  }

  return dateFormatter.format(new Date(value));
}
