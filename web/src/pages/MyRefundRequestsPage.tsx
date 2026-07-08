import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import {
  approveSellerRefundRequest,
  getSellerRefundRequests,
  rejectSellerRefundRequest,
  type RefundRequest,
  type RefundRequestPage,
  type RefundRequestSearchInput,
  type RefundRequestStatusFilter,
} from '../features/refunds/refundApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const REFUND_REQUEST_PAGE_SIZE = 10;

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

type RejectMutationInput = {
  refundRequestId: number;
  rejectReason: string;
};

const statusFilters: RefundRequestStatusFilter[] = ['REQUESTED', 'APPROVED', 'REJECTED', 'ALL'];

export function MyRefundRequestsPage() {
  const queryClient = useQueryClient();
  const [searchInput, setSearchInput] = useState<RefundRequestSearchInput>({
    status: 'REQUESTED',
    page: 0,
    size: REFUND_REQUEST_PAGE_SIZE,
  });
  const [rejectingRefundRequestId, setRejectingRefundRequestId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const refundRequestsQuery = useQuery({
    queryKey: ['refund-operations', 'seller', searchInput],
    queryFn: () => getSellerRefundRequests(searchInput),
  });

  const approveMutation = useMutation({
    mutationFn: approveSellerRefundRequest,
    onSuccess: invalidateRefundOperationResources,
  });
  const rejectMutation = useMutation({
    mutationFn: ({ refundRequestId, rejectReason }: RejectMutationInput) =>
      rejectSellerRefundRequest(refundRequestId, rejectReason),
    onSuccess: async () => {
      resetRejectForm();
      await invalidateRefundOperationResources();
    },
  });

  const refundRequests = refundRequestsQuery.data?.content ?? [];
  const shouldShowPagination = Boolean(refundRequestsQuery.data && refundRequestsQuery.data.totalElements > 0);
  const isActionPending = approveMutation.isPending || rejectMutation.isPending;
  const actionError = approveMutation.error ?? rejectMutation.error;

  function invalidateRefundOperationResources() {
    return Promise.all([
      queryClient.invalidateQueries({ queryKey: ['refund-operations'] }),
      queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
      queryClient.invalidateQueries({ queryKey: ['admin-operations', 'orders'] }),
    ]);
  }

  function changeStatusFilter(status: RefundRequestStatusFilter) {
    approveMutation.reset();
    rejectMutation.reset();
    resetRejectForm();
    setSearchInput({
      status,
      page: 0,
      size: REFUND_REQUEST_PAGE_SIZE,
    });
  }

  function movePage(page: number) {
    approveMutation.reset();
    rejectMutation.reset();
    resetRejectForm();
    setSearchInput((current) => ({ ...current, page }));
  }

  function startReject(refundRequestId: number) {
    if (isActionPending) {
      return;
    }

    approveMutation.reset();
    rejectMutation.reset();
    setRejectingRefundRequestId(refundRequestId);
    setRejectReason('');
  }

  function resetRejectForm() {
    setRejectingRefundRequestId(null);
    setRejectReason('');
    rejectMutation.reset();
  }

  function approveRefundRequest(refundRequestId: number) {
    if (isActionPending) {
      return;
    }

    approveMutation.reset();
    rejectMutation.reset();

    if (!window.confirm(`환불 요청 #${refundRequestId}을 승인하시겠습니까?`)) {
      return;
    }

    resetRejectForm();
    approveMutation.mutate(refundRequestId);
  }

  function submitReject(event: FormEvent<HTMLFormElement>, refundRequestId: number) {
    event.preventDefault();

    if (isActionPending) {
      return;
    }

    rejectMutation.reset();
    rejectMutation.mutate({ refundRequestId, rejectReason: rejectReason.trim() });
  }

  return (
    <section className="refund-operations-page">
      <div className="list-page-header">
        <h1>환불 요청 관리</h1>
        <p>판매 상품의 환불 요청을 검토하고 승인 또는 거절 처리합니다.</p>
      </div>

      <div className="admin-panel-heading-row">
        <div>
          <h2>판매자 환불 요청</h2>
          <p className="status-text">상태별로 환불 요청을 확인합니다.</p>
        </div>
        <span className="admin-execution-meta">페이지당 {REFUND_REQUEST_PAGE_SIZE}건</span>
      </div>

      {renderStatusFilter(searchInput.status, changeStatusFilter)}

      {actionError ? <p className="error-text">{toErrorMessage(actionError)}</p> : null}
      {refundRequestsQuery.isLoading ? <p className="status-text">환불 요청 목록을 불러오고 있습니다.</p> : null}
      {refundRequestsQuery.error ? <ErrorState message="환불 요청 목록을 불러오지 못했습니다." /> : null}
      {!refundRequestsQuery.isLoading && !refundRequestsQuery.error && refundRequests.length === 0 ? (
        <EmptyState title="환불 요청이 없습니다" description="선택한 조건에 맞는 환불 요청이 없습니다." />
      ) : null}

      {refundRequests.length > 0 ? (
        <>
          <div className="refund-operations-table" aria-label="판매자 환불 요청 목록">
            <div className="refund-operations-table-head refund-operations-grid" role="row">
              <span>환불 요청</span>
              <span>상품</span>
              <span>구매자</span>
              <span>상태</span>
              <span>요청일</span>
              <span>처리</span>
            </div>
            {refundRequests.map((refundRequest) =>
              renderRefundRequestRow({
                refundRequest,
                rejectingRefundRequestId,
                rejectReason,
                approvePendingRefundRequestId: approveMutation.variables,
                rejectPendingRefundRequestId: rejectMutation.isPending ? rejectMutation.variables?.refundRequestId : undefined,
                isApproving: approveMutation.isPending,
                isActionPending,
                onApprove: approveRefundRequest,
                onStartReject: startReject,
                onCancelReject: resetRejectForm,
                onRejectReasonChange: setRejectReason,
                onSubmitReject: submitReject,
              }),
            )}
          </div>
        </>
      ) : null}
      {shouldShowPagination && refundRequestsQuery.data ? (
        renderPagination(refundRequestsQuery.data, refundRequestsQuery.isFetching, movePage)
      ) : null}
    </section>
  );
}

function renderStatusFilter(
  selectedStatus: RefundRequestStatusFilter,
  onChangeStatus: (status: RefundRequestStatusFilter) => void,
) {
  return (
    <div className="admin-search-form" aria-label="환불 요청 상태 필터">
      <label>
        상태
        <select value={selectedStatus} onChange={(event) => onChangeStatus(event.target.value as RefundRequestStatusFilter)}>
          {statusFilters.map((status) => (
            <option value={status} key={status}>
              {formatStatusFilter(status)}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}

type RefundRequestRowProps = {
  refundRequest: RefundRequest;
  rejectingRefundRequestId: number | null;
  rejectReason: string;
  approvePendingRefundRequestId: number | undefined;
  rejectPendingRefundRequestId: number | undefined;
  isApproving: boolean;
  isActionPending: boolean;
  onApprove: (refundRequestId: number) => void;
  onStartReject: (refundRequestId: number) => void;
  onCancelReject: () => void;
  onRejectReasonChange: (rejectReason: string) => void;
  onSubmitReject: (event: FormEvent<HTMLFormElement>, refundRequestId: number) => void;
};

function renderRefundRequestRow({
  refundRequest,
  rejectingRefundRequestId,
  rejectReason,
  approvePendingRefundRequestId,
  rejectPendingRefundRequestId,
  isApproving,
  isActionPending,
  onApprove,
  onStartReject,
  onCancelReject,
  onRejectReasonChange,
  onSubmitReject,
}: RefundRequestRowProps) {
  const isRejectFormOpen = rejectingRefundRequestId === refundRequest.id;
  const isApprovePending = isApproving && approvePendingRefundRequestId === refundRequest.id;
  const isRejectPending = rejectPendingRefundRequestId === refundRequest.id;
  const isRejectReasonInvalid = rejectReason.trim().length < 5 || rejectReason.trim().length > 500;

  return (
    <article className="refund-operations-row" key={refundRequest.id}>
      <div className="refund-operations-grid">
        <span>#{refundRequest.id}</span>
        <span>
          #{refundRequest.productId} {refundRequest.productTitle}
        </span>
        <span>
          #{refundRequest.buyerId} {refundRequest.buyerNickname}
        </span>
        <span>
          <StatusBadge status={refundRequest.status} />
        </span>
        <span>{formatDate(refundRequest.requestedAt)}</span>
        <span>
          {renderRefundRequestActions(
            refundRequest,
            isApprovePending,
            isRejectPending,
            isRejectFormOpen,
            isActionPending,
            onApprove,
            onStartReject,
          )}
        </span>
      </div>
      <dl className="refund-operation-detail">
        <div>
          <dt>주문 번호</dt>
          <dd>#{refundRequest.orderId}</dd>
        </div>
        <div>
          <dt>환불 사유</dt>
          <dd>{refundRequest.reason}</dd>
        </div>
        <div>
          <dt>처리자</dt>
          <dd>{formatNullableNumber(refundRequest.handledById)}</dd>
        </div>
        <div>
          <dt>처리일시</dt>
          <dd>{formatDate(refundRequest.handledAt)}</dd>
        </div>
        <div>
          <dt>거절 사유</dt>
          <dd>{refundRequest.rejectReason ?? '-'}</dd>
        </div>
      </dl>
      {isRejectFormOpen ? (
        <form className="refund-reject-form" onSubmit={(event) => onSubmitReject(event, refundRequest.id)}>
          <label>
            거절 사유
            <textarea
              value={rejectReason}
              minLength={5}
              maxLength={500}
              required
              rows={4}
              disabled={isActionPending}
              onChange={(event) => onRejectReasonChange(event.target.value)}
            />
          </label>
          <div className="refund-reject-actions">
            <button type="submit" className="text-button danger-button" disabled={isActionPending || isRejectReasonInvalid}>
              {isRejectPending ? '거절 처리 중' : '거절 처리'}
            </button>
            <button type="button" className="text-button secondary-button" disabled={isActionPending} onClick={onCancelReject}>
              취소
            </button>
          </div>
        </form>
      ) : null}
    </article>
  );
}

function renderRefundRequestActions(
  refundRequest: RefundRequest,
  isApprovePending: boolean,
  isRejectPending: boolean,
  isRejectFormOpen: boolean,
  isActionPending: boolean,
  onApprove: (refundRequestId: number) => void,
  onStartReject: (refundRequestId: number) => void,
) {
  if (refundRequest.status !== 'REQUESTED') {
    return <span className="muted-text">처리 완료</span>;
  }

  return (
    <>
      <button
        type="button"
        className="text-button"
        disabled={isActionPending || isRejectFormOpen}
        onClick={() => onApprove(refundRequest.id)}
      >
        {isApprovePending ? '승인 중' : '승인'}
      </button>
      <button
        type="button"
        className="text-button danger-button"
        disabled={isActionPending || isRejectFormOpen}
        onClick={() => onStartReject(refundRequest.id)}
      >
        {isRejectPending ? '거절 중' : '거절'}
      </button>
    </>
  );
}

function renderPagination(data: RefundRequestPage, isFetching: boolean, onMovePage: (page: number) => void) {
  const currentPage = data.number;
  const totalPages = data.totalPages;
  const hasPreviousPage = currentPage > 0;
  const hasNextPage = totalPages > currentPage + 1;

  return (
    <div className="admin-pagination">
      <button
        type="button"
        className="text-button"
        disabled={!hasPreviousPage || isFetching}
        onClick={() => onMovePage(currentPage - 1)}
      >
        이전
      </button>
      <span>
        {currentPage + 1} / {Math.max(totalPages, 1)}
      </span>
      <button
        type="button"
        className="text-button"
        disabled={!hasNextPage || isFetching}
        onClick={() => onMovePage(currentPage + 1)}
      >
        다음
      </button>
    </div>
  );
}

function formatStatusFilter(status: RefundRequestStatusFilter) {
  switch (status) {
    case 'REQUESTED':
      return '요청';
    case 'APPROVED':
      return '승인';
    case 'REJECTED':
      return '거절';
    case 'ALL':
      return '전체';
  }
}

function formatNullableNumber(value: number | null) {
  return value === null ? '-' : `#${value}`;
}

function formatDate(value: string | null | undefined) {
  if (!value) {
    return '-';
  }

  return dateFormatter.format(new Date(value));
}

function toErrorMessage(error: unknown, fallbackMessage = '환불 요청을 처리하지 못했습니다.') {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
