import { type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';

type RequireAdminProps = {
  children: ReactNode;
};

export function RequireAdmin({ children }: RequireAdminProps) {
  const { loading, member } = useAuth();
  const location = useLocation();

  if (loading) {
    return <p className="status-text">권한을 확인하고 있습니다.</p>;
  }

  if (!member) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (member.role !== 'ADMIN') {
    return <p className="status-text">접근 권한이 없습니다.</p>;
  }

  return children;
}
