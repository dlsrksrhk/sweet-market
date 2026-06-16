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

export type AdminSettlementStatus = 'READY' | 'COMPLETED' | 'FAILED';

export type AdminSettlementSummary = {
  settlementId: number;
  orderId: number;
  sellerId: number;
  sellerNickname: string;
  productId: number;
  productTitle: string;
  amount: number;
  status: AdminSettlementStatus;
  settledAt: string | null;
};

export type AdminSettlementDetail = AdminSettlementSummary & {
  orderStatus: string;
  confirmedAt: string | null;
  buyerId: number;
  buyerNickname: string;
};

export type AdminSettlementPage = {
  content: AdminSettlementSummary[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
};

export type AdminSettlementSearchInput = {
  orderId?: number;
  sellerId?: number;
  status?: AdminSettlementStatus | '';
  settledFrom?: string;
  settledTo?: string;
  page: number;
  size: number;
};

export type AdminSettlementRetryResultCode =
  | 'CREATED'
  | 'ALREADY_SETTLED'
  | 'ORDER_NOT_CONFIRMED'
  | 'ORDER_NOT_FOUND'
  | 'BATCH_FAILED';

export type AdminSettlementRetryResult = {
  resultCode: AdminSettlementRetryResultCode;
  orderId: number;
  settlementId: number | null;
  jobExecutionId: number | null;
  message: string;
};

function appendOptionalParam(searchParams: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && value !== '') {
    searchParams.set(key, String(value));
  }
}

export function getAdminSettlements(input: AdminSettlementSearchInput) {
  const searchParams = new URLSearchParams();
  appendOptionalParam(searchParams, 'orderId', input.orderId);
  appendOptionalParam(searchParams, 'sellerId', input.sellerId);
  appendOptionalParam(searchParams, 'status', input.status);
  appendOptionalParam(searchParams, 'settledFrom', input.settledFrom);
  appendOptionalParam(searchParams, 'settledTo', input.settledTo);
  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));

  return api<AdminSettlementPage>(`/api/admin/settlements?${searchParams.toString()}`);
}

export function getAdminSettlementDetail(settlementId: number) {
  return api<AdminSettlementDetail>(`/api/admin/settlements/${settlementId}`);
}

export function retryAdminSettlement(orderId: number) {
  return api<AdminSettlementRetryResult>('/api/admin/settlements/retry', {
    method: 'POST',
    body: JSON.stringify({ orderId }),
  });
}

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
