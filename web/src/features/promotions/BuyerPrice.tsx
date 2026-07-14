const currencyFormatter = new Intl.NumberFormat('ko-KR');

export type BuyerPromotionPrice = {
  price: number;
  listPrice: number;
  promotionId: number | null;
  promotionTitle: string | null;
  promotionDiscountAmount: number;
  effectivePrice: number;
};

type BuyerPriceProps = {
  price: BuyerPromotionPrice;
};

export function BuyerPrice({ price }: BuyerPriceProps) {
  if (price.promotionId === null) {
    return <strong>{currencyFormatter.format(price.price)}원</strong>;
  }

  return (
    <span className="buyer-price">
      {price.promotionTitle ? <span className="buyer-price-promotion">{price.promotionTitle}</span> : null}
      <del>{currencyFormatter.format(price.listPrice)}원</del>
      <span className="buyer-price-discount">{currencyFormatter.format(price.promotionDiscountAmount)}원 할인</span>
      <strong>{currencyFormatter.format(price.effectivePrice)}원</strong>
    </span>
  );
}
