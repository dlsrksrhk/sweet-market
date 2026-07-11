import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useMemo, useRef, useState } from 'react';
import {
  approveBusinessStore,
  getAdminBusinessStore,
  getAdminBusinessStores,
  reactivateBusinessStore,
  rejectBusinessStore,
  storeQueryKeys,
  suspendBusinessStore,
  type AdminBusinessStore,
  type AdminBusinessStoreSearchInput,
  type StoreStatus,
} from '../features/stores/storeApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const BUSINESS_STORE_PAGE_SIZE = 20;
const REJECTION_REASON_ID = 'business-store-rejection-reason';
const REJECTION_REASON_ERROR_ID = 'business-store-rejection-reason-error';

const storeStatuses: StoreStatus[] = ['PENDING', 'ACTIVE', 'REJECTED', 'SUSPENDED'];

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

type RejectMutationInput = {
  storeId: number;
  reason: string;
};

export function AdminBusinessStoresPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<StoreStatus | ''>('');
  const [page, setPage] = useState(0);
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(null);
  const [isRejectFormOpen, setIsRejectFormOpen] = useState(false);
  const [rejectionReason, setRejectionReason] = useState('');
  const [rejectionValidationError, setRejectionValidationError] = useState<string | null>(null);
  const actionLockRef = useRef(false);

  const searchInput = useMemo<AdminBusinessStoreSearchInput>(() => {
    const input: AdminBusinessStoreSearchInput = {
      page,
      size: BUSINESS_STORE_PAGE_SIZE,
    };

    if (status) {
      input.status = status;
    }

    return input;
  }, [page, status]);

  const listQuery = useQuery({
    queryKey: storeQueryKeys.adminList(searchInput),
    queryFn: () => getAdminBusinessStores(searchInput),
  });
  const detailQuery = useQuery({
    queryKey: storeQueryKeys.adminDetail(selectedStoreId ?? 0),
    queryFn: () => getAdminBusinessStore(selectedStoreId ?? 0),
    enabled: selectedStoreId !== null,
  });

  const approveMutation = useMutation({
    mutationFn: approveBusinessStore,
    onSuccess: (store) => handleMutationSuccess(store.storeId),
    onSettled: releaseActionLock,
  });
  const rejectMutation = useMutation({
    mutationFn: ({ storeId, reason }: RejectMutationInput) => rejectBusinessStore(storeId, reason),
    onSuccess: (store) => handleMutationSuccess(store.storeId),
    onSettled: releaseActionLock,
  });
  const suspendMutation = useMutation({
    mutationFn: suspendBusinessStore,
    onSuccess: (store) => handleMutationSuccess(store.storeId),
    onSettled: releaseActionLock,
  });
  const reactivateMutation = useMutation({
    mutationFn: reactivateBusinessStore,
    onSuccess: (store) => handleMutationSuccess(store.storeId),
    onSettled: releaseActionLock,
  });

  const stores = listQuery.data?.content ?? [];
  const isActionPending =
    approveMutation.isPending || rejectMutation.isPending || suspendMutation.isPending || reactivateMutation.isPending;
  const actionError =
    approveMutation.error ?? rejectMutation.error ?? suspendMutation.error ?? reactivateMutation.error;

  function handleMutationSuccess(storeId: number) {
    closeRejectForm();
    return Promise.all([
      queryClient.invalidateQueries({ queryKey: storeQueryKeys.admin() }),
      queryClient.invalidateQueries({ queryKey: storeQueryKeys.me() }),
      queryClient.invalidateQueries({ queryKey: storeQueryKeys.public(storeId) }),
    ]);
  }

  function clearSettledActionState() {
    if (actionLockRef.current || isActionPending) {
      return false;
    }

    approveMutation.reset();
    rejectMutation.reset();
    suspendMutation.reset();
    reactivateMutation.reset();
    closeRejectForm();
    return true;
  }

  function releaseActionLock() {
    actionLockRef.current = false;
  }

  function closeRejectForm() {
    setIsRejectFormOpen(false);
    setRejectionReason('');
    setRejectionValidationError(null);
  }

  function changeStatus(nextStatus: StoreStatus | '') {
    if (!clearSettledActionState()) {
      return;
    }

    setStatus(nextStatus);
    setPage(0);
    setSelectedStoreId(null);
  }

  function movePage(nextPage: number) {
    if (!clearSettledActionState()) {
      return;
    }

    setPage(nextPage);
    setSelectedStoreId(null);
  }

  function selectStore(storeId: number) {
    if (!clearSettledActionState()) {
      return;
    }

    setSelectedStoreId(storeId);
  }

  function startReject() {
    if (!clearSettledActionState()) {
      return;
    }

    setIsRejectFormOpen(true);
  }

  function cancelReject() {
    clearSettledActionState();
  }

  function submitReject(event: FormEvent<HTMLFormElement>, storeId: number) {
    event.preventDefault();

    if (actionLockRef.current || isActionPending) {
      return;
    }

    const reason = rejectionReason.trim();

    if (!reason) {
      setRejectionValidationError('거절 사유를 입력해주세요.');
      return;
    }

    setRejectionValidationError(null);
    actionLockRef.current = true;
    rejectMutation.mutate({ storeId, reason });
  }

  function runAction(action: 'approve' | 'suspend' | 'reactivate', storeId: number) {
    if (!clearSettledActionState()) {
      return;
    }

    actionLockRef.current = true;

    if (action === 'approve') {
      approveMutation.mutate(storeId);
    } else if (action === 'suspend') {
      suspendMutation.mutate(storeId);
    } else {
      reactivateMutation.mutate(storeId);
    }
  }

  return (
    <section className="admin-operations-page">
      <div className="list-page-header">
        <h1>사업자 상점 심사</h1>
        <p>사업자 상점 신청을 검토하고 상점 운영 상태를 관리합니다.</p>
      </div>

      <section className="admin-tool-panel" aria-labelledby="business-store-list-title">
        <div className="admin-panel-heading-row">
          <div>
            <h2 id="business-store-list-title">사업자 상점 목록</h2>
            <p className="status-text">상태별 신청 및 운영 상점을 조회합니다.</p>
          </div>
          <span className="admin-execution-meta">페이지당 {BUSINESS_STORE_PAGE_SIZE}건</span>
        </div>

        <div className="admin-search-form" aria-label="사업자 상점 상태 필터">
          <label>
            상태
            <select
              value={status}
              disabled={isActionPending}
              onChange={(event) => changeStatus(event.target.value as StoreStatus | '')}
            >
              <option value="">전체</option>
              {storeStatuses.map((storeStatus) => (
                <option value={storeStatus} key={storeStatus}>
                  {toStatusLabel(storeStatus)}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="admin-operations-section-grid">
          <div className="admin-operations-list">
            {listQuery.isLoading ? <p className="status-text">사업자 상점 목록을 불러오고 있습니다.</p> : null}
            {listQuery.isFetching && !listQuery.isLoading ? (
              <p className="status-text">사업자 상점 목록을 갱신하고 있습니다.</p>
            ) : null}
            {listQuery.error ? <ErrorState message="사업자 상점 목록을 불러오지 못했습니다." /> : null}
            {!listQuery.isLoading && !listQuery.error && stores.length === 0 ? (
              <EmptyState title="사업자 상점이 없습니다" description="선택한 상태에 해당하는 사업자 상점이 없습니다." />
            ) : null}
            {stores.length > 0 ? (
              <div className="admin-operations-table">
                <table
                  className="admin-business-store-table"
                  aria-label="사업자 상점 검색 결과"
                  aria-busy={listQuery.isFetching}
                >
                  <thead>
                    <tr>
                      <th scope="col">상점</th>
                      <th scope="col">소유 회원</th>
                      <th scope="col">상태</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stores.map((store) => {
                      const isSelected = selectedStoreId === store.storeId;

                      return (
                        <tr className={isSelected ? 'admin-operations-row-selected' : undefined} key={store.storeId}>
                          <td>
                            <button
                              type="button"
                              className="text-button"
                              aria-label={`#${store.storeId} ${store.publicName} 상점 선택`}
                              aria-pressed={isSelected}
                              disabled={isActionPending}
                              onClick={() => selectStore(store.storeId)}
                            >
                              #{store.storeId} {store.publicName}
                            </button>
                          </td>
                          <td>#{store.ownerMemberId}</td>
                          <td>
                            <StatusBadge status={store.status} />
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            ) : null}
            {listQuery.data && listQuery.data.totalElements > 0 ? (
              <Pagination
                currentPage={listQuery.data.number}
                totalPages={listQuery.data.totalPages}
                isFetching={listQuery.isFetching}
                isActionPending={isActionPending}
                onMovePage={movePage}
              />
            ) : null}
          </div>

          <BusinessStoreDetail
            detail={detailQuery.data}
            error={detailQuery.error}
            actionError={actionError}
            isLoading={detailQuery.isLoading}
            isActionPending={isActionPending}
            isRejectFormOpen={isRejectFormOpen}
            rejectionReason={rejectionReason}
            rejectionValidationError={rejectionValidationError}
            selectedStoreId={selectedStoreId}
            onAction={runAction}
            onCancelReject={cancelReject}
            onChangeRejectionReason={(reason) => {
              setRejectionReason(reason);
              setRejectionValidationError(null);
            }}
            onStartReject={startReject}
            onSubmitReject={submitReject}
          />
        </div>
      </section>
    </section>
  );
}

type BusinessStoreDetailProps = {
  detail: AdminBusinessStore | undefined;
  error: unknown;
  actionError: unknown;
  isLoading: boolean;
  isActionPending: boolean;
  isRejectFormOpen: boolean;
  rejectionReason: string;
  rejectionValidationError: string | null;
  selectedStoreId: number | null;
  onAction: (action: 'approve' | 'suspend' | 'reactivate', storeId: number) => void;
  onCancelReject: () => void;
  onChangeRejectionReason: (reason: string) => void;
  onStartReject: () => void;
  onSubmitReject: (event: FormEvent<HTMLFormElement>, storeId: number) => void;
};

function BusinessStoreDetail({
  detail,
  error,
  actionError,
  isLoading,
  isActionPending,
  isRejectFormOpen,
  rejectionReason,
  rejectionValidationError,
  selectedStoreId,
  onAction,
  onCancelReject,
  onChangeRejectionReason,
  onStartReject,
  onSubmitReject,
}: BusinessStoreDetailProps) {
  return (
    <aside className="admin-operations-detail" aria-labelledby="business-store-detail-title">
      <h3 id="business-store-detail-title">사업자 상점 상세</h3>
      {selectedStoreId === null ? <p className="status-text">상점 행을 선택하면 상세 정보가 표시됩니다.</p> : null}
      {isLoading ? <p className="status-text">사업자 상점 상세를 불러오고 있습니다.</p> : null}
      {error ? <ErrorState message="사업자 상점 상세를 불러오지 못했습니다." /> : null}
      {actionError ? (
        <p className="error-text" role="alert">
          {toErrorMessage(actionError)}
        </p>
      ) : null}
      {selectedStoreId !== null && !isLoading && !error && !detail ? (
        <p className="status-text">선택한 사업자 상점 정보를 찾을 수 없습니다.</p>
      ) : null}
      {detail ? (
        <>
          <dl className="compact-definition-list">
            <DetailItem label="상점 번호" value={`#${detail.storeId}`} />
            <DetailItem label="소유 회원" value={`#${detail.ownerMemberId}`} />
            <DetailItem label="상점 유형" value={detail.type === 'BUSINESS' ? '사업자 상점' : '개인 상점'} />
            <DetailItem label="상호명" value={detail.legalBusinessName} />
            <DetailItem label="사업자 등록번호" value={detail.businessRegistrationId} />
            <DetailItem label="공개 상점명" value={detail.publicName} />
            <DetailItem label="공개 소개" value={detail.introduction} />
            <div>
              <dt>상태</dt>
              <dd>
                <StatusBadge status={detail.status} />
              </dd>
            </div>
            <DetailItem label="거절 사유" value={detail.rejectionReason} />
            <DetailItem label="신청일시" value={formatDate(detail.createdAt)} />
            <DetailItem label="수정일시" value={formatDate(detail.updatedAt)} />
          </dl>

          <div className="admin-detail-actions">
            {renderActions(detail, isActionPending, isRejectFormOpen, onAction, onStartReject)}
          </div>

          {isRejectFormOpen && detail.status === 'PENDING' ? (
            <form className="admin-batch-form" onSubmit={(event) => onSubmitReject(event, detail.storeId)}>
              <label htmlFor={REJECTION_REASON_ID}>
                거절 사유
                <textarea
                  id={REJECTION_REASON_ID}
                  value={rejectionReason}
                  rows={4}
                  maxLength={1000}
                  required
                  disabled={isActionPending}
                  aria-describedby={rejectionValidationError ? REJECTION_REASON_ERROR_ID : undefined}
                  aria-invalid={Boolean(rejectionValidationError)}
                  onChange={(event) => onChangeRejectionReason(event.target.value)}
                />
              </label>
              {rejectionValidationError ? (
                <p className="error-text" id={REJECTION_REASON_ERROR_ID} role="alert">
                  {rejectionValidationError}
                </p>
              ) : null}
              <div className="admin-detail-actions">
                <button type="submit" className="text-button danger-button" disabled={isActionPending}>
                  {isActionPending ? '거절 처리 중' : '거절 처리'}
                </button>
                <button type="button" className="text-button secondary-button" disabled={isActionPending} onClick={onCancelReject}>
                  취소
                </button>
              </div>
            </form>
          ) : null}
        </>
      ) : null}
    </aside>
  );
}

type DetailItemProps = {
  label: string;
  value: string | null;
};

function DetailItem({ label, value }: DetailItemProps) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value || '-'}</dd>
    </div>
  );
}

function renderActions(
  detail: AdminBusinessStore,
  isActionPending: boolean,
  isRejectFormOpen: boolean,
  onAction: (action: 'approve' | 'suspend' | 'reactivate', storeId: number) => void,
  onStartReject: () => void,
) {
  if (detail.status === 'PENDING') {
    return (
      <>
        <button
          type="button"
          className="text-button"
          disabled={isActionPending || isRejectFormOpen}
          onClick={() => onAction('approve', detail.storeId)}
        >
          {isActionPending ? '처리 중' : '승인'}
        </button>
        <button
          type="button"
          className="text-button danger-button"
          disabled={isActionPending || isRejectFormOpen}
          onClick={onStartReject}
        >
          거절
        </button>
      </>
    );
  }

  if (detail.status === 'ACTIVE') {
    return (
      <button
        type="button"
        className="text-button danger-button"
        disabled={isActionPending}
        onClick={() => onAction('suspend', detail.storeId)}
      >
        {isActionPending ? '운영 중지 처리 중' : '운영 중지'}
      </button>
    );
  }

  if (detail.status === 'SUSPENDED') {
    return (
      <button
        type="button"
        className="text-button"
        disabled={isActionPending}
        onClick={() => onAction('reactivate', detail.storeId)}
      >
        {isActionPending ? '재활성화 처리 중' : '재활성화'}
      </button>
    );
  }

  return null;
}

type PaginationProps = {
  currentPage: number;
  totalPages: number;
  isFetching: boolean;
  isActionPending: boolean;
  onMovePage: (page: number) => void;
};

function Pagination({ currentPage, totalPages, isFetching, isActionPending, onMovePage }: PaginationProps) {
  const displayTotalPages = Math.max(totalPages, 1);
  const hasPreviousPage = currentPage > 0;
  const hasNextPage = totalPages > currentPage + 1;

  return (
    <div className="admin-pagination">
      <button
        type="button"
        className="text-button"
        disabled={!hasPreviousPage || isFetching || isActionPending}
        onClick={() => onMovePage(currentPage - 1)}
      >
        이전
      </button>
      <span>
        {Math.min(currentPage + 1, displayTotalPages)} / {displayTotalPages}
      </span>
      <button
        type="button"
        className="text-button"
        disabled={!hasNextPage || isFetching || isActionPending}
        onClick={() => onMovePage(currentPage + 1)}
      >
        다음
      </button>
    </div>
  );
}

function toStatusLabel(status: StoreStatus) {
  switch (status) {
    case 'PENDING':
      return '심사 대기';
    case 'ACTIVE':
      return '활성';
    case 'REJECTED':
      return '거절';
    case 'SUSPENDED':
      return '운영 중지';
  }
}

function formatDate(value: string) {
  return dateFormatter.format(new Date(value));
}

function toErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? '사업자 상점 상태를 변경하지 못했습니다.';
}
