import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, setErrorHandlers } from '@/api/client';
import { authApi, setupApi } from '@/api/endpoints';
import type { Me } from '@/api/types';

interface AuthState {
  me: Me | null;
  /** 初始化状态:null = 尚未知 */
  initialized: boolean | null;
  loading: boolean;
  refresh: () => Promise<Me | null>;
  /** 重新读取初始化状态(初始化完成后调用) */
  recheckSetup: () => Promise<void>;
  login: (username: string, password: string) => Promise<Me>;
  logout: () => Promise<void>;
}

const Ctx = createContext<AuthState | null>(null);
export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth 需要在 AuthProvider 内使用');
  return v;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [initialized, setInitialized] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const location = useLocation();

  // 全局错误分发:UNAUTHORIZED → 登录;SETUP_REQUIRED → 初始化
  useEffect(() => {
    setErrorHandlers({
      unauthorized: () => {
        setMe(null);
        const here = location.pathname + location.search;
        if (!location.pathname.startsWith('/login')) {
          navigate(`/login?redirect=${encodeURIComponent(here)}`, { replace: true });
        }
      },
      setupRequired: () => {
        setInitialized(false);
        navigate('/setup', { replace: true });
      },
    });
  }, [navigate, location.pathname, location.search]);

  const refresh = useCallback(async () => {
    try {
      const m = await authApi.me();
      setMe(m);
      return m;
    } catch (e) {
      setMe(null);
      if (!(e instanceof ApiError)) throw e;
      return null;
    }
  }, []);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const s = await setupApi.status();
        if (!alive) return;
        setInitialized(s.initialized);
        if (s.initialized) await refresh();
        else navigate('/setup', { replace: true });
      } catch {
        /* 网络错误:停在当前页,请求层会提示 */
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const recheckSetup = useCallback(async () => {
    const s = await setupApi.status();
    setInitialized(s.initialized);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const m = await authApi.login({ username, password });
    setMe(m);
    return m;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setMe(null);
      navigate('/login', { replace: true });
    }
  }, [navigate]);

  const value = useMemo(() => ({ me, initialized, loading, refresh, recheckSetup, login, logout }), [me, initialized, loading, refresh, recheckSetup, login, logout]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
