import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthProvider';
import { CartToggle } from '../features/cart/CartToggle';
import { type CartResponse } from '../features/cart/cartApi';
import { createOrder } from '../features/orders/orderApi';
import { couponQueryKeys, getEligibleCoupons } from '../features/coupons/couponApi';
import { getProduct, hideProduct, toProductImageSrc, type WishlistResponse } from '../features/products/productApi';
import { getProductReviews } from '../features/reviews/reviewApi';
import { getMyStores, storeQueryKeys } from '../features/stores/storeApi';
import { BuyerPrice } from '../features/promotions/BuyerPrice';
import { WishlistToggle } from '../features/wishlist/WishlistToggle';
import { type ApiError } from '../shared/api/http';
import { BuyerAvailabilityBadge, EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';
import { parsePositiveIntegerParam } from '../shared/utils/parseId';

const ratingFormatter = new Intl.NumberFormat('ko-KR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
});
const currencyFormatter = new Intl.NumberFormat('ko-KR');

export function ProductDetailPage() {
  const navigate = useNavigate();
  const { productId } = useParams();
  const { loading: authLoading, member } = useAuth();
  const queryClient = useQueryClient();
  const parsedProductId = parsePositiveIntegerParam(productId);
  const hasValidProductId = parsedProductId !== null;
  const [wishlistState, setWishlistState] = useState<WishlistResponse | null>(null);
  const [cartState, setCartState] = useState<CartResponse | null>(null);
  const [selectedMemberCouponId, setSelectedMemberCouponId] = useState<number | null>(null);

  const { data: product, error, isLoading } = useQuery({
    queryKey: ['products', parsedProductId],
    queryFn: () => getProduct(parsedProductId ?? 0),
    enabled: hasValidProductId,
  });
  const { data: reviews, error: reviewsError, isLoading: reviewsLoading } = useQuery({
    queryKey: ['product-reviews', parsedProductId],
    queryFn: () => getProductReviews(parsedProductId ?? 0),
    enabled: hasValidProductId,
  });
  const ownedStoresQuery = useQuery({
    queryKey: storeQueryKeys.me(),
    queryFn: getMyStores,
    enabled: !authLoading && member !== null,
  });
  const ownedStoreIds = useMemo(
    () => new Set((ownedStoresQuery.data ?? []).map((store) => store.storeId)),
    [ownedStoresQuery.data],
  );
  const eligibleCouponsQuery = useQuery({
    queryKey: couponQueryKeys.eligible(parsedProductId ?? 0),
    queryFn: () => getEligibleCoupons(parsedProductId ?? 0),
    enabled: !authLoading && member !== null && product !== undefined && !ownedStoreIds.has(product.storeId),
  });

  const hideMutation = useMutation({
    mutationFn: () => hideProduct(parsedProductId ?? 0),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['products'] });
      navigate('/');
    },
  });
  const orderMutation = useMutation({
    mutationFn: (memberCouponId: number | null) => createOrder(parsedProductId ?? 0, memberCouponId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
        queryClient.invalidateQueries({ queryKey: ['my-cart'] }),
        queryClient.invalidateQueries({ queryKey: couponQueryKeys.all }),
      ]);
      navigate('/me/orders');
    },
  });

  useEffect(() => {
    setWishlistState(null);
    setCartState(null);
  }, [product?.id, product?.wishlisted, product?.wishlistCount, product?.carted]);

  useEffect(() => {
    setSelectedMemberCouponId(null);
  }, [parsedProductId]);

  if (!hasValidProductId) {
    return <ErrorState message="상품 주소가 올바르지 않습니다." />;
  }

  if (isLoading || authLoading || (member !== null && ownedStoresQuery.isLoading)) {
    return <p className="status-text">상품 정보를 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="상품 정보를 불러오지 못했습니다." />;
  }

  if (member !== null && ownedStoresQuery.error) {
    return <ErrorState message={toErrorMessage(ownedStoresQuery.error, '소유한 상점 정보를 불러오지 못했습니다.')} />;
  }

  if (!product) {
    return <EmptyState title="상품을 찾을 수 없습니다" description="이미 숨겨졌거나 삭제된 상품일 수 있습니다." />;
  }

  const isOwner = ownedStoreIds.has(product.storeId);
  const displayedWishlist = wishlistState?.productId === product.id ? wishlistState : null;
  const displayedCart = cartState?.productId === product.id ? cartState : null;
  const galleryImages = product.images
    .slice()
    .sort((firstImage, secondImage) => Number(secondImage.representative) - Number(firstImage.representative) || firstImage.sortOrder - secondImage.sortOrder);
  const eligibleCoupons = eligibleCouponsQuery.data ?? [];
  const selectedCoupon = eligibleCoupons.find((coupon) => coupon.id === selectedMemberCouponId) ?? null;

  return (
    <section className="product-detail">
      <div className="product-gallery">
        {galleryImages.length > 0 ? (
          galleryImages.map((image) => <img key={image.id} src={toProductImageSrc(image.imageUrl) ?? image.imageUrl} alt="" />)
        ) : (
          <div className="product-gallery-fallback">Sweet Market</div>
        )}
      </div>
      <article className="product-detail-info">
        <div className="product-detail-heading">
          <div className="product-detail-status-row">
            <BuyerAvailabilityBadge availability={product.availability} />
            <WishlistToggle
              productId={product.id}
              sellerId={product.sellerId}
              wishlisted={displayedWishlist?.wishlisted ?? product.wishlisted}
              wishlistCount={displayedWishlist?.wishlistCount ?? product.wishlistCount}
              purchasable={product.purchasable}
              onChanged={setWishlistState}
            />
            <CartToggle
              productId={product.id}
              sellerId={product.sellerId}
              carted={displayedCart?.carted ?? product.carted}
              purchasable={product.purchasable}
              onChanged={setCartState}
            />
          </div>
          <h1>{product.title}</h1>
          <BuyerPrice price={product} />
          <div>
            <span>상점 </span>
            <Link to={`/stores/${product.storeId}`}>{product.storeName}</Link>{' '}
            <StatusBadge status={product.storeType} />
          </div>
        </div>
        <p>{product.description}</p>
        <div className="product-actions">
          {isOwner ? (
            <>
              <Link className="primary-link" to={`/products/${product.id}/edit`}>
                수정
              </Link>
              <button
                type="button"
                className="text-button danger-button"
                disabled={hideMutation.isPending}
                onClick={() => hideMutation.mutate()}
              >
                {hideMutation.isPending ? '숨기는 중' : '숨기기'}
              </button>
            </>
          ) : !product.purchasable ? (
            <p className="status-text">현재 구매하거나 장바구니와 관심 상품에 새로 추가할 수 없는 상품입니다.</p>
          ) : !member ? (
            <Link className="primary-link" to="/login">
              로그인하고 구매하기
            </Link>
          ) : (
            <>
              <label>
                사용할 쿠폰
                <select
                  value={selectedMemberCouponId ?? ''}
                  disabled={orderMutation.isPending || eligibleCouponsQuery.isLoading}
                  onChange={(event) => setSelectedMemberCouponId(event.target.value === '' ? null : Number(event.target.value))}
                >
                  <option value="">쿠폰 사용 안 함</option>
                  {eligibleCoupons.map((coupon) => (
                    <option key={coupon.id} value={coupon.id}>
                      {coupon.title} (-{currencyFormatter.format(coupon.discountAmount)}원)
                    </option>
                  ))}
                </select>
              </label>
              {eligibleCouponsQuery.isLoading ? <p className="status-text">적용 가능한 쿠폰을 불러오고 있습니다.</p> : null}
              {eligibleCouponsQuery.isError ? <p className="error-text">적용 가능한 쿠폰을 불러오지 못했습니다.</p> : null}
              {selectedCoupon ? (
                <p className="status-text">
                  쿠폰 할인 {currencyFormatter.format(selectedCoupon.discountAmount)}원 · 예상 결제금액 {currencyFormatter.format(selectedCoupon.finalPrice)}원
                </p>
              ) : null}
              <button
                type="button"
                className="text-button"
                disabled={orderMutation.isPending}
                onClick={() => orderMutation.mutate(selectedCoupon?.id ?? null)}
              >
                {orderMutation.isPending ? '주문 중' : '주문하기'}
              </button>
            </>
          )}
        </div>
        {hideMutation.isError ? <p className="error-text">상품을 숨기지 못했습니다.</p> : null}
        {orderMutation.isError ? <p className="error-text">{toErrorMessage(orderMutation.error)}</p> : null}
        <section className="product-review-section" aria-labelledby="product-review-title">
          <div className="review-summary">
            <div>
              <h2 id="product-review-title">상품 리뷰</h2>
              <strong>{formatRating(product.averageRating)}</strong>
              <span>{product.reviewCount}개 리뷰</span>
            </div>
            <div>
              <h2>판매자 평점</h2>
              <strong>{formatRating(product.sellerAverageRating)}</strong>
              <span>{product.sellerReviewCount}개 리뷰</span>
            </div>
          </div>
          {reviewsLoading ? (
            <p className="status-text">리뷰를 불러오고 있습니다.</p>
          ) : reviewsError ? (
            <p className="error-text">리뷰를 불러오지 못했습니다.</p>
          ) : reviews?.content.length ? (
            <div className="review-list">
              {reviews.content.map((review) => (
                <article className="review-item" key={review.reviewId}>
                  <div className="review-item-heading">
                    <strong>{review.buyerNickname}</strong>
                    <span>{formatRating(review.rating)}</span>
                  </div>
                  <p>{review.content}</p>
                  <time dateTime={review.createdAt}>{dateFormatter.format(new Date(review.createdAt))}</time>
                </article>
              ))}
            </div>
          ) : (
            <p className="muted-text">아직 작성된 리뷰가 없습니다.</p>
          )}
        </section>
      </article>
    </section>
  );
}

function formatRating(value: number | null) {
  if (value === null) {
    return '-';
  }

  return ratingFormatter.format(value);
}

function toErrorMessage(error: unknown, fallbackMessage = '주문을 생성하지 못했습니다.') {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
