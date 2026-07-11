import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ProductCard } from '../features/products/ProductCard';
import { getProducts, type ProductSummary } from '../features/products/productApi';
import { EmptyState, ErrorState } from '../shared/ui/ResourceStates';

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
        <ProductCard key={product.id} product={product} />
      ))}
    </div>
  );
}
