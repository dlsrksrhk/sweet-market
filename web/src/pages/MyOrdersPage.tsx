import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { completeDelivery, startDelivery } from '../features/deliveries/deliveryApi';
import {
  cancelOrder,
  confirmOrder,
  getMyOrders,
  type OrderStatus,
  type OrderSummary,
} from '../features/orders/orderApi';
import { approvePayment, cancelPayment } from '../features/payments/paymentApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

type OrderAction = 'approve-payment' | 'cancel-order' | 'start-delivery' | 'cancel-payment' | 'complete-delivery' | 'confirm-order';

export function MyOrdersPage() {
  const queryClient = useQueryClient();
  const { data, error, isLoading } = useQuery({
    queryKey: ['my-orders'],
    queryFn: getMyOrders,
  });

  const actionMutation = useMutation({
    mutationFn: ({ action, orderId }: { action: OrderAction; orderId: number }) => runOrderAction(action, orderId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
      ]);
    },
  });

  if (isLoading) {
    return <p className="status-text">주문 내역을 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="주문 내역을 불러오지 못했습니다." />;
  }

  const orders = data?.content ?? [];

  return (
    <section className="list-page">
      <header className="list-page-header">
        <h1>내 주문</h1>
        <p>구매한 상품의 결제, 배송, 구매 확정을 진행할 수 있습니다.</p>
      </header>
      {orders.length === 0 ? (
        <EmptyState title="아직 주문이 없습니다" description="마음에 드는 상품을 주문하면 이곳에서 진행 상태를 볼 수 있습니다." />
      ) : (
        <div className="record-list" aria-label="내 주문 목록">
          {orders.map((order) => (
            <article className="record-card" key={order.id}>
              <div className="record-main">
                <StatusBadge status={order.status} />
                <h2>
                  <Link to={`/products/${order.productId}`}>{order.productTitle}</Link>
                </h2>
                <strong>{currencyFormatter.format(order.productPrice)}원</strong>
              </div>
              <dl className="record-meta">
                <div>
                  <dt>판매자</dt>
                  <dd>{order.sellerNickname}</dd>
                </div>
                <div>
                  <dt>상품 상태</dt>
                  <dd>
                    <StatusBadge status={order.productStatus} />
                  </dd>
                </div>
                <div>
                  <dt>주문일</dt>
                  <dd>{formatDateTime(order.orderedAt)}</dd>
                </div>
              </dl>
              <OrderActions
                disabled={actionMutation.isPending}
                order={order}
                onAction={(action) => actionMutation.mutate({ action, orderId: order.id })}
              />
            </article>
          ))}
        </div>
      )}
      {actionMutation.isError ? <p className="error-text">{toErrorMessage(actionMutation.error)}</p> : null}
    </section>
  );
}

type OrderActionsProps = {
  disabled: boolean;
  order: OrderSummary;
  onAction: (action: OrderAction) => void;
};

function OrderActions({ disabled, order, onAction }: OrderActionsProps) {
  const actions = getActions(order.status);

  if (actions.length === 0) {
    return <p className="status-text">추가로 진행할 작업이 없습니다.</p>;
  }

  return (
    <div className="record-actions">
      {actions.map((action) => (
        <button
          key={action.type}
          type="button"
          className={`text-button${action.danger ? ' danger-button' : ''}`}
          disabled={disabled}
          onClick={() => onAction(action.type)}
        >
          {action.label}
        </button>
      ))}
    </div>
  );
}

function getActions(status: OrderStatus): { type: OrderAction; label: string; danger?: boolean }[] {
  switch (status) {
    case 'CREATED':
      return [
        { type: 'approve-payment', label: '결제 승인' },
        { type: 'cancel-order', label: '주문 취소', danger: true },
      ];
    case 'PAID':
      return [
        { type: 'start-delivery', label: '배송 시작' },
        { type: 'cancel-payment', label: '결제 취소', danger: true },
      ];
    case 'SHIPPING':
      return [{ type: 'complete-delivery', label: '배송 완료' }];
    case 'DELIVERED':
      return [{ type: 'confirm-order', label: '구매 확정' }];
    case 'CONFIRMED':
    case 'CANCELED':
      return [];
  }
}

async function runOrderAction(action: OrderAction, orderId: number) {
  switch (action) {
    case 'approve-payment':
      await approvePayment(orderId);
      return;
    case 'cancel-order':
      await cancelOrder(orderId);
      return;
    case 'start-delivery':
      await startDelivery(orderId);
      return;
    case 'cancel-payment':
      await cancelPayment(orderId);
      return;
    case 'complete-delivery':
      await completeDelivery(orderId);
      return;
    case 'confirm-order':
      await confirmOrder(orderId);
      return;
  }
}

function formatDateTime(value: string | null) {
  if (!value) {
    return '-';
  }

  return new Date(value).toLocaleString('ko-KR');
}

function toErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;

  return apiError.message ?? '주문 작업을 처리하지 못했습니다.';
}
