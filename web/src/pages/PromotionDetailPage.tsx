import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { getPromotion, transitionPromotion, updatePromotion } from '../features/promotions/promotionApi';
import { getStoreCatalogProducts, getOperableStores, storeOperationQueryKeys } from '../features/stores/storeOperationsApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';
import { parsePositiveIntegerParam } from '../shared/utils/parseId';
import { formatKstDateTime, invalidatePromotionPrices, PromotionAccessState, PromotionForm } from './PromotionWorkspacePage';

export function PromotionDetailPage() {
  const { storeId, promotionId } = useParams();
  const queryClient = useQueryClient();
  const parsedStoreId = parsePositiveIntegerParam(storeId);
  const parsedPromotionId = parsePositiveIntegerParam(promotionId);
  const hasValidParams = parsedStoreId !== null && parsedPromotionId !== null;
  const storesQuery = useQuery({ queryKey: storeOperationQueryKeys.stores(), queryFn: getOperableStores });
  const allowedStore = (storesQuery.data ?? []).find((store) => store.storeId === parsedStoreId && store.type === 'BUSINESS' && store.role === 'OWNER' && store.status === 'ACTIVE');
  const promotionQuery = useQuery({ queryKey: ['promotions', 'store', parsedStoreId, 'detail', parsedPromotionId], queryFn: () => getPromotion(parsedStoreId ?? 0, parsedPromotionId ?? 0), enabled: hasValidParams && allowedStore !== undefined });
  const productsQuery = useQuery({ queryKey: storeOperationQueryKeys.productList(parsedStoreId ?? 0, { sort: 'NEWEST', page: 0, size: 100 }), queryFn: () => getStoreCatalogProducts(parsedStoreId ?? 0, { sort: 'NEWEST', page: 0, size: 100 }), enabled: allowedStore !== undefined });
  const updateMutation = useMutation({
    mutationFn: (input: Parameters<typeof updatePromotion>[2]) => updatePromotion(parsedStoreId ?? 0, parsedPromotionId ?? 0, input),
    onSuccess: async () => { await invalidatePromotionPrices(queryClient, parsedStoreId ?? 0); },
  });
  const transitionMutation = useMutation({
    mutationFn: (action: 'schedule' | 'pause' | 'resume' | 'end') => transitionPromotion(parsedStoreId ?? 0, parsedPromotionId ?? 0, action),
    onSuccess: async () => { await invalidatePromotionPrices(queryClient, parsedStoreId ?? 0); },
  });

  if (!hasValidParams) return <ErrorState message="프로모션 주소가 올바르지 않습니다." />;
  if (storesQuery.isLoading) return <p className="status-text">프로모션 권한을 확인하고 있습니다.</p>;
  if (storesQuery.error) return <ErrorState message={toErrorMessage(storesQuery.error, '상점 정보를 불러오지 못했습니다.')} />;
  if (!allowedStore) return <PromotionAccessState />;
  if (promotionQuery.isLoading) return <p className="status-text">프로모션을 불러오고 있습니다.</p>;
  if (promotionQuery.error) return <ErrorState message={toErrorMessage(promotionQuery.error, '프로모션을 불러오지 못했습니다.')} />;
  if (!promotionQuery.data) return <EmptyState title="프로모션을 찾을 수 없습니다" />;

  const promotion = promotionQuery.data;
  const initialValue = { scope: promotion.scope, discountType: promotion.discountType, discountValue: promotion.discountValue, priority: promotion.priority, title: promotion.title, label: promotion.label ?? undefined, startsAt: promotion.startsAt, endsAt: promotion.endsAt, productIds: promotion.targets?.map((target) => target.productId) ?? [] };
  return (
    <main className="promotion-workspace">
      <section className="promotion-workspace-header">
        <div><p className="eyebrow">PROMOTION DETAIL</p><h1>{promotion.title}</h1><p>{allowedStore.publicName} · {formatKstDateTime(promotion.startsAt)} ~ {formatKstDateTime(promotion.endsAt)}</p></div>
        <Link className="text-button" to="/me/store/promotions">목록으로</Link>
      </section>
      <section className="promotion-panel">
        <div className="promotion-detail-status"><StatusBadge status={promotion.effectiveStatus} /><span>{promotion.buyerText}</span><strong>{promotion.discountText}</strong><span>{promotion.scope === 'STORE_WIDE' ? '상점 전체 적용' : `${promotion.targetCount}개 선택 상품 적용`}</span></div>
        <div className="promotion-lifecycle-actions">
          {promotion.lifecycleStatus === 'DRAFT' ? <button className="text-button" type="button" disabled={transitionMutation.isPending} onClick={() => transitionMutation.mutate('schedule')}>예약 시작</button> : null}
          {promotion.lifecycleStatus === 'SCHEDULED' ? <button className="text-button" type="button" disabled={transitionMutation.isPending} onClick={() => transitionMutation.mutate('pause')}>일시 중지</button> : null}
          {promotion.lifecycleStatus === 'PAUSED' ? <button className="text-button" type="button" disabled={transitionMutation.isPending} onClick={() => transitionMutation.mutate('resume')}>재개</button> : null}
          {promotion.lifecycleStatus !== 'ENDED' ? <button className="text-button danger-button" type="button" disabled={transitionMutation.isPending} onClick={() => transitionMutation.mutate('end')}>종료</button> : null}
          {transitionMutation.isError ? <p className="error-text">{toErrorMessage(transitionMutation.error, '프로모션 상태를 변경하지 못했습니다.')}</p> : null}
        </div>
        {promotion.targets?.length ? <section className="promotion-target-summary"><h2>적용 상품</h2><ul>{promotion.targets.map((target) => <li key={target.productId}>{target.title} · {target.price.toLocaleString('ko-KR')}원</li>)}</ul></section> : null}
        <section className="promotion-edit-section"><div><p className="eyebrow">EDIT</p><h2>프로모션 수정</h2><p>초안 상태에서만 할인 조건과 기간을 수정할 수 있습니다.</p></div><PromotionForm products={productsQuery.data?.content ?? []} initialValue={initialValue} pending={updateMutation.isPending} disabled={promotion.lifecycleStatus !== 'DRAFT'} error={updateMutation.error} submitLabel="수정 저장" onSubmit={(input) => updateMutation.mutate(input)} /></section>
      </section>
    </main>
  );
}

function toErrorMessage(error: unknown, fallback: string) { const apiError = error as Partial<ApiError>; return apiError.fieldErrors?.[0]?.message ?? apiError.message ?? fallback; }
