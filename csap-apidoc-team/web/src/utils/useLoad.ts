import { useCallback, useEffect, useRef, useState } from 'react';
import { showError } from './errors';

/** 简单的异步加载 hook:自动处理 loading / 错误提示 / 竞态 */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[], opts: { silent?: boolean; skip?: boolean } = {}) {
  const [data, setData] = useState<T | undefined>();
  const [loading, setLoading] = useState(!opts.skip);
  const [error, setError] = useState<unknown>();
  const seq = useRef(0);
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const reload = useCallback(async () => {
    const my = ++seq.current;
    setLoading(true);
    setError(undefined);
    try {
      const r = await fnRef.current();
      if (my === seq.current) setData(r);
      return r;
    } catch (e) {
      if (my === seq.current) {
        setError(e);
        if (!opts.silent) showError(e);
      }
      return undefined;
    } finally {
      if (my === seq.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(() => {
    if (opts.skip) {
      setLoading(false);
      return;
    }
    void reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return { data, loading, error, reload, setData };
}
