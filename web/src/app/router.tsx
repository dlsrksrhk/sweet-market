import { Route, Routes } from 'react-router-dom';
import { RequireAdmin } from '../features/auth/RequireAdmin';
import { RequireAuth } from '../features/auth/RequireAuth';
import { AdminOperationsPage } from '../pages/AdminOperationsPage';
import { AdminRefundRequestsPage } from '../pages/AdminRefundRequestsPage';
import { AdminSettlementBatchPage } from '../pages/AdminSettlementBatchPage';
import { HomePage } from '../pages/HomePage';
import { LoginPage } from '../pages/LoginPage';
import { MyCartPage } from '../pages/MyCartPage';
import { MyOrdersPage } from '../pages/MyOrdersPage';
import { MyRefundRequestsPage } from '../pages/MyRefundRequestsPage';
import { MyReportsPage } from '../pages/MyReportsPage';
import { MySalesPage } from '../pages/MySalesPage';
import { MySettlementsPage } from '../pages/MySettlementsPage';
import { MyWishlistPage } from '../pages/MyWishlistPage';
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
          path="me/wishlist"
          element={
            <RequireAuth>
              <MyWishlistPage />
            </RequireAuth>
          }
        />
        <Route
          path="me/cart"
          element={
            <RequireAuth>
              <MyCartPage />
            </RequireAuth>
          }
        />
        <Route
          path="me/orders"
          element={
            <RequireAuth>
              <MyOrdersPage />
            </RequireAuth>
          }
        />
        <Route
          path="me/sales"
          element={
            <RequireAuth>
              <MySalesPage />
            </RequireAuth>
          }
        />
        <Route
          path="me/settlements"
          element={
            <RequireAuth>
              <MySettlementsPage />
            </RequireAuth>
          }
        />
        <Route
          path="me/reports"
          element={
            <RequireAuth>
              <MyReportsPage />
            </RequireAuth>
          }
        />
        <Route
          path="me/refunds"
          element={
            <RequireAuth>
              <MyRefundRequestsPage />
            </RequireAuth>
          }
        />
        <Route
          path="admin/operations"
          element={
            <RequireAdmin>
              <AdminOperationsPage />
            </RequireAdmin>
          }
        />
        <Route
          path="admin/refunds"
          element={
            <RequireAdmin>
              <AdminRefundRequestsPage />
            </RequireAdmin>
          }
        />
        <Route
          path="admin/batches/settlements"
          element={
            <RequireAdmin>
              <AdminSettlementBatchPage />
            </RequireAdmin>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
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
