export function HomePage() {
  return (
    <section className="home-page">
      <div className="home-copy">
        <p className="eyebrow">Sweet Market</p>
        <h1>믿고 거래하는 동네 마켓</h1>
        <p>상품 목록과 거래 흐름을 연결할 준비를 하고 있습니다.</p>
      </div>
      <div className="product-placeholder" aria-label="상품 목록 준비 중">
        <div>
          <strong>상품 목록</strong>
          <span>판매 중인 상품을 곧 보여드릴게요.</span>
        </div>
      </div>
    </section>
  );
}
