/**
 * HeadersStore — localStorage-backed CRUD for scoped global headers.
 *
 * Three scopes, from broad to narrow:
 *   - global:      applies to every request
 *   - service:     applies only when the target service matches scopeRefId
 *   - environment: applies only when the active env id matches scopeRefId
 *
 * Narrower scopes override broader scopes on the same header key
 * (case-insensitive) — see `headersResolver.ts`.
 *
 * See docs/features/environment-auth-headers.md §5.2.
 *
 * Note: `isSecret` and the vault system arrive in M3/M6. For M2 the value
 * is always stored in plaintext under `value`.
 */

import { serviceRefIdFor } from './serviceRefId';

const STORAGE_KEY = 'csap-apidoc:headers';
const SCHEMA_VERSION = 1;

export type HeaderScope = 'global' | 'service' | 'environment';

export interface HeaderRule {
  id: string;
  scope: HeaderScope;
  /** For scope=service: service URL/id. For scope=environment: envId. Null for global. */
  scopeRefId: string | null;
  key: string;
  value: string;
  enabled: boolean;
  description?: string;
}

export interface HeadersState {
  version: 1;
  items: HeaderRule[];
}

const DEFAULT_STATE: HeadersState = {
  version: SCHEMA_VERSION,
  items: [],
};

function genId(): string {
  return 'h_' + Math.random().toString(36).slice(2, 10);
}

function isValidRule(value: unknown): value is HeaderRule {
  if (!value || typeof value !== 'object') return false;
  const r = value as Partial<HeaderRule>;
  return (
    typeof r.id === 'string' &&
    (r.scope === 'global' || r.scope === 'service' || r.scope === 'environment') &&
    (r.scopeRefId === null || typeof r.scopeRefId === 'string') &&
    typeof r.key === 'string' &&
    typeof r.value === 'string' &&
    typeof r.enabled === 'boolean'
  );
}

function safeRead(): HeadersState {
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
      items: parsed.items.filter(isValidRule),
    };
  } catch (err) {
    console.warn('[csap-apidoc] failed to read headers from localStorage', err);
    return { ...DEFAULT_STATE };
  }
}

function safeWrite(state: HeadersState): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (err) {
    console.warn('[csap-apidoc] failed to write headers to localStorage', err);
  }
}

type Listener = (state: HeadersState) => void;
const listeners = new Set<Listener>();
let cache: HeadersState | null = null;

function getState(): HeadersState {
  if (cache === null) cache = safeRead();
  return cache;
}

function setState(next: HeadersState): void {
  cache = next;
  safeWrite(next);
  listeners.forEach((l) => l(next));
}

export interface HeaderRuleInput {
  scope: HeaderScope;
  scopeRefId?: string | null;
  key: string;
  value: string;
  enabled?: boolean;
  description?: string;
}

export const headersStore = {
  read(): HeadersState {
    return getState();
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },

  add(input: HeaderRuleInput): HeaderRule {
    const rawScopeRef =
      input.scope === 'global' ? null : input.scopeRefId ?? null;
    const rule: HeaderRule = {
      id: genId(),
      scope: input.scope,
      // Service bindings live under the canonical key so trailing-slash /
      // host-case variants don't fragment the lookup. Env / global use the
      // raw value (env id is already canonical, global is always null).
      scopeRefId:
        input.scope === 'service' ? serviceRefIdFor(rawScopeRef) : rawScopeRef,
      key: input.key,
      value: input.value,
      enabled: input.enabled ?? true,
      description: input.description,
    };
    const s = getState();
    setState({ ...s, items: [...s.items, rule] });
    return rule;
  },

  update(
    id: string,
    patch: Partial<Omit<HeaderRule, 'id'>>,
  ): HeaderRule | null {
    const s = getState();
    const idx = s.items.findIndex((h) => h.id === id);
    if (idx < 0) return null;
    const next: HeaderRule = { ...s.items[idx], ...patch, id };
    if (next.scope === 'global') {
      next.scopeRefId = null;
    } else if (next.scope === 'service') {
      next.scopeRefId = serviceRefIdFor(next.scopeRefId);
    }
    const items = [...s.items];
    items[idx] = next;
    setState({ ...s, items });
    return next;
  },

  remove(id: string): void {
    const s = getState();
    setState({ ...s, items: s.items.filter((h) => h.id !== id) });
  },

  /** Remove every rule that references a now-deleted env / service id. */
  removeByScopeRef(scope: HeaderScope, scopeRefId: string): void {
    const s = getState();
    // Match canonical-vs-stored for service scope so callers that pass the
    // raw URL (or a canonical form) both clean up legacy entries.
    const targetCanonical =
      scope === 'service' ? serviceRefIdFor(scopeRefId) : scopeRefId;
    const items = s.items.filter((h) => {
      if (h.scope !== scope) return true;
      if (scope === 'service') {
        return serviceRefIdFor(h.scopeRefId) !== targetCanonical;
      }
      return h.scopeRefId !== scopeRefId;
    });
    if (items.length !== s.items.length) setState({ ...s, items });
  },

  reset(): void {
    setState({ ...DEFAULT_STATE });
  },
};

export const HEADERS_STORAGE_KEY = STORAGE_KEY;
