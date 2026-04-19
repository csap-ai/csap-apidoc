/**
 * HeadersContext — React binding around the HeadersStore singleton.
 *
 * Exposes state + CRUD + a `resolve(serviceRefId)` helper that produces
 * the merged header map for a given request context. The active env is
 * read from EnvironmentContext, so HeadersProvider must be rendered
 * inside EnvironmentProvider.
 */

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import {
  headersStore,
  HeaderRule,
  HeadersState,
  HeaderRuleInput,
} from '@/stores/headersStore';
import { mergeHeaders, explainActiveRules } from '@/stores/headersResolver';
import { useActiveEnvironment } from '@/contexts/EnvironmentContext';

interface HeadersContextValue {
  state: HeadersState;
  add: (input: HeaderRuleInput) => HeaderRule;
  update: (
    id: string,
    patch: Partial<Omit<HeaderRule, 'id'>>,
  ) => HeaderRule | null;
  remove: (id: string) => void;
  /**
   * Merged, variable-expanded headers for the given request context.
   * Callers (M5) pass the current service ref; env is pulled from the
   * active environment automatically.
   */
  resolve: (args?: {
    serviceRefId?: string | null;
    endpointHeaders?: Record<string, string>;
  }) => Record<string, string>;
  /** Enabled rules that would apply to the given context (for UI badges / diagnostics). */
  explain: (args?: {
    serviceRefId?: string | null;
  }) => HeaderRule[];
}

const HeadersContext = createContext<HeadersContextValue | null>(null);

export const HeadersProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [state, setStateLocal] = useState<HeadersState>(() =>
    headersStore.read(),
  );
  const env = useActiveEnvironment();

  useEffect(() => {
    return headersStore.subscribe(setStateLocal);
  }, []);

  const add = useCallback(headersStore.add.bind(headersStore), []);
  const update = useCallback(headersStore.update.bind(headersStore), []);
  const remove = useCallback(headersStore.remove.bind(headersStore), []);

  const resolve = useCallback(
    (args?: {
      serviceRefId?: string | null;
      endpointHeaders?: Record<string, string>;
    }) =>
      mergeHeaders({
        rules: state.items,
        serviceRefId: args?.serviceRefId ?? null,
        envId: env?.id ?? null,
        endpointHeaders: args?.endpointHeaders,
        env,
      }),
    [state.items, env],
  );

  const explain = useCallback(
    (args?: { serviceRefId?: string | null }) =>
      explainActiveRules({
        rules: state.items,
        serviceRefId: args?.serviceRefId ?? null,
        envId: env?.id ?? null,
      }),
    [state.items, env],
  );

  const value = useMemo<HeadersContextValue>(
    () => ({ state, add, update, remove, resolve, explain }),
    [state, add, update, remove, resolve, explain],
  );

  return (
    <HeadersContext.Provider value={value}>{children}</HeadersContext.Provider>
  );
};

export function useHeaders(): HeadersContextValue {
  const ctx = useContext(HeadersContext);
  if (!ctx) {
    throw new Error('useHeaders must be used within <HeadersProvider>');
  }
  return ctx;
}
