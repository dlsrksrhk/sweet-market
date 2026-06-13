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
    default:
      return status;
  }
}
