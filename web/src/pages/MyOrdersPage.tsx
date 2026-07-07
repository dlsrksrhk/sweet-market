import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { type FormEvent, useRef, useState } from 'react';
import { completeDelivery, startDelivery } from '../features/deliveries/deliveryApi';
import { useAuth } from '../features/auth/AuthProvider';
import { cancelOrder, confirmOrder, createRefundRequest, getMyOrders, type OrderSummary } from '../features/orders/orderApi';
import { approvePayment } from '../features/payments/paymentApi';
import { createReview } from '../features/reviews/reviewApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

type OrderAction = 'approve-payment' | 'cancel-order' | 'start-delivery' | 'complete-delivery' | 'confirm-order';
type ReviewMutationInput = {
  order: OrderSummary;
  rating: number;
  content: string;
};
type RefundRequestMutationInput = {
  order: OrderSummary;
  reason: string;
};

export function MyOrdersPage() {
  const { member } = useAuth();
  const memberId = member?.id;
  const queryClient = useQueryClient();
  const pendingOrderActionIdsRef = useRef(new Set<string>());
  const [pendingOrderActionIds, setPendingOrderActionIds] = useState(() => new Set<string>());
  const [reviewingOrderId, setReviewingOrderId] = useState<number | null>(null);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewContent, setReviewContent] = useState('');
  const [refundingOrderId, setRefundingOrderId] = useState<number | null>(null);
  const [refundReason, setRefundReason] = useState('');
  const { data, error, isLoading } = useQuery({
    queryKey: ['my-orders', memberId],
    queryFn: getMyOrders,
    enabled: memberId !== undefined,
  });

  const approveMutation = useMutation({
    mutationFn: (order: OrderSummary) => approvePayment(order.id),
    onSuccess: (_payment, order) => invalidateOrderResources(queryClient, order.productId),
  });
  const cancelOrderMutation = useMutation({
    mutationFn: (order: OrderSummary) => cancelOrder(order.id),
    onSuccess: (_canceledOrder, order) => invalidateOrderResources(queryClient, order.productId),
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
  const refundRequestMutation = useMutation({
    mutationFn: ({ order, reason }: RefundRequestMutationInput) => createRefundRequest(order.id, reason),
    onSuccess: async (_refundRequest, { order }) => {
      resetRefundRequestForm();
      await invalidateOrderResources(queryClient, order.productId);
    },
  });
  const reviewMutation = useMutation({
    mutationFn: ({ order, rating, content }: ReviewMutationInput) => createReview(order.id, { rating, content }),
    onSuccess: async (_review, { order }) => {
      resetReviewForm();
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['products', order.productId] }),
        queryClient.invalidateQueries({ queryKey: ['product-reviews', order.productId] }),
      ]);
    },
  });

  const actionError =
    approveMutation.error ??
    cancelOrderMutation.error ??
    startDeliveryMutation.error ??
    completeDeliveryMutation.error ??
    confirmMutation.error ??
    refundRequestMutation.error ??
    reviewMutation.error;

  if (isLoading) {
    return <p className="status-text">주문 목록을 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="주문 목록을 불러오지 못했습니다." />;
  }

  const orders = data?.content ?? [];

  function setOrderActionPending(actionId: string, pending: boolean) {
    const nextPendingOrderActionIds = new Set(pendingOrderActionIdsRef.current);

    if (pending) {
      nextPendingOrderActionIds.add(actionId);
    } else {
      nextPendingOrderActionIds.delete(actionId);
    }

    pendingOrderActionIdsRef.current = nextPendingOrderActionIds;
    setPendingOrderActionIds(nextPendingOrderActionIds);
  }

  function runOrderAction(order: OrderSummary, action: OrderAction, mutateAsync: (order: OrderSummary) => Promise<unknown>) {
    const actionId = getOrderActionId(order.id, action);

    if (pendingOrderActionIdsRef.current.has(actionId)) {
      return;
    }

    setOrderActionPending(actionId, true);
    void mutateAsync(order)
      .catch(() => undefined)
      .finally(() => setOrderActionPending(actionId, false));
  }

  function isOrderActionPending(orderId: number, action: OrderAction) {
    return pendingOrderActionIds.has(getOrderActionId(orderId, action));
  }

  function startReview(orderId: number) {
    reviewMutation.reset();
    setReviewingOrderId(orderId);
    setReviewRating(5);
    setReviewContent('');
  }

  function resetReviewForm() {
    setReviewingOrderId(null);
    setReviewRating(5);
    setReviewContent('');
    reviewMutation.reset();
  }

  function startRefundRequest(orderId: number) {
    refundRequestMutation.reset();
    setRefundingOrderId(orderId);
    setRefundReason('');
  }

  function resetRefundRequestForm() {
    setRefundingOrderId(null);
    setRefundReason('');
    refundRequestMutation.reset();
  }

  function submitReview(event: FormEvent<HTMLFormElement>, order: OrderSummary) {
    event.preventDefault();

    if (reviewMutation.isPending) {
      return;
    }

    reviewMutation.mutate({ order, rating: reviewRating, content: reviewContent });
  }

  function submitRefundRequest(event: FormEvent<HTMLFormElement>, order: OrderSummary) {
    event.preventDefault();

    if (refundRequestMutation.isPending) {
      return;
    }

    refundRequestMutation.mutate({ order, reason: refundReason.trim() });
  }

  function renderOrderActions(order: OrderSummary) {
    switch (order.status) {
      case 'CREATED':
        const approvePending = isOrderActionPending(order.id, 'approve-payment');
        const cancelOrderPending = isOrderActionPending(order.id, 'cancel-order');

        return (
          <>
            <button
              type="button"
              className="text-button"
              disabled={approvePending}
              onClick={() => runOrderAction(order, 'approve-payment', approveMutation.mutateAsync)}
            >
              결제 승인
            </button>
            <button
              type="button"
              className="text-button danger-button"
              disabled={cancelOrderPending}
              onClick={() => runOrderAction(order, 'cancel-order', cancelOrderMutation.mutateAsync)}
            >
              주문 취소
            </button>
          </>
        );
      case 'PAID':
        const startDeliveryPending = isOrderActionPending(order.id, 'start-delivery');
        const paidCancelOrderPending = isOrderActionPending(order.id, 'cancel-order');

        return (
          <>
            <button
              type="button"
              className="text-button"
              disabled={startDeliveryPending}
              onClick={() => runOrderAction(order, 'start-delivery', startDeliveryMutation.mutateAsync)}
            >
              배송 시작
            </button>
            <button
              type="button"
              className="text-button danger-button"
              disabled={paidCancelOrderPending}
              onClick={() => runOrderAction(order, 'cancel-order', cancelOrderMutation.mutateAsync)}
            >
              주문 취소
            </button>
          </>
        );
      case 'SHIPPING':
        const completeDeliveryPending = isOrderActionPending(order.id, 'complete-delivery');

        return (
          <button
            type="button"
            className="text-button"
            disabled={completeDeliveryPending}
            onClick={() => runOrderAction(order, 'complete-delivery', completeDeliveryMutation.mutateAsync)}
          >
            배송 완료
          </button>
        );
      case 'DELIVERED':
        const confirmPending = isOrderActionPending(order.id, 'confirm-order');
        const refundRequestPending = refundRequestMutation.isPending && refundRequestMutation.variables?.order.id === order.id;
        const refundFormOpen = refundingOrderId === order.id;

        return (
          <>
            <button
              type="button"
              className="text-button"
              disabled={confirmPending || refundFormOpen || refundRequestPending}
              onClick={() => runOrderAction(order, 'confirm-order', confirmMutation.mutateAsync)}
            >
              구매 확정
            </button>
            {order.refundStatus ? null : (
              <button
                type="button"
                className="text-button danger-button"
                disabled={refundRequestPending}
                onClick={() => startRefundRequest(order.id)}
              >
                환불 요청
              </button>
            )}
          </>
        );
      case 'REFUND_REQUESTED':
        return <span className="muted-text">환불 요청 처리 중</span>;
      case 'REFUNDED':
        return <span className="muted-text">환불 완료</span>;
      case 'CONFIRMED':
        if (order.reviewed) {
          return <span className="muted-text">리뷰 작성 완료</span>;
        }

        return (
          <button
            type="button"
            className="text-button"
            onClick={() => startReview(order.id)}
          >
            리뷰 작성
          </button>
        );
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
                {renderRefundStatusNote(order)}
                <div className="record-actions">{renderOrderActions(order)}</div>
                {refundingOrderId === order.id ? (
                  <form className="review-form" onSubmit={(event) => submitRefundRequest(event, order)}>
                    <label>
                      환불 사유
                      <textarea
                        value={refundReason}
                        minLength={10}
                        maxLength={500}
                        required
                        rows={4}
                        disabled={refundRequestMutation.isPending}
                        onChange={(event) => setRefundReason(event.target.value)}
                      />
                    </label>
                    <div className="review-form-actions">
                      <button type="submit" className="text-button" disabled={refundRequestMutation.isPending}>
                        {refundRequestMutation.isPending ? '요청 중' : '요청'}
                      </button>
                      <button
                        type="button"
                        className="text-button secondary-button"
                        disabled={refundRequestMutation.isPending}
                        onClick={resetRefundRequestForm}
                      >
                        취소
                      </button>
                    </div>
                  </form>
                ) : null}
                {reviewingOrderId === order.id ? (
                  <form className="review-form" onSubmit={(event) => submitReview(event, order)}>
                    <fieldset className="rating-field" disabled={reviewMutation.isPending}>
                      <legend>평점</legend>
                      {[1, 2, 3, 4, 5].map((rating) => (
                        <button
                          type="button"
                          className={`rating-button${rating <= reviewRating ? ' rating-button-selected' : ''}`}
                          aria-pressed={rating <= reviewRating}
                          onClick={() => setReviewRating(rating)}
                          key={rating}
                        >
                          {rating}
                        </button>
                      ))}
                    </fieldset>
                    <label>
                      리뷰 내용
                      <textarea
                        value={reviewContent}
                        minLength={10}
                        maxLength={500}
                        required
                        rows={4}
                        disabled={reviewMutation.isPending}
                        onChange={(event) => setReviewContent(event.target.value)}
                      />
                    </label>
                    <div className="review-form-actions">
                      <button type="submit" className="text-button" disabled={reviewMutation.isPending}>
                        {reviewMutation.isPending ? '등록 중' : '등록'}
                      </button>
                      <button
                        type="button"
                        className="text-button secondary-button"
                        disabled={reviewMutation.isPending}
                        onClick={resetReviewForm}
                      >
                        취소
                      </button>
                    </div>
                  </form>
                ) : null}
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

function getOrderActionId(orderId: number, action: OrderAction) {
  return `${orderId}:${action}`;
}

function renderRefundStatusNote(order: OrderSummary) {
  if (!order.refundStatus) {
    return null;
  }

  return (
    <p className="status-text">
      <StatusPill status={order.refundStatus} /> {formatRefundStatusNote(order)}
    </p>
  );
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
    case 'REFUND_REQUESTED':
      return '환불 요청';
    case 'REFUNDED':
      return '환불 완료';
    case 'REQUESTED':
      return '요청';
    case 'APPROVED':
      return '승인';
    case 'REJECTED':
      return '거절';
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

function formatRefundStatusNote(order: OrderSummary) {
  switch (order.refundStatus) {
    case 'REQUESTED':
      return `환불 요청일시 ${formatDate(order.refundRequestedAt)}`;
    case 'APPROVED':
      return `환불 처리일시 ${formatDate(order.refundHandledAt)}`;
    case 'REJECTED':
      return `환불 거절 사유: ${order.refundRejectReason ?? '확인되지 않았습니다.'}`;
    default:
      return '';
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
