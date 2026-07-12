import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { type FormEvent, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { toProductImageSrc, type ProductStatus } from '../products/productApi';
import { storeQueryKeys } from './storeApi';
import {
  adjustProductInventory,
  getProductInventoryHistory,
  getStoreCatalogProducts,
  hideStoreProducts,
  showStoreProducts,
  storeOperationQueryKeys,
  type InventoryAdjustmentInput,
  type InventoryAdjustmentReason,
  type StoreCatalogProduct,
  type StoreCatalogSort,
} from './storeOperationsApi';
import { type ApiError } from '../../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../../shared/ui/ResourceStates';

type StoreCatalogPanelProps = {
  storeId: number;
  catalogWritable: boolean;
};

type CatalogAction = 'hide' | 'show';

const PAGE_SIZE = 20;
const HISTORY_PAGE_SIZE = 10;
const currencyFormatter = new Intl.NumberFormat('ko-KR');
const statusOptions: { value: ProductStatus | ''; label: string }[] = [
  { value: '', label: '전체 상태' },
  { value: 'ON_SALE', label: '판매중' },
  { value: 'RESERVED', label: '예약중' },
  { value: 'SOLD_OUT', label: '판매완료' },
  { value: 'HIDDEN', label: '숨김' },
];
const adjustmentReasons: { value: InventoryAdjustmentReason; label: string }[] = [
  { value: 'RESTOCK', label: '입고' },
  { value: 'STOCKTAKE', label: '재고 실사' },
  { value: 'DAMAGE_OR_DISPOSAL', label: '파손·폐기' },
  { value: 'RETURN_RESTOCK', label: '반품 재입고' },
  { value: 'OTHER', label: '기타' },
];

export function StoreCatalogPanel({ storeId, catalogWritable }: StoreCatalogPanelProps) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<ProductStatus | ''>('');
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [sort, setSort] = useState<StoreCatalogSort>('NEWEST');
  const [page, setPage] = useState(0);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [adjustmentProduct, setAdjustmentProduct] = useState<StoreCatalogProduct | null>(null);
  const [historyProduct, setHistoryProduct] = useState<StoreCatalogProduct | null>(null);
  const [historyPage, setHistoryPage] = useState(0);
  const searchInput = { status: status || undefined, keyword, sort, page, size: PAGE_SIZE };
  const catalogQuery = useQuery({
    queryKey: storeOperationQueryKeys.productList(storeId, searchInput),
    queryFn: () => getStoreCatalogProducts(storeId, searchInput),
  });
  const mutation = useMutation({
    mutationFn: ({ action, productIds }: { action: CatalogAction; productIds: number[] }) =>
      action === 'hide' ? hideStoreProducts(storeId, productIds) : showStoreProducts(storeId, productIds),
  });
  const adjustmentMutation = useMutation({
    mutationFn: ({ productId, input }: { productId: number; input: InventoryAdjustmentInput }) =>
      adjustProductInventory(storeId, productId, input),
  });
  const historyQuery = useQuery({
    queryKey: storeOperationQueryKeys.inventoryHistory(
      storeId,
      historyProduct?.productId ?? 0,
      historyPage,
      HISTORY_PAGE_SIZE,
    ),
    queryFn: () => getProductInventoryHistory(storeId, historyProduct?.productId ?? 0, historyPage, HISTORY_PAGE_SIZE),
    enabled: historyProduct !== null,
  });

  useEffect(() => {
    setSelectedIds(new Set());
    setMutationError(null);
    setSuccessMessage(null);
    setAdjustmentProduct(null);
    setHistoryProduct(null);
    setHistoryPage(0);
  }, [keyword, page, sort, status, storeId]);

  const products = useMemo(() => catalogQuery.data?.content ?? [], [catalogQuery.data]);
  const selectedProducts = products.filter((product) => selectedIds.has(product.productId));
  const batchAction = selectedProducts.length > 0 && selectedProducts.every((product) => product.status === 'ON_SALE')
    ? 'hide'
    : selectedProducts.length > 0 && selectedProducts.every((product) => product.status === 'HIDDEN')
      ? 'show'
      : null;
  const allRowsSelected = products.length > 0 && products.every((product) => selectedIds.has(product.productId));
  const commandsDisabled = !catalogWritable || mutation.isPending || adjustmentMutation.isPending;

  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    setKeyword(keywordInput.trim());
    setPage(0);
  };

  const runAction = async (action: CatalogAction, productIds: number[]) => {
    if (commandsDisabled || productIds.length === 0) return;
    const actionLabel = action === 'hide' ? '숨김' : '공개';
    if (!window.confirm(`선택한 상품 ${productIds.length}개를 ${actionLabel} 처리하시겠습니까?`)) return;

    setMutationError(null);
    setSuccessMessage(null);
    try {
      await mutation.mutateAsync({ action, productIds });
      await reconcileCatalogQueries(queryClient, storeId);
      setSelectedIds(new Set());
      setSuccessMessage(`${productIds.length}개 상품을 ${actionLabel} 처리했습니다.`);
    } catch (error) {
      setMutationError(toErrorMessage(error, `상품을 ${actionLabel} 처리하지 못했습니다.`));
      await reconcileCatalogQueries(queryClient, storeId, true);
      setSelectedIds(new Set());
    }
  };

  const submitAdjustment = async (input: InventoryAdjustmentInput) => {
    if (!adjustmentProduct || commandsDisabled) return;

    setMutationError(null);
    setSuccessMessage(null);
    try {
      await adjustmentMutation.mutateAsync({ productId: adjustmentProduct.productId, input });
      await Promise.allSettled([
        reconcileCatalogQueries(queryClient, storeId),
        queryClient.invalidateQueries({ queryKey: storeOperationQueryKeys.inventory(storeId, adjustmentProduct.productId) }),
      ]);
      setHistoryProduct(adjustmentProduct);
      setHistoryPage(0);
      setSuccessMessage(`${adjustmentProduct.title}의 총 재고를 ${input.totalQuantity}개로 조정했습니다.`);
      setAdjustmentProduct(null);
    } catch (error) {
      setMutationError(toErrorMessage(error, '재고를 조정하지 못했습니다.'));
    }
  };

  return (
    <section className="store-operations-panel" aria-labelledby="store-catalog-title">
      <div className="store-operations-panel-heading">
        <div><p className="eyebrow">CATALOG</p><h2 id="store-catalog-title">상품 카탈로그</h2></div>
        {catalogWritable ? <Link className="primary-link" to={`/products/new?storeId=${storeId}`}>상품 등록</Link> : <button type="button" className="text-button" disabled>상품 등록 불가</button>}
      </div>

      {!catalogWritable ? <div className="resource-state"><strong>읽기 전용 카탈로그</strong><p>상점이 활성 상태가 아니어서 상품 명령을 사용할 수 없습니다.</p></div> : null}

      <form className="store-catalog-toolbar" onSubmit={submitSearch}>
        <label><span>상태</span><select value={status} onChange={(event) => { setStatus(event.target.value as ProductStatus | ''); setPage(0); }}>{statusOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
        <label className="store-catalog-keyword"><span>상품 검색</span><input value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} placeholder="상품명" /></label>
        <button className="text-button" type="submit">검색</button>
        <label><span>정렬</span><select value={sort} onChange={(event) => { setSort(event.target.value as StoreCatalogSort); setPage(0); }}><option value="NEWEST">최신순</option><option value="OLDEST">오래된순</option></select></label>
      </form>

      {selectedProducts.length > 0 ? (
        <div className="store-catalog-batch" aria-live="polite">
          <span>{selectedProducts.length}개 선택</span>
          {batchAction ? <button className="text-button" type="button" disabled={commandsDisabled} onClick={() => runAction(batchAction, selectedProducts.map((product) => product.productId))}>{batchAction === 'hide' ? '선택 상품 숨기기' : '선택 상품 공개하기'}</button> : <span className="status-text">같은 상태의 판매중 또는 숨김 상품만 일괄 처리할 수 있습니다.</span>}
        </div>
      ) : null}
      {mutationError ? <p className="error-text" role="alert">{mutationError}</p> : null}
      {successMessage ? <p className="status-text" role="status">{successMessage}</p> : null}

      {catalogQuery.isLoading ? <p className="status-text">상품을 불러오고 있습니다.</p> : null}
      {catalogQuery.error ? <ErrorState message={toErrorMessage(catalogQuery.error, '상품 목록을 불러오지 못했습니다.')} /> : null}
      {catalogQuery.data?.content.length === 0 ? <EmptyState title="조건에 맞는 상품이 없습니다" description="검색 조건을 바꿔보세요." /> : null}
      {products.length > 0 ? (
        <div className="store-catalog-table">
          <div className="store-catalog-table-head"><span><input type="checkbox" aria-label="현재 페이지 전체 선택" checked={allRowsSelected} disabled={commandsDisabled} onChange={() => toggleAll(products.map((product) => product.productId), allRowsSelected, setSelectedIds)} /></span><span>상품</span><span>상태</span><span>재고</span><span>가격</span><span>작업</span></div>
          {products.map((product) => (
            <div className={`store-catalog-row${selectedIds.has(product.productId) ? ' is-selected' : ''}`} key={product.productId}>
              <label className="store-catalog-select"><input type="checkbox" aria-label={`${product.title} 선택`} checked={selectedIds.has(product.productId)} disabled={commandsDisabled} onChange={() => toggleOne(product.productId, setSelectedIds)} /></label>
              <div className="store-catalog-product">{product.thumbnailUrl ? <img src={toProductImageSrc(product.thumbnailUrl) ?? product.thumbnailUrl} alt="" /> : <div className="store-catalog-thumbnail-placeholder">이미지 없음</div>}<strong>{product.title}</strong></div>
              <div data-label="상태"><StatusBadge status={product.status} /></div>
              <div className="store-catalog-inventory" data-label="재고">{renderInventory(product)}</div>
              <strong data-label="가격">{currencyFormatter.format(product.price)}원</strong>
              <div className="store-catalog-actions" data-label="작업">
                {renderRowActions(product, commandsDisabled, runAction)}
                {product.salesPolicy === 'STOCK_MANAGED' ? (
                  <>
                    <button className="text-button" type="button" disabled={commandsDisabled} onClick={() => setAdjustmentProduct(product)}>재고 조정</button>
                    <button className="text-button secondary-button" type="button" onClick={() => { setHistoryProduct(product); setHistoryPage(0); }}>변경 이력</button>
                  </>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      ) : null}
      {historyProduct ? (
        <InventoryHistoryPanel
          product={historyProduct}
          page={historyPage}
          query={historyQuery}
          onClose={() => setHistoryProduct(null)}
          onMovePage={setHistoryPage}
        />
      ) : null}
      {catalogQuery.data && catalogQuery.data.totalPages > 0 ? (
        <nav className="store-catalog-pagination" aria-label="카탈로그 페이지 이동"><button className="text-button" type="button" disabled={page === 0 || catalogQuery.isFetching} onClick={() => setPage(page - 1)}>이전</button><span>{page + 1} / {Math.max(catalogQuery.data.totalPages, 1)}</span><button className="text-button" type="button" disabled={page + 1 >= catalogQuery.data.totalPages || catalogQuery.isFetching} onClick={() => setPage(page + 1)}>다음</button></nav>
      ) : null}
      {adjustmentProduct ? (
        <InventoryAdjustmentModal
          product={adjustmentProduct}
          error={mutationError}
          pending={adjustmentMutation.isPending}
          onClose={() => { if (!adjustmentMutation.isPending) { setAdjustmentProduct(null); setMutationError(null); } }}
          onSubmit={submitAdjustment}
        />
      ) : null}
    </section>
  );
}

async function reconcileCatalogQueries(queryClient: QueryClient, storeId: number, includeStoreList = false) {
  const invalidations = [
    queryClient.invalidateQueries({ queryKey: storeOperationQueryKeys.summary(storeId) }),
    queryClient.invalidateQueries({ queryKey: storeOperationQueryKeys.products(storeId) }),
    queryClient.invalidateQueries({ queryKey: storeQueryKeys.publicProducts(storeId) }),
    queryClient.invalidateQueries({ queryKey: ['products'] }),
    queryClient.invalidateQueries({ queryKey: ['my-cart'] }),
  ];

  if (includeStoreList) {
    invalidations.push(queryClient.invalidateQueries({ queryKey: storeOperationQueryKeys.stores() }));
  }

  await Promise.allSettled(invalidations);
}

function renderRowActions(product: StoreCatalogProduct, disabled: boolean, runAction: (action: CatalogAction, productIds: number[]) => void) {
  const { productId, status } = product;
  if (status === 'ON_SALE') return <>{disabled ? <button type="button" className="text-button" disabled>수정</button> : <Link className="text-button" to={`/products/${productId}/edit`}>수정</Link>}<button className="text-button danger-button" type="button" disabled={disabled} onClick={() => runAction('hide', [productId])}>숨기기</button></>;
  if (status === 'HIDDEN') return <button className="text-button" type="button" disabled={disabled} onClick={() => runAction('show', [productId])}>공개하기</button>;
  if (status === 'SOLD_OUT') return disabled ? <button type="button" className="text-button" disabled>수정</button> : <Link className="text-button" to={`/products/${productId}/edit`}>수정</Link>;
  return <span className="status-text">작업 없음</span>;
}

function renderInventory(product: StoreCatalogProduct) {
  if (product.salesPolicy === 'SINGLE_ITEM') {
    return <span className="status-text">단일 상품</span>;
  }

  return (
    <span>
      총 {product.totalQuantity ?? 0} · 예약 {product.reservedQuantity ?? 0} · 가용 {product.availableQuantity ?? 0}
      <small>부족 기준 {product.lowStockThreshold ?? '-'}개</small>
    </span>
  );
}

type InventoryAdjustmentModalProps = {
  product: StoreCatalogProduct;
  error: string | null;
  pending: boolean;
  onClose: () => void;
  onSubmit: (input: InventoryAdjustmentInput) => Promise<void>;
};

function InventoryAdjustmentModal({ product, error, pending, onClose, onSubmit }: InventoryAdjustmentModalProps) {
  const [totalQuantity, setTotalQuantity] = useState(product.totalQuantity ?? 0);
  const [reason, setReason] = useState<InventoryAdjustmentReason>('RESTOCK');
  const [referenceNote, setReferenceNote] = useState('');

  const submit = (event: FormEvent) => {
    event.preventDefault();
    void onSubmit({
      totalQuantity,
      reason,
      referenceNote: referenceNote.trim() || undefined,
    });
  };

  return (
    <div className="inventory-modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className="inventory-modal" role="dialog" aria-modal="true" aria-labelledby="inventory-adjustment-title">
        <div className="inventory-modal-heading">
          <div><p className="eyebrow">INVENTORY</p><h3 id="inventory-adjustment-title">{product.title} 재고 조정</h3></div>
          <button type="button" className="text-button secondary-button" disabled={pending} onClick={onClose}>닫기</button>
        </div>
        <p className="status-text">현재 총 {product.totalQuantity ?? 0}개 · 예약 {product.reservedQuantity ?? 0}개</p>
        <form className="inventory-adjustment-form" onSubmit={submit}>
          <label>조정 후 총 재고<input type="number" min={product.reservedQuantity ?? 0} step="1" required value={totalQuantity} onChange={(event) => setTotalQuantity(Number(event.target.value))} /></label>
          <label>조정 사유<select value={reason} onChange={(event) => setReason(event.target.value as InventoryAdjustmentReason)}>{adjustmentReasons.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
          <label>참고 메모 (선택)<textarea rows={3} maxLength={500} value={referenceNote} onChange={(event) => setReferenceNote(event.target.value)} /></label>
          {error ? <p className="error-text" role="alert">{error}</p> : null}
          <button type="submit" className="text-button" disabled={pending}>{pending ? '조정 중' : '재고 조정하기'}</button>
        </form>
      </section>
    </div>
  );
}

type InventoryHistoryPanelProps = {
  product: StoreCatalogProduct;
  page: number;
  query: ReturnType<typeof useQuery<Awaited<ReturnType<typeof getProductInventoryHistory>>, Error>>;
  onClose: () => void;
  onMovePage: (page: number) => void;
};

function InventoryHistoryPanel({ product, page, query, onClose, onMovePage }: InventoryHistoryPanelProps) {
  return (
    <section className="inventory-history" aria-labelledby="inventory-history-title">
      <div className="inventory-history-heading">
        <div><p className="eyebrow">HISTORY</p><h3 id="inventory-history-title">{product.title} 재고 변경 이력</h3></div>
        <button type="button" className="text-button secondary-button" onClick={onClose}>닫기</button>
      </div>
      {query.isLoading ? <p className="status-text">재고 변경 이력을 불러오고 있습니다.</p> : null}
      {query.error ? <ErrorState message={toErrorMessage(query.error, '재고 변경 이력을 불러오지 못했습니다.')} /> : null}
      {query.data?.content.length === 0 ? <EmptyState title="재고 변경 이력이 없습니다" /> : null}
      {query.data?.content.length ? (
        <div className="inventory-history-list">
          {query.data.content.map((adjustment) => (
            <article key={adjustment.adjustmentId}>
              <div><strong>{toAdjustmentReasonLabel(adjustment.reason)}</strong><time dateTime={adjustment.occurredAt}>{new Date(adjustment.occurredAt).toLocaleString('ko-KR')}</time></div>
              <p>총 재고 {adjustment.beforeTotalQuantity} → {adjustment.afterTotalQuantity} · 예약 {adjustment.beforeReservedQuantity} → {adjustment.afterReservedQuantity}</p>
              <span className="status-text">{adjustment.actorNickname ?? '시스템'}{adjustment.orderId ? ` · 주문 #${adjustment.orderId}` : ''}{adjustment.referenceNote ? ` · ${adjustment.referenceNote}` : ''}</span>
            </article>
          ))}
        </div>
      ) : null}
      {query.data && query.data.totalPages > 0 ? (
        <nav className="store-catalog-pagination" aria-label="재고 변경 이력 페이지 이동">
          <button className="text-button" type="button" disabled={page === 0 || query.isFetching} onClick={() => onMovePage(page - 1)}>이전</button>
          <span>{page + 1} / {Math.max(query.data.totalPages, 1)}</span>
          <button className="text-button" type="button" disabled={page + 1 >= query.data.totalPages || query.isFetching} onClick={() => onMovePage(page + 1)}>다음</button>
        </nav>
      ) : null}
    </section>
  );
}

function toAdjustmentReasonLabel(reason: InventoryAdjustmentReason | null) {
  if (reason === null) return '시스템 변경';
  return adjustmentReasons.find((option) => option.value === reason)?.label ?? reason;
}

function toggleOne(productId: number, setSelectedIds: React.Dispatch<React.SetStateAction<Set<number>>>) {
  setSelectedIds((current) => { const next = new Set(current); if (next.has(productId)) next.delete(productId); else next.add(productId); return next; });
}

function toggleAll(productIds: number[], allSelected: boolean, setSelectedIds: React.Dispatch<React.SetStateAction<Set<number>>>) {
  setSelectedIds(allSelected ? new Set() : new Set(productIds));
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  return apiError.fieldErrors?.[0]?.message ?? apiError.message ?? fallbackMessage;
}
