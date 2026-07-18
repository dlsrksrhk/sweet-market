import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthProvider';

export function Shell() {
  const { loading, logout, member } = useAuth();

  return (
    <div className="app-shell">
      <header className="top-nav">
        <Link className="brand-link" to="/">
          Sweet Market
        </Link>
        <nav className="nav-links" aria-label="주요 메뉴">
          <NavLink to="/">상품</NavLink>
          {member ? (
            <>
              <NavLink to="/me/store">내 상점</NavLink>
              <NavLink to="/me/wishlist">찜한 상품</NavLink>
              <NavLink to="/me/cart">장바구니</NavLink>
              <NavLink to="/me/orders">내 주문</NavLink>
              <NavLink to="/me/coupons">내 쿠폰</NavLink>
              <NavLink to="/me/refunds">환불 내역</NavLink>
              <NavLink to="/me/sales">내 판매</NavLink>
              <NavLink to="/me/settlements">정산</NavLink>
              <NavLink to="/me/reports">리포트</NavLink>
              <NavLink to="/me/sales/refunds">판매 환불 관리</NavLink>
              {member.role === 'ADMIN' ? (
                <>
                  <NavLink to="/admin/operations">관리자</NavLink>
                  <NavLink to="/admin/dashboard">운영 대시보드</NavLink>
                  <NavLink to="/admin/business-stores">사업자 상점 심사</NavLink>
                  <NavLink to="/admin/refunds">관리자 환불</NavLink>
                  <NavLink to="/admin/coupons">쿠폰 캠페인</NavLink>
                </>
              ) : null}
            </>
          ) : null}
        </nav>
        <div className="account-actions">
          {loading ? (
            <span className="muted-text">확인 중</span>
          ) : member ? (
            <>
              <span className="member-name">{member.nickname}</span>
              <button type="button" className="text-button" onClick={logout}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <Link to="/login">로그인</Link>
              <Link className="primary-link" to="/signup">
                회원가입
              </Link>
            </>
          )}
        </div>
      </header>
      <main className="page-content">
        <Outlet />
      </main>
    </div>
  );
}
