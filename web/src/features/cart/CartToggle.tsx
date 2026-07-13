import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { addCart, removeCart, type CartResponse } from './cartApi';

type CartToggleProps = {
  productId: number;
  sellerId: number;
  carted: boolean;
  purchasable?: boolean;
  onChanged?: (response: CartResponse) => void;
};

export function CartToggle({ productId, sellerId, carted, purchasable = true, onChanged }: CartToggleProps) {
  const { loading, member } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isOwnProduct = member?.id === sellerId;

  const mutation = useMutation({
    mutationFn: (currentCarted: boolean) => (currentCarted ? removeCart(productId) : addCart(productId)),
    onSuccess: async (response) => {
      onChanged?.(response);

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['products', productId] }),
        queryClient.invalidateQueries({ queryKey: ['catalog'] }),
        queryClient.invalidateQueries({ queryKey: ['my-cart'] }),
      ]);
    },
  });

  const latestResponse = mutation.data?.productId === productId ? mutation.data : null;
  const displayedCarted = latestResponse?.carted ?? carted;

  if (isOwnProduct) {
    return <span className="cart-own-product">내 상품</span>;
  }

  return (
    <button
      type="button"
      className={displayedCarted ? 'cart-button cart-button-active' : 'cart-button'}
      disabled={loading || mutation.isPending || (!purchasable && !displayedCarted)}
      aria-pressed={displayedCarted}
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

        mutation.mutate(displayedCarted);
      }}
    >
      {displayedCarted ? '장바구니 담김' : '장바구니'}
    </button>
  );
}
