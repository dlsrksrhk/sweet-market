import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { OperationsPeriodControls } from '../features/operations/OperationsPeriodControls';
import { OperationsSummaryCards } from '../features/operations/OperationsSummaryCards';
import {
  getStoreCampaignAudits,
  getStoreCampaignMetrics,
  getStoreCouponOutcomes,
  getStoreInventoryPressure,
  getStoreOperationsDashboard,
  getStorePurchaseOutcomes,
  storeOperationsDashboardQueryKeys,
  type DashboardPeriodInput,
  type DashboardPeriodPreset,
  type Page,
  type StoreCampaignAudit,
  type StoreCampaignMetric,
  type StoreCouponOutcome,
  type StoreInventoryPressure,
  type StorePurchaseOutcome,
} from '../features/operations/storeOperationsDashboardApi';
import { getOperableStores, storeOperationQueryKeys, type OperableStore } from '../features/stores/storeOperationsApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const PAGE_SIZE = 20;
type DrilldownTab = 'campaigns' | 'coupon-outcomes' | 'inventory-pressure' | 'purchase-outcomes' | 'campaign-audits';

const drilldownTabs: { value: DrilldownTab; label: string }[] = [
  { value: 'campaigns', label: '캠페인 성과' },
  { value: 'coupon-outcomes', label: '쿠폰 결과' },
  { value: 'inventory-pressure', label: '재고 주의' },
  { value: 'purchase-outcomes', label: '주문 결과' },
  { value: 'campaign-audits', label: '캠페인 변경' },
];

export function StoreOperationsDashboardPage() {
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(null);
  const [period, setPeriod] = useState<DashboardPeriodInput>({ preset: 'LAST_30_DAYS' });
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo] = useState('');
  const [periodValidation, setPeriodValidation] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<DrilldownTab>('campaigns');
  const [page, setPage] = useState(0);
  const [campaignKind, setCampaignKind] = useState('');
  const [campaignStatus, setCampaignStatus] = useState('');
  const [couponReason, setCouponReason] = useState('');
  const [attentionOnly, setAttentionOnly] = useState(true);
  const [purchaseReason, setPurchaseReason] = useState('');
  const [auditKind, setAuditKind] = useState('');
  const [auditCommand, setAuditCommand] = useState('');

  const storesQuery = useQuery({ queryKey: storeOperationQueryKeys.stores(), queryFn: getOperableStores });
  const stores = storesQuery.data ?? [];
  const selectedStore = stores.find((store) => store.storeId === selectedStoreId) ?? stores[0] ?? null;

  useEffect(() => {
    setSelectedStoreId((current) => stores.some((store) => store.storeId === current) ? current : stores[0]?.storeId ?? null);
  }, [stores]);

  useEffect(() => {
    setPage(0);
  }, [selectedStore?.storeId, period, activeTab]);

  const storeId = selectedStore?.storeId ?? 0;
  const dashboardQuery = useQuery({
    queryKey: storeOperationsDashboardQueryKeys.dashboard(storeId, period),
    queryFn: () => getStoreOperationsDashboard(storeId, period),
    enabled: selectedStore !== null,
  });
  const campaignsInput = { ...period, campaignKind: campaignKind || undefined, status: campaignStatus || undefined, page, size: PAGE_SIZE };
  const couponOutcomesInput = { ...period, reason: couponReason.trim() || undefined, page, size: PAGE_SIZE };
  const inventoryInput = { ...period, attentionOnly, page, size: PAGE_SIZE };
  const purchaseInput = { ...period, reason: purchaseReason.trim() || undefined, page, size: PAGE_SIZE };
  const auditsInput = { ...period, campaignKind: auditKind || undefined, command: auditCommand.trim() || undefined, page, size: PAGE_SIZE };
  const campaignsQuery = useQuery({
    queryKey: storeOperationsDashboardQueryKeys.campaigns(storeId, campaignsInput),
    queryFn: () => getStoreCampaignMetrics(storeId, campaignsInput),
    enabled: selectedStore !== null && activeTab === 'campaigns',
  });
  const couponOutcomesQuery = useQuery({
    queryKey: storeOperationsDashboardQueryKeys.couponOutcomes(storeId, couponOutcomesInput),
    queryFn: () => getStoreCouponOutcomes(storeId, couponOutcomesInput),
    enabled: selectedStore !== null && activeTab === 'coupon-outcomes',
  });
  const inventoryQuery = useQuery({
    queryKey: storeOperationsDashboardQueryKeys.inventoryPressure(storeId, inventoryInput),
    queryFn: () => getStoreInventoryPressure(storeId, inventoryInput),
    enabled: selectedStore !== null && activeTab === 'inventory-pressure',
  });
  const purchaseOutcomesQuery = useQuery({
    queryKey: storeOperationsDashboardQueryKeys.purchaseOutcomes(storeId, purchaseInput),
    queryFn: () => getStorePurchaseOutcomes(storeId, purchaseInput),
    enabled: selectedStore !== null && activeTab === 'purchase-outcomes',
  });
  const campaignAuditsQuery = useQuery({
    queryKey: storeOperationsDashboardQueryKeys.campaignAudits(storeId, auditsInput),
    queryFn: () => getStoreCampaignAudits(storeId, auditsInput),
    enabled: selectedStore !== null && activeTab === 'campaign-audits',
  });

  if (storesQuery.isLoading) return <p className="status-text" role="status">운영 대시보드 접근 권한을 확인하고 있습니다.</p>;
  if (storesQuery.error) return <ErrorState message={errorMessage(storesQuery.error, '운영 가능한 상점을 불러오지 못했습니다.')} />;
  if (!selectedStore) return <EmptyState title="운영 대시보드를 볼 수 있는 상점이 없습니다" description="OWNER 또는 MANAGER 권한이 있는 상점인지 확인해주세요." />;

  const applyPreset = (preset: DashboardPeriodPreset) => {
    setPeriodValidation(null);
    setPeriod({ preset });
  };
  const applyCustomPeriod = () => {
    if (!customFrom || !customTo) {
      setPeriodValidation('시작일과 종료일을 모두 입력해주세요.');
      return;
    }
    const inclusiveDays = Math.floor((Date.parse(customTo) - Date.parse(customFrom)) / 86_400_000) + 1;
    if (inclusiveDays < 1 || inclusiveDays > 90) {
      setPeriodValidation('조회 기간은 1일 이상 90일 이하여야 합니다.');
      return;
    }
    setPeriodValidation(null);
    setPeriod({ from: customFrom, to: customTo });
  };

  return (
    <main className="operations-dashboard">
      <header className="operations-dashboard-header">
        <div className="operations-dashboard-heading">
          <div>
            <p className="eyebrow">STORE OPERATIONS DASHBOARD</p>
            <h1>운영 대시보드</h1>
            <p>판매, 캠페인, 쿠폰, 재고 흐름을 한곳에서 점검합니다.</p>
          </div>
          <Link className="text-button secondary-button" to="/me/store">상점 운영으로</Link>
        </div>
        <label className="operations-store-selector">
          운영 상점
          <select value={selectedStore.storeId} onChange={(event) => setSelectedStoreId(Number(event.target.value))}>
            {stores.map((store) => <option key={store.storeId} value={store.storeId}>{store.publicName} · {store.role === 'OWNER' ? '소유자' : '매니저'}</option>)}
          </select>
        </label>
        <div className="operations-store-identity">
          <strong>{selectedStore.publicName}</strong>
          <StatusBadge status={selectedStore.role} />
          <StatusBadge status={selectedStore.status} />
        </div>
        <OperationsRouteLinks store={selectedStore} />
      </header>

      <OperationsPeriodControls
        period={period}
        customFrom={customFrom}
        customTo={customTo}
        validationMessage={periodValidation}
        onPresetChange={applyPreset}
        onCustomFromChange={setCustomFrom}
        onCustomToChange={setCustomTo}
        onCustomApply={applyCustomPeriod}
      />

      <section className="operations-dashboard-panel" aria-labelledby="operations-overview-title">
        <div className="operations-panel-heading">
          <div><p className="eyebrow">OVERVIEW</p><h2 id="operations-overview-title">운영 요약</h2></div>
          {dashboardQuery.data ? <span>{dashboardQuery.data.period.from} ~ {dashboardQuery.data.period.to} KST</span> : null}
        </div>
        {dashboardQuery.isLoading ? <p className="status-text" role="status">운영 요약을 불러오고 있습니다.</p> : null}
        {dashboardQuery.error ? <ErrorState message={errorMessage(dashboardQuery.error, '운영 요약 요청에 실패했습니다. 잠시 후 다시 시도해주세요.')} /> : null}
        {dashboardQuery.data ? (
          <>
            <ProjectionFreshness
              generatedAt={dashboardQuery.data.generatedAt}
              projectionUpdatedAt={dashboardQuery.data.projectionUpdatedAt}
              projectionLagSeconds={dashboardQuery.data.projectionLagSeconds}
              trackingStartedAt={dashboardQuery.data.trackingStartedAt}
            />
            <OperationsSummaryCards dashboard={dashboardQuery.data} />
            <LeadingFailureReasons reasons={dashboardQuery.data.leadingFailureReasons} trackingStarted={dashboardQuery.data.trackingStartedAt !== null} />
          </>
        ) : null}
      </section>

      <section className="operations-dashboard-panel" aria-labelledby="operations-drilldown-title">
        <div className="operations-panel-heading"><div><p className="eyebrow">DRILLDOWN</p><h2 id="operations-drilldown-title">영역별 상세</h2></div></div>
        <nav className="operations-drilldown-tabs" aria-label="운영 상세 영역">
          {drilldownTabs.map((tab) => (
            <button type="button" key={tab.value} aria-current={activeTab === tab.value ? 'page' : undefined} onClick={() => setActiveTab(tab.value)}>{tab.label}</button>
          ))}
        </nav>
        <DrilldownFilters
          activeTab={activeTab}
          campaignKind={campaignKind}
          campaignStatus={campaignStatus}
          couponReason={couponReason}
          attentionOnly={attentionOnly}
          purchaseReason={purchaseReason}
          auditKind={auditKind}
          auditCommand={auditCommand}
          resetPage={() => setPage(0)}
          setCampaignKind={setCampaignKind}
          setCampaignStatus={setCampaignStatus}
          setCouponReason={setCouponReason}
          setAttentionOnly={setAttentionOnly}
          setPurchaseReason={setPurchaseReason}
          setAuditKind={setAuditKind}
          setAuditCommand={setAuditCommand}
        />
        <DrilldownContent
          activeTab={activeTab}
          store={selectedStore}
          campaigns={campaignsQuery.data}
          couponOutcomes={couponOutcomesQuery.data}
          inventory={inventoryQuery.data}
          purchaseOutcomes={purchaseOutcomesQuery.data}
          audits={campaignAuditsQuery.data}
          loading={activeTab === 'campaigns' ? campaignsQuery.isLoading : activeTab === 'coupon-outcomes' ? couponOutcomesQuery.isLoading : activeTab === 'inventory-pressure' ? inventoryQuery.isLoading : activeTab === 'purchase-outcomes' ? purchaseOutcomesQuery.isLoading : campaignAuditsQuery.isLoading}
          error={activeTab === 'campaigns' ? campaignsQuery.error : activeTab === 'coupon-outcomes' ? couponOutcomesQuery.error : activeTab === 'inventory-pressure' ? inventoryQuery.error : activeTab === 'purchase-outcomes' ? purchaseOutcomesQuery.error : campaignAuditsQuery.error}
        />
        <DashboardPagination data={activeTab === 'campaigns' ? campaignsQuery.data : activeTab === 'coupon-outcomes' ? couponOutcomesQuery.data : activeTab === 'inventory-pressure' ? inventoryQuery.data : activeTab === 'purchase-outcomes' ? purchaseOutcomesQuery.data : campaignAuditsQuery.data} page={page} onPageChange={setPage} />
      </section>
    </main>
  );
}

export function OperationsRouteLinks({ store }: { store: OperableStore }) {
  const canManageCampaigns = store.role === 'OWNER' && store.type === 'BUSINESS' && store.status === 'ACTIVE';
  return (
    <nav className="operations-route-links" aria-label="운영 화면 바로가기">
      <Link to="/me/sales">판매 내역</Link>
      <Link to="/me/store">재고·상품</Link>
      <Link to="/me/sales/refunds">환불 관리</Link>
      <Link to="/me/settlements">정산</Link>
      <Link to="/me/reports">리포트</Link>
      {canManageCampaigns ? <Link className="owner-control" to="/me/store/promotions">프로모션 관리</Link> : null}
      {canManageCampaigns ? <Link className="owner-control" to="/me/store/coupons">쿠폰 관리</Link> : null}
    </nav>
  );
}

export function ProjectionFreshness({ generatedAt, projectionUpdatedAt, projectionLagSeconds, trackingStartedAt }: { generatedAt: string; projectionUpdatedAt: string | null; projectionLagSeconds: number; trackingStartedAt: string | null }) {
  if (!trackingStartedAt) {
    return <div className="operations-freshness is-neutral" role="status"><strong>운영 지표 추적을 아직 시작하지 않았습니다.</strong><span>이 상점의 측정 이벤트가 들어오면 지표가 표시됩니다.</span></div>;
  }
  if (!projectionUpdatedAt) {
    return <div className="operations-freshness is-delayed" role="status"><strong>프로젝션 반영이 지연되고 있습니다.</strong><span>요청은 완료됐지만 집계 갱신 시각을 확인할 수 없습니다.</span></div>;
  }
  return (
    <div className="operations-freshness" role="status">
      <strong>프로젝션 최신성</strong>
      <span>갱신 {formatKstDateTime(projectionUpdatedAt)} · 생성 {formatKstDateTime(generatedAt)} · 지연 {formatLag(projectionLagSeconds)}</span>
    </div>
  );
}

function LeadingFailureReasons({ reasons, trackingStarted }: { reasons: { reason: string; count: number }[]; trackingStarted: boolean }) {
  return (
    <section className="operations-reasons" aria-labelledby="operations-reasons-title">
      <h3 id="operations-reasons-title">주요 실패 사유</h3>
      {!trackingStarted ? <p>추적을 시작하지 않아 실패 사유가 없습니다.</p> : reasons.length === 0 ? <p>측정된 실패 사유가 없습니다.</p> : (
        <ol>{reasons.map((reason) => <li key={reason.reason}><span>{reasonLabel(reason.reason)}</span><strong>{reason.count.toLocaleString('ko-KR')}건</strong></li>)}</ol>
      )}
    </section>
  );
}

type DrilldownFiltersProps = {
  activeTab: DrilldownTab;
  campaignKind: string;
  campaignStatus: string;
  couponReason: string;
  attentionOnly: boolean;
  purchaseReason: string;
  auditKind: string;
  auditCommand: string;
  resetPage: () => void;
  setCampaignKind: (value: string) => void;
  setCampaignStatus: (value: string) => void;
  setCouponReason: (value: string) => void;
  setAttentionOnly: (value: boolean) => void;
  setPurchaseReason: (value: string) => void;
  setAuditKind: (value: string) => void;
  setAuditCommand: (value: string) => void;
};

function DrilldownFilters(props: DrilldownFiltersProps) {
  const select = (setter: (value: string) => void, value: string) => { setter(value); props.resetPage(); };
  if (props.activeTab === 'campaigns') return <div className="operations-filters"><KindFilter value={props.campaignKind} onChange={(value) => select(props.setCampaignKind, value)} /><label>상태<select value={props.campaignStatus} onChange={(event) => select(props.setCampaignStatus, event.target.value)}><option value="">전체</option><option value="DRAFT">초안</option><option value="SCHEDULED">예정</option><option value="ACTIVE">진행 중</option><option value="PAUSED">중지</option><option value="ENDED">종료</option></select></label></div>;
  if (props.activeTab === 'coupon-outcomes') return <div className="operations-filters"><label>결과 사유<input value={props.couponReason} placeholder="예: EXHAUSTED" onChange={(event) => select(props.setCouponReason, event.target.value)} /></label></div>;
  if (props.activeTab === 'inventory-pressure') return <div className="operations-filters"><label className="operations-checkbox"><input type="checkbox" checked={props.attentionOnly} onChange={(event) => { props.setAttentionOnly(event.target.checked); props.resetPage(); }} />주의가 필요한 상품만</label></div>;
  if (props.activeTab === 'purchase-outcomes') return <div className="operations-filters"><label>결과 사유<input value={props.purchaseReason} placeholder="예: PAYMENT_FAILED" onChange={(event) => select(props.setPurchaseReason, event.target.value)} /></label></div>;
  return <div className="operations-filters"><KindFilter value={props.auditKind} onChange={(value) => select(props.setAuditKind, value)} /><label>명령<input value={props.auditCommand} placeholder="예: PAUSE" onChange={(event) => select(props.setAuditCommand, event.target.value)} /></label></div>;
}

function KindFilter({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return <label>캠페인 종류<select value={value} onChange={(event) => onChange(event.target.value)}><option value="">전체</option><option value="PROMOTION">프로모션</option><option value="COUPON">쿠폰</option></select></label>;
}

type DrilldownContentProps = {
  activeTab: DrilldownTab;
  store: OperableStore;
  campaigns?: Page<StoreCampaignMetric>;
  couponOutcomes?: Page<StoreCouponOutcome>;
  inventory?: Page<StoreInventoryPressure>;
  purchaseOutcomes?: Page<StorePurchaseOutcome>;
  audits?: Page<StoreCampaignAudit>;
  loading: boolean;
  error: unknown;
};

function DrilldownContent({ activeTab, store, campaigns, couponOutcomes, inventory, purchaseOutcomes, audits, loading, error }: DrilldownContentProps) {
  if (loading) return <p className="status-text" role="status">상세 운영 지표를 불러오고 있습니다.</p>;
  if (error) return <ErrorState message={errorMessage(error, '상세 운영 지표 요청에 실패했습니다. 요약 지표는 계속 확인할 수 있습니다.')} />;
  const rows = activeTab === 'campaigns' ? campaigns?.content : activeTab === 'coupon-outcomes' ? couponOutcomes?.content : activeTab === 'inventory-pressure' ? inventory?.content : activeTab === 'purchase-outcomes' ? purchaseOutcomes?.content : audits?.content;
  if (rows?.length === 0) return <EmptyState title="해당 조건의 상세 지표가 없습니다" description="기간이나 필터를 바꿔 다시 확인해보세요." />;
  if (activeTab === 'campaigns' && campaigns) return <CampaignTable page={campaigns} store={store} />;
  if (activeTab === 'coupon-outcomes' && couponOutcomes) return <CouponOutcomeTable page={couponOutcomes} />;
  if (activeTab === 'inventory-pressure' && inventory) return <InventoryTable page={inventory} />;
  if (activeTab === 'purchase-outcomes' && purchaseOutcomes) return <PurchaseOutcomeTable page={purchaseOutcomes} />;
  if (activeTab === 'campaign-audits' && audits) return <AuditTable page={audits} store={store} />;
  return null;
}

export function CampaignTable({ page, store }: { page: Page<StoreCampaignMetric>; store: OperableStore }) {
  const canManage = store.role === 'OWNER' && store.type === 'BUSINESS' && store.status === 'ACTIVE';
  return <div className="operations-table" role="table" aria-label="캠페인 성과"><div className="operations-table-head campaign-grid" role="row"><span role="columnheader">캠페인</span><span role="columnheader">상태</span><span role="columnheader">발급</span><span role="columnheader">사용</span><span role="columnheader">주문·실패</span><span role="columnheader">할인 실현</span></div>{page.content.map((row) => <div className="operations-table-row campaign-grid" role="row" key={row.id}><span role="cell" data-label="캠페인"><strong>{kindLabel(row.campaignKind)} #{row.campaignId}</strong>{canManage ? <Link to={row.campaignKind === 'PROMOTION' ? `/me/store/promotions/${store.storeId}/${row.campaignId}` : `/me/store/coupons/${store.storeId}/${row.campaignId}`}>관리</Link> : null}</span><span role="cell" data-label="상태"><StatusBadge status={row.status} /></span><span role="cell" data-label="발급">성공 {row.claimSuccessCount} · 실패 {row.claimFailureCount}</span><span role="cell" data-label="사용">성공 {row.redemptionSuccessCount} · 실패 {row.redemptionFailureCount}</span><span role="cell" data-label="주문·실패">{row.orderSuccessCount} · {row.purchaseFailureCount}</span><span role="cell" data-label="할인 실현">{money(row.promotionDiscounts.realized + row.couponDiscounts.realized)}</span></div>)}</div>;
}

function CouponOutcomeTable({ page }: { page: Page<StoreCouponOutcome> }) {
  return <div className="operations-table" role="table" aria-label="쿠폰 결과"><div className="operations-table-head coupon-outcome-grid" role="row"><span role="columnheader">쿠폰</span><span role="columnheader">결과 사유</span><span role="columnheader">발급</span><span role="columnheader">사용</span><span role="columnheader">실현 할인</span></div>{page.content.map((row) => <div className="operations-table-row coupon-outcome-grid" role="row" key={row.id}><span role="cell" data-label="쿠폰">#{row.campaignId}</span><span role="cell" data-label="결과 사유">{reasonLabel(row.reason)}</span><span role="cell" data-label="발급">성공 {row.claimSuccessCount} · 실패 {row.claimFailureCount}</span><span role="cell" data-label="사용">성공 {row.redemptionSuccessCount} · 실패 {row.redemptionFailureCount}</span><span role="cell" data-label="실현 할인">{money(row.discounts.realized)}</span></div>)}</div>;
}

function InventoryTable({ page }: { page: Page<StoreInventoryPressure> }) {
  return <div className="operations-table" role="table" aria-label="재고 주의 상품"><div className="operations-table-head inventory-pressure-grid" role="row"><span role="columnheader">상품</span><span role="columnheader">재고</span><span role="columnheader">저재고</span><span role="columnheader">예약 실패</span><span role="columnheader">마지막 실패</span></div>{page.content.map((row) => <div className="operations-table-row inventory-pressure-grid" role="row" key={row.productId}><span role="cell" data-label="상품"><Link to={`/products/${row.productId}`}>상품 #{row.productId}</Link></span><span role="cell" data-label="재고">{row.availableQuantity === null ? '단품' : `${row.availableQuantity}개`}</span><span role="cell" data-label="저재고">{row.lowStock ? '주의 필요' : '정상'}</span><span role="cell" data-label="예약 실패">{row.reservationFailureCount}건</span><span role="cell" data-label="마지막 실패">{row.lastReservationFailureAt ? formatKstDateTime(row.lastReservationFailureAt) : '측정된 실패 없음'}</span></div>)}</div>;
}

function PurchaseOutcomeTable({ page }: { page: Page<StorePurchaseOutcome> }) {
  return <div className="operations-table" role="table" aria-label="주문 결과"><div className="operations-table-head purchase-outcome-grid" role="row"><span role="columnheader">결과 사유</span><span role="columnheader">주문 성공</span><span role="columnheader">구매 실패</span><span role="columnheader">예약 실패</span><span role="columnheader">최근 버킷</span></div>{page.content.map((row) => <div className="operations-table-row purchase-outcome-grid" role="row" key={row.id}><span role="cell" data-label="결과 사유">{reasonLabel(row.reason)}</span><span role="cell" data-label="주문 성공">{row.orderSuccessCount}건</span><span role="cell" data-label="구매 실패">{row.purchaseFailureCount}건</span><span role="cell" data-label="예약 실패">{row.reservationFailureCount}건</span><span role="cell" data-label="최근 버킷">{formatKstDateTime(row.latestBucketStart)}</span></div>)}</div>;
}

function AuditTable({ page, store }: { page: Page<StoreCampaignAudit>; store: OperableStore }) {
  const canManage = store.role === 'OWNER' && store.type === 'BUSINESS' && store.status === 'ACTIVE';
  return <div className="operations-table" role="table" aria-label="캠페인 변경 이력"><div className="operations-table-head audit-grid" role="row"><span role="columnheader">캠페인</span><span role="columnheader">명령</span><span role="columnheader">실행자</span><span role="columnheader">버전</span><span role="columnheader">발생 시각</span></div>{page.content.map((row) => <div className="operations-table-row audit-grid" role="row" key={row.eventId}><span role="cell" data-label="캠페인"><strong>{kindLabel(row.campaignKind)} #{row.campaignId}</strong>{canManage ? <Link to={row.campaignKind === 'PROMOTION' ? `/me/store/promotions/${store.storeId}/${row.campaignId}` : `/me/store/coupons/${store.storeId}/${row.campaignId}`}>관리</Link> : null}</span><span role="cell" data-label="명령">{row.command}</span><span role="cell" data-label="실행자">{row.actorMemberId ? `회원 #${row.actorMemberId}` : '시스템'}</span><span role="cell" data-label="버전">{row.aggregateVersion ?? '—'}</span><span role="cell" data-label="발생 시각">{formatKstDateTime(row.occurredAt)}</span></div>)}</div>;
}

function DashboardPagination({ data, page, onPageChange }: { data?: Page<unknown>; page: number; onPageChange: (page: number) => void }) {
  if (!data || data.totalPages <= 1) return null;
  return <nav className="operations-pagination" aria-label="상세 지표 페이지 이동"><button className="secondary-button" type="button" disabled={data.first} onClick={() => onPageChange(page - 1)}>이전</button><span>{page + 1} / {data.totalPages}</span><button className="secondary-button" type="button" disabled={data.last} onClick={() => onPageChange(page + 1)}>다음</button></nav>;
}

function formatKstDateTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value)) + ' KST';
}

function formatLag(seconds: number) {
  if (seconds < 60) return `${seconds}초`;
  return `${Math.floor(seconds / 60)}분 ${seconds % 60}초`;
}

function money(value: number) { return `${value.toLocaleString('ko-KR')}원`; }
function kindLabel(kind: string) { return kind === 'PROMOTION' ? '프로모션' : kind === 'COUPON' ? '쿠폰' : kind; }
function reasonLabel(reason: string) {
  const labels: Record<string, string> = { NONE: '정상', EXHAUSTED: '수량 소진', PAYMENT_FAILED: '결제 실패', SOLD_OUT: '품절', INVENTORY_SHORTAGE: '재고 부족' };
  return labels[reason] ?? reason;
}
function errorMessage(error: unknown, fallback: string) {
  const apiError = error as Partial<ApiError>;
  return apiError.fieldErrors?.[0]?.message ?? apiError.message ?? fallback;
}
