import { Link } from 'react-router-dom';
import { CartToggle } from '../cart/CartToggle';
import { WishlistToggle } from '../wishlist/WishlistToggle';
import { StatusBadge } from '../../shared/ui/ResourceStates';
import type { ProductStatus } from './productApi';
import { toProductImageSrc } from './productApi';
import type { StoreType } from '../stores/storeApi';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

export type BuyerProductCard = {
  id: number;
  storeId: number;
  storeName: string;
  storeType: StoreType;
  sellerId: number;
  title: string;
  price: number;
  status: ProductStatus;
  thumbnailUrl: string | null;
  wishlistCount: number;
  wishlisted: boolean;
  carted: boolean;
};

type ProductCardProps = {
  product: BuyerProductCard;
};

export function ProductCard({ product }: ProductCardProps) {
  return (
    <article className="product-card">
      <Link className="product-card-link" to={`/products/${product.id}`}>
        <ProductThumb product={product} />
        <div className="product-card-body">
          <div className="product-card-title-row">
            <h2>{product.title}</h2>
            <StatusBadge status={product.status} />
          </div>
          <strong>{currencyFormatter.format(product.price)}원</strong>
        </div>
      </Link>
      <div className="product-card-actions">
        <Link to={`/stores/${product.storeId}`}>{product.storeName}</Link>
        <StatusBadge status={product.storeType} />
        <WishlistToggle
          productId={product.id}
          sellerId={product.sellerId}
          wishlisted={product.wishlisted}
          wishlistCount={product.wishlistCount}
        />
        <CartToggle productId={product.id} sellerId={product.sellerId} carted={product.carted} />
      </div>
    </article>
  );
}

type ProductThumbProps = {
  product: Pick<BuyerProductCard, 'thumbnailUrl'>;
};

function ProductThumb({ product }: ProductThumbProps) {
  const thumbnailSrc = toProductImageSrc(product.thumbnailUrl);

  if (thumbnailSrc) {
    return <img className="product-thumb" src={thumbnailSrc} alt="" />;
  }

  return <div className="product-thumb product-thumb-fallback">Sweet Market</div>;
}
