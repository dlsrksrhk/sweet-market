import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { StoreCatalogPanel } from '../features/stores/StoreCatalogPanel';
import { StoreMembershipPanel } from '../features/stores/StoreMembershipPanel';
import { StoreProfilePanel } from '../features/stores/StoreProfilePanel';
import {
  getOperableStores,
  getStoreCatalogSummary,
  storeOperationQueryKeys,
  type OperableStore,
} from '../features/stores/storeOperationsApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

type WorkspaceTab = 'catalog' | 'profile' | 'memberships';

export function MyStorePage() {
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<WorkspaceTab>('catalog');
  const storesQuery = useQuery({ queryKey: storeOperationQueryKeys.stores(), queryFn: getOperableStores });
  const stores = storesQuery.data ?? [];
  const selectedStore = stores.find((store) => store.storeId === selectedStoreId) ?? stores[0] ?? null;
  const summaryQuery = useQuery({
    queryKey: storeOperationQueryKeys.summary(selectedStore?.storeId ?? 0),
    queryFn: () => getStoreCatalogSummary(selectedStore?.storeId ?? 0),
    enabled: selectedStore !== null,
  });

  useEffect(() => {
    setSelectedStoreId((currentId) => stores.some((store) => store.storeId === currentId) ? currentId : stores[0]?.storeId ?? null);
  }, [stores]);

  useEffect(() => {
    setActiveTab('catalog');
  }, [selectedStore?.storeId]);

  if (storesQuery.isLoading) return <p className="status-text">운영 가능한 상점을 불러오고 있습니다.</p>;
  if (storesQuery.error) return <ErrorState message={toErrorMessage(storesQuery.error, '운영 가능한 상점을 불러오지 못했습니다.')} />;
  if (!selectedStore) return <EmptyState title="운영 가능한 상점이 없습니다" description="상점 운영 권한을 확인해주세요." />;

  const isOwner = selectedStore.role === 'OWNER';
  const catalogWritable = selectedStore.status === 'ACTIVE' && (summaryQuery.data?.catalogWritable ?? false);

  return (
    <main className="store-operations-workspace">
      <section className="store-operations-header">
        <div className="store-operations-heading-row">
          <div>
            <p className="eyebrow">STORE OPERATIONS</p>
            <h1>상점 운영</h1>
            <p>상품 카탈로그와 상점 운영 정보를 한곳에서 관리합니다.</p>
          </div>
          {selectedStore.status === 'ACTIVE' ? <Link className="primary-link" to={`/stores/${selectedStore.storeId}`}>공개 상점 보기</Link> : null}
        </div>

        <label className="store-operations-selector">
          <span>운영할 상점</span>
          <select value={selectedStore.storeId} onChange={(event) => {
            setActiveTab('catalog');
            setSelectedStoreId(Number(event.target.value));
          }}>
            {stores.map((store) => <option key={store.storeId} value={store.storeId}>{store.publicName} · {toRoleLabel(store.role)}</option>)}
          </select>
        </label>

        <div className="store-operations-identity">
          <div><strong>{selectedStore.publicName}</strong><span>{selectedStore.type === 'PERSONAL' ? '개인 상점' : '사업자 상점'}</span></div>
          <StatusBadge status={selectedStore.role} />
          <StatusBadge status={selectedStore.status} />
        </div>

        <SummaryStrip store={selectedStore} summaryQuery={summaryQuery} />

        <nav className="store-owner-links" aria-label="상점 운영 메뉴">
          <Link to="/me/store/dashboard">운영 대시보드</Link>
          {isOwner ? <Link to="/me/sales">판매 관리</Link> : null}
          {isOwner ? <Link to="/me/sales/refunds">환불 관리</Link> : null}
          {isOwner ? <Link to="/me/settlements">정산</Link> : null}
          {isOwner ? <Link to="/me/reports">리포트</Link> : null}
          {isOwner ? <Link to="/me/store/promotions">프로모션</Link> : null}
          {isOwner ? <Link to="/me/store/coupons">쿠폰</Link> : null}
        </nav>
      </section>

      <nav className="store-operations-tabs" aria-label="상점 운영 탭">
        <button type="button" aria-current={activeTab === 'catalog' ? 'page' : undefined} onClick={() => setActiveTab('catalog')}>카탈로그</button>
        {isOwner ? <button type="button" aria-current={activeTab === 'profile' ? 'page' : undefined} onClick={() => setActiveTab('profile')}>프로필</button> : null}
        {isOwner ? <button type="button" aria-current={activeTab === 'memberships' ? 'page' : undefined} onClick={() => setActiveTab('memberships')}>운영 멤버</button> : null}
      </nav>

      {activeTab === 'catalog' ? <StoreCatalogPanel key={selectedStore.storeId} storeId={selectedStore.storeId} catalogWritable={catalogWritable} /> : null}
      {isOwner && activeTab === 'profile' ? <StoreProfilePanel storeId={selectedStore.storeId} /> : null}
      {isOwner && activeTab === 'memberships' ? <StoreMembershipPanel storeId={selectedStore.storeId} /> : null}
    </main>
  );
}

type SummaryStripProps = {
  store: OperableStore;
  summaryQuery: ReturnType<typeof useQuery<Awaited<ReturnType<typeof getStoreCatalogSummary>>, Error>>;
};

function SummaryStrip({ store, summaryQuery }: SummaryStripProps) {
  if (summaryQuery.isLoading) return <p className="status-text">카탈로그 요약을 불러오고 있습니다.</p>;
  if (summaryQuery.error) return <ErrorState message={toErrorMessage(summaryQuery.error, '카탈로그 요약을 불러오지 못했습니다.')} />;
  if (!summaryQuery.data) return null;

  return (
    <dl className="store-operations-summary" aria-label={`${store.publicName} 카탈로그 요약`}>
      <div><dt>판매중</dt><dd>{summaryQuery.data.onSaleCount}</dd></div>
      <div><dt>예약중</dt><dd>{summaryQuery.data.reservedCount}</dd></div>
      <div><dt>판매완료</dt><dd>{summaryQuery.data.soldOutCount}</dd></div>
      <div><dt>숨김</dt><dd>{summaryQuery.data.hiddenCount}</dd></div>
    </dl>
  );
}

function toRoleLabel(role: OperableStore['role']) {
  return role === 'OWNER' ? '소유자' : '매니저';
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  return apiError.fieldErrors?.[0]?.message ?? apiError.message ?? fallbackMessage;
}
