import { Navigate, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuth } from './AuthContext';
import type { ReactNode } from 'react';

export default function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading, initialized } = useAuth();
  const loc = useLocation();
  if (loading) return <Spin style={{ display: 'block', margin: '30vh auto' }} />;
  if (initialized === false) return <Navigate to="/setup" replace />;
  if (!me) return <Navigate to={`/login?redirect=${encodeURIComponent(loc.pathname + loc.search)}`} replace />;
  return <>{children}</>;
}
