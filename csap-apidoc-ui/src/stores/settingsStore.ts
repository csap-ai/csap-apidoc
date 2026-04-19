/**
 * SettingsStore — localStorage-backed user preferences.
 *
 * Schema follows §5.5 of docs/features/environment-auth-headers.md but
 * flattens the nested `tryItOut` object so the M5 try-it-out panel and the
 * M6 vault context can read individual fields without re-walking a tree.
 *
 * Browser-local only. No backend, no accounts.
 */

const STORAGE_KEY = 'csap-apidoc:settings';
const SCHEMA_VERSION = 1;

export type VaultMode = 'plaintext' | 'encrypted';

export interface SettingsState {
  version: 1;
  /** Which vault driver is in effect on next page load. */
  vaultMode: VaultMode;
  /** Auto-lock idle timeout in minutes. 0 = never auto-lock. */
  vaultLockTimeoutMin: number;
  /** Optional CORS proxy URL for try-it-out (M5). */
  tryItOutProxyUrl: string | null;
  /** Try-it-out request timeout in ms. */
  tryItOutTimeoutMs: number;
  /**
   * Whether Try-it-out should set `withCredentials` on the underlying
   * XHR/fetch so cross-origin requests carry the user's cookies (and any
   * cookie-bound API key from an Auth scheme is actually honoured by the
   * browser). Default `false` because enabling this globally is a
   * privacy footgun — the user must opt in per workstation.
   */
  tryItOutWithCredentials: boolean;
  /** UI language. M8 wires actual i18n; M6 just persists the preference. */
  language: string;
}

export const DEFAULT_SETTINGS: SettingsState = {
  version: SCHEMA_VERSION,
  vaultMode: 'plaintext',
  vaultLockTimeoutMin: 30,
  tryItOutProxyUrl: null,
  tryItOutTimeoutMs: 30_000,
  tryItOutWithCredentials: false,
  language: 'zh-CN',
};

function isValidSettings(value: unknown): value is SettingsState {
  if (!value || typeof value !== 'object') return false;
  const s = value as Partial<SettingsState>;
  return (
    s.version === SCHEMA_VERSION &&
    (s.vaultMode === 'plaintext' || s.vaultMode === 'encrypted') &&
    typeof s.vaultLockTimeoutMin === 'number' &&
    typeof s.tryItOutTimeoutMs === 'number' &&
    typeof s.language === 'string' &&
    (s.tryItOutProxyUrl === null || typeof s.tryItOutProxyUrl === 'string') &&
    // tryItOutWithCredentials added later; absence is OK (DEFAULT_SETTINGS
    // backfills via the {...defaults, ...parsed} merge in safeRead).
    (s.tryItOutWithCredentials === undefined ||
      typeof s.tryItOutWithCredentials === 'boolean')
  );
}

function safeRead(): SettingsState {
  if (typeof window === 'undefined') return { ...DEFAULT_SETTINGS };
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_SETTINGS };
    const parsed = JSON.parse(raw);
    if (!isValidSettings(parsed)) return { ...DEFAULT_SETTINGS };
    // Re-apply defaults for any fields a future version might add.
    return { ...DEFAULT_SETTINGS, ...parsed, version: SCHEMA_VERSION };
  } catch (err) {
    console.warn('[csap-apidoc] failed to read settings from localStorage', err);
    return { ...DEFAULT_SETTINGS };
  }
}

function safeWrite(state: SettingsState): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (err) {
    console.warn('[csap-apidoc] failed to write settings to localStorage', err);
  }
}

type Listener = (state: SettingsState) => void;
const listeners = new Set<Listener>();
let cache: SettingsState | null = null;

function getState(): SettingsState {
  if (cache === null) cache = safeRead();
  return cache;
}

function setState(next: SettingsState): void {
  cache = next;
  safeWrite(next);
  listeners.forEach((l) => {
    try {
      l(next);
    } catch (err) {
      console.warn('[csap-apidoc] settings listener threw', err);
    }
  });
}

export const settingsStore = {
  getState(): SettingsState {
    return getState();
  },

  /** Shallow-merge a patch into the current state. */
  update(patch: Partial<Omit<SettingsState, 'version'>>): SettingsState {
    const next: SettingsState = {
      ...getState(),
      ...patch,
      version: SCHEMA_VERSION,
    };
    setState(next);
    return next;
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },

  /** Replace the whole state (used by import). */
  replace(state: SettingsState): SettingsState {
    const sane: SettingsState = {
      ...DEFAULT_SETTINGS,
      ...state,
      version: SCHEMA_VERSION,
    };
    setState(sane);
    return sane;
  },

  reset(): void {
    setState({ ...DEFAULT_SETTINGS });
  },
};

export const SETTINGS_STORAGE_KEY = STORAGE_KEY;
