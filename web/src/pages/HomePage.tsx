import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { CartToggle } from '../features/cart/CartToggle';
import { getProducts, toProductImageSrc, type ProductSummary } from '../features/products/productApi';
import { WishlistToggle } from '../features/wishlist/WishlistToggle';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

export function HomePage() {
  const { data, error, isLoading } = useQuery({
    queryKey: ['products'],
    queryFn: getProducts,
  });
  const productCount = data?.totalElements ?? 0;

  return (
    <section className="home-page">
      <div className="home-copy">
        <div>
          <p className="eyebrow">Sweet Market</p>
          <h1>믿고 거래하는 동네 마켓</h1>
          <p>판매 중인 상품을 둘러보고 마음에 드는 거래를 찾아보세요.</p>
        </div>
        <Link className="primary-link home-action" to="/products/new">
          상품 등록
        </Link>
      </div>
      <section className="product-list-section" aria-labelledby="product-list-title">
        <div className="product-list-heading">
          <div>
            <p className="eyebrow">Products</p>
            <h2 id="product-list-title">최근 등록 상품</h2>
          </div>
          {!isLoading && !error && <span>{currencyFormatter.format(productCount)}개 상품</span>}
        </div>
        <ProductGrid error={error} isLoading={isLoading} products={data?.content ?? []} />
      </section>
    </section>
  );
}

type ProductGridProps = {
  error: Error | null;
  isLoading: boolean;
  products: ProductSummary[];
};

function ProductGrid({ error, isLoading, products }: ProductGridProps) {
  if (isLoading) {
    return <p className="status-text">상품을 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="상품 목록을 불러오지 못했습니다." />;
  }

  if (products.length === 0) {
    return <EmptyState title="아직 등록된 상품이 없습니다" description="첫 상품을 등록해 마켓을 채워보세요." />;
  }

  return (
    <div className="product-grid" aria-label="상품 목록">
      {products.map((product) => (
        <article className="product-card" key={product.id}>
          <Link className="product-card-link" to={`/products/${product.id}`}>
            <ProductThumb product={product} />
            <div className="product-card-body">
              <div className="product-card-title-row">
                <h2>{product.title}</h2>
                <StatusBadge status={product.status} />
              </div>
              <strong>{currencyFormatter.format(product.price)}원</strong>
              <span>{product.sellerNickname}</span>
            </div>
          </Link>
          <div className="product-card-actions">
            <WishlistToggle
              productId={product.id}
              sellerId={product.sellerId}
              wishlisted={product.wishlisted}
              wishlistCount={product.wishlistCount}
            />
            <CartToggle productId={product.id} sellerId={product.sellerId} carted={product.carted} />
          </div>
        </article>
      ))}
    </div>
  );
}

type ProductThumbProps = {
  product: ProductSummary;
};

function ProductThumb({ product }: ProductThumbProps) {
  const thumbnailSrc = toProductImageSrc(product.thumbnailUrl);

  if (thumbnailSrc) {
    return <img className="product-thumb" src={thumbnailSrc} alt="" />;
  }

  return <div className="product-thumb product-thumb-fallback">Sweet Market</div>;
}
