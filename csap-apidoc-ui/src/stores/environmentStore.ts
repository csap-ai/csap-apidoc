/**
 * EnvironmentStore — localStorage-backed CRUD for environments.
 *
 * Browser-local persistence only. No backend, no accounts.
 * See docs/features/environment-auth-headers.md §5.1.
 */

const STORAGE_KEY = 'csap-apidoc:environments';
const SCHEMA_VERSION = 1;

export interface Environment {
  id: string;
  name: string;
  color: string;
  baseUrl: string;
  isDefault?: boolean;
  variables: Record<string, string>;
}

export interface EnvironmentsState {
  version: 1;
  activeId: string | null;
  items: Environment[];
}

const DEFAULT_STATE: EnvironmentsState = {
  version: SCHEMA_VERSION,
  activeId: null,
  items: [],
};

const PRESET_COLORS = [
  '#52c41a',
  '#fa8c16',
  '#f5222d',
  '#1890ff',
  '#722ed1',
  '#13c2c2',
  '#eb2f96',
];

function genId(): string {
  return 'env_' + Math.random().toString(36).slice(2, 10);
}

function safeRead(): EnvironmentsState {
  if (typeof window === 'undefined') return { ...DEFAULT_STATE };
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_STATE };
    const parsed = JSON.parse(raw);
    if (parsed?.version !== SCHEMA_VERSION || !Array.isArray(parsed.items)) {
      return { ...DEFAULT_STATE };
    }
    return {
      version: SCHEMA_VERSION,
      activeId: typeof parsed.activeId === 'string' ? parsed.activeId : null,
      items: parsed.items.filter(isValidEnvironment),
    };
  } catch (err) {
    console.warn('[csap-apidoc] failed to read environments from localStorage', err);
    return { ...DEFAULT_STATE };
  }
}

function safeWrite(state: EnvironmentsState): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (err) {
    console.warn('[csap-apidoc] failed to write environments to localStorage', err);
  }
}

function isValidEnvironment(value: unknown): value is Environment {
  if (!value || typeof value !== 'object') return false;
  const e = value as Partial<Environment>;
  return (
    typeof e.id === 'string' &&
    typeof e.name === 'string' &&
    typeof e.baseUrl === 'string' &&
    typeof e.color === 'string'
  );
}

type Listener = (state: EnvironmentsState) => void;
const listeners = new Set<Listener>();
let cache: EnvironmentsState | null = null;

function getState(): EnvironmentsState {
  if (cache === null) cache = safeRead();
  return cache;
}

function setState(next: EnvironmentsState): void {
  cache = next;
  safeWrite(next);
  listeners.forEach((l) => l(next));
}

export const environmentStore = {
  /** Get the current state (synchronous, cached). */
  read(): EnvironmentsState {
    return getState();
  },

  /** Subscribe to state changes. Returns an unsubscribe fn. */
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },

  /** Get the currently active environment, or null. */
  getActive(): Environment | null {
    const s = getState();
    if (!s.activeId) return null;
    return s.items.find((e) => e.id === s.activeId) ?? null;
  },

  /** Add a new environment. Returns the created entity. */
  add(input: Omit<Environment, 'id'>): Environment {
    const env: Environment = {
      id: genId(),
      ...input,
      variables: input.variables ?? {},
    };
    const s = getState();
    const next: EnvironmentsState = {
      ...s,
      items: [...s.items, env],
      activeId: s.activeId ?? env.id,
    };
    setState(next);
    return env;
  },

  /** Update an existing environment by id. */
  update(id: string, patch: Partial<Omit<Environment, 'id'>>): Environment | null {
    const s = getState();
    const idx = s.items.findIndex((e) => e.id === id);
    if (idx < 0) return null;
    const updated: Environment = { ...s.items[idx], ...patch, id };
    const items = [...s.items];
    items[idx] = updated;
    setState({ ...s, items });
    return updated;
  },

  /** Remove an environment. If it was active, activeId is reset. */
  remove(id: string): void {
    const s = getState();
    const items = s.items.filter((e) => e.id !== id);
    const activeId = s.activeId === id ? items[0]?.id ?? null : s.activeId;
    setState({ ...s, items, activeId });
  },

  /** Switch the active environment. */
  setActive(id: string | null): void {
    const s = getState();
    if (id !== null && !s.items.some((e) => e.id === id)) return;
    setState({ ...s, activeId: id });
  },

  /** Suggest the next color from the preset palette, avoiding duplicates. */
  suggestColor(): string {
    const s = getState();
    const used = new Set(s.items.map((e) => e.color));
    return PRESET_COLORS.find((c) => !used.has(c)) ?? PRESET_COLORS[0];
  },

  /** Reset to empty state — used by tests / settings 'reset all'. */
  reset(): void {
    setState({ ...DEFAULT_STATE });
  },
};

export const ENV_STORAGE_KEY = STORAGE_KEY;
export const ENV_PRESET_COLORS = PRESET_COLORS;
