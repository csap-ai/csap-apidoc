// @vitest-environment jsdom

import { describe, it, expect, beforeEach } from 'vitest';
import { vault } from './vault';
import {
  EncryptedDriver,
  PlaintextDriver,
  ENCRYPTED_STORAGE_KEY,
  PLAINTEXT_STORAGE_KEY,
} from './vaultDriver';
import { deriveKey, randomSalt } from './vaultCrypto';
import { settingsStore } from './settingsStore';
import { buildExportPayload, exportConfigJson, applyImportJson } from './configTransfer';

const FAST_ITER = 1000;
const SECRET = 'super-secret-token-123';

beforeEach(() => {
  window.localStorage.clear();
  vault.__installDriver(new PlaintextDriver({}));
  vault.reset();
  settingsStore.reset();
});

async function installUnlockedEncrypted(pwd = 'pw') {
  const salt = randomSalt();
  const key = await deriveKey(pwd, salt, FAST_ITER);
  const enc = new EncryptedDriver({
    key,
    salt,
    iterations: FAST_ITER,
    initialItems: { tok_a: SECRET },
  });
  await enc.flushNow();
  vault.__installDriver(enc);
  settingsStore.update({ vaultMode: 'encrypted' });
  return { enc, key, salt };
}

describe('config export never contains plaintext secrets', () => {
  it('plaintext vault: secrets omitted', () => {
    vault.put(SECRET);
    const json = exportConfigJson();
    expect(json).not.toContain(SECRET);
    const payload = JSON.parse(json);
    expect(payload.vaultIncluded).toBe(false);
    expect(payload.vault).toBeUndefined();
    expect(payload.vaultEncrypted).toBeUndefined();
  });

  it('encrypted + unlocked vault: only ciphertext exported', async () => {
    await installUnlockedEncrypted();
    expect(vault.getDriverState()).toBe('encrypted-unlocked');
    expect(vault.get('vault:tok_a')).toBe(SECRET);
    const json = exportConfigJson();
    expect(json).not.toContain(SECRET);
    const payload = buildExportPayload();
    expect(payload.vaultIncluded).toBe(true);
    expect(payload.vaultMode).toBe('encrypted');
    expect(payload.vault).toBeUndefined();
    expect(Object.keys(payload.vaultEncrypted!.items)).toEqual(['tok_a']);
    expect(payload.vaultEncrypted!.items.tok_a).toHaveProperty('ct');
  });

  it('encrypted + locked vault: ciphertext still exported', async () => {
    const { enc } = await installUnlockedEncrypted();
    enc.lock();
    const payload = buildExportPayload();
    expect(payload.vaultIncluded).toBe(true);
    expect(payload.vaultEncrypted).toBeTruthy();
  });
});

describe('config import', () => {
  it('ciphertext export round-trips into a locked vault that unlocks with the original password', async () => {
    const { salt } = await installUnlockedEncrypted('pw');
    const json = exportConfigJson();

    // Simulate another browser.
    window.localStorage.clear();
    vault.__installDriver(new PlaintextDriver({}));
    settingsStore.reset();

    expect(applyImportJson(json)).toBe('encrypted');
    expect(vault.getDriverState()).toBe('encrypted-locked');
    expect(settingsStore.getState().vaultMode).toBe('encrypted');
    expect(window.localStorage.getItem(PLAINTEXT_STORAGE_KEY)).toBeNull();
    expect(window.localStorage.getItem(ENCRYPTED_STORAGE_KEY)).not.toContain(SECRET);

    const key = await deriveKey('pw', salt, FAST_ITER);
    const driver = vault.__getDriver() as EncryptedDriver;
    expect(await driver.unlockWithKey(key)).toBe(true);
    expect(vault.get('vault:tok_a')).toBe(SECRET);
  });

  it('accepts legacy exports that carry plaintext vault items', () => {
    const legacy = JSON.stringify({
      version: 1,
      exportedAt: '2026-01-01T00:00:00.000Z',
      vaultMode: 'plaintext',
      vaultIncluded: true,
      settings: { ...settingsStore.getState(), tryItOutTimeoutMs: 5000 },
      vault: { items: { tok_old: 'legacy' } },
    });
    expect(applyImportJson(legacy)).toBe('plaintext');
    expect(vault.getDriverState()).toBe('plaintext');
    expect(vault.get('vault:tok_old')).toBe('legacy');
    // Imported settings are kept (not clobbered by the in-memory cache).
    expect(settingsStore.getState().tryItOutTimeoutMs).toBe(5000);
  });

  it('a secrets-free export leaves the local vault untouched', () => {
    const ref = vault.put('local');
    const json = exportConfigJson();
    applyImportJson(json);
    expect(vault.get(ref)).toBe('local');
    expect(settingsStore.getState().vaultMode).toBe('plaintext');
  });

  it('rejects invalid JSON', () => {
    expect(() => applyImportJson('{nope')).toThrow('invalid JSON');
  });
});
