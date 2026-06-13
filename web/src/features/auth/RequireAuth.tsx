import { type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';

type RequireAuthProps = {
  children: ReactNode;
};

export function RequireAuth({ children }: RequireAuthProps) {
  const { loading, member } = useAuth();
  const location = useLocation();

  if (loading) {
    return <p className="status-text">로그인 상태를 확인하고 있습니다.</p>;
  }

  if (!member) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}${location.hash}` }} />;
  }

  return children;
}
