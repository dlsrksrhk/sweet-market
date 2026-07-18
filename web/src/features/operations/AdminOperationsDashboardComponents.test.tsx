import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import type { PerformanceMeasurement } from './adminOperationsDashboardApi';
import { PerformanceMeasurementDetail } from './PerformanceMeasurementPanel';
import { ProjectionHealthSummary } from './ProjectionHealthPanel';

describe('PerformanceMeasurementDetail', () => {
  it('비교가능한_OFF_ON_원시값과_delta와_근거경로를_표시한다', () => {
    const html = renderToStaticMarkup(<PerformanceMeasurementDetail measurement={measurement} />);

    expect(html).toContain('OFF 20.00');
    expect(html).toContain('ON 10.00');
    expect(html).toContain('Δ -10.00');
    expect(html).toContain('개선');
    expect(html).toContain('performance/results/m30-v1');
    expect(html).toContain('GLOBAL_CATALOG');
    expect(html).toContain('Index Scan');
  });

  it('비교불가능하면_두_mode를_독립표시하고_개선표현을_생략한다', () => {
    const html = renderToStaticMarkup(
      <PerformanceMeasurementDetail measurement={{ ...measurement, comparable: false }} />,
    );

    expect(html).toContain('비교 조건 불일치');
    expect(html).toContain('OFF 20.00');
    expect(html).toContain('ON 10.00');
    expect(html).not.toContain('개선');
  });
});

describe('ProjectionHealthSummary', () => {
  it('projector_queue와_지연과_마지막갱신을_구분해_표시한다', () => {
    const html = renderToStaticMarkup(<ProjectionHealthSummary health={{
      pendingCount: 2,
      retryCount: 1,
      deadCount: 3,
      oldestUnprocessedAt: '2026-07-17T00:00:00Z',
      projectionLagSeconds: 65,
      projectionUpdatedAt: '2026-07-17T00:05:00Z',
    }} />);

    expect(html).toContain('대기');
    expect(html).toContain('재시도');
    expect(html).toContain('DEAD');
    expect(html).toContain('1분 5초');
    expect(html).toContain('KST');
  });
});

const measurement: PerformanceMeasurement = {
  runId: 1,
  measurementId: '385b4525-21a2-4f4a-875f-364449f59957',
  payloadHash: 'abc123',
  gitCommit: '0123456789abcdef0123456789abcdef01234567',
  dirtyWorktree: false,
  fixtureVersion: 'm30-v1',
  scenarioVersion: 'm30-catalog-reads-v1',
  environmentName: 'local',
  hardwareDescription: '4 CPU / 8 GiB',
  artifactDirectory: 'performance/results/m30-v1',
  warmupSeconds: 60,
  measuredSeconds: 300,
  offStartedAt: '2026-07-17T00:00:00Z',
  offCompletedAt: '2026-07-17T00:05:00Z',
  onStartedAt: '2026-07-17T00:10:00Z',
  onCompletedAt: '2026-07-17T00:15:00Z',
  registeredBy: 1,
  registeredAt: '2026-07-17T00:20:00Z',
  valid: true,
  comparable: true,
  endpointMetrics: [
    { cacheMode: 'OFF', endpoint: 'catalog', p50Millis: 20, p95Millis: 50, throughputPerSecond: 100, errorRate: 0.01, jdbcStatementCount: 1000, cacheHitCount: 0, cacheMissCount: 100, cacheEvictionCount: 0 },
    { cacheMode: 'ON', endpoint: 'catalog', p50Millis: 10, p95Millis: 25, throughputPerSecond: 180, errorRate: 0, jdbcStatementCount: 300, cacheHitCount: 80, cacheMissCount: 20, cacheEvictionCount: 1 },
  ],
  queryEvidence: [
    { cacheMode: 'OFF', queryShape: 'GLOBAL_CATALOG', bindSummary: 'category=FOOD', planSummary: 'Index Scan', executionMillis: 2.5, actualRows: 20, sharedHitBlocks: 12, sharedReadBlocks: 1 },
  ],
};
