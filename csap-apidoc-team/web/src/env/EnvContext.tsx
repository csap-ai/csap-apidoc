// 环境上下文(1.1 §8):当前环境存在 URL 查询参数 env(便于分享链接),并记在 localStorage。
// 只配置一个环境(或后端还是 1.0、没有 /envs)时 multi=false,env 为 undefined:所有请求不带 env 参数,界面与 1.0 一致。
import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import { envApi } from '@/api/endpoints';
import type { Env } from '@/api/types';
import { useLoad } from '@/utils/useLoad';

export const ENV_STORAGE_KEY = 'csap-team-env';

export function readStoredEnv(): string | null {
  try {
    return localStorage.getItem(ENV_STORAGE_KEY);
  } catch {
    return null;
  }
}
function writeStoredEnv(key: string) {
  try {
    localStorage.setItem(ENV_STORAGE_KEY, key);
  } catch {
    /* 隐私模式 / 禁用存储:忽略 */
  }
}

/** 纯函数:按"URL 参数 → localStorage → 默认环境"的顺序决定当前环境(只认存在的环境) */
export function resolveEnv(envs: Env[], fromUrl: string | null, stored: string | null): string | undefined {
  if (envs.length < 2) return undefined;
  const ok = (k: string | null): k is string => !!k && envs.some((e) => e.key === k);
  if (ok(fromUrl)) return fromUrl;
  if (ok(stored)) return stored;
  return (envs.find((e) => e.default) ?? envs[0]).key;
}

interface EnvState {
  /** 后端返回的全部环境,按展示顺序;单环境 / 1.0 后端时可能只有一个或为空 */
  envs: Env[];
  loading: boolean;
  /** 配置了 2 个以上环境 */
  multi: boolean;
  /** 当前环境 key;单环境时为 undefined(请求不带 env 参数) */
  env: string | undefined;
  /** 默认环境的 key(后端是 1.0 时为 undefined);省略 env 参数 = 它 */
  defaultKey: string | undefined;
  current: Env | undefined;
  setEnv: (key: string) => void;
  find: (key: string | null | undefined) => Env | undefined;
  /** 环境显示名;找不到时回退 key */
  nameOf: (key: string | null | undefined) => string;
  /** 当前环境的下一个(没有则 undefined) */
  next: Env | undefined;
  reload: () => void;
}

const Ctx = createContext<EnvState | null>(null);
export function useEnv(): EnvState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useEnv 需要在 EnvProvider 内使用');
  return v;
}

export function EnvProvider({ children }: { children: ReactNode }) {
  const [sp, setSp] = useSearchParams();
  // /envs 失败(例如连的是 1.0 后端)按单环境处理,不打扰用户
  const { data, loading, reload: reloadEnvs } = useLoad(() => envApi.list(), [], { silent: true });
  const envs = useMemo(() => [...(data ?? [])].sort((a, b) => a.order - b.order), [data]);
  const multi = envs.length > 1;
  const urlEnv = sp.get('env');
  const env = resolveEnv(envs, urlEnv, readStoredEnv());

  // 把当前环境同步回 URL 与 localStorage
  useEffect(() => {
    if (!env) return;
    writeStoredEnv(env);
    if (urlEnv !== env) {
      setSp((prev) => {
        const n = new URLSearchParams(prev);
        n.set('env', env);
        return n;
      }, { replace: true });
    }
  }, [env, urlEnv, setSp]);

  const setEnv = useCallback((key: string) => {
    writeStoredEnv(key);
    setSp((prev) => {
      const n = new URLSearchParams(prev);
      n.set('env', key);
      return n;
    });
  }, [setSp]);

  const find = useCallback((key: string | null | undefined) => envs.find((e) => e.key === key), [envs]);
  const value = useMemo<EnvState>(() => {
    const idx = envs.findIndex((e) => e.key === env);
    return {
      envs, loading, multi, env,
      defaultKey: (envs.find((e) => e.default) ?? envs[0])?.key,
      current: idx >= 0 ? envs[idx] : undefined,
      setEnv, find,
      nameOf: (key) => (key ? envs.find((e) => e.key === key)?.name ?? key : ''),
      next: idx >= 0 ? envs[idx + 1] : undefined,
      reload: () => void reloadEnvs(),
    };
  }, [envs, loading, reloadEnvs, multi, env, setEnv, find]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
