import { api } from '../../shared/api/http';
import type {
  DashboardPeriod,
  DashboardPeriodInput,
  DiscountAmountSummary,
  OutcomeReasonCount,
  Page,
  StoreCampaignAudit,
  StoreCampaignMetric,
  StoreInventoryPressure,
} from './storeOperationsDashboardApi';

export type AdminDashboardInput = DashboardPeriodInput & {
  storeId?: number;
};

export type ProjectionHealth = {
  pendingCount: number;
  retryCount: number;
  deadCount: number;
  oldestUnprocessedAt: string | null;
  projectionLagSeconds: number;
  projectionUpdatedAt: string | null;
};

export type AdminOperationsDashboard = {
  storeId: number | null;
  period: DashboardPeriod;
  generatedAt: string;
  projectionUpdatedAt: string | null;
  projectionLagSeconds: number;
  trackingStartedAt: string | null;
  claimSuccessCount: number;
  redemptionSuccessCount: number;
  orderSuccessCount: number;
  purchaseFailureCount: number;
  promotionDiscounts: DiscountAmountSummary;
  couponDiscounts: DiscountAmountSummary;
  lowStockCount: number;
  soldOutTransitionCount: number;
  auditCount: number;
  leadingFailureReasons: OutcomeReasonCount[];
  health: ProjectionHealth;
};

export type AdminCampaignInput = AdminDashboardInput & {
  ownerType?: string;
  campaignKind?: string;
  campaignStatus?: string;
  page: number;
  size: number;
};

export type AdminOutcomeMetric = {
  id: string;
  outcomeType: string;
  latestBucketStart: string;
  storeId: number | null;
  campaignKind: string | null;
  campaignId: number | null;
  ownerType: string | null;
  ownerStoreId: number | null;
  productId: number | null;
  reason: string;
  successCount: number;
  failureCount: number;
  reservationFailureCount: number;
};

export type AdminOutcomeInput = AdminDashboardInput & {
  ownerType?: string;
  campaignKind?: string;
  productId?: number;
  reason?: string;
  page: number;
  size: number;
};

export type AdminInventoryInput = AdminDashboardInput & {
  productId?: number;
  attentionOnly: boolean;
  page: number;
  size: number;
};

export type AdminAuditInput = AdminDashboardInput & {
  ownerType?: string;
  campaignKind?: string;
  page: number;
  size: number;
};

export type DeadOperationalEvent = {
  id: number;
  eventId: string;
  eventType: string;
  schemaVersion: number;
  aggregateType: string;
  aggregateId: number | null;
  aggregateVersion: number | null;
  storeId: number | null;
  campaignId: number | null;
  partitionKey: string;
  occurredAt: string;
  payload: unknown;
  deliveryState: 'DEAD';
  attemptCount: number;
  nextAttemptAt: string | null;
  lastError: string | null;
  createdAt: string;
};

export type EndpointMetric = {
  cacheMode: 'OFF' | 'ON';
  endpoint: string;
  p50Millis: number;
  p95Millis: number;
  throughputPerSecond: number;
  errorRate: number;
  jdbcStatementCount: number;
  cacheHitCount: number;
  cacheMissCount: number;
  cacheEvictionCount: number;
};

export type QueryEvidence = {
  cacheMode: 'OFF' | 'ON';
  queryShape: string;
  bindSummary: string;
  planSummary: string;
  executionMillis: number;
  actualRows: number;
  sharedHitBlocks: number;
  sharedReadBlocks: number;
};

export type PerformanceMeasurement = {
  runId: number;
  measurementId: string;
  payloadHash: string;
  gitCommit: string;
  dirtyWorktree: boolean;
  fixtureVersion: string;
  scenarioVersion: string;
  environmentName: string;
  hardwareDescription: string;
  artifactDirectory: string;
  warmupSeconds: number;
  measuredSeconds: number;
  offStartedAt: string;
  offCompletedAt: string;
  onStartedAt: string;
  onCompletedAt: string;
  registeredBy: number;
  registeredAt: string;
  valid: boolean;
  comparable: boolean;
  endpointMetrics: EndpointMetric[];
  queryEvidence: QueryEvidence[];
};

export type ProjectionRebuildResult = {
  generationId: number;
  status: string;
  cutoff: string;
  activatedAt: string;
};

function normalized(value: string | undefined) {
  return value?.trim() || null;
}

function dashboardKey(input: AdminDashboardInput) {
  return [input.preset ?? null, normalized(input.from), normalized(input.to), input.storeId ?? null] as const;
}

export const adminOperationsDashboardQueryKeys = {
  all: ['admin-operations-dashboard'] as const,
  dashboard: (input: AdminDashboardInput) => [...adminOperationsDashboardQueryKeys.all, 'dashboard', ...dashboardKey(input)] as const,
  campaigns: (input: AdminCampaignInput) => [...adminOperationsDashboardQueryKeys.all, 'campaigns', ...dashboardKey(input), normalized(input.ownerType), normalized(input.campaignKind), normalized(input.campaignStatus), input.page, input.size] as const,
  outcomes: (input: AdminOutcomeInput) => [...adminOperationsDashboardQueryKeys.all, 'outcomes', ...dashboardKey(input), normalized(input.ownerType), normalized(input.campaignKind), input.productId ?? null, normalized(input.reason), input.page, input.size] as const,
  inventory: (input: AdminInventoryInput) => [...adminOperationsDashboardQueryKeys.all, 'inventory', ...dashboardKey(input), input.productId ?? null, input.attentionOnly, input.page, input.size] as const,
  audits: (input: AdminAuditInput) => [...adminOperationsDashboardQueryKeys.all, 'audits', ...dashboardKey(input), normalized(input.ownerType), normalized(input.campaignKind), input.page, input.size] as const,
  deadEvents: (page: number, size: number) => [...adminOperationsDashboardQueryKeys.all, 'dead-events', page, size] as const,
  measurements: (page: number, size: number) => [...adminOperationsDashboardQueryKeys.all, 'measurements', page, size] as const,
  measurement: (runId: number | null) => [...adminOperationsDashboardQueryKeys.all, 'measurement', runId] as const,
};

function addDashboardFilters(query: URLSearchParams, input: AdminDashboardInput) {
  if (input.preset) query.set('preset', input.preset);
  if (normalized(input.from)) query.set('from', input.from!.trim());
  if (normalized(input.to)) query.set('to', input.to!.trim());
  if (input.storeId !== undefined) query.set('storeId', String(input.storeId));
}

function addOptionalString(query: URLSearchParams, name: string, value: string | undefined) {
  const clean = normalized(value);
  if (clean) query.set(name, clean);
}

function addOptionalNumber(query: URLSearchParams, name: string, value: number | undefined) {
  if (value !== undefined) query.set(name, String(value));
}

function finishPage(query: URLSearchParams, input: { page: number; size: number }) {
  query.set('page', String(input.page));
  query.set('size', String(input.size));
  return query.toString();
}

export function getAdminOperationsDashboard(input: AdminDashboardInput) {
  const query = new URLSearchParams();
  addDashboardFilters(query, input);
  const suffix = query.size ? `?${query.toString()}` : '';
  return api<AdminOperationsDashboard>(`/api/admin/operations-dashboard${suffix}`);
}

export function getAdminCampaignMetrics(input: AdminCampaignInput) {
  const query = new URLSearchParams();
  addDashboardFilters(query, input);
  addOptionalString(query, 'ownerType', input.ownerType);
  addOptionalString(query, 'campaignKind', input.campaignKind);
  addOptionalString(query, 'campaignStatus', input.campaignStatus);
  return api<Page<StoreCampaignMetric>>(`/api/admin/operations-dashboard/campaigns?${finishPage(query, input)}`);
}

export function getAdminOutcomeMetrics(input: AdminOutcomeInput) {
  const query = new URLSearchParams();
  addDashboardFilters(query, input);
  addOptionalString(query, 'ownerType', input.ownerType);
  addOptionalString(query, 'campaignKind', input.campaignKind);
  addOptionalNumber(query, 'productId', input.productId);
  addOptionalString(query, 'reason', input.reason);
  return api<Page<AdminOutcomeMetric>>(`/api/admin/operations-dashboard/outcomes?${finishPage(query, input)}`);
}

export function getAdminInventoryPressure(input: AdminInventoryInput) {
  const query = new URLSearchParams();
  addDashboardFilters(query, input);
  addOptionalNumber(query, 'productId', input.productId);
  query.set('attentionOnly', String(input.attentionOnly));
  return api<Page<StoreInventoryPressure>>(`/api/admin/operations-dashboard/inventory-pressure?${finishPage(query, input)}`);
}

export function getAdminCampaignAudits(input: AdminAuditInput) {
  const query = new URLSearchParams();
  addDashboardFilters(query, input);
  addOptionalString(query, 'ownerType', input.ownerType);
  addOptionalString(query, 'campaignKind', input.campaignKind);
  return api<Page<StoreCampaignAudit>>(`/api/admin/operations-dashboard/audits?${finishPage(query, input)}`);
}

export function getDeadOperationalEvents(page: number, size: number) {
  return api<Page<DeadOperationalEvent>>(`/api/admin/operational-events/dead?page=${page}&size=${size}`);
}

export function retryOperationalEvent(eventId: string) {
  return api<null>(`/api/admin/operational-events/${eventId}/retry`, { method: 'POST', body: undefined });
}

export function rebuildOperationalProjections() {
  return api<ProjectionRebuildResult>('/api/admin/operational-projections/rebuild', { method: 'POST', body: undefined });
}

export function getPerformanceMeasurements(page: number, size: number) {
  return api<Page<PerformanceMeasurement>>(`/api/admin/performance-measurements?page=${page}&size=${size}`);
}

export function getPerformanceMeasurement(runId: number) {
  return api<PerformanceMeasurement>(`/api/admin/performance-measurements/${runId}`);
}
