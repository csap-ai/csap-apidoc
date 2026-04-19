/**
 * Vault — opaque-reference storage for sensitive strings.
 *
 * Sensitive fields (bearer tokens, basic-auth passwords, api-key values,
 * oauth2 client secrets, cached oauth2 access tokens) never live in the
 * primary `auth-schemes` payload. Instead each call site holds a
 * `vault:<id>` reference and dereferences it through this module right
 * before sending the request.
 *
 * Two storage backends ("drivers") implement the same interface:
 *   - PlaintextDriver — M3 default; values land in `csap-apidoc:vault`.
 *   - EncryptedDriver — M6; values land in `csap-apidoc:vault.encrypted`
 *     under AES-GCM, keyed by a PBKDF2-derived master password key that
 *     never leaves browser memory.
 *
 * The driver swap is orchestrated by `VaultContext` at startup (and on
 * Settings → enable/disable encryption). Consumers (`authStore`,
 * `authResolver`, `AuthSchemesDrawer`) keep using the same `vault.put` /
 * `vault.get` surface — the only new behaviour they may observe is
 * `vault.get(...)` returning `null` when the encrypted vault is currently
 * locked. A custom event `csap-apidoc:vault-locked-access` fires on the
 * window in that case so a UI banner can prompt the user to unlock.
 *
 * Public API (unchanged from M3, plus `getDriverState`):
 *   vault.put(value, existingRef?)   → "vault:tok_xxxx"
 *   vault.get(ref)                   → string | null
 *   vault.remove(ref)                → void
 *   vault.isVaultRef(s)              → boolean
 *   vault.subscribe(fn)              → unsubscribe
 *   vault.retainOnly(refs)           → void
 *   vault.reset()                    → void
 *   vault.getDriverState()           → 'plaintext' | 'encrypted-locked' | 'encrypted-unlocked'
 *   vault.__installDriver(driver)    → @internal — used by VaultContext
 */

import {
  PlaintextDriver,
  EncryptedDriver,
  NoopDriver,
  VaultDriver,
  VaultRef,
  isVaultRef as driverIsVaultRef,
  hasEncryptedVaultData,
  readEncryptedHeader,
  decodeSalt,
  PLAINTEXT_STORAGE_KEY,
  ENCRYPTED_STORAGE_KEY,
} from './vaultDriver';

export type { VaultRef } from './vaultDriver';
export const VAULT_STORAGE_KEY = PLAINTEXT_STORAGE_KEY;
export const VAULT_ENCRYPTED_STORAGE_KEY = ENCRYPTED_STORAGE_KEY;

export type VaultDriverState =
  | 'plaintext'
  | 'encrypted-locked'
  | 'encrypted-unlocked';

export const VAULT_LOCKED_ACCESS_EVENT = 'csap-apidoc:vault-locked-access';

/**
 * Initial driver — picked synchronously at module load so existing
 * consumers don't see a temporarily-empty vault before the React
 * VaultProvider mounts.
 *
 * Heuristic:
 *   - If encrypted storage exists, install a LOCKED encrypted shell. The
 *     driver carries the salt + iterations from disk so VaultContext can
 *     unlock it later without re-reading the file. We don't have the user's
 *     password here so plaintext stays empty until unlock.
 *   - Otherwise, install the plaintext driver.
 *
 * SSR safety: when window is undefined we install a NoopDriver. The
 * VaultProvider will swap in a real driver on hydration.
 */
function pickInitialDriver(): VaultDriver {
  if (typeof window === 'undefined') return new NoopDriver();
  try {
    if (hasEncryptedVaultData()) {
      const header = readEncryptedHeader();
      if (header) {
        return new EncryptedDriver({
          key: null,
          salt: decodeSalt(header.salt),
          iterations: header.iterations,
        });
      }
    }
  } catch (err) {
    console.warn('[csap-apidoc] vault initial driver pick failed', err);
  }
  return new PlaintextDriver();
}

let driver: VaultDriver = pickInitialDriver();

type Listener = () => void;
const listeners = new Set<Listener>();

function notify(): void {
  listeners.forEach((l) => {
    try {
      l();
    } catch (err) {
      console.warn('[csap-apidoc] vault listener threw', err);
    }
  });
}

function emitLockedAccess(ref: string | null | undefined): void {
  if (typeof window === 'undefined') return;
  try {
    window.dispatchEvent(
      new CustomEvent(VAULT_LOCKED_ACCESS_EVENT, { detail: { ref } }),
    );
  } catch {
    /* CustomEvent may not exist in some test envs; non-fatal. */
  }
}

function getDriverState(): VaultDriverState {
  if (driver.mode === 'plaintext') return 'plaintext';
  return driver.isLocked() ? 'encrypted-locked' : 'encrypted-unlocked';
}

export const vault = {
  /**
   * Store (or update) a secret value.
   *
   * Pass `existingRef` to overwrite an existing slot in place — important so
   * editing a Bearer token in the UI doesn't leak orphan slots into the
   * vault on every keystroke.
   *
   * When the vault is encrypted-locked this is a no-op that returns a
   * sentinel ref (or `existingRef` if supplied). UI should disable Save
   * buttons in that state; this is just the safety net.
   */
  put(value: string, existingRef?: string | null): VaultRef {
    const wasLocked = driver.isLocked();
    const ref = driver.put(value, existingRef);
    if (!wasLocked) notify();
    else emitLockedAccess(existingRef ?? null);
    return ref;
  },

  /** Returns the stored secret, or `null` for unknown / malformed refs / locked vault. */
  get(ref: string | null | undefined): string | null {
    if (!ref) return null;
    if (driver.isLocked()) {
      emitLockedAccess(ref);
      return null;
    }
    return driver.get(ref);
  },

  /** Drops a secret. Safe to call with unknown refs. */
  remove(ref: string | null | undefined): void {
    if (!ref) return;
    driver.remove(ref);
    notify();
  },

  isVaultRef(s: unknown): s is VaultRef {
    return driverIsVaultRef(s);
  },

  /**
   * Garbage-collect any vault entries no longer referenced by the auth
   * store. Called by authStore on scheme delete / type change. Caller
   * supplies the live ref set so vault stays decoupled from authStore.
   */
  retainOnly(liveRefs: Set<string>): void {
    driver.retainOnly(liveRefs);
    notify();
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },

  /** Mainly for unit tests / Settings → Reset. */
  reset(): void {
    if (driver.mode === 'plaintext') {
      driver = new PlaintextDriver({});
    } else {
      // For encrypted mode, hydrate-empty would require the key. The
      // resetAll() flow in VaultContext blows away storage and rebuilds
      // a fresh PlaintextDriver instead.
      driver = new PlaintextDriver({});
      try {
        if (typeof window !== 'undefined') {
          window.localStorage.removeItem(ENCRYPTED_STORAGE_KEY);
        }
      } catch {
        /* noop */
      }
    }
    notify();
  },

  /** Current driver state (for the lock banner / settings UI). */
  getDriverState,

  /**
   * @internal — used by VaultContext to swap drivers (lock / unlock /
   * enable encryption / disable encryption / import). Not part of the
   * stable public API; do not call from feature code.
   */
  __installDriver(next: VaultDriver): void {
    driver = next;
    notify();
  },

  /**
   * @internal — handle to the live driver for advanced operations
   * (e.g. EncryptedDriver.flushNow during migration). Not for general use.
   */
  __getDriver(): VaultDriver {
    return driver;
  },
};
