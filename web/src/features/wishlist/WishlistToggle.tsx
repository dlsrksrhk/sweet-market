import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { addWishlist, removeWishlist, type WishlistResponse } from '../products/productApi';

type WishlistToggleProps = {
  productId: number;
  sellerId: number;
  wishlisted: boolean;
  wishlistCount?: number;
  purchasable?: boolean;
  onChanged?: (response: WishlistResponse) => void;
};

export function WishlistToggle({
  productId,
  sellerId,
  wishlisted,
  wishlistCount,
  purchasable = true,
  onChanged,
}: WishlistToggleProps) {
  const { loading, member } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isOwnProduct = member?.id === sellerId;

  const mutation = useMutation({
    mutationFn: (currentWishlisted: boolean) => (currentWishlisted ? removeWishlist(productId) : addWishlist(productId)),
    onSuccess: async (response) => {
      onChanged?.(response);

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['products', productId] }),
        queryClient.invalidateQueries({ queryKey: ['catalog'] }),
        queryClient.invalidateQueries({ queryKey: ['my-wishlist'] }),
      ]);
    },
  });

  const latestResponse = mutation.data?.productId === productId ? mutation.data : null;
  const displayedWishlisted = latestResponse?.wishlisted ?? wishlisted;
  const displayedWishlistCount = latestResponse?.wishlistCount ?? wishlistCount;

  if (isOwnProduct) {
    return <span className="wishlist-own-product">내 상품{displayedWishlistCount === undefined ? '' : ` · 관심 ${displayedWishlistCount}`}</span>;
  }

  return (
    <button
      type="button"
      className={displayedWishlisted ? 'wishlist-button wishlist-button-active' : 'wishlist-button'}
      disabled={loading || mutation.isPending || (!purchasable && !displayedWishlisted)}
      aria-pressed={displayedWishlisted}
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();

        if (loading) {
          return;
        }

        if (!member) {
          navigate('/login', { state: { from: `${location.pathname}${location.search}${location.hash}` } });
          return;
        }

        mutation.mutate(displayedWishlisted);
      }}
    >
      관심{displayedWishlistCount === undefined ? '' : ` ${displayedWishlistCount}`}
    </button>
  );
}
