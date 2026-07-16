import { Link } from 'react-router-dom';
import { CatalogPanel } from '../features/catalog/CatalogPanel';
import { ActiveEventStrip } from '../features/discovery/ActiveEventStrip';
import { PopularProductGrid } from '../features/discovery/PopularProductGrid';

export function HomePage() {
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
      <ActiveEventStrip />
      <PopularProductGrid />
      <section className="product-list-section" aria-labelledby="product-list-title">
        <div className="product-list-heading">
          <div>
            <p className="eyebrow">Products</p>
            <h2 id="product-list-title">상품 둘러보기</h2>
          </div>
        </div>
        <CatalogPanel />
      </section>
    </section>
  );
}
