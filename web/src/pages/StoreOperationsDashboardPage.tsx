import { useQuery } from '@tanstack/react-query';
import { type ReactNode, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
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
  type DashboardPeriodPreset,
  type Page,
  type StoreCampaignAudit,
  type StoreCampaignMetric,
  type StoreCouponOutcome,
  type StoreInventoryPressure,
  type StorePurchaseOutcome,
} from '../features/operations/storeOperationsDashboardApi';
import {
  copyDashboardUrlState,
  deriveDashboardUrlState,
  pageParamByTab,
  resetAllPages,
  toDashboardSearchParams,
  type DashboardUrlState,
  type DrilldownTab,
} from '../features/operations/storeOperationsDashboardState';
import { getOperableStores, storeOperationQueryKeys, type OperableStore } from '../features/stores/storeOperationsApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const PAGE_SIZE = 20;
const drilldownTabs: { value: DrilldownTab; label: string }[] = [
  { value: 'campaigns', label: '캠페인 성과' },
  { value: 'coupon-outcomes', label: '쿠폰 결과' },
  { value: 'inventory-pressure', label: '재고 주의' },
  { value: 'purchase-outcomes', label: '주문 결과' },
  { value: 'campaign-audits', label: '캠페인 변경' },
];

export function StoreOperationsDashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [customFrom, setCustomFrom] = useState(searchParams.get('from') ?? '');
  const [customTo, setCustomTo] = useState(searchParams.get('to') ?? '');
  const [periodValidation, setPeriodValidation] = useState<string | null>(null);
  const storesQuery = useQuery({ queryKey: storeOperationQueryKeys.stores(), queryFn: getOperableStores });
  const stores = storesQuery.data ?? [];
  const urlState = deriveDashboardUrlState(searchParams, stores);
  const selectedStore = stores.find((store) => store.storeId === urlState.storeId) ?? null;

  useEffect(() => {
    if (!selectedStore) return;
    const canonical = toDashboardSearchParams(urlState);
    if (canonical.toString() !== searchParams.toString()) setSearchParams(canonical, { replace: true });
  }, [searchParams, selectedStore, setSearchParams, urlState]);

  useEffect(() => {
    setCustomFrom(urlState.period.from ?? '');
    setCustomTo(urlState.period.to ?? '');
  }, [urlState.period.from, urlState.period.to]);

  const updateUrlState = (update: (next: DashboardUrlState) => void) => {
    const next = copyDashboardUrlState(urlState);
    update(next);
    setSearchParams(toDashboardSearchParams(next));
  };
  const storeId = selectedStore?.storeId ?? 0;
  const activePage = urlState.pages[urlState.tab];
  const campaignsInput = { ...urlState.period, campaignKind: urlState.campaignKind || undefined, status: urlState.campaignStatus, page: urlState.pages.campaigns, size: PAGE_SIZE };
  const couponOutcomesInput = { ...urlState.period, reason: urlState.couponReason || undefined, page: urlState.pages['coupon-outcomes'], size: PAGE_SIZE };
  const inventoryInput = { ...urlState.period, attentionOnly: urlState.attentionOnly, page: urlState.pages['inventory-pressure'], size: PAGE_SIZE };
  const purchaseInput = { ...urlState.period, reason: urlState.purchaseReason || undefined, page: urlState.pages['purchase-outcomes'], size: PAGE_SIZE };
  const auditsInput = { ...urlState.period, campaignKind: urlState.auditKind || undefined, command: urlState.auditCommand || undefined, page: urlState.pages['campaign-audits'], size: PAGE_SIZE };
  const dashboardQuery = useQuery({ queryKey: storeOperationsDashboardQueryKeys.dashboard(storeId, urlState.period), queryFn: () => getStoreOperationsDashboard(storeId, urlState.period), enabled: selectedStore !== null });
  const campaignsQuery = useQuery({ queryKey: storeOperationsDashboardQueryKeys.campaigns(storeId, campaignsInput), queryFn: () => getStoreCampaignMetrics(storeId, campaignsInput), enabled: selectedStore !== null && urlState.tab === 'campaigns' });
  const couponOutcomesQuery = useQuery({ queryKey: storeOperationsDashboardQueryKeys.couponOutcomes(storeId, couponOutcomesInput), queryFn: () => getStoreCouponOutcomes(storeId, couponOutcomesInput), enabled: selectedStore !== null && urlState.tab === 'coupon-outcomes' });
  const inventoryQuery = useQuery({ queryKey: storeOperationsDashboardQueryKeys.inventoryPressure(storeId, inventoryInput), queryFn: () => getStoreInventoryPressure(storeId, inventoryInput), enabled: selectedStore !== null && urlState.tab === 'inventory-pressure' });
  const purchaseOutcomesQuery = useQuery({ queryKey: storeOperationsDashboardQueryKeys.purchaseOutcomes(storeId, purchaseInput), queryFn: () => getStorePurchaseOutcomes(storeId, purchaseInput), enabled: selectedStore !== null && urlState.tab === 'purchase-outcomes' });
  const campaignAuditsQuery = useQuery({ queryKey: storeOperationsDashboardQueryKeys.campaignAudits(storeId, auditsInput), queryFn: () => getStoreCampaignAudits(storeId, auditsInput), enabled: selectedStore !== null && urlState.tab === 'campaign-audits' });

  if (storesQuery.isLoading) return <p className="status-text" role="status">운영 대시보드 접근 권한을 확인하고 있습니다.</p>;
  if (storesQuery.error) return <ErrorState message={errorMessage(storesQuery.error, '운영 가능한 상점을 불러오지 못했습니다.')} />;
  if (!selectedStore) return <EmptyState title="운영 대시보드를 볼 수 있는 상점이 없습니다" description="OWNER 또는 MANAGER 권한이 있는 상점인지 확인해주세요." />;

  const applyPreset = (preset: DashboardPeriodPreset) => {
    setPeriodValidation(null);
    updateUrlState((next) => { next.period = { preset }; resetAllPages(next); });
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
    updateUrlState((next) => { next.period = { from: customFrom, to: customTo }; resetAllPages(next); });
  };

  return (
    <main className="operations-dashboard">
      <header className="operations-dashboard-header">
        <div className="operations-dashboard-heading"><div><p className="eyebrow">STORE OPERATIONS DASHBOARD</p><h1>운영 대시보드</h1><p>판매, 캠페인, 쿠폰, 재고 흐름을 한곳에서 점검합니다.</p></div><Link className="text-button secondary-button" to="/me/store">상점 운영으로</Link></div>
        <label className="operations-store-selector">운영 상점<select value={selectedStore.storeId} onChange={(event) => updateUrlState((next) => { next.storeId = Number(event.target.value); resetAllPages(next); })}>{stores.map((store) => <option key={store.storeId} value={store.storeId}>{store.publicName} · {store.role === 'OWNER' ? '소유자' : '매니저'}</option>)}</select></label>
        <div className="operations-store-identity"><strong>{selectedStore.publicName}</strong><StatusBadge status={selectedStore.role} /><StatusBadge status={selectedStore.status} /></div>
        <OperationsRouteLinks store={selectedStore} />
      </header>

      <OperationsPeriodControls period={urlState.period} customFrom={customFrom} customTo={customTo} validationMessage={periodValidation} onPresetChange={applyPreset} onCustomFromChange={setCustomFrom} onCustomToChange={setCustomTo} onCustomApply={applyCustomPeriod} />

      <section className="operations-dashboard-panel" aria-labelledby="operations-overview-title">
        <div className="operations-panel-heading"><div><p className="eyebrow">OVERVIEW</p><h2 id="operations-overview-title">운영 요약</h2></div>{dashboardQuery.data ? <span>{dashboardQuery.data.period.from} ~ {dashboardQuery.data.period.to} KST</span> : null}</div>
        {dashboardQuery.isLoading ? <p className="status-text" role="status">운영 요약을 불러오고 있습니다.</p> : null}
        {dashboardQuery.error ? <ErrorState message={errorMessage(dashboardQuery.error, '운영 요약 요청에 실패했습니다. 잠시 후 다시 시도해주세요.')} /> : null}
        {dashboardQuery.data ? <><ProjectionFreshness generatedAt={dashboardQuery.data.generatedAt} projectionUpdatedAt={dashboardQuery.data.projectionUpdatedAt} projectionLagSeconds={dashboardQuery.data.projectionLagSeconds} trackingStartedAt={dashboardQuery.data.trackingStartedAt} /><OperationsSummaryCards dashboard={dashboardQuery.data} /><LeadingFailureReasons reasons={dashboardQuery.data.leadingFailureReasons} trackingStarted={dashboardQuery.data.trackingStartedAt !== null} /></> : null}
      </section>

      <section className="operations-dashboard-panel" aria-labelledby="operations-drilldown-title">
        <div className="operations-panel-heading"><div><p className="eyebrow">DRILLDOWN</p><h2 id="operations-drilldown-title">영역별 상세</h2></div></div>
        <nav className="operations-drilldown-tabs" aria-label="운영 상세 영역">{drilldownTabs.map((tab) => <button type="button" key={tab.value} aria-current={urlState.tab === tab.value ? 'page' : undefined} onClick={() => updateUrlState((next) => { next.tab = tab.value; })}>{tab.label}</button>)}</nav>
        <DrilldownFilters state={urlState} update={(field, value) => updateUrlState((next) => { (next as unknown as Record<string, unknown>)[field] = value; next.pages[urlState.tab] = 0; })} />
        <DrilldownContent activeTab={urlState.tab} store={selectedStore} campaigns={campaignsQuery.data} couponOutcomes={couponOutcomesQuery.data} inventory={inventoryQuery.data} purchaseOutcomes={purchaseOutcomesQuery.data} audits={campaignAuditsQuery.data} loading={urlState.tab === 'campaigns' ? campaignsQuery.isLoading : urlState.tab === 'coupon-outcomes' ? couponOutcomesQuery.isLoading : urlState.tab === 'inventory-pressure' ? inventoryQuery.isLoading : urlState.tab === 'purchase-outcomes' ? purchaseOutcomesQuery.isLoading : campaignAuditsQuery.isLoading} error={urlState.tab === 'campaigns' ? campaignsQuery.error : urlState.tab === 'coupon-outcomes' ? couponOutcomesQuery.error : urlState.tab === 'inventory-pressure' ? inventoryQuery.error : urlState.tab === 'purchase-outcomes' ? purchaseOutcomesQuery.error : campaignAuditsQuery.error} />
        <DashboardPagination data={urlState.tab === 'campaigns' ? campaignsQuery.data : urlState.tab === 'coupon-outcomes' ? couponOutcomesQuery.data : urlState.tab === 'inventory-pressure' ? inventoryQuery.data : urlState.tab === 'purchase-outcomes' ? purchaseOutcomesQuery.data : campaignAuditsQuery.data} page={activePage} onPageChange={(page) => updateUrlState((next) => { next.pages[urlState.tab] = page; })} />
      </section>
    </main>
  );
}

export function OperationsRouteLinks({ store }: { store: OperableStore }) {
  const canManageCampaigns = canManageStoreCampaigns(store);
  return <nav className="operations-route-links" aria-label="운영 화면 바로가기"><Link to="/me/sales">판매 내역</Link><Link to="/me/store">재고·상품</Link><Link to="/me/sales/refunds">환불 관리</Link><Link to="/me/settlements">정산</Link><Link to="/me/reports">리포트</Link>{canManageCampaigns ? <Link className="owner-control" to="/me/store/promotions">프로모션 관리</Link> : null}{canManageCampaigns ? <Link className="owner-control" to="/me/store/coupons">쿠폰 관리</Link> : null}</nav>;
}

export function ProjectionFreshness({ generatedAt, projectionUpdatedAt, projectionLagSeconds, trackingStartedAt }: { generatedAt: string; projectionUpdatedAt: string | null; projectionLagSeconds: number; trackingStartedAt: string | null }) {
  if (!trackingStartedAt) return <div className="operations-freshness is-neutral" role="status"><strong>운영 지표 추적을 아직 시작하지 않았습니다.</strong><span>이 상점의 측정 이벤트가 들어오면 지표가 표시됩니다.</span></div>;
  if (!projectionUpdatedAt) return <div className="operations-freshness is-delayed" role="status"><strong>프로젝션 반영이 지연되고 있습니다.</strong><span>요청은 완료됐지만 집계 갱신 시각을 확인할 수 없습니다.</span></div>;
  return <div className="operations-freshness" role="status"><strong>프로젝션 최신성</strong><span>갱신 {formatKstDateTime(projectionUpdatedAt)} · 생성 {formatKstDateTime(generatedAt)} · 지연 {formatLag(projectionLagSeconds)}</span></div>;
}

function LeadingFailureReasons({ reasons, trackingStarted }: { reasons: { reason: string; count: number }[]; trackingStarted: boolean }) {
  return <section className="operations-reasons" aria-labelledby="operations-reasons-title"><h3 id="operations-reasons-title">주요 실패 사유</h3>{!trackingStarted ? <p>추적을 시작하지 않아 실패 사유가 없습니다.</p> : reasons.length === 0 ? <p>측정된 실패 사유가 없습니다.</p> : <ol>{reasons.map((reason) => <li key={reason.reason}><span>{reasonLabel(reason.reason)}</span><strong>{reason.count.toLocaleString('ko-KR')}건</strong></li>)}</ol>}</section>;
}

function DrilldownFilters({ state, update }: { state: DashboardUrlState; update: (field: keyof DashboardUrlState, value: string | boolean) => void }) {
  if (state.tab === 'campaigns') return <div className="operations-filters"><KindFilter value={state.campaignKind} onChange={(value) => update('campaignKind', value)} /><label>상태<select value={state.campaignStatus} onChange={(event) => update('campaignStatus', event.target.value)}><option value="DRAFT">초안</option><option value="SCHEDULED">예정</option><option value="ACTIVE">진행 중</option><option value="PAUSED">중지</option><option value="ENDED">종료</option></select></label></div>;
  if (state.tab === 'coupon-outcomes') return <div className="operations-filters"><label>결과 사유<input value={state.couponReason} placeholder="예: EXHAUSTED" onChange={(event) => update('couponReason', event.target.value)} /></label></div>;
  if (state.tab === 'inventory-pressure') return <div className="operations-filters"><label className="operations-checkbox"><input type="checkbox" checked={state.attentionOnly} onChange={(event) => update('attentionOnly', event.target.checked)} />주의가 필요한 상품만</label></div>;
  if (state.tab === 'purchase-outcomes') return <div className="operations-filters"><label>결과 사유<input value={state.purchaseReason} placeholder="예: PAYMENT_FAILED" onChange={(event) => update('purchaseReason', event.target.value)} /></label></div>;
  return <div className="operations-filters"><KindFilter value={state.auditKind} onChange={(value) => update('auditKind', value)} /><label>명령<input value={state.auditCommand} placeholder="예: PAUSE" onChange={(event) => update('auditCommand', event.target.value)} /></label></div>;
}

function KindFilter({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return <label>캠페인 종류<select value={value} onChange={(event) => onChange(event.target.value)}><option value="">전체</option><option value="PROMOTION">프로모션</option><option value="COUPON">쿠폰</option></select></label>;
}

type DrilldownContentProps = { activeTab: DrilldownTab; store: OperableStore; campaigns?: Page<StoreCampaignMetric>; couponOutcomes?: Page<StoreCouponOutcome>; inventory?: Page<StoreInventoryPressure>; purchaseOutcomes?: Page<StorePurchaseOutcome>; audits?: Page<StoreCampaignAudit>; loading: boolean; error: unknown };

function DrilldownContent({ activeTab, store, campaigns, couponOutcomes, inventory, purchaseOutcomes, audits, loading, error }: DrilldownContentProps) {
  if (loading) return <p className="status-text" role="status">상세 운영 지표를 불러오고 있습니다.</p>;
  if (error) return <ErrorState message={errorMessage(error, '상세 운영 지표 요청에 실패했습니다. 요약 지표는 계속 확인할 수 있습니다.')} />;
  const rows = activeTab === 'campaigns' ? campaigns?.content : activeTab === 'coupon-outcomes' ? couponOutcomes?.content : activeTab === 'inventory-pressure' ? inventory?.content : activeTab === 'purchase-outcomes' ? purchaseOutcomes?.content : audits?.content;
  if (rows?.length === 0) {
    if (activeTab === 'campaigns' && store.type === 'PERSONAL') return <EmptyState title="개인 상점에는 운영할 상점 캠페인이 없습니다" description="주문과 재고 운영 지표는 다른 상세 영역에서 계속 확인할 수 있습니다." />;
    return <EmptyState title="해당 조건의 상세 지표가 없습니다" description="기간이나 필터를 바꿔 다시 확인해보세요." />;
  }
  if (activeTab === 'campaigns' && campaigns) return <CampaignTable page={campaigns} store={store} />;
  if (activeTab === 'coupon-outcomes' && couponOutcomes) return <CouponOutcomeTable page={couponOutcomes} store={store} />;
  if (activeTab === 'inventory-pressure' && inventory) return <InventoryTable page={inventory} />;
  if (activeTab === 'purchase-outcomes' && purchaseOutcomes) return <PurchaseOutcomeTable page={purchaseOutcomes} />;
  if (activeTab === 'campaign-audits' && audits) return <AuditTable page={audits} store={store} />;
  return null;
}

export function CampaignTable({ page, store }: { page: Page<StoreCampaignMetric>; store: OperableStore }) {
  return <TableFrame label="캠페인 성과" className="campaign-grid" headers={[['campaign-name', '캠페인'], ['campaign-owner', '소유'], ['campaign-status', '상태'], ['campaign-claim', '발급'], ['campaign-redemption', '사용'], ['campaign-order', '주문·실패'], ['campaign-discount', '할인 실현']]}>{page.content.map((row) => <tr key={row.id}><Cell header="campaign-name" label="캠페인"><strong>{kindLabel(row.campaignKind)} #{row.campaignId}</strong>{canManageRow(store, row.ownerType, row.ownerStoreId) ? <Link to={row.campaignKind === 'PROMOTION' ? `/me/store/promotions/${store.storeId}/${row.campaignId}` : `/me/store/coupons/${store.storeId}/${row.campaignId}`}>관리</Link> : null}</Cell><Cell header="campaign-owner" label="소유">{ownershipLabel(row.ownerType, row.ownerStoreId, store)}</Cell><Cell header="campaign-status" label="상태"><StatusBadge status={row.status} /></Cell><Cell header="campaign-claim" label="발급">성공 {row.claimSuccessCount} · 실패 {row.claimFailureCount}</Cell><Cell header="campaign-redemption" label="사용">성공 {row.redemptionSuccessCount} · 실패 {row.redemptionFailureCount}</Cell><Cell header="campaign-order" label="주문·실패">{row.orderSuccessCount} · {row.purchaseFailureCount}</Cell><Cell header="campaign-discount" label="할인 실현">{money(row.promotionDiscounts.realized + row.couponDiscounts.realized)}</Cell></tr>)}</TableFrame>;
}

function CouponOutcomeTable({ page, store }: { page: Page<StoreCouponOutcome>; store: OperableStore }) {
  return <TableFrame label="쿠폰 결과" className="coupon-outcome-grid" headers={[['coupon-name', '쿠폰'], ['coupon-owner', '소유'], ['coupon-reason', '결과 사유'], ['coupon-claim', '발급'], ['coupon-redemption', '사용'], ['coupon-discount', '실현 할인']]}>{page.content.map((row) => <tr key={row.id}><Cell header="coupon-name" label="쿠폰"><strong>쿠폰 #{row.campaignId}</strong>{canManageRow(store, row.ownerType, row.ownerStoreId) ? <Link to={`/me/store/coupons/${store.storeId}/${row.campaignId}`}>관리</Link> : null}</Cell><Cell header="coupon-owner" label="소유">{ownershipLabel(row.ownerType, row.ownerStoreId, store)}</Cell><Cell header="coupon-reason" label="결과 사유">{reasonLabel(row.reason)}</Cell><Cell header="coupon-claim" label="발급">성공 {row.claimSuccessCount} · 실패 {row.claimFailureCount}</Cell><Cell header="coupon-redemption" label="사용">성공 {row.redemptionSuccessCount} · 실패 {row.redemptionFailureCount}</Cell><Cell header="coupon-discount" label="실현 할인">{money(row.discounts.realized)}</Cell></tr>)}</TableFrame>;
}

function InventoryTable({ page }: { page: Page<StoreInventoryPressure> }) {
  return <TableFrame label="재고 주의 상품" className="inventory-pressure-grid" headers={[['inventory-product', '상품'], ['inventory-stock', '재고'], ['inventory-low', '저재고'], ['inventory-failure', '예약 실패'], ['inventory-last', '마지막 실패']]}>{page.content.map((row) => <tr key={row.productId}><Cell header="inventory-product" label="상품"><Link to={`/products/${row.productId}`}>상품 #{row.productId}</Link></Cell><Cell header="inventory-stock" label="재고">{row.availableQuantity === null ? '단품' : `${row.availableQuantity}개`}</Cell><Cell header="inventory-low" label="저재고">{row.lowStock ? '주의 필요' : '정상'}</Cell><Cell header="inventory-failure" label="예약 실패">{row.reservationFailureCount}건</Cell><Cell header="inventory-last" label="마지막 실패">{row.lastReservationFailureAt ? formatKstDateTime(row.lastReservationFailureAt) : '측정된 실패 없음'}</Cell></tr>)}</TableFrame>;
}

function PurchaseOutcomeTable({ page }: { page: Page<StorePurchaseOutcome> }) {
  return <TableFrame label="주문 결과" className="purchase-outcome-grid" headers={[['purchase-reason', '결과 사유'], ['purchase-order', '주문 성공'], ['purchase-failure', '구매 실패'], ['purchase-reservation', '예약 실패'], ['purchase-latest', '최근 버킷']]}>{page.content.map((row) => <tr key={row.id}><Cell header="purchase-reason" label="결과 사유">{reasonLabel(row.reason)}</Cell><Cell header="purchase-order" label="주문 성공">{row.orderSuccessCount}건</Cell><Cell header="purchase-failure" label="구매 실패">{row.purchaseFailureCount}건</Cell><Cell header="purchase-reservation" label="예약 실패">{row.reservationFailureCount}건</Cell><Cell header="purchase-latest" label="최근 버킷">{formatKstDateTime(row.latestBucketStart)}</Cell></tr>)}</TableFrame>;
}

function AuditTable({ page, store }: { page: Page<StoreCampaignAudit>; store: OperableStore }) {
  return <TableFrame label="캠페인 변경 이력" className="audit-grid" headers={[['audit-campaign', '캠페인'], ['audit-owner', '소유'], ['audit-command', '명령'], ['audit-actor', '실행자'], ['audit-version', '버전'], ['audit-time', '발생 시각']]}>{page.content.map((row) => <tr key={row.eventId}><Cell header="audit-campaign" label="캠페인"><strong>{kindLabel(row.campaignKind)} #{row.campaignId}</strong>{canManageRow(store, row.ownerType, row.ownerStoreId) ? <Link to={row.campaignKind === 'PROMOTION' ? `/me/store/promotions/${store.storeId}/${row.campaignId}` : `/me/store/coupons/${store.storeId}/${row.campaignId}`}>관리</Link> : null}</Cell><Cell header="audit-owner" label="소유">{ownershipLabel(row.ownerType, row.ownerStoreId, store)}</Cell><Cell header="audit-command" label="명령">{row.command}</Cell><Cell header="audit-actor" label="실행자">{row.actorMemberId ? `회원 #${row.actorMemberId}` : '시스템'}</Cell><Cell header="audit-version" label="버전">{row.aggregateVersion ?? '—'}</Cell><Cell header="audit-time" label="발생 시각">{formatKstDateTime(row.occurredAt)}</Cell></tr>)}</TableFrame>;
}

function TableFrame({ label, className, headers, children }: { label: string; className: string; headers: [string, string][]; children: ReactNode }) {
  return <div className="operations-table-scroll"><table className={`operations-table ${className}`} aria-label={label}><thead><tr>{headers.map(([id, text]) => <th id={id} scope="col" key={id}>{text}</th>)}</tr></thead><tbody>{children}</tbody></table></div>;
}

function Cell({ header, label, children }: { header: string; label: string; children: ReactNode }) {
  return <td headers={header} data-label={label}>{children}</td>;
}

function DashboardPagination({ data, page, onPageChange }: { data?: Page<unknown>; page: number; onPageChange: (page: number) => void }) {
  if (!data || data.totalPages <= 1) return null;
  return <nav className="operations-pagination" aria-label="상세 지표 페이지 이동"><button className="secondary-button" type="button" disabled={data.first} onClick={() => onPageChange(page - 1)}>이전</button><span>{page + 1} / {data.totalPages}</span><button className="secondary-button" type="button" disabled={data.last} onClick={() => onPageChange(page + 1)}>다음</button></nav>;
}

function canManageStoreCampaigns(store: OperableStore) { return store.role === 'OWNER' && store.type === 'BUSINESS' && store.status === 'ACTIVE'; }
function canManageRow(store: OperableStore, ownerType: string, ownerStoreId: number | null) { return canManageStoreCampaigns(store) && ownerType === 'STORE' && ownerStoreId === store.storeId; }
function ownershipLabel(ownerType: string, ownerStoreId: number | null, store: OperableStore) { return ownerType === 'PLATFORM' ? '플랫폼' : ownerStoreId === store.storeId ? `${store.publicName} 소유` : ownerStoreId === null ? '상점 소유' : `다른 상점 #${ownerStoreId} 소유`; }
function formatKstDateTime(value: string) { return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value)) + ' KST'; }
function formatLag(seconds: number) { return seconds < 60 ? `${seconds}초` : `${Math.floor(seconds / 60)}분 ${seconds % 60}초`; }
function money(value: number) { return `${value.toLocaleString('ko-KR')}원`; }
function kindLabel(kind: string) { return kind === 'PROMOTION' ? '프로모션' : kind === 'COUPON' ? '쿠폰' : kind; }
function reasonLabel(reason: string) { const labels: Record<string, string> = { NONE: '정상', EXHAUSTED: '수량 소진', PAYMENT_FAILED: '결제 실패', SOLD_OUT: '품절', INVENTORY_SHORTAGE: '재고 부족' }; return labels[reason] ?? reason; }
function errorMessage(error: unknown, fallback: string) { const apiError = error as Partial<ApiError>; return apiError.fieldErrors?.[0]?.message ?? apiError.message ?? fallback; }
