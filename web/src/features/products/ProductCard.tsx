import { Link } from 'react-router-dom';
import { CartToggle } from '../cart/CartToggle';
import { WishlistToggle } from '../wishlist/WishlistToggle';
import { BuyerAvailabilityBadge, StatusBadge } from '../../shared/ui/ResourceStates';
import type { BuyerAvailability, ProductStatus } from './productApi';
import { toProductImageSrc } from './productApi';
import type { StoreType } from '../stores/storeApi';
import { BuyerPrice } from '../promotions/BuyerPrice';

export type BuyerProductCard = {
  id: number;
  storeId: number;
  storeName: string;
  storeType: StoreType;
  sellerId: number;
  title: string;
  price: number;
  listPrice: number;
  promotionId: number | null;
  promotionTitle: string | null;
  promotionDiscountAmount: number;
  effectivePrice: number;
  status: ProductStatus;
  thumbnailUrl: string | null;
  wishlistCount: number;
  wishlisted: boolean;
  carted: boolean;
  availability: BuyerAvailability;
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
            <BuyerAvailabilityBadge availability={product.availability} />
          </div>
          <BuyerPrice price={product} />
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
