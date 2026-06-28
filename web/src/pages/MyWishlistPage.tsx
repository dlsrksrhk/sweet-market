import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getMyWishlist, toProductImageSrc, type WishlistItem } from '../features/products/productApi';
import { WishlistToggle } from '../features/wishlist/WishlistToggle';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

export function MyWishlistPage() {
  const { data, error, isLoading } = useQuery({
    queryKey: ['my-wishlist'],
    queryFn: getMyWishlist,
  });

  if (isLoading) {
    return <p className="status-text">찜한 상품을 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="찜한 상품 목록을 불러오지 못했습니다." />;
  }

  const wishlistItems = data?.content ?? [];

  return (
    <section className="list-page">
      <div className="list-page-header">
        <h1>찜한 상품</h1>
        <p>관심 있는 상품을 모아보고 판매 상태를 확인합니다.</p>
      </div>
      {wishlistItems.length === 0 ? (
        <EmptyState title="찜한 상품이 없습니다" description="마음에 드는 상품을 찜하면 이곳에서 다시 확인할 수 있습니다." />
      ) : (
        <div className="wishlist-list" aria-label="찜한 상품 목록">
          {wishlistItems.map((item) => (
            <WishlistCard item={item} key={item.wishlistItemId} />
          ))}
        </div>
      )}
    </section>
  );
}

type WishlistCardProps = {
  item: WishlistItem;
};

function WishlistCard({ item }: WishlistCardProps) {
  const canOpenDetail = item.status === 'ON_SALE';
  const mainContent = <WishlistCardMain item={item} />;

  return (
    <article className="wishlist-card">
      {canOpenDetail ? (
        <Link className="wishlist-card-main" to={`/products/${item.productId}`}>
          {mainContent}
        </Link>
      ) : (
        <div className="wishlist-card-main">{mainContent}</div>
      )}
      <div className="wishlist-card-actions">
        <WishlistToggle
          productId={item.productId}
          sellerId={item.sellerId}
          wishlisted={item.wishlisted}
          wishlistCount={item.wishlistCount}
        />
        {!canOpenDetail ? <span className="muted-text">현재 구매할 수 없는 상품입니다.</span> : null}
      </div>
    </article>
  );
}

function WishlistCardMain({ item }: WishlistCardProps) {
  const thumbnailSrc = toProductImageSrc(item.thumbnailUrl);

  return (
    <>
      {thumbnailSrc ? (
        <img className="wishlist-card-thumb" src={thumbnailSrc} alt="" />
      ) : (
        <div className="wishlist-card-thumb wishlist-card-thumb-fallback">Sweet Market</div>
      )}
      <div className="wishlist-card-body">
        <div className="wishlist-card-title-row">
          <h2>{item.title}</h2>
          <StatusBadge status={item.status} />
        </div>
        <strong>{currencyFormatter.format(item.price)}원</strong>
        <span>{item.sellerNickname}</span>
      </div>
    </>
  );
}
