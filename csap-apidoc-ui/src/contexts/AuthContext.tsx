/**
 * AuthContext — React binding around the AuthStore singleton + vault.
 *
 * Exposes:
 *   - state                    AuthState snapshot (re-renders on change)
 *   - vaultTick                bumps when vault entries change so consumers
 *                              (e.g. preview UIs) can re-render
 *   - add/update/remove        scheme CRUD
 *   - bindToService            (serviceRefId, schemeId | null)
 *   - getActiveSchemeFor(svc)  pick the scheme currently bound to a service
 *   - apply(svc)               async — full auth patch including OAuth2 token fetch
 *   - applySync(svc)           sync — patch from cached state only
 *
 * Sits inside <EnvironmentProvider> because applyAuth needs the active env
 * for variable expansion + per-env credential overrides.
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
  authStore,
  AuthScheme,
  AuthSchemeInput,
  AuthState,
  OAuth2ClientConfig,
} from '@/stores/authStore';
import {
  applyAuth,
  applyAuthSync,
  AuthApplyResult,
} from '@/stores/authResolver';
import { vault } from '@/stores/vault';
import { useActiveEnvironment } from '@/contexts/EnvironmentContext';

interface AuthContextValue {
  state: AuthState;
  /** Increments when the vault changes; consumers can use as a useMemo dep. */
  vaultTick: number;
  add: (input: AuthSchemeInput) => AuthScheme;
  update: (
    id: string,
    patch: Partial<Omit<AuthScheme, 'id'>>,
  ) => AuthScheme | null;
  remove: (id: string) => void;
  bindToService: (serviceRefId: string, schemeId: string | null) => void;
  getActiveSchemeFor: (
    serviceRefId: string | null | undefined,
  ) => AuthScheme | null;
  apply: (
    serviceRefId: string | null | undefined,
  ) => Promise<AuthApplyResult>;
  applySync: (serviceRefId: string | null | undefined) => AuthApplyResult;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [state, setStateLocal] = useState<AuthState>(() => authStore.read());
  const [vaultTick, setVaultTick] = useState(0);
  const env = useActiveEnvironment();

  useEffect(() => authStore.subscribe(setStateLocal), []);
  useEffect(
    () => vault.subscribe(() => setVaultTick((t) => t + 1)),
    [],
  );

  const add = useCallback(authStore.add.bind(authStore), []);
  const update = useCallback(authStore.update.bind(authStore), []);
  const remove = useCallback(authStore.remove.bind(authStore), []);
  const bindToService = useCallback(
    authStore.bindToService.bind(authStore),
    [],
  );

  const getActiveSchemeFor = useCallback(
    (serviceRefId: string | null | undefined) =>
      authStore.getActiveSchemeFor(serviceRefId),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [state.activeBindings, state.items],
  );

  const apply = useCallback(
    (serviceRefId: string | null | undefined) => {
      const scheme = authStore.getActiveSchemeFor(serviceRefId);
      return applyAuth(scheme, {
        env,
        onCacheUpdate: (schemeId, patch: Partial<OAuth2ClientConfig>) => {
          const cur = authStore
            .read()
            .items.find((s) => s.id === schemeId);
          if (!cur || cur.type !== 'oauth2_client') return;
          authStore.update(schemeId, {
            config: { ...(cur.config as OAuth2ClientConfig), ...patch },
          });
        },
      });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [state.activeBindings, state.items, env, vaultTick],
  );

  const applySync = useCallback(
    (serviceRefId: string | null | undefined) => {
      const scheme = authStore.getActiveSchemeFor(serviceRefId);
      return applyAuthSync(scheme, { env });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [state.activeBindings, state.items, env, vaultTick],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      vaultTick,
      add,
      update,
      remove,
      bindToService,
      getActiveSchemeFor,
      apply,
      applySync,
    }),
    [
      state,
      vaultTick,
      add,
      update,
      remove,
      bindToService,
      getActiveSchemeFor,
      apply,
      applySync,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within <AuthProvider>');
  }
  return ctx;
}
