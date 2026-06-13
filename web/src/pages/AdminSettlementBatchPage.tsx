import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  getSettlementBatchExecution,
  getSettlementBatchExecutions,
  runSettlementBatch,
  type RunSettlementBatchInput,
  type SettlementBatchRunResult,
} from '../features/admin/adminBatchApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const EXECUTION_HISTORY_SIZE = 20;
const MAX_LIMIT = 1000;
const MAX_CHUNK_SIZE = 100;

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

type BatchFormValues = {
  confirmedBefore: string;
  limit: number;
  chunkSize: number;
};

export function AdminSettlementBatchPage() {
  const queryClient = useQueryClient();
  const [selectedExecutionId, setSelectedExecutionId] = useState<number | null>(null);
  const [lastRunResult, setLastRunResult] = useState<SettlementBatchRunResult | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

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

  const limitValue = watch('limit');
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

  const isRunning = isSubmitting || runMutation.isPending;
  const executions = executionListQuery.data ?? [];

  return (
    <section className="admin-batch-page">
      <div className="list-page-header">
        <h1>정산 배치</h1>
        <p>구매 확정된 거래의 정산 배치를 실행하고 최근 실행 결과를 확인합니다.</p>
      </div>

      <div className="admin-batch-layout">
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

function toLocalDateTimeInputValue(date: Date) {
  const offsetDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);

  return offsetDate.toISOString().slice(0, 16);
}

function normalizeLocalDateTime(value: string) {
  return value.length === 16 ? `${value}:00` : value;
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

function toErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? '정산 배치 요청을 처리하지 못했습니다.';
}
