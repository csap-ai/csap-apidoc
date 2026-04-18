/**
 * AuthStore — localStorage-backed CRUD for auth schemes + per-service bindings.
 *
 * A "scheme" is a reusable credential set (e.g. "Dev Bearer",
 * "Staging API-Key"). A "binding" maps a target service (identified by its
 * URL or stable id, the same key the Header service-scope uses) to one
 * scheme that should be applied automatically when the user switches to or
 * sends a request against that service.
 *
 * Sensitive fields live in the vault (`stores/vault.ts`) and are referenced
 * here by `vault:tok_xxxx` strings. The store itself never sees the
 * decoded secret value — that decoupling is what lets M6 swap the vault
 * for an AES-GCM-encrypted impl without touching this file.
 *
 * See docs/features/environment-auth-headers.md §5.3.
 */

import { vault } from './vault';
import { serviceRefIdFor } from './serviceRefId';

const STORAGE_KEY = 'csap-apidoc:auth-schemes';
const SCHEMA_VERSION = 1;

export type AuthSchemeType = 'bearer' | 'basic' | 'apikey' | 'oauth2_client';
export type ApiKeyIn = 'header' | 'query' | 'cookie';

export interface BearerConfig {
  tokenRef: string;
}

export interface BasicConfig {
  username: string;
  passwordRef: string;
}

export interface ApiKeyConfig {
  in: ApiKeyIn;
  name: string;
  valueRef: string;
}

export interface OAuth2ClientConfig {
  tokenUrl: string;
  clientId: string;
  clientSecretRef: string;
  scope?: string;
  /** Set after the first successful token fetch; refreshed on expiry. */
  cachedTokenRef?: string;
  /** Epoch ms when the cached access token expires. 0 / undefined = unknown / always re-fetch. */
  cachedExpiresAt?: number;
}

export type AuthSchemeConfig =
  | BearerConfig
  | BasicConfig
  | ApiKeyConfig
  | OAuth2ClientConfig;

export interface AuthScheme {
  id: string;
  name: string;
  type: AuthSchemeType;
  config: AuthSchemeConfig;
  /** Optional per-env credential overrides; keys are env ids. */
  envBindings?: Record<string, AuthSchemeConfig>;
  description?: string;
}

export interface AuthState {
  version: 1;
  /** serviceRefId → schemeId */
  activeBindings: Record<string, string>;
  items: AuthScheme[];
}

const DEFAULT_STATE: AuthState = {
  version: SCHEMA_VERSION,
  activeBindings: {},
  items: [],
};

function genId(): string {
  return 'scheme_' + Math.random().toString(36).slice(2, 10);
}

export function defaultConfigFor(type: AuthSchemeType): AuthSchemeConfig {
  switch (type) {
    case 'bearer':
      return { tokenRef: '' };
    case 'basic':
      return { username: '', passwordRef: '' };
    case 'apikey':
      return { in: 'header', name: 'X-API-Key', valueRef: '' };
    case 'oauth2_client':
      return {
        tokenUrl: '',
        clientId: '',
        clientSecretRef: '',
        scope: '',
      };
  }
}

function isValidScheme(value: unknown): value is AuthScheme {
  if (!value || typeof value !== 'object') return false;
  const s = value as Partial<AuthScheme>;
  return (
    typeof s.id === 'string' &&
    typeof s.name === 'string' &&
    (s.type === 'bearer' ||
      s.type === 'basic' ||
      s.type === 'apikey' ||
      s.type === 'oauth2_client') &&
    !!s.config &&
    typeof s.config === 'object'
  );
}

function safeRead(): AuthState {
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
      activeBindings: parsed.activeBindings ?? {},
      items: parsed.items.filter(isValidScheme),
    };
  } catch (err) {
    console.warn(
      '[csap-apidoc] failed to read auth-schemes from localStorage',
      err,
    );
    return { ...DEFAULT_STATE };
  }
}

function safeWrite(state: AuthState): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (err) {
    console.warn(
      '[csap-apidoc] failed to write auth-schemes to localStorage',
      err,
    );
  }
}

type Listener = (state: AuthState) => void;
const listeners = new Set<Listener>();
let cache: AuthState | null = null;

function getState(): AuthState {
  if (cache === null) cache = safeRead();
  return cache;
}

function setState(next: AuthState): void {
  cache = next;
  safeWrite(next);
  listeners.forEach((l) => l(next));
}

/** Walk a state tree and collect every `vault:tok_*` reference it holds. */
function collectVaultRefs(state: AuthState): Set<string> {
  const out = new Set<string>();
  const visit = (cfg: AuthSchemeConfig | undefined): void => {
    if (!cfg) return;
    for (const v of Object.values(cfg)) {
      if (typeof v === 'string' && vault.isVaultRef(v)) out.add(v);
    }
  };
  for (const s of state.items) {
    visit(s.config);
    if (s.envBindings) {
      for (const cfg of Object.values(s.envBindings)) visit(cfg);
    }
  }
  return out;
}

export interface AuthSchemeInput {
  name: string;
  type: AuthSchemeType;
  config?: AuthSchemeConfig;
  envBindings?: Record<string, AuthSchemeConfig>;
  description?: string;
}

export const authStore = {
  read(): AuthState {
    return getState();
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },

  add(input: AuthSchemeInput): AuthScheme {
    const scheme: AuthScheme = {
      id: genId(),
      name: input.name,
      type: input.type,
      config: input.config ?? defaultConfigFor(input.type),
      envBindings: input.envBindings,
      description: input.description,
    };
    const s = getState();
    setState({ ...s, items: [...s.items, scheme] });
    return scheme;
  },

  update(
    id: string,
    patch: Partial<Omit<AuthScheme, 'id'>>,
  ): AuthScheme | null {
    const s = getState();
    const idx = s.items.findIndex((x) => x.id === id);
    if (idx < 0) return null;
    const prev = s.items[idx];
    const next: AuthScheme = {
      ...prev,
      ...patch,
      id,
      // When the type changes, reset config to the default shape unless a
      // matching shape is supplied — this avoids type-mismatched configs
      // accidentally surviving a type switch in the form.
      config:
        patch.type && patch.type !== prev.type
          ? patch.config ?? defaultConfigFor(patch.type)
          : patch.config ?? prev.config,
    };
    const items = [...s.items];
    items[idx] = next;
    const nextState = { ...s, items };
    setState(nextState);
    vault.retainOnly(collectVaultRefs(nextState));
    return next;
  },

  remove(id: string): void {
    const s = getState();
    const items = s.items.filter((x) => x.id !== id);
    if (items.length === s.items.length) return;
    const activeBindings = { ...s.activeBindings };
    for (const [svc, schemeId] of Object.entries(activeBindings)) {
      if (schemeId === id) delete activeBindings[svc];
    }
    const nextState: AuthState = { ...s, items, activeBindings };
    setState(nextState);
    vault.retainOnly(collectVaultRefs(nextState));
  },

  bindToService(serviceRefId: string, schemeId: string | null): void {
    const s = getState();
    const activeBindings = { ...s.activeBindings };
    // Always store under the canonical key so subsequent lookups by raw
    // URL (with or without a trailing slash etc.) resolve identically.
    const canonical =
      serviceRefId === '*' ? '*' : serviceRefIdFor(serviceRefId) ?? serviceRefId;
    if (schemeId == null) {
      delete activeBindings[canonical];
      // Defensive: remove a stale legacy entry stored under the raw key.
      if (canonical !== serviceRefId) delete activeBindings[serviceRefId];
    } else {
      activeBindings[canonical] = schemeId;
      // Defensive: drop a stale legacy entry under the raw form so we
      // don't leave two competing rows for the same logical service.
      if (canonical !== serviceRefId) delete activeBindings[serviceRefId];
    }
    setState({ ...s, activeBindings });
  },

  /** Look up the active scheme for a service ref, falling back to a global default if any. */
  getActiveSchemeFor(serviceRefId: string | null | undefined): AuthScheme | null {
    const s = getState();
    const canonical = serviceRefIdFor(serviceRefId);
    if (canonical) {
      // Direct hit on the canonical key.
      let schemeId: string | undefined = s.activeBindings[canonical];
      // Pre-existing un-canonicalized data: scan and canonicalize on the fly.
      if (!schemeId) {
        for (const [k, v] of Object.entries(s.activeBindings)) {
          if (k === '*') continue;
          if (serviceRefIdFor(k) === canonical) {
            schemeId = v;
            break;
          }
        }
      }
      if (schemeId) {
        const scheme = s.items.find((x) => x.id === schemeId);
        if (scheme) return scheme;
      }
    }
    const wildcard = s.activeBindings['*'];
    if (wildcard) {
      return s.items.find((x) => x.id === wildcard) ?? null;
    }
    return null;
  },

  reset(): void {
    setState({ ...DEFAULT_STATE });
    vault.retainOnly(new Set());
  },
};

export const AUTH_STORAGE_KEY = STORAGE_KEY;
