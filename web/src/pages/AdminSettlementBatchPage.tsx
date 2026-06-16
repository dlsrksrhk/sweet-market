import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  getAdminSettlementDetail,
  getAdminSettlements,
  getSettlementBatchExecution,
  getSettlementBatchExecutions,
  retryAdminSettlement,
  runOrderAutoConfirm,
  runSettlementBatch,
  type AdminSettlementDetail,
  type AdminSettlementRetryResult,
  type AdminSettlementSearchInput,
  type AdminSettlementStatus,
  type OrderAutoConfirmResult,
  type RunSettlementBatchInput,
  type SettlementBatchRunResult,
} from '../features/admin/adminBatchApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const EXECUTION_HISTORY_SIZE = 20;
const SETTLEMENT_SEARCH_PAGE_SIZE = 10;
const MAX_LIMIT = 1000;
const MAX_CHUNK_SIZE = 100;

const currencyFormatter = new Intl.NumberFormat('ko-KR');
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

type BatchFormValues = {
  confirmedBefore: string;
  limit: number;
  chunkSize: number;
};

type SettlementSearchFormValues = {
  orderId: string;
  sellerId: string;
  status: AdminSettlementStatus | '';
  settledFrom: string;
  settledTo: string;
};

type SettlementRetryFormValues = {
  orderId: string;
};

export function AdminSettlementBatchPage() {
  const queryClient = useQueryClient();
  const [selectedExecutionId, setSelectedExecutionId] = useState<number | null>(null);
  const [settlementSearchInput, setSettlementSearchInput] = useState<AdminSettlementSearchInput>({
    page: 0,
    size: SETTLEMENT_SEARCH_PAGE_SIZE,
  });
  const [selectedSettlementId, setSelectedSettlementId] = useState<number | null>(null);
  const [lastRunResult, setLastRunResult] = useState<SettlementBatchRunResult | null>(null);
  const [lastAutoConfirmResult, setLastAutoConfirmResult] = useState<OrderAutoConfirmResult | null>(null);
  const [lastRetryResult, setLastRetryResult] = useState<AdminSettlementRetryResult | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [autoConfirmError, setAutoConfirmError] = useState<string | null>(null);
  const [retryError, setRetryError] = useState<string | null>(null);

  const defaultValues = useMemo<BatchFormValues>(
    () => ({
      confirmedBefore: toLocalDateTimeInputValue(new Date()),
      limit: 100,
      chunkSize: 20,
    }),
    [],
  );

  const {
    formState: { errors, isSubmitting },
    handleSubmit,
    register,
    watch,
  } = useForm<BatchFormValues>({ defaultValues });
  const settlementSearchForm = useForm<SettlementSearchFormValues>({
    defaultValues: { orderId: '', sellerId: '', status: '', settledFrom: '', settledTo: '' },
  });
  const settlementRetryForm = useForm<SettlementRetryFormValues>({
    defaultValues: { orderId: '' },
  });

  const limitValue = watch('limit');
  const settlementSearchQuery = useQuery({
    queryKey: ['admin-settlements', 'search', settlementSearchInput],
    queryFn: () => getAdminSettlements(settlementSearchInput),
  });
  const settlementDetailQuery = useQuery({
    queryKey: ['admin-settlements', 'detail', selectedSettlementId],
    queryFn: () => getAdminSettlementDetail(selectedSettlementId ?? 0),
    enabled: selectedSettlementId !== null,
  });
  const executionListQuery = useQuery({
    queryKey: ['admin-settlement-batch-executions', 'list', EXECUTION_HISTORY_SIZE],
    queryFn: () => getSettlementBatchExecutions(EXECUTION_HISTORY_SIZE),
  });
  const executionDetailQuery = useQuery({
    queryKey: ['admin-settlement-batch-executions', 'detail', selectedExecutionId],
    queryFn: () => getSettlementBatchExecution(selectedExecutionId ?? 0),
    enabled: selectedExecutionId !== null,
  });
  const runMutation = useMutation({
    mutationFn: (input: RunSettlementBatchInput) => runSettlementBatch(input),
    onSuccess: async (result) => {
      setLastRunResult(result);
      setSelectedExecutionId(result.jobExecutionId);
      await queryClient.invalidateQueries({ queryKey: ['admin-settlement-batch-executions'] });
    },
  });
  const autoConfirmMutation = useMutation({
    mutationFn: runOrderAutoConfirm,
    onSuccess: async (result) => {
      setLastAutoConfirmResult(result);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
      ]);
    },
  });
  const retrySettlementMutation = useMutation({
    mutationFn: retryAdminSettlement,
    onSuccess: async (result) => {
      setLastRetryResult(result);
      setRetryError(null);

      if (result.settlementId !== null) {
        setSelectedSettlementId(result.settlementId);
      }

      if (result.resultCode === 'CREATED') {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ['admin-settlements'] }),
          queryClient.invalidateQueries({ queryKey: ['admin-settlement-batch-executions'] }),
          queryClient.invalidateQueries({ queryKey: ['my-settlements'] }),
        ]);
      }
    },
  });

  const onSettlementSearch = settlementSearchForm.handleSubmit((values) => {
    const nextInput: AdminSettlementSearchInput = {
      page: 0,
      size: SETTLEMENT_SEARCH_PAGE_SIZE,
    };
    const orderId = toOptionalNumber(values.orderId);
    const sellerId = toOptionalNumber(values.sellerId);
    const settledFrom = normalizeOptionalLocalDateTime(values.settledFrom);
    const settledTo = normalizeOptionalLocalDateTime(values.settledTo);

    if (orderId !== undefined) {
      nextInput.orderId = orderId;
    }

    if (sellerId !== undefined) {
      nextInput.sellerId = sellerId;
    }

    if (values.status !== '') {
      nextInput.status = values.status;
    }

    if (settledFrom !== undefined) {
      nextInput.settledFrom = settledFrom;
    }

    if (settledTo !== undefined) {
      nextInput.settledTo = settledTo;
    }

    setSelectedSettlementId(null);
    setSettlementSearchInput(nextInput);
  });

  const onSettlementRetry = settlementRetryForm.handleSubmit(async (values) => {
    setRetryError(null);
    setLastRetryResult(null);

    const orderId = toOptionalNumber(values.orderId);
    if (orderId === undefined) {
      setRetryError('주문 번호를 숫자로 입력해주세요.');
      return;
    }

    try {
      await retrySettlementMutation.mutateAsync(orderId);
    } catch (caughtError) {
      setRetryError(toErrorMessage(caughtError, '단건 정산 재처리 요청을 처리하지 못했습니다.'));
    }
  });

  const moveSettlementPage = (page: number) => {
    setSettlementSearchInput((current) => ({ ...current, page }));
  };

  const fillRetryOrderId = (orderId: number) => {
    settlementRetryForm.setValue('orderId', String(orderId), { shouldDirty: true, shouldValidate: true });
  };

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);

    try {
      await runMutation.mutateAsync({
        confirmedBefore: normalizeLocalDateTime(values.confirmedBefore),
        limit: values.limit,
        chunkSize: values.chunkSize,
      });
    } catch (caughtError) {
      setSubmitError(toErrorMessage(caughtError));
    }
  });

  const handleAutoConfirm = async () => {
    setAutoConfirmError(null);

    try {
      await autoConfirmMutation.mutateAsync();
    } catch (caughtError) {
      setAutoConfirmError(toErrorMessage(caughtError, '자동 구매 확정 요청을 처리하지 못했습니다.'));
    }
  };

  const isRunning = isSubmitting || runMutation.isPending;
  const executions = executionListQuery.data ?? [];
  const settlements = settlementSearchQuery.data?.content ?? [];
  const currentSettlementPage = settlementSearchQuery.data?.number ?? settlementSearchInput.page;
  const totalSettlementPages = settlementSearchQuery.data?.totalPages ?? 0;
  const hasPreviousSettlementPage = currentSettlementPage > 0;
  const hasNextSettlementPage = totalSettlementPages > currentSettlementPage + 1;

  return (
    <section className="admin-batch-page">
      <div className="list-page-header">
        <h1>정산 배치</h1>
        <p>구매 확정된 거래의 정산 배치를 실행하고 최근 실행 결과를 확인합니다.</p>
      </div>

      <div className="admin-batch-layout">
        <div className="admin-batch-sidebar">
          <section className="admin-tool-panel" aria-labelledby="settlement-batch-run-title">
            <h2 id="settlement-batch-run-title">배치 실행</h2>
            <form className="admin-batch-form" onSubmit={onSubmit}>
              <label>
                확정 기준 일시
                <input
                  type="datetime-local"
                  {...register('confirmedBefore', {
                    required: '확정 기준 일시를 입력해주세요.',
                  })}
                />
                {errors.confirmedBefore ? <span className="error-text">{errors.confirmedBefore.message}</span> : null}
              </label>
              <label>
                처리 한도
                <input
                  type="number"
                  min="1"
                  max={MAX_LIMIT}
                  step="1"
                  {...register('limit', {
                    required: '처리 한도를 입력해주세요.',
                    min: { value: 1, message: '처리 한도는 1 이상이어야 합니다.' },
                    max: { value: MAX_LIMIT, message: `처리 한도는 ${MAX_LIMIT} 이하여야 합니다.` },
                    valueAsNumber: true,
                    validate: (value) => Number.isInteger(value) || '처리 한도는 정수로 입력해주세요.',
                  })}
                />
                {errors.limit ? <span className="error-text">{errors.limit.message}</span> : null}
              </label>
              <label>
                청크 크기
                <input
                  type="number"
                  min="1"
                  max={MAX_CHUNK_SIZE}
                  step="1"
                  {...register('chunkSize', {
                    required: '청크 크기를 입력해주세요.',
                    min: { value: 1, message: '청크 크기는 1 이상이어야 합니다.' },
                    max: { value: MAX_CHUNK_SIZE, message: `청크 크기는 ${MAX_CHUNK_SIZE} 이하여야 합니다.` },
                    valueAsNumber: true,
                    validate: (value) => {
                      if (!Number.isInteger(value)) {
                        return '청크 크기는 정수로 입력해주세요.';
                      }

                      if (!isValidBatchInteger(value, MAX_CHUNK_SIZE) || !isValidBatchInteger(limitValue, MAX_LIMIT)) {
                        return true;
                      }

                      return value <= limitValue || '청크 크기는 처리 한도보다 클 수 없습니다.';
                    },
                  })}
                />
                {errors.chunkSize ? <span className="error-text">{errors.chunkSize.message}</span> : null}
              </label>
              {submitError ? <p className="error-text">{submitError}</p> : null}
              <button type="submit" className="text-button" disabled={isRunning}>
                {isRunning ? '실행 중' : '정산 배치 실행'}
              </button>
            </form>

            {lastRunResult ? (
              <div className="admin-result-panel" aria-live="polite">
                <h3>최근 실행 요청</h3>
                <dl className="compact-definition-list">
                  <div>
                    <dt>실행 ID</dt>
                    <dd>{lastRunResult.jobExecutionId}</dd>
                  </div>
                  <div>
                    <dt>작업명</dt>
                    <dd>{lastRunResult.jobName}</dd>
                  </div>
                  <div>
                    <dt>상태</dt>
                    <dd>
                      <StatusBadge status={lastRunResult.status} />
                    </dd>
                  </div>
                  <div>
                    <dt>기준 일시</dt>
                    <dd>{formatParameterDate(lastRunResult.parameters.confirmedBefore)}</dd>
                  </div>
                  <div>
                    <dt>한도 / 청크</dt>
                    <dd>
                      {lastRunResult.parameters.limit} / {lastRunResult.parameters.chunkSize}
                    </dd>
                  </div>
                </dl>
              </div>
            ) : null}
          </section>

          <section className="admin-tool-panel" aria-labelledby="order-auto-confirm-title">
            <h2 id="order-auto-confirm-title">자동 구매 확정</h2>
            <p className="status-text">배송 완료 후 7일이 지난 거래를 구매 확정 상태로 전환합니다.</p>
            {autoConfirmError ? <p className="error-text">{autoConfirmError}</p> : null}
            <button
              type="button"
              className="text-button"
              disabled={autoConfirmMutation.isPending}
              onClick={handleAutoConfirm}
            >
              {autoConfirmMutation.isPending ? '실행 중' : '자동 구매 확정 실행'}
            </button>

            {lastAutoConfirmResult ? (
              <div className="admin-result-panel" aria-live="polite">
                <h3>최근 자동 확정 결과</h3>
                <dl className="compact-definition-list">
                  <div>
                    <dt>확정 건수</dt>
                    <dd>{lastAutoConfirmResult.confirmedCount}</dd>
                  </div>
                  <div>
                    <dt>기준 기간</dt>
                    <dd>{lastAutoConfirmResult.thresholdDays}일</dd>
                  </div>
                  <div>
                    <dt>배송 완료 기준</dt>
                    <dd>{formatParameterDate(lastAutoConfirmResult.deliveredBefore)}</dd>
                  </div>
                  <div>
                    <dt>실행 일시</dt>
                    <dd>{formatParameterDate(lastAutoConfirmResult.executedAt)}</dd>
                  </div>
                </dl>
              </div>
            ) : null}
          </section>
        </div>

        <section className="admin-tool-panel" aria-labelledby="settlement-batch-history-title">
          <h2 id="settlement-batch-history-title">최근 실행 내역</h2>
          {executionListQuery.isLoading ? <p className="status-text">실행 내역을 불러오고 있습니다.</p> : null}
          {executionListQuery.error ? <ErrorState message="실행 내역을 불러오지 못했습니다." /> : null}
          {!executionListQuery.isLoading && !executionListQuery.error && executions.length === 0 ? (
            <EmptyState title="실행 내역이 없습니다" description="정산 배치를 실행하면 최근 작업 결과가 이곳에 표시됩니다." />
          ) : null}
          {executions.length > 0 ? (
            <div className="admin-execution-list" aria-label="최근 정산 배치 실행 목록">
              {executions.map((execution) => (
                <button
                  type="button"
                  className={`admin-execution-row ${
                    selectedExecutionId === execution.executionId ? 'admin-execution-row-selected' : ''
                  }`}
                  key={execution.executionId}
                  onClick={() => setSelectedExecutionId(execution.executionId)}
                >
                  <span className="admin-execution-title">
                    #{execution.executionId} {execution.jobName}
                  </span>
                  <span>
                    <StatusBadge status={execution.status} />
                  </span>
                  <span className="admin-execution-meta">종료 코드 {execution.exitCode}</span>
                  <span className="admin-execution-meta">생성 {formatDate(execution.createTime)}</span>
                  <span className="admin-execution-meta">시작 {formatDate(execution.startTime)}</span>
                  <span className="admin-execution-meta">종료 {formatDate(execution.endTime)}</span>
                </button>
              ))}
            </div>
          ) : null}
        </section>
      </div>

      <section className="admin-tool-panel" aria-labelledby="settlement-search-title">
        <div className="admin-panel-heading-row">
          <div>
            <h2 id="settlement-search-title">정산 검색</h2>
            <p className="status-text">주문, 판매자, 상태, 정산 일시 기준으로 정산 건을 조회합니다.</p>
          </div>
          <span className="admin-execution-meta">페이지당 {SETTLEMENT_SEARCH_PAGE_SIZE}건</span>
        </div>
        <form className="admin-search-form" onSubmit={onSettlementSearch}>
          <label>
            주문 번호
            <input
              type="text"
              inputMode="numeric"
              placeholder="orderId"
              {...settlementSearchForm.register('orderId', {
                validate: (value) => value.trim() === '' || toOptionalNumber(value) !== undefined || '숫자로 입력해주세요.',
              })}
            />
            {settlementSearchForm.formState.errors.orderId ? (
              <span className="error-text">{settlementSearchForm.formState.errors.orderId.message}</span>
            ) : null}
          </label>
          <label>
            판매자 번호
            <input
              type="text"
              inputMode="numeric"
              placeholder="sellerId"
              {...settlementSearchForm.register('sellerId', {
                validate: (value) => value.trim() === '' || toOptionalNumber(value) !== undefined || '숫자로 입력해주세요.',
              })}
            />
            {settlementSearchForm.formState.errors.sellerId ? (
              <span className="error-text">{settlementSearchForm.formState.errors.sellerId.message}</span>
            ) : null}
          </label>
          <label>
            상태
            <select {...settlementSearchForm.register('status')}>
              <option value="">전체</option>
              <option value="READY">대기</option>
              <option value="COMPLETED">완료</option>
              <option value="FAILED">실패</option>
            </select>
          </label>
          <label>
            정산 시작
            <input type="datetime-local" {...settlementSearchForm.register('settledFrom')} />
          </label>
          <label>
            정산 종료
            <input type="datetime-local" {...settlementSearchForm.register('settledTo')} />
          </label>
          <button type="submit" className="text-button">
            검색
          </button>
        </form>

        {settlementSearchQuery.isLoading ? <p className="status-text">정산 내역을 불러오고 있습니다.</p> : null}
        {settlementSearchQuery.error ? <ErrorState message="정산 내역을 불러오지 못했습니다." /> : null}
        {!settlementSearchQuery.isLoading && !settlementSearchQuery.error && settlements.length === 0 ? (
          <EmptyState title="정산 내역이 없습니다" description="검색 조건에 맞는 정산 건이 없습니다." />
        ) : null}
        {settlements.length > 0 ? (
          <>
            <div className="admin-settlement-table" aria-label="관리자 정산 검색 결과">
              <div className="admin-settlement-table-head" role="row">
                <span>정산 ID</span>
                <span>주문 ID</span>
                <span>판매자</span>
                <span>상품</span>
                <span>금액</span>
                <span>상태</span>
                <span>정산일</span>
              </div>
              {settlements.map((settlement) => (
                <button
                  type="button"
                  className={`admin-settlement-row ${
                    selectedSettlementId === settlement.settlementId ? 'admin-settlement-row-selected' : ''
                  }`}
                  key={settlement.settlementId}
                  onClick={() => {
                    setSelectedSettlementId(settlement.settlementId);
                    fillRetryOrderId(settlement.orderId);
                  }}
                >
                  <span>#{settlement.settlementId}</span>
                  <span>#{settlement.orderId}</span>
                  <span>
                    #{settlement.sellerId} {settlement.sellerNickname}
                  </span>
                  <span>
                    #{settlement.productId} {settlement.productTitle}
                  </span>
                  <span>{currencyFormatter.format(settlement.amount)}원</span>
                  <span>
                    <StatusBadge status={settlement.status} />
                  </span>
                  <span>{formatDate(settlement.settledAt)}</span>
                </button>
              ))}
            </div>
            <div className="admin-pagination">
              <button
                type="button"
                className="text-button"
                disabled={!hasPreviousSettlementPage || settlementSearchQuery.isFetching}
                onClick={() => moveSettlementPage(currentSettlementPage - 1)}
              >
                이전
              </button>
              <span>
                {currentSettlementPage + 1} / {Math.max(totalSettlementPages, 1)}
              </span>
              <button
                type="button"
                className="text-button"
                disabled={!hasNextSettlementPage || settlementSearchQuery.isFetching}
                onClick={() => moveSettlementPage(currentSettlementPage + 1)}
              >
                다음
              </button>
            </div>
          </>
        ) : null}
      </section>

      <div className="admin-batch-layout">
        <AdminSettlementDetailPanel
          detail={settlementDetailQuery.data}
          error={settlementDetailQuery.error}
          isLoading={settlementDetailQuery.isLoading}
          selectedSettlementId={selectedSettlementId}
          onUseOrderId={fillRetryOrderId}
        />

        <section className="admin-tool-panel" aria-labelledby="settlement-retry-title">
          <div className="admin-panel-heading-row">
            <h2 id="settlement-retry-title">단건 정산 재처리</h2>
          </div>
          <form className="admin-batch-form" onSubmit={onSettlementRetry}>
            <label>
              주문 번호
              <input
                type="text"
                inputMode="numeric"
                {...settlementRetryForm.register('orderId', {
                  required: '주문 번호를 입력해주세요.',
                  validate: (value) => toOptionalNumber(value) !== undefined || '주문 번호를 숫자로 입력해주세요.',
                })}
              />
              {settlementRetryForm.formState.errors.orderId ? (
                <span className="error-text">{settlementRetryForm.formState.errors.orderId.message}</span>
              ) : null}
            </label>
            {retryError ? <p className="error-text">{retryError}</p> : null}
            <button type="submit" className="text-button" disabled={retrySettlementMutation.isPending}>
              {retrySettlementMutation.isPending ? '재처리 중' : '정산 재처리'}
            </button>
          </form>

          {lastRetryResult ? (
            <div className="admin-result-panel" aria-live="polite">
              <h3>최근 재처리 결과</h3>
              <p className="status-text">{formatRetryResult(lastRetryResult)}</p>
              <dl className="compact-definition-list">
                <div>
                  <dt>결과 코드</dt>
                  <dd>{lastRetryResult.resultCode}</dd>
                </div>
                <div>
                  <dt>주문 ID</dt>
                  <dd>{lastRetryResult.orderId}</dd>
                </div>
                <div>
                  <dt>정산 ID</dt>
                  <dd>{formatNullableNumber(lastRetryResult.settlementId)}</dd>
                </div>
                <div>
                  <dt>실행 ID</dt>
                  <dd>{formatNullableNumber(lastRetryResult.jobExecutionId)}</dd>
                </div>
                <div>
                  <dt>메시지</dt>
                  <dd>{lastRetryResult.message}</dd>
                </div>
              </dl>
            </div>
          ) : null}
        </section>
      </div>

      <section className="admin-tool-panel" aria-labelledby="settlement-batch-detail-title">
        <h2 id="settlement-batch-detail-title">실행 상세</h2>
        {selectedExecutionId === null ? <p className="status-text">실행 내역을 선택하면 상세 정보가 표시됩니다.</p> : null}
        {executionDetailQuery.isLoading ? <p className="status-text">실행 상세를 불러오고 있습니다.</p> : null}
        {executionDetailQuery.error ? <ErrorState message="실행 상세를 불러오지 못했습니다." /> : null}
        {executionDetailQuery.data ? (
          <div className="admin-detail-grid">
            <dl className="compact-definition-list">
              <div>
                <dt>실행 ID</dt>
                <dd>{executionDetailQuery.data.executionId}</dd>
              </div>
              <div>
                <dt>작업명</dt>
                <dd>{executionDetailQuery.data.jobName}</dd>
              </div>
              <div>
                <dt>상태</dt>
                <dd>
                  <StatusBadge status={executionDetailQuery.data.status} />
                </dd>
              </div>
              <div>
                <dt>종료 코드</dt>
                <dd>{executionDetailQuery.data.exitCode}</dd>
              </div>
              <div>
                <dt>생성 / 시작 / 종료</dt>
                <dd>
                  {formatDate(executionDetailQuery.data.createTime)} / {formatDate(executionDetailQuery.data.startTime)} /{' '}
                  {formatDate(executionDetailQuery.data.endTime)}
                </dd>
              </div>
            </dl>
            <dl className="compact-definition-list">
              <div>
                <dt>기준 일시</dt>
                <dd>{formatParameterDate(executionDetailQuery.data.parameters.confirmedBefore)}</dd>
              </div>
              <div>
                <dt>처리 한도</dt>
                <dd>{formatNullableNumber(executionDetailQuery.data.parameters.limit)}</dd>
              </div>
              <div>
                <dt>청크 크기</dt>
                <dd>{formatNullableNumber(executionDetailQuery.data.parameters.chunkSize)}</dd>
              </div>
            </dl>
            {executionDetailQuery.data.step ? (
              <dl className="admin-count-grid">
                <div>
                  <dt>읽음</dt>
                  <dd>{executionDetailQuery.data.step.readCount}</dd>
                </div>
                <div>
                  <dt>쓰기</dt>
                  <dd>{executionDetailQuery.data.step.writeCount}</dd>
                </div>
                <div>
                  <dt>스킵</dt>
                  <dd>{executionDetailQuery.data.step.skipCount}</dd>
                </div>
                <div>
                  <dt>롤백</dt>
                  <dd>{executionDetailQuery.data.step.rollbackCount}</dd>
                </div>
              </dl>
            ) : (
              <p className="status-text">단계 집계가 없습니다.</p>
            )}
            {executionDetailQuery.data.failureMessages.length > 0 ? (
              <div className="admin-failure-list">
                <h3>실패 메시지</h3>
                <ul>
                  {executionDetailQuery.data.failureMessages.map((message, index) => (
                    <li key={`${index}-${message}`}>{message}</li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>
        ) : null}
      </section>
    </section>
  );
}

type AdminSettlementDetailPanelProps = {
  detail: AdminSettlementDetail | undefined;
  error: unknown;
  isLoading: boolean;
  selectedSettlementId: number | null;
  onUseOrderId: (orderId: number) => void;
};

function AdminSettlementDetailPanel({
  detail,
  error,
  isLoading,
  selectedSettlementId,
  onUseOrderId,
}: AdminSettlementDetailPanelProps) {
  return (
    <section className="admin-tool-panel" aria-labelledby="settlement-detail-title">
      <div className="admin-panel-heading-row">
        <h2 id="settlement-detail-title">정산 상세</h2>
        {detail ? (
          <button type="button" className="text-button" onClick={() => onUseOrderId(detail.orderId)}>
            재처리 주문 사용
          </button>
        ) : null}
      </div>
      {selectedSettlementId === null ? <p className="status-text">정산 행을 선택하면 상세 정보가 표시됩니다.</p> : null}
      {isLoading ? <p className="status-text">정산 상세를 불러오고 있습니다.</p> : null}
      {error ? <ErrorState message="정산 상세를 불러오지 못했습니다." /> : null}
      {selectedSettlementId !== null && !isLoading && !error && !detail ? (
        <p className="status-text">선택한 정산 정보를 찾을 수 없습니다.</p>
      ) : null}
      {detail ? (
        <dl className="compact-definition-list">
          <div>
            <dt>정산 ID</dt>
            <dd>{detail.settlementId}</dd>
          </div>
          <div>
            <dt>주문 상태</dt>
            <dd>
              <StatusBadge status={detail.orderStatus} />
            </dd>
          </div>
          <div>
            <dt>구매 확정</dt>
            <dd>{formatDate(detail.confirmedAt)}</dd>
          </div>
          <div>
            <dt>판매자</dt>
            <dd>
              #{detail.sellerId} {detail.sellerNickname}
            </dd>
          </div>
          <div>
            <dt>구매자</dt>
            <dd>
              #{detail.buyerId} {detail.buyerNickname}
            </dd>
          </div>
          <div>
            <dt>상품</dt>
            <dd>
              #{detail.productId} {detail.productTitle}
            </dd>
          </div>
        </dl>
      ) : null}
    </section>
  );
}

function toLocalDateTimeInputValue(date: Date) {
  const offsetDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);

  return offsetDate.toISOString().slice(0, 16);
}

function normalizeLocalDateTime(value: string) {
  return value.length === 16 ? `${value}:00` : value;
}

function normalizeOptionalLocalDateTime(value: string) {
  const trimmedValue = value.trim();

  if (trimmedValue === '') {
    return undefined;
  }

  return normalizeLocalDateTime(trimmedValue);
}

function toOptionalNumber(value: string) {
  const trimmedValue = value.trim();

  if (trimmedValue === '') {
    return undefined;
  }

  const parsedValue = Number(trimmedValue);

  return Number.isInteger(parsedValue) && parsedValue > 0 ? parsedValue : undefined;
}

function isValidBatchInteger(value: number, max: number) {
  return Number.isInteger(value) && value >= 1 && value <= max;
}

function formatDate(value: string | null) {
  if (!value) {
    return '-';
  }

  return dateFormatter.format(new Date(value));
}

function formatParameterDate(value: string | null) {
  return value ? value.replace('T', ' ') : '-';
}

function formatNullableNumber(value: number | null) {
  return value === null ? '-' : value;
}

function formatRetryResult(result: AdminSettlementRetryResult) {
  switch (result.resultCode) {
    case 'CREATED':
      return '정산이 생성되었습니다.';
    case 'ALREADY_SETTLED':
      return '이미 정산된 주문입니다.';
    case 'ORDER_NOT_CONFIRMED':
      return '구매 확정되지 않은 주문입니다.';
    case 'ORDER_NOT_FOUND':
      return '주문을 찾을 수 없습니다.';
    case 'BATCH_FAILED':
      return '정산 배치가 실패했습니다.';
    default:
      return result.message;
  }
}

function toErrorMessage(error: unknown, fallbackMessage = '정산 배치 요청을 처리하지 못했습니다.') {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
