import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { createStoreCouponCampaign, couponQueryKeys, getStoreCouponCampaigns, type CouponCampaignInput, type CouponEffectiveStatus, type CouponValidityType } from '../features/coupons/couponApi';
import { getOperableStores, getStoreCatalogProducts, storeOperationQueryKeys } from '../features/stores/storeOperationsApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const PAGE_SIZE = 20;
const statuses: { value: CouponEffectiveStatus | ''; label: string }[] = [{ value: '', label: '전체 상태' }, { value: 'SCHEDULED', label: '예정' }, { value: 'ACTIVE', label: '발급 중' }, { value: 'PAUSED', label: '일시 중지' }, { value: 'ENDED', label: '종료' }];

export function CouponCampaignWorkspacePage() {
  const queryClient = useQueryClient();
  const [storeId, setStoreId] = useState<number | null>(null);
  const [status, setStatus] = useState<CouponEffectiveStatus | ''>('');
  const [page, setPage] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const storesQuery = useQuery({ queryKey: storeOperationQueryKeys.stores(), queryFn: getOperableStores });
  const stores = (storesQuery.data ?? []).filter((store) => store.type === 'BUSINESS' && store.role === 'OWNER' && store.status === 'ACTIVE');
  const store = stores.find((candidate) => candidate.storeId === storeId) ?? stores[0] ?? null;
  const input = { status: status || undefined, page, size: PAGE_SIZE };
  const campaignsQuery = useQuery({ queryKey: couponQueryKeys.storeList(store?.storeId ?? 0, input), queryFn: () => getStoreCouponCampaigns(store?.storeId ?? 0, input), enabled: store !== null });
  const productsQuery = useQuery({ queryKey: storeOperationQueryKeys.productList(store?.storeId ?? 0, { sort: 'NEWEST', page: 0, size: 100 }), queryFn: () => getStoreCatalogProducts(store?.storeId ?? 0, { sort: 'NEWEST', page: 0, size: 100 }), enabled: store !== null && showForm });
  const createMutation = useMutation({ mutationFn: (value: CouponCampaignInput) => createStoreCouponCampaign(store?.storeId ?? 0, value), onSuccess: async () => { await invalidateCoupons(queryClient, store?.storeId); setShowForm(false); } });

  useEffect(() => { setStoreId((current) => stores.some((item) => item.storeId === current) ? current : stores[0]?.storeId ?? null); }, [storesQuery.data]);
  if (storesQuery.isLoading) return <p className="status-text">쿠폰 운영 권한을 확인하고 있습니다.</p>;
  if (storesQuery.error) return <ErrorState message={errorMessage(storesQuery.error, '상점 정보를 불러오지 못했습니다.')} />;
  if (!store) return <CouponCampaignAccessState />;

  return <main className="coupon-workspace">
    <section className="coupon-workspace-header"><div><p className="eyebrow">COUPON CAMPAIGNS</p><h1>쿠폰 캠페인 관리</h1><p>활성 사업자 상점의 소유자만 쿠폰을 만들고 발급 상태를 관리할 수 있습니다.</p></div><label className="coupon-store-selector">상점<select value={store.storeId} onChange={(event) => { setStoreId(Number(event.target.value)); setPage(0); setShowForm(false); }}>{stores.map((item) => <option key={item.storeId} value={item.storeId}>{item.publicName}</option>)}</select></label></section>
    <section className="coupon-panel"><div className="coupon-panel-heading"><div><p className="eyebrow">CAMPAIGNS</p><h2>{store.publicName} 쿠폰</h2></div><button className="text-button" type="button" onClick={() => setShowForm((value) => !value)}>{showForm ? '등록 닫기' : '쿠폰 등록'}</button></div>
      {showForm ? <CouponCampaignForm products={productsQuery.data?.content ?? []} pending={createMutation.isPending} error={createMutation.error} submitLabel="등록하기" onSubmit={(value) => createMutation.mutate(value)} /> : null}
      <label className="coupon-filter">상태<select value={status} onChange={(event) => { setStatus(event.target.value as CouponEffectiveStatus | ''); setPage(0); }}>{statuses.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
      {campaignsQuery.isLoading ? <p className="status-text">쿠폰 캠페인을 불러오고 있습니다.</p> : null}{campaignsQuery.error ? <ErrorState message={errorMessage(campaignsQuery.error, '쿠폰 캠페인을 불러오지 못했습니다.')} /> : null}
      {campaignsQuery.data?.content.length === 0 ? <EmptyState title="등록된 쿠폰이 없습니다" description="발급 기간과 할인 조건을 설정해 새 쿠폰을 등록해보세요." /> : null}
      <div className="coupon-list">{campaignsQuery.data?.content.map((campaign) => <article className="coupon-card" key={campaign.id}><div><StatusBadge status={campaign.effectiveStatus} /><h3>{campaign.title}</h3><p>{discountText(campaign)} · 최소 {money(campaign.minimumPurchaseAmount)}원</p><span>발급 {formatDate(campaign.issueStartsAt)} ~ {formatDate(campaign.issueEndsAt)}</span></div><div className="coupon-card-actions"><span>{campaign.scope === 'ALL_PRODUCTS' ? '전체 상품' : `${campaign.targetCount}개 상품`}</span><Link className="text-button" to={`/me/store/coupons/${store.storeId}/${campaign.id}`}>상세·수정</Link></div></article>)}</div>
      {campaignsQuery.data && campaignsQuery.data.totalPages > 1 ? <nav className="coupon-pagination"><button className="text-button" type="button" disabled={page === 0 || campaignsQuery.isFetching} onClick={() => setPage((value) => value - 1)}>이전</button><span>{page + 1} / {campaignsQuery.data.totalPages}</span><button className="text-button" type="button" disabled={page + 1 >= campaignsQuery.data.totalPages || campaignsQuery.isFetching} onClick={() => setPage((value) => value + 1)}>다음</button></nav> : null}
    </section></main>;
}

export function CouponCampaignAccessState() { return <EmptyState title="쿠폰 관리 권한이 없습니다" description="활성 사업자 상점의 소유자만 쿠폰을 관리할 수 있습니다. 개인 상점과 매니저 권한에서는 사용할 수 없습니다." />; }

type CouponCampaignFormProps = { products: { productId: number; title: string; price: number }[]; initialValue?: CouponCampaignInput; pending: boolean; disabled?: boolean; error: unknown; submitLabel: string; onSubmit: (input: CouponCampaignInput) => void };
export function CouponCampaignForm({ products, initialValue, pending, disabled = false, error, submitLabel, onSubmit }: CouponCampaignFormProps) {
  const [scope, setScope] = useState(initialValue?.scope ?? 'ALL_PRODUCTS'); const [discountType, setDiscountType] = useState(initialValue?.discountType ?? 'PERCENTAGE'); const [discountValue, setDiscountValue] = useState(initialValue?.discountValue ?? 10); const [maxDiscountAmount, setMaxDiscountAmount] = useState<number | undefined>(initialValue?.maxDiscountAmount ?? undefined); const [minimumPurchaseAmount, setMinimumPurchaseAmount] = useState(initialValue?.minimumPurchaseAmount ?? 0); const [stackable, setStackable] = useState(initialValue?.stackable ?? false); const [title, setTitle] = useState(initialValue?.title ?? ''); const [label, setLabel] = useState(initialValue?.label ?? ''); const [issueStartsAt, setIssueStartsAt] = useState(dateInput(initialValue?.issueStartsAt)); const [issueEndsAt, setIssueEndsAt] = useState(dateInput(initialValue?.issueEndsAt)); const [validityType, setValidityType] = useState<CouponValidityType>(initialValue?.validityType ?? 'COMMON_EXPIRY'); const [commonExpiresAt, setCommonExpiresAt] = useState(dateInput(initialValue?.commonExpiresAt)); const [validityDays, setValidityDays] = useState(initialValue?.validityDays ?? 7); const [productIds, setProductIds] = useState(initialValue?.productIds ?? []); const [message, setMessage] = useState<string | null>(null);
  const initialValueSignature = JSON.stringify(initialValue ?? null);
  useEffect(() => { setScope(initialValue?.scope ?? 'ALL_PRODUCTS'); setDiscountType(initialValue?.discountType ?? 'PERCENTAGE'); setDiscountValue(initialValue?.discountValue ?? 10); setMaxDiscountAmount(initialValue?.maxDiscountAmount ?? undefined); setMinimumPurchaseAmount(initialValue?.minimumPurchaseAmount ?? 0); setStackable(initialValue?.stackable ?? false); setTitle(initialValue?.title ?? ''); setLabel(initialValue?.label ?? ''); setIssueStartsAt(dateInput(initialValue?.issueStartsAt)); setIssueEndsAt(dateInput(initialValue?.issueEndsAt)); setValidityType(initialValue?.validityType ?? 'COMMON_EXPIRY'); setCommonExpiresAt(dateInput(initialValue?.commonExpiresAt)); setValidityDays(initialValue?.validityDays ?? 7); setProductIds(initialValue?.productIds ?? []); setMessage(null); }, [initialValueSignature]);
  const submit = (event: FormEvent) => { event.preventDefault(); if (scope === 'SELECTED_PRODUCTS' && productIds.length === 0) { setMessage('대상 상품을 하나 이상 선택해주세요.'); return; } if (!issueStartsAt || !issueEndsAt || (validityType === 'COMMON_EXPIRY' && !commonExpiresAt)) { setMessage('발급 기간과 쿠폰 유효기간을 모두 입력해주세요.'); return; } setMessage(null); onSubmit({ scope, discountType, discountValue, maxDiscountAmount: discountType === 'PERCENTAGE' ? maxDiscountAmount : undefined, minimumPurchaseAmount, stackable, title: title.trim(), label: label.trim() || undefined, issueStartsAt, issueEndsAt, validityType, commonExpiresAt: validityType === 'COMMON_EXPIRY' ? commonExpiresAt : undefined, validityDays: validityType === 'DAYS_FROM_ISSUANCE' ? validityDays : undefined, productIds: scope === 'SELECTED_PRODUCTS' ? productIds : [] }); };
  return <form className="coupon-form" onSubmit={submit}>
    <label>쿠폰 이름<input disabled={disabled} value={title} maxLength={100} required onChange={(event) => setTitle(event.target.value)} /></label>
    <label>구매자 표시 문구 (선택)<input disabled={disabled} value={label} maxLength={200} onChange={(event) => setLabel(event.target.value)} /></label>
    <label>대상 범위<select disabled={disabled} value={scope} onChange={(event) => setScope(event.target.value as CouponCampaignInput['scope'])}><option value="ALL_PRODUCTS">전체 상품</option><option value="SELECTED_PRODUCTS">선택 상품</option></select></label>
    <label>할인 방식<select disabled={disabled} value={discountType} onChange={(event) => setDiscountType(event.target.value as CouponCampaignInput['discountType'])}><option value="PERCENTAGE">정률 할인 (%)</option><option value="FIXED_AMOUNT">정액 할인 (원)</option></select></label>
    <label>할인 값<input disabled={disabled} type="number" min="1" required value={discountValue} onChange={(event) => setDiscountValue(Number(event.target.value))} /></label>
    {discountType === 'PERCENTAGE' ? <label>최대 할인 금액 (선택)<input disabled={disabled} type="number" min="0" value={maxDiscountAmount ?? ''} onChange={(event) => setMaxDiscountAmount(event.target.value === '' ? undefined : Number(event.target.value))} /></label> : null}
    <label>최소 구매 금액<input disabled={disabled} type="number" min="0" required value={minimumPurchaseAmount} onChange={(event) => setMinimumPurchaseAmount(Number(event.target.value))} /></label>
    <label className="coupon-checkbox"><input disabled={disabled} type="checkbox" checked={stackable} onChange={(event) => setStackable(event.target.checked)} />다른 혜택과 중복 적용</label>
    <label>발급 시작 시각 (KST)<input disabled={disabled} type="datetime-local" required value={issueStartsAt} onChange={(event) => setIssueStartsAt(event.target.value)} /></label>
    <label>발급 종료 시각 (KST)<input disabled={disabled} type="datetime-local" required value={issueEndsAt} onChange={(event) => setIssueEndsAt(event.target.value)} /></label>
    <label>유효기간 방식<select disabled={disabled} value={validityType} onChange={(event) => setValidityType(event.target.value as CouponValidityType)}><option value="COMMON_EXPIRY">공통 만료 시각</option><option value="DAYS_FROM_ISSUANCE">발급 후 일수</option></select></label>
    {validityType === 'COMMON_EXPIRY' ? <label>공통 만료 시각 (KST)<input disabled={disabled} type="datetime-local" required value={commonExpiresAt} onChange={(event) => setCommonExpiresAt(event.target.value)} /></label> : <label>발급 후 유효일<input disabled={disabled} type="number" min="1" required value={validityDays} onChange={(event) => setValidityDays(Number(event.target.value))} /></label>}
    {scope === 'SELECTED_PRODUCTS' ? <fieldset className="coupon-targets"><legend>대상 상품</legend>{products.map((product) => <label key={product.productId}><input disabled={disabled} type="checkbox" checked={productIds.includes(product.productId)} onChange={() => setProductIds((current) => current.includes(product.productId) ? current.filter((id) => id !== product.productId) : [...current, product.productId])} />{product.title} · {money(product.price)}원</label>)}</fieldset> : null}
    {message ? <p className="error-text" role="alert">{message}</p> : null}{error ? <p className="error-text" role="alert">{errorMessage(error, '쿠폰 캠페인을 저장하지 못했습니다.')}</p> : null}
    <button className="text-button" type="submit" disabled={pending || disabled}>{pending ? '저장 중' : disabled ? '수정 불가' : submitLabel}</button>
  </form>;
}

export async function invalidateCoupons(queryClient: ReturnType<typeof useQueryClient>, storeId?: number) { await Promise.allSettled([queryClient.invalidateQueries({ queryKey: couponQueryKeys.all }), storeId ? queryClient.invalidateQueries({ queryKey: couponQueryKeys.store(storeId) }) : Promise.resolve()]); }
export function dateInput(value: string | null | undefined) { return value ? value.slice(0, 16) : ''; }
export function formatDate(value: string) { return value ? `${value.slice(0, 16).replace('T', ' ') } KST` : '-'; }
export function money(value: number) { return value.toLocaleString('ko-KR'); }
export function discountText(campaign: { discountType: 'FIXED_AMOUNT' | 'PERCENTAGE'; discountValue: number; maxDiscountAmount: number | null }) { return campaign.discountType === 'PERCENTAGE' ? `${campaign.discountValue}% 할인${campaign.maxDiscountAmount ? ` (최대 ${money(campaign.maxDiscountAmount)}원)` : ''}` : `${money(campaign.discountValue)}원 할인`; }
export function errorMessage(error: unknown, fallback: string) { const apiError = error as Partial<ApiError>; return apiError.fieldErrors?.[0]?.message ?? apiError.message ?? fallback; }
