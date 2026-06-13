import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getMySettlements } from '../features/settlements/settlementApi';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

export function MySettlementsPage() {
  const { data: settlements = [], error, isLoading } = useQuery({
    queryKey: ['my-settlements'],
    queryFn: getMySettlements,
  });

  if (isLoading) {
    return <p className="status-text">정산 내역을 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="정산 내역을 불러오지 못했습니다." />;
  }

  return (
    <section className="list-page">
      <header className="list-page-header">
        <h1>정산</h1>
        <p>확정된 거래의 정산 상태와 금액을 확인할 수 있습니다.</p>
      </header>
      {settlements.length === 0 ? (
        <EmptyState title="정산 내역이 없습니다" description="구매 확정된 판매 거래가 정산되면 이곳에 표시됩니다." />
      ) : (
        <div className="record-list" aria-label="내 정산 목록">
          {settlements.map((settlement) => (
            <article className="record-card" key={settlement.id}>
              <div className="record-main">
                <StatusBadge status={settlement.status} />
                <h2>
                  <Link to={`/products/${settlement.productId}`}>{settlement.productTitle}</Link>
                </h2>
                <strong>{currencyFormatter.format(settlement.amount)}원</strong>
              </div>
              <dl className="record-meta">
                <div>
                  <dt>주문 번호</dt>
                  <dd>{settlement.orderId}</dd>
                </div>
                <div>
                  <dt>정산일</dt>
                  <dd>{formatDateTime(settlement.settledAt)}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function formatDateTime(value: string | null) {
  if (!value) {
    return '-';
  }

  return new Date(value).toLocaleString('ko-KR');
}
