type EmptyStateProps = {
  title: string;
  description: string;
};

export function EmptyState({ title, description }: EmptyStateProps) {
  return (
    <div className="resource-state">
      <strong>{title}</strong>
      <p>{description}</p>
    </div>
  );
}

type ErrorStateProps = {
  title?: string;
  message: string;
};

export function ErrorState({ title = '요청을 처리하지 못했습니다', message }: ErrorStateProps) {
  return (
    <div className="resource-state resource-state-error">
      <strong>{title}</strong>
      <p>{message}</p>
    </div>
  );
}

type StatusBadgeProps = {
  status: string;
};

export function StatusBadge({ status }: StatusBadgeProps) {
  return <span className={`status-badge status-badge-${status.toLowerCase()}`}>{formatStatus(status)}</span>;
}

function formatStatus(status: string) {
  switch (status) {
    case 'ON_SALE':
      return '판매중';
    case 'SOLD_OUT':
      return '판매완료';
    case 'RESERVED':
      return '예약중';
    case 'HIDDEN':
      return '숨김';
    case 'CREATED':
      return '주문 생성';
    case 'PAID':
      return '결제 완료';
    case 'SHIPPING':
      return '배송 중';
    case 'DELIVERED':
      return '배송 완료';
    case 'CONFIRMED':
      return '구매 확정';
    case 'CANCELED':
      return '취소';
    case 'REFUND_REQUESTED':
      return '환불 요청';
    case 'REFUNDED':
      return '환불 완료';
    case 'READY':
      return '대기';
    case 'REQUESTED':
      return '요청';
    case 'APPROVED':
      return '승인';
    case 'REJECTED':
      return '거절';
    case 'COMPLETED':
      return '완료';
    case 'FAILED':
      return '실패';
    case 'PERSONAL':
      return '개인 상점';
    case 'BUSINESS':
      return '사업자 상점';
    case 'PENDING':
      return '확인 중';
    case 'ACTIVE':
      return '활성';
    case 'SUSPENDED':
      return '운영 중지';
    default:
      return status;
  }
}
