import { Link } from 'react-router-dom';
import { CartToggle } from '../cart/CartToggle';
import { toProductImageSrc } from '../products/productApi';
import { WishlistToggle } from '../wishlist/WishlistToggle';
import { BuyerAvailabilityBadge, StatusBadge } from '../../shared/ui/ResourceStates';
import type { CatalogProductCard as CatalogProductCardModel } from './catalogApi';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

type CatalogProductCardProps = {
  product: CatalogProductCardModel;
};

export function CatalogProductCard({ product }: CatalogProductCardProps) {
  const thumbnailSrc = toProductImageSrc(product.representativeImageUrl);
  const purchasable = product.availability.status !== 'SOLD_OUT';

  return (
    <article className="catalog-product-card">
      <Link className="catalog-product-card-link" to={`/products/${product.id}`}>
        {thumbnailSrc ? (
          <img className="catalog-product-thumb" src={thumbnailSrc} alt="" />
        ) : (
          <div className="catalog-product-thumb catalog-product-thumb-fallback">Sweet Market</div>
        )}
        <div className="catalog-product-card-body">
          <span className="catalog-category-label">{categoryLabel(product.category)}</span>
          <h3>{product.title}</h3>
          <strong>{currencyFormatter.format(product.price)}원</strong>
          <BuyerAvailabilityBadge availability={product.availability} />
        </div>
      </Link>
      <div className="catalog-product-card-meta">
        <Link to={`/stores/${product.storeId}`}>{product.storeName}</Link>
        <StatusBadge status={product.storeType} />
      </div>
      <div className="catalog-product-card-actions">
        <WishlistToggle productId={product.id} sellerId={product.sellerId} wishlisted={product.wishlisted} purchasable={purchasable} />
        <CartToggle productId={product.id} sellerId={product.sellerId} carted={product.carted} purchasable={purchasable} />
      </div>
    </article>
  );
}

function categoryLabel(category: CatalogProductCardModel['category']) {
  switch (category) {
    case 'COMPUTERS':
      return '컴퓨터';
    case 'MOBILE':
      return '모바일';
    case 'HOME_APPLIANCES':
      return '생활가전';
    case 'VEHICLES':
      return '차량';
    case 'LIVING_HOBBY':
      return '생활·취미';
    case 'OTHER':
      return '기타';
  }
}
