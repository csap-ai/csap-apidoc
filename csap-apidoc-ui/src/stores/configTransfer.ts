/**
 * Config export / import (Settings → 数据).
 *
 * Security rule: an export file NEVER contains plaintext vault secrets.
 *   - encrypted vault (locked or unlocked) → the on-disk ciphertext
 *     (`vaultEncrypted`: salt + iterations + AES-GCM blobs) is exported
 *     verbatim. Importing it restores the encrypted vault in the LOCKED
 *     state; the original master password is needed to unlock it.
 *   - plaintext vault → secrets are omitted (`vaultIncluded: false`). The
 *     UI tells the user to enable encryption first if they want secrets to
 *     travel with the export.
 *
 * Import stays compatible with files produced by earlier versions, which
 * carried plaintext secrets under `vault.items`: those are loaded into a
 * plaintext vault exactly as before.
 *
 * These helpers only touch localStorage + the vault singleton; the caller
 * (VaultContext) is responsible for reloading the page afterwards.
 */

import { vault } from './vault';
import {
  PlaintextDriver,
  EncryptedDriver,
  EncryptedOnDisk,
  ENCRYPTED_STORAGE_KEY,
  readEncryptedHeader,
  clearEncryptedStorage,
  clearPlaintextStorage,
  decodeSalt,
} from './vaultDriver';
import { settingsStore, SETTINGS_STORAGE_KEY, VaultMode } from './settingsStore';
import { environmentStore, ENV_STORAGE_KEY } from './environmentStore';
import { headersStore, HEADERS_STORAGE_KEY } from './headersStore';
import { authStore, AUTH_STORAGE_KEY } from './authStore';

export interface ExportPayload {
  version: 1;
  exportedAt: string;
  vaultMode: VaultMode;
  /** True when vault secrets (in encrypted form) are part of this file. */
  vaultIncluded: boolean;
  environments?: unknown;
  headers?: unknown;
  authSchemes?: unknown;
  settings?: unknown;
  /** Encrypted vault exactly as stored on disk (ciphertext only). */
  vaultEncrypted?: EncryptedOnDisk;
  /**
   * @deprecated Plaintext vault items written by exports before 1.1.1.
   * Never produced any more; still accepted on import.
   */
  vault?: { items: Record<string, string> };
}

function isEncryptedPayload(v: unknown): v is EncryptedOnDisk {
  if (!v || typeof v !== 'object') return false;
  const e = v as Partial<EncryptedOnDisk>;
  return (
    e.version === 1 &&
    typeof e.salt === 'string' &&
    typeof e.iterations === 'number' &&
    !!e.items &&
    typeof e.items === 'object'
  );
}

export function buildExportPayload(now: Date = new Date()): ExportPayload {
  const vaultMode = settingsStore.getState().vaultMode;
  const encrypted =
    vault.getDriverState() !== 'plaintext' ? readEncryptedHeader() : null;
  return {
    version: 1,
    exportedAt: now.toISOString(),
    vaultMode: encrypted ? 'encrypted' : vaultMode,
    vaultIncluded: !!encrypted,
    environments: environmentStore.read(),
    headers: headersStore.read(),
    authSchemes: authStore.read(),
    settings: settingsStore.getState(),
    vaultEncrypted: encrypted ?? undefined,
  };
}

export function exportConfigJson(now?: Date): string {
  return JSON.stringify(buildExportPayload(now), null, 2);
}

/**
 * Apply an exported JSON file to localStorage + the vault singleton.
 * Returns the vault mode that is in effect afterwards.
 */
export function applyImportJson(json: string): VaultMode {
  let parsed: ExportPayload;
  try {
    parsed = JSON.parse(json) as ExportPayload;
  } catch {
    throw new Error('invalid JSON');
  }
  if (!parsed || typeof parsed !== 'object') {
    throw new Error('invalid payload');
  }
  const currentMode: VaultMode =
    vault.getDriverState() === 'plaintext' ? 'plaintext' : 'encrypted';

  if (typeof window !== 'undefined') {
    try {
      if (parsed.environments)
        window.localStorage.setItem(ENV_STORAGE_KEY, JSON.stringify(parsed.environments));
      if (parsed.headers)
        window.localStorage.setItem(HEADERS_STORAGE_KEY, JSON.stringify(parsed.headers));
      if (parsed.authSchemes)
        window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(parsed.authSchemes));
      if (parsed.settings)
        window.localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(parsed.settings));
    } catch (err) {
      console.warn('[csap-apidoc] importConfig storage write failed', err);
    }
  }

  let nextMode: VaultMode;
  if (isEncryptedPayload(parsed.vaultEncrypted)) {
    // Ciphertext export: restore as a LOCKED encrypted vault.
    const enc = parsed.vaultEncrypted;
    clearPlaintextStorage();
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(ENCRYPTED_STORAGE_KEY, JSON.stringify(enc));
    }
    vault.__installDriver(
      new EncryptedDriver({ key: null, salt: decodeSalt(enc.salt), iterations: enc.iterations }),
    );
    nextMode = 'encrypted';
  } else if (parsed.vault && parsed.vault.items && typeof parsed.vault.items === 'object') {
    // Legacy (pre-1.1.1) export carrying plaintext items.
    clearEncryptedStorage();
    vault.__installDriver(new PlaintextDriver(parsed.vault.items));
    nextMode = 'plaintext';
  } else {
    // No secrets in the file: keep the local vault untouched.
    nextMode = currentMode;
  }
  // Settings were overwritten above; vaultMode must describe the vault
  // that is actually on disk now, not whatever the exporting browser had.
  settingsStore.replace({ ...settingsStoreFromDisk(), vaultMode: nextMode });
  return nextMode;
}

function settingsStoreFromDisk() {
  if (typeof window === 'undefined') return settingsStore.getState();
  try {
    const raw = window.localStorage.getItem(SETTINGS_STORAGE_KEY);
    if (raw) return { ...settingsStore.getState(), ...JSON.parse(raw) };
  } catch {
    /* fall through */
  }
  return settingsStore.getState();
}
