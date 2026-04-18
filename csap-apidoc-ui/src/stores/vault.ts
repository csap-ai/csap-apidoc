/**
 * Vault — opaque-reference storage for sensitive strings.
 *
 * Sensitive fields (bearer tokens, basic-auth passwords, api-key values,
 * oauth2 client secrets, cached oauth2 access tokens) never live in the
 * primary `auth-schemes` payload. Instead each call site holds a
 * `vault:<id>` reference and dereferences it through this module right
 * before sending the request.
 *
 * M3 ships **plaintext mode only** — the secrets sit in
 * `localStorage[csap-apidoc:vault]` as a flat `{ id: value }` map. This
 * keeps the wire-format and consumer API identical to the M6 encrypted
 * variant, so M6 can swap the storage shape without touching authStore /
 * authResolver / any UI form. The migration path is documented in §5.4 of
 * docs/features/environment-auth-headers.md.
 *
 * Public API:
 *   vault.put(value, existingRef?)   → "vault:tok_xxxx"
 *   vault.get(ref)                   → string | null
 *   vault.remove(ref)                → void
 *   vault.isVaultRef(s)              → boolean
 *   vault.subscribe(fn)              → unsubscribe
 */

const STORAGE_KEY = 'csap-apidoc:vault';
const SCHEMA_VERSION = 1;
const REF_PREFIX = 'vault:';

export type VaultRef = `${typeof REF_PREFIX}${string}`;

export interface PlaintextVaultState {
  version: 1;
  encrypted: false;
  items: Record<string, string>;
}

const DEFAULT_STATE: PlaintextVaultState = {
  version: SCHEMA_VERSION,
  encrypted: false,
  items: {},
};

function genId(): string {
  return 'tok_' + Math.random().toString(36).slice(2, 10);
}

function isPlaintextState(value: unknown): value is PlaintextVaultState {
  if (!value || typeof value !== 'object') return false;
  const v = value as Partial<PlaintextVaultState>;
  return (
    v.version === SCHEMA_VERSION &&
    v.encrypted === false &&
    !!v.items &&
    typeof v.items === 'object'
  );
}

function safeRead(): PlaintextVaultState {
  if (typeof window === 'undefined') return { ...DEFAULT_STATE, items: {} };
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_STATE, items: {} };
    const parsed = JSON.parse(raw);
    if (!isPlaintextState(parsed)) {
      // M6 encrypted-mode payloads are intentionally NOT decoded here. Once
      // M6 lands it will install its own driver before vault.* is called.
      console.warn(
        '[csap-apidoc] vault payload is not in plaintext mode; ignoring',
      );
      return { ...DEFAULT_STATE, items: {} };
    }
    return {
      version: SCHEMA_VERSION,
      encrypted: false,
      items: { ...parsed.items },
    };
  } catch (err) {
    console.warn('[csap-apidoc] failed to read vault from localStorage', err);
    return { ...DEFAULT_STATE, items: {} };
  }
}

function safeWrite(state: PlaintextVaultState): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (err) {
    console.warn('[csap-apidoc] failed to write vault to localStorage', err);
  }
}

type Listener = () => void;
const listeners = new Set<Listener>();
let cache: PlaintextVaultState | null = null;

function getState(): PlaintextVaultState {
  if (cache === null) cache = safeRead();
  return cache;
}

function setState(next: PlaintextVaultState): void {
  cache = next;
  safeWrite(next);
  listeners.forEach((l) => l());
}

function refToId(ref: string): string | null {
  if (!ref.startsWith(REF_PREFIX)) return null;
  const id = ref.slice(REF_PREFIX.length);
  return id || null;
}

export const vault = {
  /**
   * Store (or update) a secret value.
   *
   * Pass `existingRef` to overwrite an existing slot in place — important so
   * editing a Bearer token in the UI doesn't leak orphan slots into the
   * vault on every keystroke.
   */
  put(value: string, existingRef?: string | null): VaultRef {
    const s = getState();
    let id = existingRef ? refToId(existingRef) : null;
    if (!id || !Object.prototype.hasOwnProperty.call(s.items, id)) {
      id = genId();
    }
    setState({ ...s, items: { ...s.items, [id]: value } });
    return (REF_PREFIX + id) as VaultRef;
  },

  /** Returns the stored secret, or `null` for unknown / malformed refs. */
  get(ref: string | null | undefined): string | null {
    if (!ref) return null;
    const id = refToId(ref);
    if (!id) return null;
    const s = getState();
    return Object.prototype.hasOwnProperty.call(s.items, id) ? s.items[id] : null;
  },

  /** Drops a secret. Safe to call with unknown refs. */
  remove(ref: string | null | undefined): void {
    if (!ref) return;
    const id = refToId(ref);
    if (!id) return;
    const s = getState();
    if (!Object.prototype.hasOwnProperty.call(s.items, id)) return;
    const next = { ...s.items };
    delete next[id];
    setState({ ...s, items: next });
  },

  isVaultRef(s: unknown): s is VaultRef {
    return typeof s === 'string' && s.startsWith(REF_PREFIX);
  },

  /**
   * Garbage-collect any vault entries no longer referenced by the auth
   * store. Called by authStore on scheme delete / type change. Caller
   * supplies the live ref set so vault stays decoupled from authStore.
   */
  retainOnly(liveRefs: Set<string>): void {
    const s = getState();
    const next: Record<string, string> = {};
    for (const id of Object.keys(s.items)) {
      if (liveRefs.has(REF_PREFIX + id)) next[id] = s.items[id];
    }
    if (Object.keys(next).length === Object.keys(s.items).length) return;
    setState({ ...s, items: next });
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },

  /** Mainly for unit tests / Settings → Reset. */
  reset(): void {
    setState({ ...DEFAULT_STATE, items: {} });
  },
};

export const VAULT_STORAGE_KEY = STORAGE_KEY;
