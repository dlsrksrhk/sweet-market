import { useQuery } from '@tanstack/react-query';
import { CatalogProductCard } from '../catalog/CatalogProductCard';
import { EmptyState, ErrorState } from '../../shared/ui/ResourceStates';
import { discoveryQueryKeys, getPopularProducts } from './discoveryApi';

export function PopularProductGrid() {
  const productsQuery = useQuery({ queryKey: discoveryQueryKeys.popularProducts(), queryFn: getPopularProducts });

  return (
    <section className="discovery-section" aria-labelledby="popular-product-title">
      <div className="discovery-heading">
        <div>
          <p className="eyebrow">POPULAR NOW</p>
          <h2 id="popular-product-title">지금 인기 있는 상품</h2>
        </div>
      </div>
      {productsQuery.isLoading ? <PopularProductSkeletons /> : null}
      {productsQuery.error ? <ErrorState message="인기 상품을 불러오지 못했습니다." /> : null}
      {productsQuery.data?.length === 0 ? <EmptyState title="아직 인기 상품이 없습니다" description="상품을 둘러보고 첫 번째 관심을 남겨보세요." /> : null}
      {productsQuery.data?.length ? <div className="popular-product-grid">{productsQuery.data.map((product) => <CatalogProductCard product={product} key={product.id} />)}</div> : null}
    </section>
  );
}

function PopularProductSkeletons() {
  return <div className="popular-product-grid" aria-label="인기 상품을 불러오는 중">{Array.from({ length: 8 }, (_, index) => <div className="popular-product-skeleton discovery-skeleton" key={index}><div /><span /><strong /><small /></div>)}</div>;
}
