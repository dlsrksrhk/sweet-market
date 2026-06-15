import { api } from '../../shared/api/http';

export type RunSettlementBatchInput = {
  confirmedBefore: string;
  limit: number;
  chunkSize: number;
};

export type SettlementBatchRunResult = {
  jobExecutionId: number;
  jobName: string;
  status: string;
  parameters: {
    confirmedBefore: string;
    limit: number;
    chunkSize: number;
  };
};

export type SettlementBatchExecutionSummary = {
  executionId: number;
  jobName: string;
  status: string;
  exitCode: string;
  createTime: string | null;
  startTime: string | null;
  endTime: string | null;
};

export type SettlementBatchExecutionDetail = SettlementBatchExecutionSummary & {
  parameters: {
    confirmedBefore: string | null;
    limit: number | null;
    chunkSize: number | null;
  };
  step: {
    readCount: number;
    writeCount: number;
    skipCount: number;
    rollbackCount: number;
  } | null;
  failureMessages: string[];
};

export type OrderAutoConfirmResult = {
  confirmedCount: number;
  deliveredBefore: string;
  thresholdDays: number;
  executedAt: string;
};

export function runSettlementBatch(input: RunSettlementBatchInput) {
  return api<SettlementBatchRunResult>('/api/admin/batches/settlements', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function runOrderAutoConfirm() {
  return api<OrderAutoConfirmResult>('/api/admin/orders/auto-confirm', {
    method: 'POST',
  });
}

export function getSettlementBatchExecutions(size = 20) {
  return api<SettlementBatchExecutionSummary[]>(`/api/admin/batches/settlements/executions?size=${size}`);
}

export function getSettlementBatchExecution(executionId: number) {
  return api<SettlementBatchExecutionDetail>(`/api/admin/batches/settlements/executions/${executionId}`);
}
