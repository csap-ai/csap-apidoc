/**
 * EnvironmentContext — React binding around the EnvironmentStore singleton.
 *
 * Components consume `useEnvironments()` to get the full state and
 * mutators, or `useActiveEnvironment()` for the much more common
 * read-only case.
 */

import React, {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  useCallback,
} from 'react';
import {
  environmentStore,
  Environment,
  EnvironmentsState,
} from '@/stores/environmentStore';

interface EnvironmentContextValue {
  state: EnvironmentsState;
  active: Environment | null;
  add: typeof environmentStore.add;
  update: typeof environmentStore.update;
  remove: typeof environmentStore.remove;
  setActive: typeof environmentStore.setActive;
  suggestColor: typeof environmentStore.suggestColor;
}

const EnvironmentContext = createContext<EnvironmentContextValue | null>(null);

export const EnvironmentProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [state, setStateLocal] = useState<EnvironmentsState>(() =>
    environmentStore.read(),
  );

  useEffect(() => {
    return environmentStore.subscribe(setStateLocal);
  }, []);

  const add = useCallback(environmentStore.add.bind(environmentStore), []);
  const update = useCallback(environmentStore.update.bind(environmentStore), []);
  const remove = useCallback(environmentStore.remove.bind(environmentStore), []);
  const setActive = useCallback(
    environmentStore.setActive.bind(environmentStore),
    [],
  );
  const suggestColor = useCallback(
    environmentStore.suggestColor.bind(environmentStore),
    [],
  );

  const active = useMemo(
    () => state.items.find((e) => e.id === state.activeId) ?? null,
    [state.items, state.activeId],
  );

  const value = useMemo<EnvironmentContextValue>(
    () => ({ state, active, add, update, remove, setActive, suggestColor }),
    [state, active, add, update, remove, setActive, suggestColor],
  );

  return (
    <EnvironmentContext.Provider value={value}>
      {children}
    </EnvironmentContext.Provider>
  );
};

export function useEnvironments(): EnvironmentContextValue {
  const ctx = useContext(EnvironmentContext);
  if (!ctx) {
    throw new Error(
      'useEnvironments must be used within <EnvironmentProvider>',
    );
  }
  return ctx;
}

export function useActiveEnvironment(): Environment | null {
  return useEnvironments().active;
}
