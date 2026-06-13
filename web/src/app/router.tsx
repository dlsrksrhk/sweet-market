import { Route, Routes } from 'react-router-dom';
import { RequireAdmin } from '../features/auth/RequireAdmin';
import { RequireAuth } from '../features/auth/RequireAuth';
import { HomePage } from '../pages/HomePage';
import { LoginPage } from '../pages/LoginPage';
import { ProductDetailPage } from '../pages/ProductDetailPage';
import { ProductFormPage } from '../pages/ProductFormPage';
import { SignupPage } from '../pages/SignupPage';
import { Shell } from '../shared/layout/Shell';

export function AppRouter() {
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="signup" element={<SignupPage />} />
        <Route path="products/:productId" element={<ProductDetailPage />} />
        <Route
          path="products/new"
          element={
            <RequireAuth>
              <ProductFormPage />
            </RequireAuth>
          }
        />
        <Route
          path="products/:productId/edit"
          element={
            <RequireAuth>
              <ProductFormPage />
            </RequireAuth>
          }
        />
        <Route
          path="me/orders"
          element={
            <RequireAuth>
              <PlaceholderPage title="내 주문" description="주문 목록 화면을 준비 중입니다." />
            </RequireAuth>
          }
        />
        <Route
          path="me/sales"
          element={
            <RequireAuth>
              <PlaceholderPage title="내 판매" description="판매 상품 관리 화면을 준비 중입니다." />
            </RequireAuth>
          }
        />
        <Route
          path="me/settlements"
          element={
            <RequireAuth>
              <PlaceholderPage title="정산" description="정산 내역 화면을 준비 중입니다." />
            </RequireAuth>
          }
        />
        <Route
          path="admin/batches/settlements"
          element={
            <RequireAdmin>
              <PlaceholderPage title="정산 배치" description="관리자 정산 배치 화면을 준비 중입니다." />
            </RequireAdmin>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

type PlaceholderPageProps = {
  title: string;
  description: string;
};

function PlaceholderPage({ title, description }: PlaceholderPageProps) {
  return (
    <section className="page-panel">
      <h1>{title}</h1>
      <p>{description}</p>
    </section>
  );
}

function NotFoundPage() {
  return (
    <section className="page-panel">
      <h1>페이지를 찾을 수 없습니다</h1>
      <p>요청하신 주소에 해당하는 화면이 없습니다.</p>
    </section>
  );
}
