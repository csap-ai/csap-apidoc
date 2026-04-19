// @vitest-environment jsdom

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { vault, VAULT_STORAGE_KEY } from './vault';
import {
  EncryptedDriver,
  PlaintextDriver,
  REF_PREFIX,
} from './vaultDriver';
import { deriveKey, randomSalt } from './vaultCrypto';

const FAST_ITER = 1000;

beforeEach(() => {
  window.localStorage.clear();
  // Force the vault back to a fresh PlaintextDriver. `reset()` clears the
  // underlying driver and notifies subscribers.
  vault.__installDriver(new PlaintextDriver({}));
  vault.reset();
});

describe('vault public API (plaintext driver)', () => {
  it('reports plaintext driver state by default', () => {
    expect(vault.getDriverState()).toBe('plaintext');
  });

  it('isVaultRef recognises the prefix', () => {
    expect(vault.isVaultRef('vault:tok_a')).toBe(true);
    expect(vault.isVaultRef('plaintext')).toBe(false);
    expect(vault.isVaultRef(undefined as unknown as string)).toBe(false);
  });

  it('put → get round-trips and persists under VAULT_STORAGE_KEY', () => {
    const ref = vault.put('shh');
    expect(ref.startsWith(REF_PREFIX + 'tok_')).toBe(true);
    expect(vault.get(ref)).toBe('shh');
    const onDisk = JSON.parse(
      window.localStorage.getItem(VAULT_STORAGE_KEY) as string,
    );
    expect(Object.values(onDisk.items)).toContain('shh');
  });

  it('put with an existingRef updates that slot in place', () => {
    const ref = vault.put('first');
    const ref2 = vault.put('second', ref);
    expect(ref2).toBe(ref);
    expect(vault.get(ref)).toBe('second');
  });

  it('get returns null for unknown / falsy refs', () => {
    expect(vault.get(undefined)).toBeNull();
    expect(vault.get(null)).toBeNull();
    expect(vault.get('not-a-ref')).toBeNull();
    expect(vault.get('vault:tok_unknown')).toBeNull();
  });

  it('remove drops a stored ref and is safe for unknown refs', () => {
    const ref = vault.put('x');
    vault.remove(ref);
    expect(vault.get(ref)).toBeNull();
    vault.remove(null);
    vault.remove('vault:tok_does_not_exist');
  });

  it('retainOnly garbage-collects orphans not in the live set', () => {
    const a = vault.put('A');
    const b = vault.put('B');
    vault.retainOnly(new Set([a]));
    expect(vault.get(a)).toBe('A');
    expect(vault.get(b)).toBeNull();
  });

  it('subscribe is notified on put / remove / retainOnly / reset', () => {
    const fn = vi.fn();
    const off = vault.subscribe(fn);
    const ref = vault.put('x');
    vault.remove(ref);
    vault.retainOnly(new Set());
    vault.reset();
    expect(fn.mock.calls.length).toBeGreaterThanOrEqual(4);
    off();
    fn.mockClear();
    vault.put('y');
    expect(fn).not.toHaveBeenCalled();
  });

  it('reset wipes the plaintext store', () => {
    const ref = vault.put('vanishing');
    vault.reset();
    expect(vault.get(ref)).toBeNull();
  });

  it('isolates listener errors so other listeners still fire', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const bad = vi.fn(() => {
      throw new Error('boom');
    });
    const good = vi.fn();
    const offBad = vault.subscribe(bad);
    const offGood = vault.subscribe(good);
    vault.put('x');
    expect(bad).toHaveBeenCalled();
    expect(good).toHaveBeenCalled();
    offBad();
    offGood();
    warn.mockRestore();
  });
});

describe('vault public API (encrypted driver)', () => {
  it('getDriverState reports encrypted-locked vs encrypted-unlocked', async () => {
    const salt = randomSalt();
    const key = await deriveKey('pw', salt, FAST_ITER);
    const enc = new EncryptedDriver({
      key,
      salt,
      iterations: FAST_ITER,
      initialItems: { tok_seed: 'S' },
    });
    await enc.flushNow();
    vault.__installDriver(enc);
    expect(vault.getDriverState()).toBe('encrypted-unlocked');
    enc.lock();
    expect(vault.getDriverState()).toBe('encrypted-locked');
  });

  it('emits the locked-access custom event on get while locked', () => {
    const salt = randomSalt();
    const enc = new EncryptedDriver({ key: null, salt, iterations: FAST_ITER });
    vault.__installDriver(enc);
    let receivedDetail: any = null;
    const handler = (e: Event) => {
      receivedDetail = (e as CustomEvent).detail;
    };
    window.addEventListener('csap-apidoc:vault-locked-access', handler);
    expect(vault.get('vault:tok_anything')).toBeNull();
    expect(receivedDetail?.ref).toBe('vault:tok_anything');
    window.removeEventListener('csap-apidoc:vault-locked-access', handler);
  });
});
