import { useQuery } from '@tanstack/react-query';
import { type ReactNode, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { OperationsPeriodControls } from '../features/operations/OperationsPeriodControls';
import { PerformanceMeasurementPanel } from '../features/operations/PerformanceMeasurementPanel';
import { ProjectionHealthPanel } from '../features/operations/ProjectionHealthPanel';
import {
  adminOperationsDashboardQueryKeys,
  getAdminCampaignAudits,
  getAdminCampaignMetrics,
  getAdminInventoryPressure,
  getAdminOperationsDashboard,
  getAdminOutcomeMetrics,
  type AdminAuditInput,
  type AdminCampaignInput,
  type AdminDashboardInput,
  type AdminOperationsDashboard,
  type AdminInventoryInput,
  type AdminOutcomeInput,
  type AdminOutcomeMetric,
} from '../features/operations/adminOperationsDashboardApi';
import type {
  DashboardPeriodPreset,
  Page,
  StoreCampaignAudit,
  StoreCampaignMetric,
  StoreInventoryPressure,
} from '../features/operations/storeOperationsDashboardApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const PAGE_SIZE = 20;

type Pages = { campaigns: number; outcomes: number; inventory: number; audits: number };

export function AdminOperationsDashboardPage() {
  const [searchParams] = useSearchParams();
  const [period, setPeriod] = useState<AdminDashboardInput>({ preset: 'LAST_30_DAYS' });
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo] = useState('');
  const [periodValidation, setPeriodValidation] = useState<string | null>(null);
  const [storeIdText, setStoreIdText] = useState('');
  const [storeValidation, setStoreValidation] = useState<string | null>(null);
  const [ownerType, setOwnerType] = useState('');
  const [campaignKind, setCampaignKind] = useState('');
  const [campaignStatus, setCampaignStatus] = useState('');
  const [outcomeReason, setOutcomeReason] = useState('');
  const [productIdText, setProductIdText] = useState('');
  const [attentionOnly, setAttentionOnly] = useState(true);
  const [pages, setPages] = useState<Pages>({ campaigns: 0, outcomes: 0, inventory: 0, audits: 0 });

  const linkedStoreId = positiveInteger(searchParams.get('storeId') ?? '');
  const linkedCampaignKind = searchParams.get('campaignKind');
  useEffect(() => {
    if (linkedStoreId !== undefined) {
      setStoreIdText(String(linkedStoreId));
      setPeriod((current) => current.storeId === linkedStoreId ? current : { ...current, storeId: linkedStoreId });
    }
    if (linkedCampaignKind === 'PROMOTION' || linkedCampaignKind === 'COUPON') setCampaignKind(linkedCampaignKind);
    setPages({ campaigns: 0, outcomes: 0, inventory: 0, audits: 0 });
  }, [linkedCampaignKind, linkedStoreId]);

  const resetPages = () => setPages({ campaigns: 0, outcomes: 0, inventory: 0, audits: 0 });
  const shared = { ...period, ownerType: ownerType || undefined, campaignKind: campaignKind || undefined };
  const productId = positiveInteger(productIdText);
  const campaignsInput: AdminCampaignInput = { ...shared, campaignStatus: campaignStatus || undefined, page: pages.campaigns, size: PAGE_SIZE };
  const outcomesInput: AdminOutcomeInput = { ...shared, productId, reason: outcomeReason || undefined, page: pages.outcomes, size: PAGE_SIZE };
  const inventoryInput: AdminInventoryInput = { ...period, productId, attentionOnly, page: pages.inventory, size: PAGE_SIZE };
  const auditsInput: AdminAuditInput = { ...shared, page: pages.audits, size: PAGE_SIZE };

  const dashboardQuery = useQuery({ queryKey: adminOperationsDashboardQueryKeys.dashboard(period), queryFn: () => getAdminOperationsDashboard(period) });
  const campaignsQuery = useQuery({ queryKey: adminOperationsDashboardQueryKeys.campaigns(campaignsInput), queryFn: () => getAdminCampaignMetrics(campaignsInput) });
  const outcomesQuery = useQuery({ queryKey: adminOperationsDashboardQueryKeys.outcomes(outcomesInput), queryFn: () => getAdminOutcomeMetrics(outcomesInput) });
  const inventoryQuery = useQuery({ queryKey: adminOperationsDashboardQueryKeys.inventory(inventoryInput), queryFn: () => getAdminInventoryPressure(inventoryInput) });
  const auditsQuery = useQuery({ queryKey: adminOperationsDashboardQueryKeys.audits(auditsInput), queryFn: () => getAdminCampaignAudits(auditsInput) });

  const applyPreset = (preset: DashboardPeriodPreset) => {
    setPeriodValidation(null);
    setPeriod((current) => ({ preset, storeId: current.storeId }));
    resetPages();
  };
  const applyCustomPeriod = () => {
    if (!customFrom || !customTo) return setPeriodValidation('시작일과 종료일을 모두 입력해주세요.');
    const days = Math.floor((Date.parse(customTo) - Date.parse(customFrom)) / 86_400_000) + 1;
    if (days < 1 || days > 90) return setPeriodValidation('조회 기간은 1일 이상 90일 이하여야 합니다.');
    setPeriodValidation(null);
    setPeriod((current) => ({ from: customFrom, to: customTo, storeId: current.storeId }));
    resetPages();
  };
  const applyStore = () => {
    const storeId = positiveInteger(storeIdText);
    if (storeIdText.trim() && storeId === undefined) return setStoreValidation('상점 ID는 1 이상의 정수여야 합니다.');
    setStoreValidation(null);
    setPeriod((current) => ({ ...current, storeId }));
    resetPages();
  };

  return (
    <main className="operations-dashboard admin-operations-dashboard">
      <header className="operations-dashboard-header">
        <div className="operations-dashboard-heading">
          <div><p className="eyebrow">ADMIN OPERATIONS DASHBOARD</p><h1>관리자 운영 대시보드</h1><p>플랫폼과 상점 운영, 성능 증거, 프로젝터 복구 상태를 함께 점검합니다.</p></div>
          <nav className="operations-route-links" aria-label="관리자 운영 바로가기"><Link to="/admin/operations">기존 관리자 운영</Link><Link to="/admin/coupons">플랫폼 쿠폰</Link></nav>
        </div>
        <div className="operations-filters admin-dashboard-primary-filters">
          <label>상점 ID<input type="number" min="1" inputMode="numeric" value={storeIdText} placeholder="전체 플랫폼" onChange={(event) => setStoreIdText(event.target.value)} /></label>
          <button className="secondary-button" type="button" onClick={applyStore}>상점 필터 적용</button>
          <label>소유 유형<select value={ownerType} onChange={(event) => { setOwnerType(event.target.value); resetPages(); }}><option value="">전체</option><option value="PLATFORM">플랫폼</option><option value="STORE">상점</option></select></label>
          <label>캠페인 종류<select value={campaignKind} onChange={(event) => { setCampaignKind(event.target.value); resetPages(); }}><option value="">전체</option><option value="PROMOTION">프로모션</option><option value="COUPON">쿠폰</option></select></label>
        </div>
        {storeValidation ? <p className="error-text" role="alert">{storeValidation}</p> : null}
      </header>

      <OperationsPeriodControls period={period} customFrom={customFrom} customTo={customTo} validationMessage={periodValidation} onPresetChange={applyPreset} onCustomFromChange={setCustomFrom} onCustomToChange={setCustomTo} onCustomApply={applyCustomPeriod} />

      <section className="operations-dashboard-panel" aria-labelledby="admin-overview-title">
        <PanelHeading eyebrow="PLATFORM OVERVIEW" title="플랫폼 운영 요약" id="admin-overview-title" aside={dashboardQuery.data ? `${dashboardQuery.data.period.from} ~ ${dashboardQuery.data.period.to} KST` : undefined} />
        <QueryState query={dashboardQuery} loading="플랫폼 운영 요약을 불러오고 있습니다." fallback="플랫폼 운영 요약을 불러오지 못했습니다.">
          {dashboardQuery.data ? <AdminSummary dashboard={dashboardQuery.data} /> : null}
        </QueryState>
      </section>

      <section className="operations-dashboard-panel" aria-labelledby="admin-campaign-outcome-title">
        <PanelHeading eyebrow="CAMPAIGNS & OUTCOMES" title="캠페인·결과 상세" id="admin-campaign-outcome-title" />
        <div className="operations-filters">
          <label>캠페인 상태<select value={campaignStatus} onChange={(event) => { setCampaignStatus(event.target.value); setPages((value) => ({ ...value, campaigns: 0 })); }}><option value="">전체</option><option value="DRAFT">초안</option><option value="SCHEDULED">예정</option><option value="ACTIVE">진행 중</option><option value="PAUSED">중지</option><option value="ENDED">종료</option></select></label>
          <label>결과 사유<input value={outcomeReason} placeholder="예: PAYMENT_FAILED" onChange={(event) => { setOutcomeReason(event.target.value); setPages((value) => ({ ...value, outcomes: 0 })); }} /></label>
          <label>상품 ID<input type="number" min="1" value={productIdText} placeholder="전체" onChange={(event) => { setProductIdText(event.target.value); setPages((value) => ({ ...value, outcomes: 0, inventory: 0 })); }} /></label>
        </div>
        <DrilldownBlock title="캠페인 성과" query={campaignsQuery} empty="해당 조건의 캠페인 성과가 없습니다."><CampaignTable page={campaignsQuery.data} /></DrilldownBlock>
        <PageNavigation page={pages.campaigns} data={campaignsQuery.data} label="캠페인 성과 페이지 이동" onChange={(campaigns) => setPages((value) => ({ ...value, campaigns }))} />
        <DrilldownBlock title="구매·발급 결과" query={outcomesQuery} empty="해당 조건의 결과 지표가 없습니다."><OutcomeTable page={outcomesQuery.data} /></DrilldownBlock>
        <PageNavigation page={pages.outcomes} data={outcomesQuery.data} label="결과 지표 페이지 이동" onChange={(outcomes) => setPages((value) => ({ ...value, outcomes }))} />
      </section>

      <section className="operations-dashboard-panel" aria-labelledby="admin-inventory-title">
        <PanelHeading eyebrow="INVENTORY PRESSURE" title="재고 압력" id="admin-inventory-title" />
        <label className="operations-checkbox"><input type="checkbox" checked={attentionOnly} onChange={(event) => { setAttentionOnly(event.target.checked); setPages((value) => ({ ...value, inventory: 0 })); }} />주의가 필요한 상품만</label>
        <DrilldownBlock query={inventoryQuery} empty="해당 조건의 재고 압력 지표가 없습니다."><InventoryTable page={inventoryQuery.data} /></DrilldownBlock>
        <PageNavigation page={pages.inventory} data={inventoryQuery.data} label="재고 압력 페이지 이동" onChange={(inventory) => setPages((value) => ({ ...value, inventory }))} />
      </section>

      <section className="operations-dashboard-panel" aria-labelledby="admin-audit-title">
        <PanelHeading eyebrow="CAMPAIGN AUDIT" title="캠페인 감사" id="admin-audit-title" />
        <DrilldownBlock query={auditsQuery} empty="해당 조건의 캠페인 감사 기록이 없습니다."><AuditTable page={auditsQuery.data} /></DrilldownBlock>
        <PageNavigation page={pages.audits} data={auditsQuery.data} label="캠페인 감사 페이지 이동" onChange={(audits) => setPages((value) => ({ ...value, audits }))} />
      </section>

      <PerformanceMeasurementPanel />
      {dashboardQuery.data ? <ProjectionHealthPanel health={dashboardQuery.data.health} /> : (
        <section className="operations-dashboard-panel"><PanelHeading eyebrow="PROJECTOR OPERATIONS" title="프로젝션 상태와 복구" id="projection-health-unavailable" /><EmptyState title="프로젝터 상태를 확인할 수 없습니다" description="운영 요약이 복구되면 재시도와 재구축 작업을 사용할 수 있습니다." /></section>
      )}
    </main>
  );
}

function AdminSummary({ dashboard }: { dashboard: AdminOperationsDashboard }) {
  const tracked = dashboard.trackingStartedAt !== null;
  const items: [string, number, boolean?][] = [
    ['쿠폰 발급 성공', dashboard.claimSuccessCount], ['쿠폰 사용 성공', dashboard.redemptionSuccessCount], ['주문 성공', dashboard.orderSuccessCount],
    ['구매 실패', dashboard.purchaseFailureCount, true], ['저재고', dashboard.lowStockCount, true], ['품절 전환', dashboard.soldOutTransitionCount, true], ['감사 이벤트', dashboard.auditCount],
  ];
  return <><div className={dashboard.projectionUpdatedAt ? 'operations-freshness' : 'operations-freshness is-delayed'} role="status"><strong>{dashboard.projectionUpdatedAt ? '프로젝션 최신성' : '프로젝션 갱신 지연'}</strong><span>{dashboard.projectionUpdatedAt ? `${formatKstDateTime(dashboard.projectionUpdatedAt)} · ${formatLag(dashboard.projectionLagSeconds)}` : '마지막 갱신 시각이 없습니다.'}</span></div><dl className="operations-summary-grid admin-summary-grid">{items.map(([label, value, warning]) => <div className={warning ? 'operations-summary-card is-warning' : 'operations-summary-card'} key={label}><dt>{label}</dt><dd>{tracked ? `${value.toLocaleString('ko-KR')}건` : '—'}</dd><small>{tracked ? '조회 기간 누적' : '추적 시작 전'}</small></div>)}</dl><div className="operations-amount-sections"><AmountPanel title="프로모션 할인" values={dashboard.promotionDiscounts} tracked={tracked} /><AmountPanel title="쿠폰 할인" values={dashboard.couponDiscounts} tracked={tracked} /></div></>;
}

function AmountPanel({ title, values, tracked }: { title: string; values: { applied: number; realized: number; canceled: number; refunded: number }; tracked: boolean }) {
  return <section className="operations-amount-panel"><h3>{title}</h3><dl>{Object.entries({ 적용: values.applied, 실현: values.realized, 취소: values.canceled, 환불: values.refunded }).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{tracked ? `${value.toLocaleString('ko-KR')}원` : '—'}</dd></div>)}</dl></section>;
}

function CampaignTable({ page }: { page?: Page<StoreCampaignMetric> }) {
  if (!page) return null;
  return <TableFrame label="관리자 캠페인 성과" headers={['캠페인', '소유', '상태', '발급·사용', '주문·실패', '할인 실현', '최근 버킷']}>{page.content.map((row) => <tr key={row.id}><Cell label="캠페인"><strong>{kindLabel(row.campaignKind)} #{row.campaignId}</strong>{row.ownerType === 'PLATFORM' && row.campaignKind === 'COUPON' ? <Link to="/admin/coupons">플랫폼 쿠폰 보기</Link> : row.ownerType === 'STORE' && row.ownerStoreId ? <Link to={`/admin/dashboard?storeId=${row.ownerStoreId}&campaignKind=${row.campaignKind}`}>상점 캠페인 검사</Link> : null}</Cell><Cell label="소유">{ownershipLabel(row.ownerType, row.ownerStoreId)}</Cell><Cell label="상태"><StatusBadge status={row.status} /></Cell><Cell label="발급·사용">발급 {row.claimSuccessCount}/{row.claimFailureCount} · 사용 {row.redemptionSuccessCount}/{row.redemptionFailureCount}</Cell><Cell label="주문·실패">{row.orderSuccessCount}/{row.purchaseFailureCount}</Cell><Cell label="할인 실현">{money(row.promotionDiscounts.realized + row.couponDiscounts.realized)}</Cell><Cell label="최근 버킷">{formatKstDateTime(row.latestBucketStart)}</Cell></tr>)}</TableFrame>;
}

function OutcomeTable({ page }: { page?: Page<AdminOutcomeMetric> }) {
  if (!page) return null;
  return <TableFrame label="관리자 구매 발급 결과" headers={['결과', '상점', '캠페인·상품', '사유', '성공', '실패·예약', '최근 버킷']}>{page.content.map((row) => <tr key={row.id}><Cell label="결과">{row.outcomeType}</Cell><Cell label="상점">#{row.storeId ?? '—'}</Cell><Cell label="캠페인·상품">#{row.campaignId ?? '—'} · 상품 #{row.productId ?? '—'}</Cell><Cell label="사유">{row.reason}</Cell><Cell label="성공">{row.successCount}건</Cell><Cell label="실패·예약">{row.failureCount} · {row.reservationFailureCount}</Cell><Cell label="최근 버킷">{formatKstDateTime(row.latestBucketStart)}</Cell></tr>)}</TableFrame>;
}

function InventoryTable({ page }: { page?: Page<StoreInventoryPressure> }) {
  if (!page) return null;
  return <TableFrame label="관리자 재고 압력" headers={['상품', '정책', '가용 재고', '상태', '예약 실패', '마지막 실패', '갱신']}>{page.content.map((row) => <tr key={row.productId}><Cell label="상품"><Link to={`/products/${row.productId}`}>상품 #{row.productId}</Link></Cell><Cell label="정책">{row.salesPolicy}</Cell><Cell label="가용 재고">{row.availableQuantity ?? '단품'}</Cell><Cell label="상태">{row.lowStock ? '주의 필요' : '정상'}</Cell><Cell label="예약 실패">{row.reservationFailureCount}건</Cell><Cell label="마지막 실패">{row.lastReservationFailureAt ? formatKstDateTime(row.lastReservationFailureAt) : '없음'}</Cell><Cell label="갱신">{formatKstDateTime(row.updatedAt)}</Cell></tr>)}</TableFrame>;
}

function AuditTable({ page }: { page?: Page<StoreCampaignAudit> }) {
  if (!page) return null;
  return <TableFrame label="관리자 캠페인 감사" headers={['캠페인', '소유', '명령', '실행자', '버전', '발생 시각']}>{page.content.map((row) => <tr key={row.eventId}><Cell label="캠페인">{kindLabel(row.campaignKind)} #{row.campaignId}</Cell><Cell label="소유">{ownershipLabel(row.ownerType, row.ownerStoreId)}</Cell><Cell label="명령">{row.command}</Cell><Cell label="실행자">{row.actorMemberId ? `회원 #${row.actorMemberId}` : '시스템'}</Cell><Cell label="버전">{row.aggregateVersion ?? '—'}</Cell><Cell label="발생 시각">{formatKstDateTime(row.occurredAt)}</Cell></tr>)}</TableFrame>;
}

function DrilldownBlock({ title, query, empty, children }: { title?: string; query: { isLoading: boolean; error: unknown; data?: Page<unknown> }; empty: string; children: ReactNode }) {
  return <section className="admin-drilldown-block">{title ? <h3>{title}</h3> : null}{query.isLoading ? <p className="status-text" role="status">상세 지표를 불러오고 있습니다.</p> : null}{query.error ? <ErrorState message={errorMessage(query.error, '상세 지표를 불러오지 못했습니다.')} /> : null}{query.data?.content.length === 0 ? <EmptyState title={empty} description="기간이나 필터를 바꿔 다시 확인해보세요." /> : null}{query.data?.content.length ? children : null}</section>;
}

function QueryState({ query, loading, fallback, children }: { query: { isLoading: boolean; error: unknown }; loading: string; fallback: string; children: ReactNode }) {
  return <>{query.isLoading ? <p className="status-text" role="status">{loading}</p> : null}{query.error ? <ErrorState message={errorMessage(query.error, fallback)} /> : null}{children}</>;
}

function PanelHeading({ eyebrow, title, id, aside }: { eyebrow: string; title: string; id: string; aside?: string }) { return <div className="operations-panel-heading"><div><p className="eyebrow">{eyebrow}</p><h2 id={id}>{title}</h2></div>{aside ? <span>{aside}</span> : null}</div>; }
function TableFrame({ label, headers, children }: { label: string; headers: string[]; children: ReactNode }) { return <div className="operations-table-scroll"><table className="operations-table admin-dashboard-table" aria-label={label}><thead><tr>{headers.map((header) => <th scope="col" key={header}>{header}</th>)}</tr></thead><tbody>{children}</tbody></table></div>; }
function Cell({ label, children }: { label: string; children: ReactNode }) { return <td data-label={label}>{children}</td>; }
function PageNavigation({ page, data, label, onChange }: { page: number; data?: Page<unknown>; label: string; onChange: (page: number) => void }) { if (!data || data.totalPages <= 1) return null; return <nav className="operations-pagination" aria-label={label}><button type="button" disabled={data.first} onClick={() => onChange(page - 1)}>이전</button><span>{page + 1} / {data.totalPages}</span><button type="button" disabled={data.last} onClick={() => onChange(page + 1)}>다음</button></nav>; }
function positiveInteger(value: string) { const parsed = Number(value); return /^\d+$/.test(value.trim()) && parsed >= 1 ? parsed : undefined; }
function ownershipLabel(ownerType: string, ownerStoreId: number | null) { return ownerType === 'PLATFORM' ? '플랫폼' : ownerStoreId ? `상점 #${ownerStoreId}` : '상점'; }
function kindLabel(kind: string) { return kind === 'PROMOTION' ? '프로모션' : kind === 'COUPON' ? '쿠폰' : kind; }
function money(value: number) { return `${value.toLocaleString('ko-KR')}원`; }
function formatLag(seconds: number) { return seconds < 60 ? `${seconds}초` : `${Math.floor(seconds / 60)}분 ${seconds % 60}초`; }
function formatKstDateTime(value: string) { return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) + ' KST'; }
function errorMessage(error: unknown, fallback: string) { const apiError = error as Partial<ApiError>; return apiError.fieldErrors?.[0]?.message ?? apiError.message ?? fallback; }
