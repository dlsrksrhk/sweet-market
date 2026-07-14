import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { checkoutCart, getMyCart, removeCart, type CartItem } from '../features/cart/cartApi';
import { toProductImageSrc } from '../features/products/productApi';
import { BuyerPrice } from '../features/promotions/BuyerPrice';
import { type ApiError } from '../shared/api/http';
import { BuyerAvailabilityBadge, EmptyState, ErrorState } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

export function MyCartPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const { data, error, isLoading } = useQuery({
    queryKey: ['my-cart'],
    queryFn: getMyCart,
  });

  const cartItems = data?.content ?? [];
  const selectableIds = useMemo(
    () => cartItems.filter((item) => item.checkoutAvailable).map((item) => item.cartItemId),
    [cartItems],
  );
  const selectableIdSet = useMemo(() => new Set(selectableIds), [selectableIds]);
  const validSelectedIds = useMemo(
    () => selectedIds.filter((cartItemId) => selectableIdSet.has(cartItemId)),
    [selectedIds, selectableIdSet],
  );
  const selectedTotal = cartItems
    .filter((item) => validSelectedIds.includes(item.cartItemId))
    .reduce((sum, item) => sum + item.effectivePrice, 0);
  const allSelectableSelected = selectableIds.length > 0 && selectableIds.every((id) => validSelectedIds.includes(id));

  useEffect(() => {
    setSelectedIds((current) => {
      const next = current.filter((cartItemId) => selectableIdSet.has(cartItemId));
      return next.length === current.length ? current : next;
    });
  }, [selectableIdSet]);

  const removeMutation = useMutation({
    mutationFn: (item: CartItem) => removeCart(item.productId),
    onSuccess: async (response, item) => {
      setSelectedIds((current) => current.filter((cartItemId) => cartItemId !== item.cartItemId));
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['my-cart'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['products', response.productId] }),
      ]);
    },
  });

  const checkoutMutation = useMutation({
    mutationFn: (cartItemIds: number[]) => checkoutCart(cartItemIds),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['my-cart'] }),
        queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
      ]);
      navigate('/me/orders');
    },
  });

  if (isLoading) {
    return <p className="status-text">장바구니를 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="장바구니를 불러오지 못했습니다." />;
  }

  function toggleItem(cartItemId: number) {
    setSelectedIds((current) =>
      current.includes(cartItemId) ? current.filter((id) => id !== cartItemId) : [...current, cartItemId],
    );
  }

  function toggleAllSelectable() {
    setSelectedIds(allSelectableSelected ? [] : selectableIds);
  }

  return (
    <section className="list-page">
      <div className="list-page-header">
        <h1>장바구니</h1>
        <p>구매할 상품을 선택해 주문으로 전환합니다.</p>
      </div>
      {checkoutMutation.isError ? <p className="error-text">{toErrorMessage(checkoutMutation.error)}</p> : null}
      {cartItems.length === 0 ? (
        <EmptyState title="장바구니가 비었습니다" description="관심 있는 판매 상품을 장바구니에 담아보세요." />
      ) : (
        <>
          <div className="cart-toolbar">
            <label>
              <input
                type="checkbox"
                checked={allSelectableSelected}
                disabled={selectableIds.length === 0}
                onChange={toggleAllSelectable}
              />
              구매 가능 상품 전체 선택
            </label>
            <span className="cart-total"><strong>{currencyFormatter.format(selectedTotal)}원</strong><small>최종 결제 금액은 주문 시점에 확정됩니다.</small></span>
            <button
              type="button"
              className="text-button"
              disabled={validSelectedIds.length === 0 || checkoutMutation.isPending}
              onClick={() => checkoutMutation.mutate(validSelectedIds)}
            >
              {checkoutMutation.isPending ? '주문 생성 중' : '선택 상품 주문하기'}
            </button>
          </div>
          <div className="cart-list" role="list" aria-label="장바구니 목록">
            {cartItems.map((item) => (
              <CartCard
                item={item}
                key={item.cartItemId}
                checked={selectedIds.includes(item.cartItemId)}
                removePending={removeMutation.isPending}
                onToggle={() => toggleItem(item.cartItemId)}
                onRemove={() => removeMutation.mutate(item)}
              />
            ))}
          </div>
        </>
      )}
    </section>
  );
}

type CartCardProps = {
  item: CartItem;
  checked: boolean;
  removePending: boolean;
  onToggle: () => void;
  onRemove: () => void;
};

function CartCard({ item, checked, removePending, onToggle, onRemove }: CartCardProps) {
  const thumbnailSrc = toProductImageSrc(item.thumbnailUrl);
  const mainContent = (
    <>
      {thumbnailSrc ? (
        <img className="wishlist-card-thumb" src={thumbnailSrc} alt="" />
      ) : (
        <div className="wishlist-card-thumb wishlist-card-thumb-fallback">Sweet Market</div>
      )}
      <div className="wishlist-card-body">
        <div className="wishlist-card-title-row">
          <h2>{item.title}</h2>
          <BuyerAvailabilityBadge availability={item.availability} />
        </div>
        <BuyerPrice price={item} />
        <span>{item.sellerNickname}</span>
        {!item.checkoutAvailable ? <span className="muted-text">{formatUnavailableReason(item.unavailableReason)}</span> : null}
      </div>
    </>
  );

  return (
    <article className="wishlist-card cart-card" role="listitem">
      <label className="cart-select">
        <input type="checkbox" checked={checked} disabled={!item.checkoutAvailable} onChange={onToggle} />
      </label>
      {item.status === 'ON_SALE' ? (
        <Link className="wishlist-card-main" to={`/products/${item.productId}`}>
          {mainContent}
        </Link>
      ) : (
        <div className="wishlist-card-main">{mainContent}</div>
      )}
      <div className="wishlist-card-actions">
        <button type="button" className="text-button danger-button" disabled={removePending} onClick={onRemove}>
          제거
        </button>
      </div>
    </article>
  );
}

function formatUnavailableReason(reason: string | null) {
  switch (reason) {
    case 'RESERVED':
      return '예약된 상품입니다.';
    case 'SOLD_OUT':
      return '품절된 상품입니다.';
    case 'HIDDEN':
      return '숨김 처리된 상품입니다.';
    case 'OWN_PRODUCT':
      return '내 상품은 주문할 수 없습니다.';
    default:
      return '현재 구매할 수 없는 상품입니다.';
  }
}

function toErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? '장바구니 주문을 처리하지 못했습니다.';
}
