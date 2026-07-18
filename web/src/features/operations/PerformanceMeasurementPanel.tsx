import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { type ApiError } from '../../shared/api/http';
import { EmptyState, ErrorState } from '../../shared/ui/ResourceStates';
import {
  adminOperationsDashboardQueryKeys,
  getPerformanceMeasurement,
  getPerformanceMeasurements,
  type EndpointMetric,
  type PerformanceMeasurement,
} from './adminOperationsDashboardApi';

const PAGE_SIZE = 20;

export function PerformanceMeasurementPanel({ page, selectedRunId, onPageChange, onSelectedRunChange }: { page: number; selectedRunId: number | null; onPageChange: (page: number) => void; onSelectedRunChange: (runId: number | null) => void }) {
  const listQuery = useQuery({
    queryKey: adminOperationsDashboardQueryKeys.measurements(page, PAGE_SIZE),
    queryFn: () => getPerformanceMeasurements(page, PAGE_SIZE),
  });

  useEffect(() => {
    if (selectedRunId === null && listQuery.data?.content[0]) {
      onSelectedRunChange(listQuery.data.content[0].runId);
    }
  }, [listQuery.data, onSelectedRunChange, selectedRunId]);

  const detailQuery = useQuery({
    queryKey: adminOperationsDashboardQueryKeys.measurement(selectedRunId),
    queryFn: () => getPerformanceMeasurement(selectedRunId!),
    enabled: selectedRunId !== null,
  });

  return (
    <section className="operations-dashboard-panel" aria-labelledby="performance-measurement-title">
      <div className="operations-panel-heading">
        <div><p className="eyebrow">PERFORMANCE EVIDENCE</p><h2 id="performance-measurement-title">성능 측정</h2></div>
        {listQuery.data ? <span>등록 {listQuery.data.totalElements.toLocaleString('ko-KR')}건</span> : null}
      </div>
      {listQuery.isLoading ? <p className="status-text" role="status">성능 측정 목록을 불러오고 있습니다.</p> : null}
      {listQuery.error ? <ErrorState message={errorMessage(listQuery.error, '성능 측정 목록을 불러오지 못했습니다.')} /> : null}
      {listQuery.data?.content.length === 0 ? (
        <EmptyState
          title="성능 측정 전"
          description="performance/collect-m30-measurement.ps1로 OFF/ON 증거를 수집한 뒤 POST /api/admin/performance-measurements에 등록해주세요."
        />
      ) : null}
      {listQuery.data?.content.length ? (
        <>
          <div className="performance-measurement-layout">
            <nav className="performance-run-list" aria-label="성능 측정 실행 목록">
              {listQuery.data.content.map((run) => (
                <button
                  type="button"
                  key={run.runId}
                  aria-current={selectedRunId === run.runId ? 'true' : undefined}
                  onClick={() => onSelectedRunChange(run.runId)}
                >
                  <strong>실행 #{run.runId}</strong>
                  <span>{formatKstDateTime(run.registeredAt)}</span>
                  <small>{run.valid ? (run.comparable ? '비교 가능' : '비교 조건 불일치') : '유효하지 않음'}</small>
                </button>
              ))}
            </nav>
            <div className="performance-run-detail">
              {detailQuery.isLoading ? <p className="status-text" role="status">선택한 측정을 불러오고 있습니다.</p> : null}
              {detailQuery.error ? <ErrorState message={errorMessage(detailQuery.error, '선택한 성능 측정을 불러오지 못했습니다.')} /> : null}
              {detailQuery.data ? <PerformanceMeasurementDetail measurement={detailQuery.data} /> : null}
            </div>
          </div>
          <Pagination
            label="성능 측정 목록 페이지 이동"
            page={page}
            totalPages={listQuery.data.totalPages}
            first={listQuery.data.first}
            last={listQuery.data.last}
            onChange={onPageChange}
          />
        </>
      ) : null}
    </section>
  );
}

export function PerformanceMeasurementDetail({ measurement }: { measurement: PerformanceMeasurement }) {
  const endpoints = [...new Set(measurement.endpointMetrics.map((metric) => metric.endpoint))];
  const comparisonAllowed = measurement.valid && measurement.comparable;

  return (
    <article className="performance-detail" aria-label={`성능 측정 실행 ${measurement.runId}`}>
      <div className="performance-detail-heading">
        <div><h3>실행 #{measurement.runId}</h3><p>{measurement.measurementId}</p></div>
        <span className={measurement.valid && measurement.comparable ? 'status-badge status-badge-active' : 'status-badge status-badge-pending'}>
          {!measurement.valid ? '유효하지 않음' : measurement.comparable ? '동일 조건 비교' : '비교 조건 불일치'}
        </span>
      </div>
      {!measurement.comparable ? <p className="operations-notice" role="status">비교 조건 불일치: OFF와 ON을 독립 측정값으로 표시하며 우열을 판단하지 않습니다.</p> : null}
      <dl className="performance-conditions">
        <Condition label="증거 경로" value={measurement.artifactDirectory} />
        <Condition label="Git commit" value={`${measurement.gitCommit.slice(0, 12)}${measurement.dirtyWorktree ? ' · dirty' : ' · clean'}`} />
        <Condition label="Fixture" value={measurement.fixtureVersion} />
        <Condition label="Scenario" value={measurement.scenarioVersion} />
        <Condition label="환경" value={`${measurement.environmentName} · ${measurement.hardwareDescription}`} />
        <Condition label="구간" value={`warm-up ${measurement.warmupSeconds}초 · 측정 ${measurement.measuredSeconds}초`} />
        <Condition label="OFF 측정 시간" value={`${formatKstDateTime(measurement.offStartedAt)} ~ ${formatKstDateTime(measurement.offCompletedAt)}`} />
        <Condition label="ON 측정 시간" value={`${formatKstDateTime(measurement.onStartedAt)} ~ ${formatKstDateTime(measurement.onCompletedAt)}`} />
      </dl>
      {endpoints.length === 0 ? <EmptyState title="endpoint 측정값이 없습니다" /> : (
        <div className="operations-table-scroll">
          <table className="operations-table performance-metric-table" aria-label="OFF ON endpoint 성능 비교">
            <thead><tr><th scope="col">Endpoint</th><th scope="col">p50 ms</th><th scope="col">p95 ms</th><th scope="col">처리량/s</th><th scope="col">오류율</th><th scope="col">JDBC</th><th scope="col">Cache hit/miss/eviction</th></tr></thead>
            <tbody>{endpoints.map((endpoint) => <MetricRow key={endpoint} endpoint={endpoint} metrics={measurement.endpointMetrics} comparable={comparisonAllowed} />)}</tbody>
          </table>
        </div>
      )}
      <section className="performance-evidence" aria-labelledby={`query-evidence-${measurement.runId}`}>
        <h4 id={`query-evidence-${measurement.runId}`}>쿼리 증거</h4>
        {measurement.queryEvidence.length === 0 ? <EmptyState title="등록된 쿼리 증거가 없습니다" /> : measurement.queryEvidence.map((evidence, index) => (
          <details key={`${evidence.cacheMode}-${evidence.queryShape}-${index}`}>
            <summary><strong>{evidence.cacheMode}</strong> {evidence.queryShape} · {number(evidence.executionMillis)} ms</summary>
            <dl>
              <Condition label="바인드 조건" value={evidence.bindSummary} />
              <Condition label="계획 요약" value={evidence.planSummary} />
              <Condition label="실제 행" value={String(evidence.actualRows)} />
              <Condition label="Buffer hit/read" value={`${evidence.sharedHitBlocks}/${evidence.sharedReadBlocks}`} />
            </dl>
          </details>
        ))}
      </section>
    </article>
  );
}

function MetricRow({ endpoint, metrics, comparable }: { endpoint: string; metrics: EndpointMetric[]; comparable: boolean }) {
  const off = metrics.find((metric) => metric.endpoint === endpoint && metric.cacheMode === 'OFF');
  const on = metrics.find((metric) => metric.endpoint === endpoint && metric.cacheMode === 'ON');
  return (
    <tr>
      <td data-label="Endpoint"><strong>{endpoint}</strong></td>
      <MetricCell label="p50 ms" off={off?.p50Millis} on={on?.p50Millis} comparable={comparable} better="lower" />
      <MetricCell label="p95 ms" off={off?.p95Millis} on={on?.p95Millis} comparable={comparable} better="lower" />
      <MetricCell label="처리량/s" off={off?.throughputPerSecond} on={on?.throughputPerSecond} comparable={comparable} better="higher" />
      <MetricCell label="오류율" off={off?.errorRate} on={on?.errorRate} comparable={comparable} better="lower" />
      <MetricCell label="JDBC" off={off?.jdbcStatementCount} on={on?.jdbcStatementCount} comparable={comparable} better="lower" />
      <td data-label="Cache hit/miss/eviction">
        <MetricValue name="hit" off={off?.cacheHitCount} on={on?.cacheHitCount} comparable={comparable} better="higher" />
        <MetricValue name="miss" off={off?.cacheMissCount} on={on?.cacheMissCount} comparable={comparable} better="lower" />
        <MetricValue name="eviction" off={off?.cacheEvictionCount} on={on?.cacheEvictionCount} comparable={comparable} better="lower" />
      </td>
    </tr>
  );
}

function MetricCell({ label, ...props }: { label: string; off?: number; on?: number; comparable: boolean; better: 'higher' | 'lower' }) {
  return <td data-label={label}><MetricValue {...props} /></td>;
}

function MetricValue({ name, off, on, comparable, better }: { name?: string; off?: number; on?: number; comparable: boolean; better: 'higher' | 'lower' }) {
  if (off === undefined || on === undefined) return <span>{name ? `${name}: ` : ''}측정값 없음</span>;
  const delta = on - off;
  const improved = better === 'lower' ? delta < 0 : delta > 0;
  return (
    <span className="performance-metric-value">
      {name ? <strong>{name}</strong> : null}
      <span>OFF {number(off)}</span>
      <span>ON {number(on)}</span>
      {comparable ? <span>Δ {signed(delta)} · {delta === 0 ? '변화 없음' : improved ? '개선' : '악화'}</span> : null}
    </span>
  );
}

function Condition({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>;
}

function Pagination({ label, page, totalPages, first, last, onChange }: { label: string; page: number; totalPages: number; first: boolean; last: boolean; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return <nav className="operations-pagination" aria-label={label}><button type="button" disabled={first} onClick={() => onChange(page - 1)}>이전</button><span>{page + 1} / {totalPages}</span><button type="button" disabled={last} onClick={() => onChange(page + 1)}>다음</button></nav>;
}

function number(value: number) { return value.toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
function signed(value: number) { return `${value > 0 ? '+' : ''}${number(value)}`; }
function formatKstDateTime(value: string) { return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) + ' KST'; }
function errorMessage(error: unknown, fallback: string) { const apiError = error as Partial<ApiError>; return apiError.fieldErrors?.[0]?.message ?? apiError.message ?? fallback; }
