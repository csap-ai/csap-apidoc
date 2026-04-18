/**
 * VaultContext — top-level orchestrator for the M6 vault state machine.
 *
 * Three states:
 *   - 'plaintext'          → settings.vaultMode === 'plaintext'
 *   - 'encrypted-locked'   → vaultMode === 'encrypted' and no key in memory
 *   - 'encrypted-unlocked' → vaultMode === 'encrypted' and key cached
 *
 * Sits as the OUTERMOST provider in App.tsx because:
 *   1. The lock banner / settings drawer are global UI surfaces.
 *   2. Auth / Headers / Environment can stay vault-agnostic — they just
 *      call vault.put/get and the driver layer hides the encryption.
 *
 * Idle auto-lock is implemented with a coarse 30s tick + a debounced
 * activity timestamp so we don't burn battery polling on every mousemove.
 *
 * Export / import / reset live here because they need the whole-app view
 * (env + headers + auth + settings + vault) and they have to coordinate
 * the state-machine reload after a destructive write.
 */

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  vault,
  VaultDriverState,
} from '@/stores/vault';
import {
  PlaintextDriver,
  EncryptedDriver,
  hasEncryptedVaultData,
  readEncryptedHeader,
  readPlaintextItems,
  clearPlaintextStorage,
  clearEncryptedStorage,
  decodeSalt,
} from '@/stores/vaultDriver';
import {
  deriveKey,
  randomSalt,
  PBKDF2_ITERATIONS,
  verifyPasswordAgainstAnyBlob,
} from '@/stores/vaultCrypto';
import {
  settingsStore,
  SettingsState,
  DEFAULT_SETTINGS,
  SETTINGS_STORAGE_KEY,
} from '@/stores/settingsStore';
import { environmentStore, ENV_STORAGE_KEY } from '@/stores/environmentStore';
import { headersStore, HEADERS_STORAGE_KEY } from '@/stores/headersStore';
import { authStore, AUTH_STORAGE_KEY } from '@/stores/authStore';
// banner is now mounted inside Layout — see layouts/index.tsx
const ACTIVITY_EVENTS = ['mousemove', 'keydown', 'click'] as const;
const ACTIVITY_DEBOUNCE_MS = 5_000;
const IDLE_TICK_MS = 30_000;

interface ExportPayload {
  version: 1;
  exportedAt: string;
  vaultMode: 'plaintext' | 'encrypted';
  /** Only present when the vault was unlocked (or in plaintext mode) at export time. */
  vaultIncluded: boolean;
  environments?: unknown;
  headers?: unknown;
  authSchemes?: unknown;
  settings?: unknown;
  /** Plaintext vault items, only when vaultIncluded === true. */
  vault?: { items: Record<string, string> };
}

interface VaultContextValue {
  state: VaultDriverState;
  hasEncryptedData: boolean;
  setMasterPassword: (pwd: string) => Promise<void>;
  enableEncryption: (pwd: string) => Promise<void>;
  disableEncryption: (pwd: string) => Promise<void>;
  changePassword: (oldPwd: string, newPwd: string) => Promise<boolean>;
  unlock: (pwd: string) => Promise<boolean>;
  lock: () => void;
  exportConfig: () => string;
  importConfig: (json: string) => void;
  resetAll: () => void;
}

const VaultContext = createContext<VaultContextValue | null>(null);

function readAllStorageKeys(): string[] {
  if (typeof window === 'undefined') return [];
  const out: string[] = [];
  for (let i = 0; i < window.localStorage.length; i++) {
    const k = window.localStorage.key(i);
    if (k && k.startsWith('csap-apidoc:')) out.push(k);
  }
  return out;
}

export const VaultProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [state, setStateLocal] = useState<VaultDriverState>(() =>
    vault.getDriverState(),
  );
  const [hasEncryptedData, setHasEncryptedData] = useState<boolean>(() =>
    typeof window !== 'undefined' ? hasEncryptedVaultData() : false,
  );
  const [settings, setSettings] = useState<SettingsState>(() =>
    settingsStore.getState(),
  );
  const lastActivityRef = useRef<number>(Date.now());
  const debounceTimerRef = useRef<number | null>(null);

  /* ---------------- driver-state subscription ---------------- */

  useEffect(() => {
    const refresh = (): void => {
      setStateLocal(vault.getDriverState());
      setHasEncryptedData(hasEncryptedVaultData());
    };
    return vault.subscribe(refresh);
  }, []);

  useEffect(() => settingsStore.subscribe(setSettings), []);

  /* ---------------- idle auto-lock ---------------- */

  useEffect(() => {
    if (typeof window === 'undefined') return undefined;
    const bump = (): void => {
      const now = Date.now();
      if (debounceTimerRef.current !== null) return;
      debounceTimerRef.current = window.setTimeout(() => {
        lastActivityRef.current = Date.now();
        debounceTimerRef.current = null;
      }, ACTIVITY_DEBOUNCE_MS);
      // First call also updates immediately so the *first* event after a long
      // idle period still resets the clock instead of waiting for the
      // debounce window.
      lastActivityRef.current = now;
    };
    for (const ev of ACTIVITY_EVENTS) {
      window.addEventListener(ev, bump, { passive: true });
    }
    return () => {
      for (const ev of ACTIVITY_EVENTS) {
        window.removeEventListener(ev, bump);
      }
      if (debounceTimerRef.current !== null) {
        window.clearTimeout(debounceTimerRef.current);
        debounceTimerRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    const minutes = settings.vaultLockTimeoutMin;
    if (minutes <= 0) return undefined; // disabled
    if (state !== 'encrypted-unlocked') return undefined;
    const ms = minutes * 60_000;
    const tick = window.setInterval(() => {
      if (Date.now() - lastActivityRef.current >= ms) {
        lockInternal();
      }
    }, IDLE_TICK_MS);
    return () => window.clearInterval(tick);
    // lockInternal is stable below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [settings.vaultLockTimeoutMin, state]);

  /* ---------------- internal driver ops ---------------- */

  const lockInternal = useCallback((): void => {
    const header = readEncryptedHeader();
    if (!header) {
      // No encrypted data; nothing to lock.
      return;
    }
    vault.__installDriver(
      new EncryptedDriver({
        key: null,
        salt: decodeSalt(header.salt),
        iterations: header.iterations,
      }),
    );
  }, []);

  const lock = useCallback((): void => {
    if (state === 'encrypted-unlocked') lockInternal();
  }, [state, lockInternal]);

  const unlock = useCallback(async (pwd: string): Promise<boolean> => {
    const header = readEncryptedHeader();
    if (!header) return false;
    const salt = decodeSalt(header.salt);
    const blobs = Object.values(header.items);
    const verify = await verifyPasswordAgainstAnyBlob(
      pwd,
      salt,
      header.iterations,
      blobs,
    );
    if (!verify.ok || !verify.key) return false;
    const driver = new EncryptedDriver({
      key: verify.key,
      salt,
      iterations: header.iterations,
    });
    const ok = await driver.unlockWithKey(verify.key);
    if (!ok) return false;
    vault.__installDriver(driver);
    lastActivityRef.current = Date.now();
    return true;
  }, []);

  const enableEncryption = useCallback(
    async (pwd: string): Promise<void> => {
      if (!pwd || pwd.length < 4) {
        throw new Error('master password too short (min 4 characters)');
      }
      // Snapshot existing plaintext items (whether currently driven by
      // PlaintextDriver or freshly read from storage).
      const existing = readPlaintextItems();
      const salt = randomSalt();
      const key = await deriveKey(pwd, salt);
      const driver = new EncryptedDriver({
        key,
        salt,
        iterations: PBKDF2_ITERATIONS,
        initialItems: existing,
      });
      try {
        await driver.flushNow();
      } catch (err) {
        console.error('[csap-apidoc] enableEncryption flush failed', err);
        throw err;
      }
      vault.__installDriver(driver);
      clearPlaintextStorage();
      settingsStore.update({ vaultMode: 'encrypted' });
      lastActivityRef.current = Date.now();
    },
    [],
  );

  const setMasterPassword = enableEncryption;

  const disableEncryption = useCallback(
    async (pwd: string): Promise<void> => {
      // We accept the current password to confirm intent + verify.
      const ok = await unlock(pwd);
      if (!ok) throw new Error('wrong master password');
      const snapshot = vault.__getDriver().serialize();
      const items = snapshot?.items ?? {};
      vault.__installDriver(new PlaintextDriver(items));
      clearEncryptedStorage();
      settingsStore.update({ vaultMode: 'plaintext' });
    },
    [unlock],
  );

  const changePassword = useCallback(
    async (oldPwd: string, newPwd: string): Promise<boolean> => {
      if (!newPwd || newPwd.length < 4) return false;
      const ok = await unlock(oldPwd);
      if (!ok) return false;
      const snapshot = vault.__getDriver().serialize();
      const items = snapshot?.items ?? {};
      // Rotate salt — best practice.
      const salt = randomSalt();
      const key = await deriveKey(newPwd, salt);
      const driver = new EncryptedDriver({
        key,
        salt,
        iterations: PBKDF2_ITERATIONS,
        initialItems: items,
      });
      await driver.flushNow();
      vault.__installDriver(driver);
      lastActivityRef.current = Date.now();
      return true;
    },
    [unlock],
  );

  /* ---------------- export / import / reset ---------------- */

  const exportConfig = useCallback((): string => {
    const driverState = vault.getDriverState();
    const includeVault =
      driverState === 'plaintext' || driverState === 'encrypted-unlocked';
    const snap = includeVault ? vault.__getDriver().serialize() : null;
    const payload: ExportPayload = {
      version: 1,
      exportedAt: new Date().toISOString(),
      vaultMode: settingsStore.getState().vaultMode,
      vaultIncluded: !!snap,
      environments: environmentStore.read(),
      headers: headersStore.read(),
      authSchemes: authStore.read(),
      settings: settingsStore.getState(),
      vault: snap ? { items: snap.items } : undefined,
    };
    return JSON.stringify(payload, null, 2);
  }, []);

  const importConfig = useCallback((json: string): void => {
    let parsed: ExportPayload;
    try {
      parsed = JSON.parse(json) as ExportPayload;
    } catch {
      throw new Error('invalid JSON');
    }
    if (!parsed || typeof parsed !== 'object') {
      throw new Error('invalid payload');
    }
    if (typeof window !== 'undefined') {
      try {
        if (parsed.environments)
          window.localStorage.setItem(
            ENV_STORAGE_KEY,
            JSON.stringify(parsed.environments),
          );
        if (parsed.headers)
          window.localStorage.setItem(
            HEADERS_STORAGE_KEY,
            JSON.stringify(parsed.headers),
          );
        if (parsed.authSchemes)
          window.localStorage.setItem(
            AUTH_STORAGE_KEY,
            JSON.stringify(parsed.authSchemes),
          );
        if (parsed.settings)
          window.localStorage.setItem(
            SETTINGS_STORAGE_KEY,
            JSON.stringify(parsed.settings),
          );
      } catch (err) {
        console.warn('[csap-apidoc] importConfig storage write failed', err);
      }
    }
    // Vault: drop any pre-existing encrypted state and switch to plaintext.
    clearEncryptedStorage();
    const items =
      parsed.vault && parsed.vault.items ? parsed.vault.items : {};
    vault.__installDriver(new PlaintextDriver(items));
    // Reset settings to plaintext on import — the user must re-enable
    // encryption with a fresh password (we don't ship a password in the
    // export, so we can't reconstruct the encrypted blobs).
    settingsStore.update({ vaultMode: 'plaintext' });
    if (typeof window !== 'undefined') window.location.reload();
  }, []);

  const resetAll = useCallback((): void => {
    if (typeof window === 'undefined') return;
    try {
      for (const k of readAllStorageKeys()) {
        window.localStorage.removeItem(k);
      }
    } catch (err) {
      console.warn('[csap-apidoc] resetAll storage clear failed', err);
    }
    settingsStore.replace({ ...DEFAULT_SETTINGS });
    environmentStore.reset();
    headersStore.reset();
    authStore.reset();
    vault.__installDriver(new PlaintextDriver({}));
    window.location.reload();
  }, []);

  const value = useMemo<VaultContextValue>(
    () => ({
      state,
      hasEncryptedData,
      setMasterPassword,
      enableEncryption,
      disableEncryption,
      changePassword,
      unlock,
      lock,
      exportConfig,
      importConfig,
      resetAll,
    }),
    [
      state,
      hasEncryptedData,
      setMasterPassword,
      enableEncryption,
      disableEncryption,
      changePassword,
      unlock,
      lock,
      exportConfig,
      importConfig,
      resetAll,
    ],
  );

  return (
    <VaultContext.Provider value={value}>{children}</VaultContext.Provider>
  );
};

export function useVault(): VaultContextValue {
  const ctx = useContext(VaultContext);
  if (!ctx) {
    throw new Error('useVault must be used within <VaultProvider>');
  }
  return ctx;
}
