import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { type ApiError } from '../../shared/api/http';
import { EmptyState, ErrorState } from '../../shared/ui/ResourceStates';
import {
  adminOperationsDashboardQueryKeys,
  getDeadOperationalEvents,
  rebuildOperationalProjections,
  retryOperationalEvent,
  type ProjectionHealth,
} from './adminOperationsDashboardApi';

const PAGE_SIZE = 20;

export function ProjectionHealthPanel({ health }: { health: ProjectionHealth | null }) {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [message, setMessage] = useState<string | null>(null);
  const deadQuery = useQuery({
    queryKey: adminOperationsDashboardQueryKeys.deadEvents(page, PAGE_SIZE),
    queryFn: () => getDeadOperationalEvents(page, PAGE_SIZE),
  });
  const invalidateOperations = async () => {
    await queryClient.invalidateQueries({ queryKey: adminOperationsDashboardQueryKeys.all });
  };
  const retryMutation = useMutation({
    mutationFn: retryOperationalEvent,
    onSuccess: async () => { setMessage('DEAD event를 재시도 대기열로 이동했습니다.'); await invalidateOperations(); },
  });
  const rebuildMutation = useMutation({
    mutationFn: rebuildOperationalProjections,
    onSuccess: async (result) => { setMessage(`프로젝션 재구축 generation #${result.generationId}이 활성화되었습니다.`); await invalidateOperations(); },
  });

  const retry = (eventId: string) => {
    if (window.confirm('이 DEAD event를 재시도하시겠습니까? 동일한 event ID만 재처리합니다.')) {
      setMessage(null);
      retryMutation.mutate(eventId);
    }
  };
  const rebuild = () => {
    if (window.confirm('전체 운영 프로젝션을 재구축하시겠습니까? 서버가 안전한 generation을 생성하고 전환합니다.')) {
      setMessage(null);
      rebuildMutation.mutate();
    }
  };

  return (
    <section className="operations-dashboard-panel" aria-labelledby="projection-health-title">
      <div className="operations-panel-heading">
        <div><p className="eyebrow">PROJECTOR OPERATIONS</p><h2 id="projection-health-title">프로젝션 상태와 복구</h2></div>
        <button className="secondary-button danger-button" type="button" disabled={rebuildMutation.isPending} onClick={rebuild}>
          {rebuildMutation.isPending ? '재구축 중…' : '프로젝션 재구축'}
        </button>
      </div>
      {health ? <ProjectionHealthSummary health={health} /> : <EmptyState title="프로젝터 상태를 확인할 수 없습니다" description="상태 요약과 별개로 DEAD event 복구 및 안전한 재구축 작업은 계속 사용할 수 있습니다." />}
      {message ? <p className="operations-success" role="status">{message}</p> : null}
      {retryMutation.error ? <ErrorState message={errorMessage(retryMutation.error, 'DEAD event 재시도에 실패했습니다.')} /> : null}
      {rebuildMutation.error ? <ErrorState message={errorMessage(rebuildMutation.error, '프로젝션 재구축에 실패했습니다.')} /> : null}
      <section className="dead-events" aria-labelledby="dead-event-title">
        <div className="operations-panel-heading"><div><h3 id="dead-event-title">DEAD events</h3><p>payload는 노출하거나 다시 전송하지 않습니다.</p></div></div>
        {deadQuery.isLoading ? <p className="status-text" role="status">DEAD event를 불러오고 있습니다.</p> : null}
        {deadQuery.error ? <ErrorState message={errorMessage(deadQuery.error, 'DEAD event 목록을 불러오지 못했습니다.')} /> : null}
        {deadQuery.data?.content.length === 0 ? <EmptyState title="DEAD event가 없습니다" description="현재 수동 복구가 필요한 이벤트가 없습니다." /> : null}
        {deadQuery.data?.content.length ? (
          <>
            <div className="operations-table-scroll">
              <table className="operations-table dead-event-table" aria-label="DEAD event 목록">
                <thead><tr><th scope="col">Event</th><th scope="col">대상</th><th scope="col">상점·캠페인</th><th scope="col">시도</th><th scope="col">오류</th><th scope="col">발생 시각</th><th scope="col">작업</th></tr></thead>
                <tbody>{deadQuery.data.content.map((event) => (
                  <tr key={event.eventId}>
                    <td data-label="Event"><strong>{event.eventType}</strong><small>{event.eventId}</small></td>
                    <td data-label="대상">{event.aggregateType} #{event.aggregateId ?? '—'}</td>
                    <td data-label="상점·캠페인">상점 #{event.storeId ?? '—'} · 캠페인 #{event.campaignId ?? '—'}</td>
                    <td data-label="시도">{event.attemptCount}회</td>
                    <td data-label="오류">{event.lastError ?? '기록 없음'}</td>
                    <td data-label="발생 시각">{formatKstDateTime(event.occurredAt)}</td>
                    <td data-label="작업"><button type="button" disabled={retryMutation.isPending} onClick={() => retry(event.eventId)}>{retryMutation.isPending && retryMutation.variables === event.eventId ? '재시도 중…' : '재시도'}</button></td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
            {deadQuery.data.totalPages > 1 ? <nav className="operations-pagination" aria-label="DEAD event 페이지 이동"><button type="button" disabled={deadQuery.data.first} onClick={() => setPage(page - 1)}>이전</button><span>{page + 1} / {deadQuery.data.totalPages}</span><button type="button" disabled={deadQuery.data.last} onClick={() => setPage(page + 1)}>다음</button></nav> : null}
          </>
        ) : null}
      </section>
    </section>
  );
}

export function ProjectionHealthSummary({ health }: { health: ProjectionHealth }) {
  return (
    <dl className="projection-health-grid" aria-label="프로젝터 처리 상태">
      <HealthItem label="대기" value={`${health.pendingCount.toLocaleString('ko-KR')}건`} />
      <HealthItem label="재시도" value={`${health.retryCount.toLocaleString('ko-KR')}건`} />
      <HealthItem label="DEAD" value={`${health.deadCount.toLocaleString('ko-KR')}건`} warning={health.deadCount > 0} />
      <HealthItem label="가장 오래된 event" value={health.oldestUnprocessedAt ? formatKstDateTime(health.oldestUnprocessedAt) : '없음'} />
      <HealthItem label="프로젝션 지연" value={formatLag(health.projectionLagSeconds)} warning={health.projectionLagSeconds > 60} />
      <HealthItem label="마지막 갱신" value={health.projectionUpdatedAt ? formatKstDateTime(health.projectionUpdatedAt) : '갱신 기록 없음'} />
    </dl>
  );
}

function HealthItem({ label, value, warning }: { label: string; value: string; warning?: boolean }) {
  return <div className={warning ? 'is-warning' : undefined}><dt>{label}</dt><dd>{value}</dd></div>;
}

function formatLag(seconds: number) { return seconds < 60 ? `${seconds}초` : `${Math.floor(seconds / 60)}분 ${seconds % 60}초`; }
function formatKstDateTime(value: string) { return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) + ' KST'; }
function errorMessage(error: unknown, fallback: string) { const apiError = error as Partial<ApiError>; return apiError.fieldErrors?.[0]?.message ?? apiError.message ?? fallback; }
