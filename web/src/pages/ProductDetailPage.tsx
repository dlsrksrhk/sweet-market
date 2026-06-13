import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthProvider';
import { getProduct, hideProduct } from '../features/products/productApi';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');

export function ProductDetailPage() {
  const navigate = useNavigate();
  const { productId } = useParams();
  const { member } = useAuth();
  const queryClient = useQueryClient();
  const parsedProductId = Number(productId);
  const hasValidProductId = Number.isInteger(parsedProductId) && parsedProductId > 0;

  const { data: product, error, isLoading } = useQuery({
    queryKey: ['products', parsedProductId],
    queryFn: () => getProduct(parsedProductId),
    enabled: hasValidProductId,
  });

  const hideMutation = useMutation({
    mutationFn: () => hideProduct(parsedProductId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['products'] });
      navigate('/');
    },
  });

  if (!hasValidProductId) {
    return <ErrorState message="상품 주소가 올바르지 않습니다." />;
  }

  if (isLoading) {
    return <p className="status-text">상품 정보를 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="상품 정보를 불러오지 못했습니다." />;
  }

  if (!product) {
    return <EmptyState title="상품을 찾을 수 없습니다" description="이미 숨겨졌거나 삭제된 상품일 수 있습니다." />;
  }

  const isSeller = member?.id === product.sellerId;

  return (
    <section className="product-detail">
      <div className="product-gallery">
        {product.images.length > 0 ? (
          product.images.map((image) => <img key={image.id} src={image.imageUrl} alt="" />)
        ) : (
          <div className="product-gallery-fallback">Sweet Market</div>
        )}
      </div>
      <article className="product-detail-info">
        <div className="product-detail-heading">
          <StatusBadge status={product.status} />
          <h1>{product.title}</h1>
          <strong>{currencyFormatter.format(product.price)}원</strong>
          <span>판매자 {product.sellerNickname}</span>
        </div>
        <p>{product.description}</p>
        <div className="product-actions">
          {!member ? (
            <Link className="primary-link" to="/login">
              로그인하고 구매하기
            </Link>
          ) : isSeller ? (
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
          ) : product.status === 'ON_SALE' ? (
            <button type="button" className="text-button" disabled>
              주문 기능은 다음 거래 마일스톤에서 제공됩니다
            </button>
          ) : (
            <p className="status-text">현재 구매할 수 없는 상품입니다.</p>
          )}
        </div>
        {hideMutation.isError ? <p className="error-text">상품을 숨기지 못했습니다.</p> : null}
      </article>
    </section>
  );
}
