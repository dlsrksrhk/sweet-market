import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  getMyRefundRequests,
  type RefundRequest,
  type RefundRequestPage,
  type RefundRequestSearchInput,
  type RefundRequestStatusFilter,
} from '../features/refunds/refundApi';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const REFUND_REQUEST_PAGE_SIZE = 10;

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

const statusFilters: RefundRequestStatusFilter[] = ['ALL', 'REQUESTED', 'APPROVED', 'REJECTED'];

export function MyRefundHistoryPage() {
  const [searchInput, setSearchInput] = useState<RefundRequestSearchInput>({
    status: 'ALL',
    page: 0,
    size: REFUND_REQUEST_PAGE_SIZE,
  });

  const refundRequestsQuery = useQuery({
    queryKey: ['refund-history', 'buyer', searchInput],
    queryFn: () => getMyRefundRequests(searchInput),
  });

  const refundRequests = refundRequestsQuery.data?.content ?? [];
  const shouldShowPagination = Boolean(refundRequestsQuery.data && refundRequestsQuery.data.totalElements > 0);

  function changeStatusFilter(status: RefundRequestStatusFilter) {
    setSearchInput({
      status,
      page: 0,
      size: REFUND_REQUEST_PAGE_SIZE,
    });
  }

  function movePage(page: number) {
    setSearchInput((current) => ({ ...current, page }));
  }

  return (
    <section className="refund-operations-page">
      <div className="list-page-header">
        <h1>환불 내역</h1>
        <p>내가 요청한 환불의 접수 상태와 처리 결과를 확인합니다.</p>
      </div>

      <div className="admin-panel-heading-row">
        <div>
          <h2>내 환불 요청</h2>
          <p className="status-text">전체 내역을 기본으로 보고 상태별로 좁혀볼 수 있습니다.</p>
        </div>
        <span className="admin-execution-meta">페이지당 {REFUND_REQUEST_PAGE_SIZE}건</span>
      </div>

      {renderStatusFilter(searchInput.status, changeStatusFilter)}

      {refundRequestsQuery.isLoading ? <p className="status-text">환불 내역을 불러오고 있습니다.</p> : null}
      {refundRequestsQuery.error ? <ErrorState message="환불 내역을 불러오지 못했습니다." /> : null}
      {!refundRequestsQuery.isLoading && !refundRequestsQuery.error && refundRequests.length === 0 ? (
        <EmptyState title="환불 내역이 없습니다" description="배송 완료 주문에서 환불을 요청하면 이곳에서 처리 상태를 확인할 수 있습니다." />
      ) : null}

      {refundRequests.length > 0 ? (
        <div className="refund-operations-table" aria-label="내 환불 요청 목록">
          <div className="refund-operations-table-head refund-operations-grid" role="row">
            <span>환불 요청</span>
            <span>상품</span>
            <span>판매자</span>
            <span>상태</span>
            <span>요청일</span>
            <span>이동</span>
          </div>
          {refundRequests.map((refundRequest) => renderRefundHistoryRow(refundRequest))}
        </div>
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
    <div className="admin-search-form" aria-label="환불 내역 상태 필터">
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

function renderRefundHistoryRow(refundRequest: RefundRequest) {
  return (
    <article className="refund-operations-row" key={refundRequest.id}>
      <div className="refund-operations-grid">
        <span>#{refundRequest.id}</span>
        <span>
          #{refundRequest.productId} {refundRequest.productTitle}
        </span>
        <span>
          #{refundRequest.sellerId} {refundRequest.sellerNickname}
        </span>
        <span>
          <StatusBadge status={refundRequest.status} />
        </span>
        <span>{formatDate(refundRequest.requestedAt)}</span>
        <span>
          <Link className="text-button secondary-button" to={`/products/${refundRequest.productId}`}>
            상품
          </Link>
          <Link className="text-button secondary-button" to="/me/orders">
            주문
          </Link>
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
    </article>
  );
}

function renderPagination(data: RefundRequestPage, isFetching: boolean, onMovePage: (page: number) => void) {
  const currentPage = data.number;
  const totalPages = data.totalPages;
  const displayTotalPages = Math.max(totalPages, 1);
  const displayPage = Math.min(currentPage + 1, displayTotalPages);
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
        {displayPage} / {displayTotalPages}
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
    case 'ALL':
      return '전체';
    case 'REQUESTED':
      return '요청';
    case 'APPROVED':
      return '승인';
    case 'REJECTED':
      return '거절';
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
