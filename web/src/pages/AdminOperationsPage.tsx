import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import {
  getAdminMemberDetail,
  getAdminMembers,
  getAdminOrderDetail,
  getAdminOrders,
  getAdminProductDetail,
  getAdminProducts,
  hideAdminProduct,
  type AdminMemberDetail,
  type AdminMemberSearchInput,
  type AdminMemberSummary,
  type AdminOrderDetail,
  type AdminOrderSearchInput,
  type AdminOrderSummary,
  type AdminProductDetail,
  type AdminProductSearchInput,
  type AdminProductSummary,
  type MemberRole,
  type OrderStatus,
  type PageResponse,
  type ProductStatus,
} from '../features/admin/adminOperationsApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const ADMIN_OPERATIONS_PAGE_SIZE = 10;

const currencyFormatter = new Intl.NumberFormat('ko-KR');
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

type ProductSearchFormValues = {
  sellerId: string;
  status: ProductStatus | '';
  keyword: string;
};

type OrderSearchFormValues = {
  buyerId: string;
  sellerId: string;
  productId: string;
  status: OrderStatus | '';
};

type MemberSearchFormValues = {
  email: string;
  nickname: string;
  role: MemberRole | '';
};

export function AdminOperationsPage() {
  const queryClient = useQueryClient();
  const [productInput, setProductInput] = useState<AdminProductSearchInput>({
    page: 0,
    size: ADMIN_OPERATIONS_PAGE_SIZE,
  });
  const [orderInput, setOrderInput] = useState<AdminOrderSearchInput>({
    page: 0,
    size: ADMIN_OPERATIONS_PAGE_SIZE,
  });
  const [memberInput, setMemberInput] = useState<AdminMemberSearchInput>({
    page: 0,
    size: ADMIN_OPERATIONS_PAGE_SIZE,
  });
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null);
  const [selectedMemberId, setSelectedMemberId] = useState<number | null>(null);
  const [hideError, setHideError] = useState<string | null>(null);

  const productForm = useForm<ProductSearchFormValues>({
    defaultValues: { sellerId: '', status: '', keyword: '' },
  });
  const orderForm = useForm<OrderSearchFormValues>({
    defaultValues: { buyerId: '', sellerId: '', productId: '', status: '' },
  });
  const memberForm = useForm<MemberSearchFormValues>({
    defaultValues: { email: '', nickname: '', role: '' },
  });

  const productListQuery = useQuery({
    queryKey: ['admin-operations', 'products', 'list', productInput],
    queryFn: () => getAdminProducts(productInput),
  });
  const productDetailQuery = useQuery({
    queryKey: ['admin-operations', 'products', 'detail', selectedProductId],
    queryFn: () => getAdminProductDetail(selectedProductId ?? 0),
    enabled: selectedProductId !== null,
  });
  const orderListQuery = useQuery({
    queryKey: ['admin-operations', 'orders', 'list', orderInput],
    queryFn: () => getAdminOrders(orderInput),
  });
  const orderDetailQuery = useQuery({
    queryKey: ['admin-operations', 'orders', 'detail', selectedOrderId],
    queryFn: () => getAdminOrderDetail(selectedOrderId ?? 0),
    enabled: selectedOrderId !== null,
  });
  const memberListQuery = useQuery({
    queryKey: ['admin-operations', 'members', 'list', memberInput],
    queryFn: () => getAdminMembers(memberInput),
  });
  const memberDetailQuery = useQuery({
    queryKey: ['admin-operations', 'members', 'detail', selectedMemberId],
    queryFn: () => getAdminMemberDetail(selectedMemberId ?? 0),
    enabled: selectedMemberId !== null,
  });
  const hideMutation = useMutation({
    mutationFn: hideAdminProduct,
    onSuccess: async () => {
      setHideError(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin-operations', 'products'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
      ]);
    },
  });

  const onProductSearch = productForm.handleSubmit((values) => {
    const nextInput: AdminProductSearchInput = {
      page: 0,
      size: ADMIN_OPERATIONS_PAGE_SIZE,
    };
    const sellerId = toOptionalNumber(values.sellerId);
    const keyword = values.keyword.trim();

    if (sellerId !== undefined) {
      nextInput.sellerId = sellerId;
    }

    if (values.status !== '') {
      nextInput.status = values.status;
    }

    if (keyword !== '') {
      nextInput.keyword = keyword;
    }

    setHideError(null);
    setSelectedProductId(null);
    setProductInput(nextInput);
  });

  const onOrderSearch = orderForm.handleSubmit((values) => {
    const nextInput: AdminOrderSearchInput = {
      page: 0,
      size: ADMIN_OPERATIONS_PAGE_SIZE,
    };
    const buyerId = toOptionalNumber(values.buyerId);
    const sellerId = toOptionalNumber(values.sellerId);
    const productId = toOptionalNumber(values.productId);

    if (buyerId !== undefined) {
      nextInput.buyerId = buyerId;
    }

    if (sellerId !== undefined) {
      nextInput.sellerId = sellerId;
    }

    if (productId !== undefined) {
      nextInput.productId = productId;
    }

    if (values.status !== '') {
      nextInput.status = values.status;
    }

    setSelectedOrderId(null);
    setOrderInput(nextInput);
  });

  const onMemberSearch = memberForm.handleSubmit((values) => {
    const nextInput: AdminMemberSearchInput = {
      page: 0,
      size: ADMIN_OPERATIONS_PAGE_SIZE,
    };
    const email = values.email.trim();
    const nickname = values.nickname.trim();

    if (email !== '') {
      nextInput.email = email;
    }

    if (nickname !== '') {
      nextInput.nickname = nickname;
    }

    if (values.role !== '') {
      nextInput.role = values.role;
    }

    setSelectedMemberId(null);
    setMemberInput(nextInput);
  });

  const hideSelectedProduct = async (productId: number) => {
    setHideError(null);

    try {
      await hideMutation.mutateAsync(productId);
    } catch (caughtError) {
      setHideError(toErrorMessage(caughtError, '상품 숨김 요청을 처리하지 못했습니다.'));
    }
  };

  return (
    <section className="admin-operations-page">
      <div className="list-page-header">
        <div className="admin-operations-header">
          <div>
            <h1>운영 콘솔</h1>
            <p>상품, 주문, 회원을 조회하고 운영에 필요한 상세 정보를 확인합니다.</p>
          </div>
          <Link className="text-button" to="/admin/batches/settlements">
            정산 배치로 이동
          </Link>
        </div>
      </div>

      <section className="admin-tool-panel" aria-labelledby="admin-products-title">
        <div className="admin-panel-heading-row">
          <div>
            <h2 id="admin-products-title">상품 운영</h2>
            <p className="status-text">판매자, 상태, 키워드로 상품을 조회하고 노출 상태를 관리합니다.</p>
          </div>
          <span className="admin-execution-meta">페이지당 {ADMIN_OPERATIONS_PAGE_SIZE}건</span>
        </div>
        <form className="admin-search-form" onSubmit={onProductSearch}>
          <label>
            판매자 번호
            <input
              type="text"
              inputMode="numeric"
              placeholder="sellerId"
              {...productForm.register('sellerId', {
                validate: (value) => value.trim() === '' || toOptionalNumber(value) !== undefined || '숫자로 입력해주세요.',
              })}
            />
            {productForm.formState.errors.sellerId ? (
              <span className="error-text">{productForm.formState.errors.sellerId.message}</span>
            ) : null}
          </label>
          <label>
            상태
            <select {...productForm.register('status')}>
              <option value="">전체</option>
              <option value="ON_SALE">판매중</option>
              <option value="RESERVED">예약중</option>
              <option value="SOLD_OUT">판매완료</option>
              <option value="HIDDEN">숨김</option>
            </select>
          </label>
          <label>
            키워드
            <input type="text" placeholder="상품명" {...productForm.register('keyword')} />
          </label>
          <button type="submit" className="text-button">
            검색
          </button>
        </form>

        <div className="admin-operations-section-grid">
          <AdminProductList
            data={productListQuery.data}
            error={productListQuery.error}
            isFetching={productListQuery.isFetching}
            isLoading={productListQuery.isLoading}
            selectedProductId={selectedProductId}
            onMovePage={(page) => setProductInput((current) => ({ ...current, page }))}
            onSelectProduct={(productId) => {
              setHideError(null);
              setSelectedProductId(productId);
            }}
          />
          <AdminProductDetailPanel
            detail={productDetailQuery.data}
            error={productDetailQuery.error}
            hideError={hideError}
            isHiding={hideMutation.isPending}
            isLoading={productDetailQuery.isLoading}
            selectedProductId={selectedProductId}
            onHide={hideSelectedProduct}
          />
        </div>
      </section>

      <section className="admin-tool-panel" aria-labelledby="admin-orders-title">
        <div className="admin-panel-heading-row">
          <div>
            <h2 id="admin-orders-title">주문 조회</h2>
            <p className="status-text">구매자, 판매자, 상품, 주문 상태 기준으로 주문 흐름을 확인합니다.</p>
          </div>
          <span className="admin-execution-meta">페이지당 {ADMIN_OPERATIONS_PAGE_SIZE}건</span>
        </div>
        <form className="admin-search-form" onSubmit={onOrderSearch}>
          <label>
            구매자 번호
            <input
              type="text"
              inputMode="numeric"
              placeholder="buyerId"
              {...orderForm.register('buyerId', {
                validate: (value) => value.trim() === '' || toOptionalNumber(value) !== undefined || '숫자로 입력해주세요.',
              })}
            />
            {orderForm.formState.errors.buyerId ? (
              <span className="error-text">{orderForm.formState.errors.buyerId.message}</span>
            ) : null}
          </label>
          <label>
            판매자 번호
            <input
              type="text"
              inputMode="numeric"
              placeholder="sellerId"
              {...orderForm.register('sellerId', {
                validate: (value) => value.trim() === '' || toOptionalNumber(value) !== undefined || '숫자로 입력해주세요.',
              })}
            />
            {orderForm.formState.errors.sellerId ? (
              <span className="error-text">{orderForm.formState.errors.sellerId.message}</span>
            ) : null}
          </label>
          <label>
            상품 번호
            <input
              type="text"
              inputMode="numeric"
              placeholder="productId"
              {...orderForm.register('productId', {
                validate: (value) => value.trim() === '' || toOptionalNumber(value) !== undefined || '숫자로 입력해주세요.',
              })}
            />
            {orderForm.formState.errors.productId ? (
              <span className="error-text">{orderForm.formState.errors.productId.message}</span>
            ) : null}
          </label>
          <label>
            주문 상태
            <select {...orderForm.register('status')}>
              <option value="">전체</option>
              <option value="CREATED">주문 생성</option>
              <option value="PAID">결제 완료</option>
              <option value="SHIPPING">배송 중</option>
              <option value="DELIVERED">배송 완료</option>
              <option value="CONFIRMED">구매 확정</option>
              <option value="CANCELED">취소</option>
            </select>
          </label>
          <button type="submit" className="text-button">
            검색
          </button>
        </form>

        <div className="admin-operations-section-grid">
          <AdminOrderList
            data={orderListQuery.data}
            error={orderListQuery.error}
            isFetching={orderListQuery.isFetching}
            isLoading={orderListQuery.isLoading}
            selectedOrderId={selectedOrderId}
            onMovePage={(page) => setOrderInput((current) => ({ ...current, page }))}
            onSelectOrder={setSelectedOrderId}
          />
          <AdminOrderDetailPanel
            detail={orderDetailQuery.data}
            error={orderDetailQuery.error}
            isLoading={orderDetailQuery.isLoading}
            selectedOrderId={selectedOrderId}
          />
        </div>
      </section>

      <section className="admin-tool-panel" aria-labelledby="admin-members-title">
        <div className="admin-panel-heading-row">
          <div>
            <h2 id="admin-members-title">회원 조회</h2>
            <p className="status-text">이메일, 닉네임, 권한으로 회원을 찾고 활동 집계를 확인합니다.</p>
          </div>
          <span className="admin-execution-meta">페이지당 {ADMIN_OPERATIONS_PAGE_SIZE}건</span>
        </div>
        <form className="admin-search-form" onSubmit={onMemberSearch}>
          <label>
            이메일
            <input type="text" placeholder="email" {...memberForm.register('email')} />
          </label>
          <label>
            닉네임
            <input type="text" placeholder="nickname" {...memberForm.register('nickname')} />
          </label>
          <label>
            권한
            <select {...memberForm.register('role')}>
              <option value="">전체</option>
              <option value="MEMBER">회원</option>
              <option value="ADMIN">관리자</option>
            </select>
          </label>
          <button type="submit" className="text-button">
            검색
          </button>
        </form>

        <div className="admin-operations-section-grid">
          <AdminMemberList
            data={memberListQuery.data}
            error={memberListQuery.error}
            isFetching={memberListQuery.isFetching}
            isLoading={memberListQuery.isLoading}
            selectedMemberId={selectedMemberId}
            onMovePage={(page) => setMemberInput((current) => ({ ...current, page }))}
            onSelectMember={setSelectedMemberId}
          />
          <AdminMemberDetailPanel
            detail={memberDetailQuery.data}
            error={memberDetailQuery.error}
            isLoading={memberDetailQuery.isLoading}
            selectedMemberId={selectedMemberId}
          />
        </div>
      </section>
    </section>
  );
}

type AdminProductListProps = {
  data: PageResponse<AdminProductSummary> | undefined;
  error: unknown;
  isFetching: boolean;
  isLoading: boolean;
  selectedProductId: number | null;
  onMovePage: (page: number) => void;
  onSelectProduct: (productId: number) => void;
};

function AdminProductList({
  data,
  error,
  isFetching,
  isLoading,
  selectedProductId,
  onMovePage,
  onSelectProduct,
}: AdminProductListProps) {
  const products = data?.content ?? [];

  return (
    <div className="admin-operations-list">
      {isLoading ? <p className="status-text">상품 목록을 불러오고 있습니다.</p> : null}
      {error ? <ErrorState message="상품 목록을 불러오지 못했습니다." /> : null}
      {!isLoading && !error && products.length === 0 ? (
        <EmptyState title="상품이 없습니다" description="검색 조건에 맞는 상품이 없습니다." />
      ) : null}
      {products.length > 0 ? (
        <>
          <div className="admin-operations-table" aria-label="관리자 상품 검색 결과">
            <div className="admin-operations-table-head admin-product-grid" role="row">
              <span>상품</span>
              <span>판매자</span>
              <span>가격</span>
              <span>상태</span>
            </div>
            {products.map((product) => (
              <button
                type="button"
                className={`admin-operations-row admin-product-grid ${
                  selectedProductId === product.productId ? 'admin-operations-row-selected' : ''
                }`}
                key={product.productId}
                onClick={() => onSelectProduct(product.productId)}
              >
                <span>
                  #{product.productId} {product.title}
                </span>
                <span>
                  #{product.sellerId} {product.sellerNickname}
                </span>
                <span>{currencyFormatter.format(product.price)}원</span>
                <span>
                  <StatusBadge status={product.status} />
                </span>
              </button>
            ))}
          </div>
          {data ? <AdminPagination data={data} isFetching={isFetching} onMovePage={onMovePage} /> : null}
        </>
      ) : null}
    </div>
  );
}

type AdminProductDetailPanelProps = {
  detail: AdminProductDetail | undefined;
  error: unknown;
  hideError: string | null;
  isHiding: boolean;
  isLoading: boolean;
  selectedProductId: number | null;
  onHide: (productId: number) => void;
};

function AdminProductDetailPanel({
  detail,
  error,
  hideError,
  isHiding,
  isLoading,
  selectedProductId,
  onHide,
}: AdminProductDetailPanelProps) {
  return (
    <aside className="admin-operations-detail" aria-labelledby="admin-product-detail-title">
      <div className="admin-panel-heading-row">
        <h3 id="admin-product-detail-title">상품 상세</h3>
        {detail && detail.status !== 'HIDDEN' ? (
          <button type="button" className="text-button danger-button" disabled={isHiding} onClick={() => onHide(detail.productId)}>
            {isHiding ? '숨김 처리 중' : '상품 숨김'}
          </button>
        ) : null}
      </div>
      {selectedProductId === null ? <p className="status-text">상품 행을 선택하면 상세 정보가 표시됩니다.</p> : null}
      {isLoading ? <p className="status-text">상품 상세를 불러오고 있습니다.</p> : null}
      {error ? <ErrorState message="상품 상세를 불러오지 못했습니다." /> : null}
      {hideError ? <p className="error-text">{hideError}</p> : null}
      {selectedProductId !== null && !isLoading && !error && !detail ? (
        <p className="status-text">선택한 상품 정보를 찾을 수 없습니다.</p>
      ) : null}
      {detail ? (
        <>
          <dl className="compact-definition-list">
            <div>
              <dt>상품 번호</dt>
              <dd>{detail.productId}</dd>
            </div>
            <div>
              <dt>상품명</dt>
              <dd>{detail.title}</dd>
            </div>
            <div>
              <dt>판매자</dt>
              <dd>
                #{detail.sellerId} {detail.sellerNickname}
              </dd>
            </div>
            <div>
              <dt>가격</dt>
              <dd>{currencyFormatter.format(detail.price)}원</dd>
            </div>
            <div>
              <dt>상태</dt>
              <dd>
                <StatusBadge status={detail.status} />
              </dd>
            </div>
            <div>
              <dt>설명</dt>
              <dd>{detail.description || '-'}</dd>
            </div>
          </dl>
          <div className="admin-detail-actions">
            <span className="status-text">이미지 {detail.imageUrls.length}개</span>
          </div>
        </>
      ) : null}
    </aside>
  );
}

type AdminOrderListProps = {
  data: PageResponse<AdminOrderSummary> | undefined;
  error: unknown;
  isFetching: boolean;
  isLoading: boolean;
  selectedOrderId: number | null;
  onMovePage: (page: number) => void;
  onSelectOrder: (orderId: number) => void;
};

function AdminOrderList({
  data,
  error,
  isFetching,
  isLoading,
  selectedOrderId,
  onMovePage,
  onSelectOrder,
}: AdminOrderListProps) {
  const orders = data?.content ?? [];

  return (
    <div className="admin-operations-list">
      {isLoading ? <p className="status-text">주문 목록을 불러오고 있습니다.</p> : null}
      {error ? <ErrorState message="주문 목록을 불러오지 못했습니다." /> : null}
      {!isLoading && !error && orders.length === 0 ? (
        <EmptyState title="주문이 없습니다" description="검색 조건에 맞는 주문이 없습니다." />
      ) : null}
      {orders.length > 0 ? (
        <>
          <div className="admin-operations-table" aria-label="관리자 주문 검색 결과">
            <div className="admin-operations-table-head admin-order-grid" role="row">
              <span>주문</span>
              <span>상품</span>
              <span>구매자</span>
              <span>판매자</span>
              <span>상태</span>
              <span>주문일</span>
            </div>
            {orders.map((order) => (
              <button
                type="button"
                className={`admin-operations-row admin-order-grid ${
                  selectedOrderId === order.orderId ? 'admin-operations-row-selected' : ''
                }`}
                key={order.orderId}
                onClick={() => onSelectOrder(order.orderId)}
              >
                <span>#{order.orderId}</span>
                <span>
                  #{order.productId} {order.productTitle}
                </span>
                <span>
                  #{order.buyerId} {order.buyerNickname}
                </span>
                <span>
                  #{order.sellerId} {order.sellerNickname}
                </span>
                <span>
                  <StatusBadge status={order.status} />
                </span>
                <span>{formatDate(order.orderedAt)}</span>
              </button>
            ))}
          </div>
          {data ? <AdminPagination data={data} isFetching={isFetching} onMovePage={onMovePage} /> : null}
        </>
      ) : null}
    </div>
  );
}

type AdminOrderDetailPanelProps = {
  detail: AdminOrderDetail | undefined;
  error: unknown;
  isLoading: boolean;
  selectedOrderId: number | null;
};

function AdminOrderDetailPanel({ detail, error, isLoading, selectedOrderId }: AdminOrderDetailPanelProps) {
  return (
    <aside className="admin-operations-detail" aria-labelledby="admin-order-detail-title">
      <h3 id="admin-order-detail-title">주문 상세</h3>
      {selectedOrderId === null ? <p className="status-text">주문 행을 선택하면 상세 정보가 표시됩니다.</p> : null}
      {isLoading ? <p className="status-text">주문 상세를 불러오고 있습니다.</p> : null}
      {error ? <ErrorState message="주문 상세를 불러오지 못했습니다." /> : null}
      {selectedOrderId !== null && !isLoading && !error && !detail ? (
        <p className="status-text">선택한 주문 정보를 찾을 수 없습니다.</p>
      ) : null}
      {detail ? (
        <dl className="compact-definition-list">
          <div>
            <dt>주문 번호</dt>
            <dd>{detail.orderId}</dd>
          </div>
          <div>
            <dt>상품</dt>
            <dd>
              #{detail.productId} {detail.productTitle}
            </dd>
          </div>
          <div>
            <dt>상품 금액</dt>
            <dd>{currencyFormatter.format(detail.productPrice)}원</dd>
          </div>
          <div>
            <dt>주문 상태</dt>
            <dd>
              <StatusBadge status={detail.status} />
            </dd>
          </div>
          <div>
            <dt>상품 상태</dt>
            <dd>
              <StatusBadge status={detail.productStatus} />
            </dd>
          </div>
          <div>
            <dt>구매자</dt>
            <dd>
              #{detail.buyerId} {detail.buyerNickname}
            </dd>
          </div>
          <div>
            <dt>판매자</dt>
            <dd>
              #{detail.sellerId} {detail.sellerNickname}
            </dd>
          </div>
          <div>
            <dt>정산 생성</dt>
            <dd>{formatBoolean(detail.settlementExists)}</dd>
          </div>
          <div>
            <dt>주문일</dt>
            <dd>{formatDate(detail.orderedAt)}</dd>
          </div>
          <div>
            <dt>취소일</dt>
            <dd>{formatDate(detail.canceledAt)}</dd>
          </div>
          <div>
            <dt>확정일</dt>
            <dd>{formatDate(detail.confirmedAt)}</dd>
          </div>
        </dl>
      ) : null}
    </aside>
  );
}

type AdminMemberListProps = {
  data: PageResponse<AdminMemberSummary> | undefined;
  error: unknown;
  isFetching: boolean;
  isLoading: boolean;
  selectedMemberId: number | null;
  onMovePage: (page: number) => void;
  onSelectMember: (memberId: number) => void;
};

function AdminMemberList({
  data,
  error,
  isFetching,
  isLoading,
  selectedMemberId,
  onMovePage,
  onSelectMember,
}: AdminMemberListProps) {
  const members = data?.content ?? [];

  return (
    <div className="admin-operations-list">
      {isLoading ? <p className="status-text">회원 목록을 불러오고 있습니다.</p> : null}
      {error ? <ErrorState message="회원 목록을 불러오지 못했습니다." /> : null}
      {!isLoading && !error && members.length === 0 ? (
        <EmptyState title="회원이 없습니다" description="검색 조건에 맞는 회원이 없습니다." />
      ) : null}
      {members.length > 0 ? (
        <>
          <div className="admin-operations-table" aria-label="관리자 회원 검색 결과">
            <div className="admin-operations-table-head admin-member-grid" role="row">
              <span>회원</span>
              <span>이메일</span>
              <span>권한</span>
            </div>
            {members.map((member) => (
              <button
                type="button"
                className={`admin-operations-row admin-member-grid ${
                  selectedMemberId === member.memberId ? 'admin-operations-row-selected' : ''
                }`}
                key={member.memberId}
                onClick={() => onSelectMember(member.memberId)}
              >
                <span>
                  #{member.memberId} {member.nickname}
                </span>
                <span>{member.email}</span>
                <span>
                  <StatusBadge status={member.role} />
                </span>
              </button>
            ))}
          </div>
          {data ? <AdminPagination data={data} isFetching={isFetching} onMovePage={onMovePage} /> : null}
        </>
      ) : null}
    </div>
  );
}

type AdminMemberDetailPanelProps = {
  detail: AdminMemberDetail | undefined;
  error: unknown;
  isLoading: boolean;
  selectedMemberId: number | null;
};

function AdminMemberDetailPanel({ detail, error, isLoading, selectedMemberId }: AdminMemberDetailPanelProps) {
  return (
    <aside className="admin-operations-detail" aria-labelledby="admin-member-detail-title">
      <h3 id="admin-member-detail-title">회원 상세</h3>
      {selectedMemberId === null ? <p className="status-text">회원 행을 선택하면 상세 정보가 표시됩니다.</p> : null}
      {isLoading ? <p className="status-text">회원 상세를 불러오고 있습니다.</p> : null}
      {error ? <ErrorState message="회원 상세를 불러오지 못했습니다." /> : null}
      {selectedMemberId !== null && !isLoading && !error && !detail ? (
        <p className="status-text">선택한 회원 정보를 찾을 수 없습니다.</p>
      ) : null}
      {detail ? (
        <dl className="compact-definition-list">
          <div>
            <dt>회원 번호</dt>
            <dd>{detail.memberId}</dd>
          </div>
          <div>
            <dt>이메일</dt>
            <dd>{detail.email}</dd>
          </div>
          <div>
            <dt>닉네임</dt>
            <dd>{detail.nickname}</dd>
          </div>
          <div>
            <dt>권한</dt>
            <dd>
              <StatusBadge status={detail.role} />
            </dd>
          </div>
          <div>
            <dt>상품 수</dt>
            <dd>{detail.productCount}</dd>
          </div>
          <div>
            <dt>주문 수</dt>
            <dd>{detail.orderCount}</dd>
          </div>
        </dl>
      ) : null}
    </aside>
  );
}

type AdminPaginationProps<T> = {
  data: PageResponse<T>;
  isFetching: boolean;
  onMovePage: (page: number) => void;
};

function AdminPagination<T>({ data, isFetching, onMovePage }: AdminPaginationProps<T>) {
  const currentPage = data.number;
  const totalPages = data.totalPages;
  const hasPreviousPage = currentPage > 0;
  const hasNextPage = totalPages > currentPage + 1;

  return (
    <div className="admin-pagination">
      <button
        type="button"
        className="text-button"
        disabled={!hasPreviousPage || isFetching}
        onClick={() => onMovePage(currentPage - 1)}
      >
        이전
      </button>
      <span>
        {currentPage + 1} / {Math.max(totalPages, 1)}
      </span>
      <button
        type="button"
        className="text-button"
        disabled={!hasNextPage || isFetching}
        onClick={() => onMovePage(currentPage + 1)}
      >
        다음
      </button>
    </div>
  );
}

function toOptionalNumber(value: string) {
  const trimmedValue = value.trim();

  if (trimmedValue === '') {
    return undefined;
  }

  const parsedValue = Number(trimmedValue);

  return Number.isInteger(parsedValue) && parsedValue > 0 ? parsedValue : undefined;
}

function formatDate(value: string | null | undefined) {
  if (!value) {
    return '-';
  }

  return dateFormatter.format(new Date(value));
}

function formatBoolean(value: boolean) {
  return value ? '정산이 생성되었습니다' : '정산이 아직 없습니다';
}

function toErrorMessage(error: unknown, fallbackMessage = '운영 요청을 처리하지 못했습니다.') {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
