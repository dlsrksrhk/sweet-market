import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  createPromotion,
  getPromotions,
  promotionQueryKeys,
  type PromotionCampaignInput,
  type PromotionEffectiveStatus,
} from '../features/promotions/promotionApi';
import { getStoreCatalogProducts, getOperableStores, storeOperationQueryKeys } from '../features/stores/storeOperationsApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const PAGE_SIZE = 20;
const statusOptions: { value: PromotionEffectiveStatus | ''; label: string }[] = [
  { value: '', label: '전체 상태' },
  { value: 'SCHEDULED', label: '예정' },
  { value: 'ACTIVE', label: '진행 중' },
  { value: 'PAUSED', label: '일시 중지' },
  { value: 'ENDED', label: '종료' },
];

export function PromotionWorkspacePage() {
  const queryClient = useQueryClient();
  const [storeId, setStoreId] = useState<number | null>(null);
  const [status, setStatus] = useState<PromotionEffectiveStatus | ''>('');
  const [periodFrom, setPeriodFrom] = useState('');
  const [periodTo, setPeriodTo] = useState('');
  const [page, setPage] = useState(0);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const storesQuery = useQuery({ queryKey: storeOperationQueryKeys.stores(), queryFn: getOperableStores });
  const promotionStores = (storesQuery.data ?? []).filter((store) => store.type === 'BUSINESS' && store.role === 'OWNER' && store.status === 'ACTIVE');
  const selectedStore = promotionStores.find((store) => store.storeId === storeId) ?? promotionStores[0] ?? null;
  const listInput = { status: status || undefined, periodFrom: periodFrom || undefined, periodTo: periodTo || undefined, page, size: PAGE_SIZE };
  const promotionsQuery = useQuery({
    queryKey: promotionQueryKeys.list(selectedStore?.storeId ?? 0, listInput),
    queryFn: () => getPromotions(selectedStore?.storeId ?? 0, listInput),
    enabled: selectedStore !== null,
  });
  const productsQuery = useQuery({
    queryKey: storeOperationQueryKeys.productList(selectedStore?.storeId ?? 0, { sort: 'NEWEST', page: 0, size: 100 }),
    queryFn: () => getStoreCatalogProducts(selectedStore?.storeId ?? 0, { sort: 'NEWEST', page: 0, size: 100 }),
    enabled: selectedStore !== null && showCreateForm,
  });
  const createMutation = useMutation({
    mutationFn: (input: PromotionCampaignInput) => createPromotion(selectedStore?.storeId ?? 0, input),
    onSuccess: async () => {
      await invalidatePromotionPrices(queryClient, selectedStore?.storeId ?? 0);
      setShowCreateForm(false);
    },
  });

  useEffect(() => {
    setStoreId((current) => promotionStores.some((store) => store.storeId === current) ? current : promotionStores[0]?.storeId ?? null);
  }, [storesQuery.data]);

  if (storesQuery.isLoading) return <p className="status-text">프로모션 권한을 확인하고 있습니다.</p>;
  if (storesQuery.error) return <ErrorState message={toErrorMessage(storesQuery.error, '상점 정보를 불러오지 못했습니다.')} />;
  if (!selectedStore) return <PromotionAccessState />;

  return (
    <main className="promotion-workspace">
      <section className="promotion-workspace-header">
        <div>
          <p className="eyebrow">PROMOTIONS</p>
          <h1>프로모션 관리</h1>
          <p>사업자 상점 소유자만 할인 프로모션을 만들고 운영할 수 있습니다.</p>
        </div>
        <label className="promotion-store-selector">상점
          <select value={selectedStore.storeId} onChange={(event) => { setStoreId(Number(event.target.value)); setPage(0); setShowCreateForm(false); }}>
            {promotionStores.map((store) => <option key={store.storeId} value={store.storeId}>{store.publicName}</option>)}
          </select>
        </label>
      </section>

      <section className="promotion-panel">
        <div className="promotion-panel-heading">
          <div><p className="eyebrow">CAMPAIGNS</p><h2>{selectedStore.publicName} 프로모션</h2></div>
          <button className="text-button" type="button" onClick={() => setShowCreateForm((current) => !current)}>{showCreateForm ? '등록 닫기' : '프로모션 등록'}</button>
        </div>
        {showCreateForm ? <PromotionForm products={productsQuery.data?.content ?? []} pending={createMutation.isPending} error={createMutation.error} submitLabel="등록하기" onSubmit={(input) => createMutation.mutate(input)} /> : null}
        <form className="promotion-filter" onSubmit={(event) => { event.preventDefault(); setPage(0); }}>
          <label>상태<select value={status} onChange={(event) => { setStatus(event.target.value as PromotionEffectiveStatus | ''); setPage(0); }}>{statusOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
          <label>시작일 이후<input type="datetime-local" value={periodFrom} onChange={(event) => { setPeriodFrom(event.target.value); setPage(0); }} /></label>
          <label>종료일 이전<input type="datetime-local" value={periodTo} onChange={(event) => { setPeriodTo(event.target.value); setPage(0); }} /></label>
          <button className="text-button" type="submit">필터 적용</button>
        </form>
        {promotionsQuery.isLoading ? <p className="status-text">프로모션을 불러오고 있습니다.</p> : null}
        {promotionsQuery.error ? <ErrorState message={toErrorMessage(promotionsQuery.error, '프로모션을 불러오지 못했습니다.')} /> : null}
        {promotionsQuery.data?.content.length === 0 ? <EmptyState title="등록된 프로모션이 없습니다" description="할인 조건과 기간을 설정해 새 프로모션을 등록해보세요." /> : null}
        <div className="promotion-list">
          {promotionsQuery.data?.content.map((promotion) => (
            <article className="promotion-card" key={promotion.id}>
              <div><StatusBadge status={promotion.effectiveStatus} /><h3>{promotion.title}</h3><p>{promotion.buyerText} · {promotion.discountText}</p><span>{formatKstDateTime(promotion.startsAt)} ~ {formatKstDateTime(promotion.endsAt)}</span></div>
              <div className="promotion-card-actions"><span>{promotion.scope === 'STORE_WIDE' ? '상점 전체' : `${promotion.targetCount}개 상품`}</span><Link className="text-button" to={`/me/store/promotions/${selectedStore.storeId}/${promotion.id}`}>상세·수정</Link></div>
            </article>
          ))}
        </div>
        {promotionsQuery.data && promotionsQuery.data.totalPages > 1 ? <nav className="promotion-pagination" aria-label="프로모션 페이지 이동"><button className="text-button" type="button" disabled={page === 0 || promotionsQuery.isFetching} onClick={() => setPage((current) => current - 1)}>이전</button><span>{page + 1} / {promotionsQuery.data.totalPages}</span><button className="text-button" type="button" disabled={page + 1 >= promotionsQuery.data.totalPages || promotionsQuery.isFetching} onClick={() => setPage((current) => current + 1)}>다음</button></nav> : null}
      </section>
    </main>
  );
}

export function PromotionAccessState() {
  return <EmptyState title="프로모션 관리 권한이 없습니다" description="활성 사업자 상점의 소유자만 프로모션을 관리할 수 있습니다. 개인 상점과 매니저 권한에서는 사용할 수 없습니다." />;
}

type PromotionFormProps = {
  products: { productId: number; title: string; price: number }[];
  initialValue?: PromotionCampaignInput;
  pending: boolean;
  disabled?: boolean;
  error: unknown;
  submitLabel: string;
  onSubmit: (input: PromotionCampaignInput) => void;
};

export function PromotionForm({ products, initialValue, pending, disabled = false, error, submitLabel, onSubmit }: PromotionFormProps) {
  const [scope, setScope] = useState(initialValue?.scope ?? 'STORE_WIDE');
  const [discountType, setDiscountType] = useState(initialValue?.discountType ?? 'PERCENTAGE');
  const [discountValue, setDiscountValue] = useState(initialValue?.discountValue ?? 10);
  const [priority, setPriority] = useState(initialValue?.priority ?? 0);
  const [title, setTitle] = useState(initialValue?.title ?? '');
  const [label, setLabel] = useState(initialValue?.label ?? '');
  const [startsAt, setStartsAt] = useState(toDateTimeInput(initialValue?.startsAt));
  const [endsAt, setEndsAt] = useState(toDateTimeInput(initialValue?.endsAt));
  const [productIds, setProductIds] = useState<number[]>(initialValue?.productIds ?? []);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);

  useEffect(() => {
    setScope(initialValue?.scope ?? 'STORE_WIDE'); setDiscountType(initialValue?.discountType ?? 'PERCENTAGE'); setDiscountValue(initialValue?.discountValue ?? 10); setPriority(initialValue?.priority ?? 0); setTitle(initialValue?.title ?? ''); setLabel(initialValue?.label ?? ''); setStartsAt(toDateTimeInput(initialValue?.startsAt)); setEndsAt(toDateTimeInput(initialValue?.endsAt)); setProductIds(initialValue?.productIds ?? []);
  }, [initialValue]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (scope === 'SELECTED_PRODUCTS' && productIds.length === 0) { setValidationMessage('대상 상품을 하나 이상 선택해주세요.'); return; }
    if (!startsAt || !endsAt) { setValidationMessage('시작과 종료 시각을 모두 입력해주세요.'); return; }
    setValidationMessage(null);
    onSubmit({ scope, discountType, discountValue, priority, title: title.trim(), label: label.trim() || undefined, startsAt, endsAt, productIds: scope === 'SELECTED_PRODUCTS' ? productIds : [] });
  };

  return (
    <form className="promotion-form" onSubmit={submit}>
      <label>프로모션 이름<input disabled={disabled} value={title} maxLength={100} required onChange={(event) => setTitle(event.target.value)} /></label>
      <label>구매자 표시 문구 (선택)<input disabled={disabled} value={label} maxLength={200} onChange={(event) => setLabel(event.target.value)} /></label>
      <label>대상 범위<select disabled={disabled} value={scope} onChange={(event) => setScope(event.target.value as PromotionCampaignInput['scope'])}><option value="STORE_WIDE">상점 전체</option><option value="SELECTED_PRODUCTS">선택 상품</option></select></label>
      <label>할인 방식<select disabled={disabled} value={discountType} onChange={(event) => setDiscountType(event.target.value as PromotionCampaignInput['discountType'])}><option value="PERCENTAGE">정률 할인 (%)</option><option value="FIXED_AMOUNT">정액 할인 (원)</option></select></label>
      <label>할인 값<input disabled={disabled} type="number" min="0" required value={discountValue} onChange={(event) => setDiscountValue(Number(event.target.value))} /></label>
      <label>우선순위<input disabled={disabled} type="number" value={priority} onChange={(event) => setPriority(Number(event.target.value))} /></label>
      <label>시작 시각 (KST)<input disabled={disabled} type="datetime-local" required value={startsAt} onChange={(event) => setStartsAt(event.target.value)} /></label>
      <label>종료 시각 (KST)<input disabled={disabled} type="datetime-local" required value={endsAt} onChange={(event) => setEndsAt(event.target.value)} /></label>
      {scope === 'SELECTED_PRODUCTS' ? <fieldset className="promotion-targets"><legend>대상 상품</legend>{products.map((product) => <label key={product.productId}><input disabled={disabled} type="checkbox" checked={productIds.includes(product.productId)} onChange={() => setProductIds((current) => current.includes(product.productId) ? current.filter((id) => id !== product.productId) : [...current, product.productId])} />{product.title} · {product.price.toLocaleString('ko-KR')}원</label>)}</fieldset> : null}
      {validationMessage ? <p className="error-text" role="alert">{validationMessage}</p> : null}
      {error ? <p className="error-text" role="alert">{toErrorMessage(error, '프로모션을 저장하지 못했습니다.')}</p> : null}
      <button className="text-button" type="submit" disabled={pending || disabled}>{pending ? '저장 중' : disabled ? '수정 불가' : submitLabel}</button>
    </form>
  );
}

export async function invalidatePromotionPrices(queryClient: ReturnType<typeof useQueryClient>, storeId: number) {
  await Promise.allSettled([
    queryClient.invalidateQueries({ queryKey: promotionQueryKeys.store(storeId) }),
    queryClient.invalidateQueries({ queryKey: ['catalog'] }),
    queryClient.invalidateQueries({ queryKey: ['products'] }),
    queryClient.invalidateQueries({ queryKey: ['my-cart'] }),
  ]);
}

export function toDateTimeInput(value: string | undefined) { return value ? value.slice(0, 16) : ''; }
export function formatKstDateTime(value: string) { const [date, time] = value.slice(0, 16).split('T'); return `${date.replaceAll('-', '.')} ${time} KST`; }
function toErrorMessage(error: unknown, fallback: string) { const apiError = error as Partial<ApiError>; return apiError.fieldErrors?.[0]?.message ?? apiError.message ?? fallback; }
