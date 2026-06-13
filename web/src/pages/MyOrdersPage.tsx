import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { completeDelivery, startDelivery } from '../features/deliveries/deliveryApi';
import { cancelOrder, confirmOrder, getMyOrders, type OrderSummary } from '../features/orders/orderApi';
import { approvePayment, cancelPayment } from '../features/payments/paymentApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

type OrderMutation = {
  isPending: boolean;
  variables?: OrderSummary;
};

export function MyOrdersPage() {
  const queryClient = useQueryClient();
  const { data, error, isLoading } = useQuery({
    queryKey: ['my-orders'],
    queryFn: getMyOrders,
  });

  const approveMutation = useMutation({
    mutationFn: (order: OrderSummary) => approvePayment(order.id),
    onSuccess: (_payment, order) => invalidateOrderResources(queryClient, order.productId),
  });
  const cancelOrderMutation = useMutation({
    mutationFn: (order: OrderSummary) => cancelOrder(order.id),
    onSuccess: (_canceledOrder, order) => invalidateOrderResources(queryClient, order.productId),
  });
  const cancelPaymentMutation = useMutation({
    mutationFn: (order: OrderSummary) => cancelPayment(order.id),
    onSuccess: (_payment, order) => invalidateOrderResources(queryClient, order.productId),
  });
  const startDeliveryMutation = useMutation({
    mutationFn: (order: OrderSummary) => startDelivery(order.id),
    onSuccess: (_delivery, order) => invalidateOrderResources(queryClient, order.productId),
  });
  const completeDeliveryMutation = useMutation({
    mutationFn: (order: OrderSummary) => completeDelivery(order.id),
    onSuccess: (_delivery, order) => invalidateOrderResources(queryClient, order.productId),
  });
  const confirmMutation = useMutation({
    mutationFn: (order: OrderSummary) => confirmOrder(order.id),
    onSuccess: (_confirmedOrder, order) => invalidateOrderResources(queryClient, order.productId),
  });

  const actionError =
    approveMutation.error ??
    cancelOrderMutation.error ??
    cancelPaymentMutation.error ??
    startDeliveryMutation.error ??
    completeDeliveryMutation.error ??
    confirmMutation.error;

  if (isLoading) {
    return <p className="status-text">주문 목록을 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="주문 목록을 불러오지 못했습니다." />;
  }

  const orders = data?.content ?? [];

  function renderOrderActions(order: OrderSummary, pending: boolean) {
    switch (order.status) {
      case 'CREATED':
        return (
          <>
            <button type="button" className="text-button" disabled={pending} onClick={() => approveMutation.mutate(order)}>
              결제 승인
            </button>
            <button
              type="button"
              className="text-button danger-button"
              disabled={pending}
              onClick={() => cancelOrderMutation.mutate(order)}
            >
              주문 취소
            </button>
          </>
        );
      case 'PAID':
        return (
          <>
            <button type="button" className="text-button" disabled={pending} onClick={() => startDeliveryMutation.mutate(order)}>
              배송 시작
            </button>
            <button
              type="button"
              className="text-button danger-button"
              disabled={pending}
              onClick={() => cancelPaymentMutation.mutate(order)}
            >
              결제 취소
            </button>
          </>
        );
      case 'SHIPPING':
        return (
          <button type="button" className="text-button" disabled={pending} onClick={() => completeDeliveryMutation.mutate(order)}>
            배송 완료
          </button>
        );
      case 'DELIVERED':
        return (
          <button type="button" className="text-button" disabled={pending} onClick={() => confirmMutation.mutate(order)}>
            구매 확정
          </button>
        );
      case 'CONFIRMED':
      case 'CANCELED':
        return <span className="muted-text">진행 가능한 작업이 없습니다.</span>;
    }
  }

  return (
    <section className="list-page">
      <div className="list-page-header">
        <h1>내 주문</h1>
        <p>구매 거래의 결제, 배송, 구매 확정을 진행합니다.</p>
      </div>
      {actionError ? <p className="error-text">{toErrorMessage(actionError)}</p> : null}
      {orders.length === 0 ? (
        <EmptyState title="아직 주문한 상품이 없습니다" description="마음에 드는 상품을 주문하면 이곳에서 거래를 진행할 수 있습니다." />
      ) : (
        <div className="record-list" aria-label="내 주문 목록">
          {orders.map((order) => {
            const pending = isPendingForOrder(order, [
              approveMutation,
              cancelOrderMutation,
              cancelPaymentMutation,
              startDeliveryMutation,
              completeDeliveryMutation,
              confirmMutation,
            ]);

            return (
              <article className="record-card" key={order.id}>
                <div className="record-main">
                  <StatusPill status={order.status} />
                  <h2>{order.productTitle}</h2>
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
                      <StatusPill status={order.productStatus} />
                    </dd>
                  </div>
                  <div>
                    <dt>주문일시</dt>
                    <dd>{formatDate(order.orderedAt)}</dd>
                  </div>
                </dl>
                <div className="record-actions">{renderOrderActions(order, pending)}</div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

async function invalidateOrderResources(queryClient: QueryClient, productId: number) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
    queryClient.invalidateQueries({ queryKey: ['products'] }),
    queryClient.invalidateQueries({ queryKey: ['products', productId] }),
  ]);
}

function isPendingForOrder(order: OrderSummary, mutations: OrderMutation[]) {
  return mutations.some((mutation) => mutation.isPending && mutation.variables?.id === order.id);
}

function StatusPill({ status }: { status: string }) {
  return <span className={`status-badge status-badge-${status.toLowerCase()}`}>{formatStatus(status)}</span>;
}

function formatStatus(status: string) {
  switch (status) {
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
    case 'ON_SALE':
      return '판매중';
    case 'RESERVED':
      return '예약중';
    case 'SOLD_OUT':
      return '판매완료';
    case 'HIDDEN':
      return '숨김';
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

function toErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? '거래 요청을 처리하지 못했습니다.';
}
