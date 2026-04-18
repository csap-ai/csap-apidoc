// @vitest-environment jsdom

import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  EncryptedDriver,
  ENCRYPTED_STORAGE_KEY,
  PLAINTEXT_STORAGE_KEY,
  PlaintextDriver,
  REF_PREFIX,
  clearEncryptedStorage,
  clearPlaintextStorage,
  decodeSalt,
  hasEncryptedVaultData,
  isVaultRef,
  readEncryptedHeader,
  readPlaintextItems,
  refToId,
} from './vaultDriver';
import { deriveKey, randomSalt } from './vaultCrypto';

const FAST_ITER = 1000;

beforeEach(() => {
  window.localStorage.clear();
  clearPlaintextStorage();
  clearEncryptedStorage();
});

describe('ref helpers', () => {
  it('isVaultRef recognises the prefix only', () => {
    expect(isVaultRef('vault:tok_abc')).toBe(true);
    expect(isVaultRef('plain')).toBe(false);
    expect(isVaultRef(123)).toBe(false);
    expect(isVaultRef(null)).toBe(false);
  });

  it('refToId strips the prefix; returns null for non-vault values', () => {
    expect(refToId('vault:tok_abc')).toBe('tok_abc');
    expect(refToId('plain')).toBeNull();
    expect(refToId(null)).toBeNull();
    expect(refToId('vault:')).toBeNull();
  });
});

describe('PlaintextDriver', () => {
  it('starts empty and put → get round-trips', () => {
    const d = new PlaintextDriver({});
    const ref = d.put('secret-value');
    expect(ref.startsWith(REF_PREFIX + 'tok_')).toBe(true);
    expect(d.get(ref)).toBe('secret-value');
    expect(d.isLocked()).toBe(false);
    expect(d.mode).toBe('plaintext');
  });

  it('reuses the same id when an existingRef is supplied', () => {
    const d = new PlaintextDriver({});
    const ref1 = d.put('a');
    const ref2 = d.put('b', ref1);
    expect(ref2).toBe(ref1);
    expect(d.get(ref1)).toBe('b');
  });

  it('mints a fresh id when existingRef is unknown to the driver', () => {
    const d = new PlaintextDriver({});
    const fresh = d.put('x', 'vault:tok_does_not_exist');
    expect(fresh).not.toBe('vault:tok_does_not_exist');
    expect(d.get(fresh)).toBe('x');
  });

  it('persists writes to localStorage under PLAINTEXT_STORAGE_KEY', () => {
    const d = new PlaintextDriver({});
    d.put('p');
    const onDisk = window.localStorage.getItem(PLAINTEXT_STORAGE_KEY);
    expect(onDisk).toBeTruthy();
    const parsed = JSON.parse(onDisk as string);
    expect(parsed.encrypted).toBe(false);
    expect(Object.values(parsed.items)).toContain('p');
  });

  it('readPlaintextItems returns the in-memory snapshot from disk', () => {
    new PlaintextDriver({ tok_a: 'A', tok_b: 'B' });
    expect(readPlaintextItems()).toEqual({ tok_a: 'A', tok_b: 'B' });
  });

  it('remove drops the entry; unknown refs are no-ops', () => {
    const d = new PlaintextDriver({});
    const ref = d.put('hi');
    d.remove(ref);
    expect(d.get(ref)).toBeNull();
    d.remove('vault:tok_does_not_exist'); // no throw
  });

  it('retainOnly drops orphans not listed in the live set', () => {
    const d = new PlaintextDriver({ tok_keep: 'k', tok_drop: 'd' });
    d.retainOnly(new Set(['vault:tok_keep']));
    expect(d.get('vault:tok_keep')).toBe('k');
    expect(d.get('vault:tok_drop')).toBeNull();
  });

  it('retainOnly is a no-op when nothing changes', () => {
    const d = new PlaintextDriver({ tok_a: 'a' });
    const beforeRaw = window.localStorage.getItem(PLAINTEXT_STORAGE_KEY);
    d.retainOnly(new Set(['vault:tok_a']));
    expect(window.localStorage.getItem(PLAINTEXT_STORAGE_KEY)).toBe(beforeRaw);
  });

  it('hydrate replaces every item', () => {
    const d = new PlaintextDriver({ tok_old: 'old' });
    d.hydrate({ tok_new: 'new' });
    expect(d.get('vault:tok_old')).toBeNull();
    expect(d.get('vault:tok_new')).toBe('new');
  });

  it('serialize returns a snapshot copy', () => {
    const d = new PlaintextDriver({ tok_a: 'A' });
    const snap1 = d.serialize();
    const snap2 = d.serialize();
    expect(snap1.items).toEqual({ tok_a: 'A' });
    expect(snap1).not.toBe(snap2);
    snap1.items.tok_a = 'mutated';
    expect(d.get('vault:tok_a')).toBe('A');
  });
});

describe('EncryptedDriver', () => {
  it('starts locked when constructed with key=null', () => {
    const d = new EncryptedDriver({ key: null, salt: randomSalt() });
    expect(d.isLocked()).toBe(true);
    expect(d.get('vault:tok_x')).toBeNull();
  });

  it('locked put returns the existingRef untouched, else a sentinel locked ref', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const d = new EncryptedDriver({ key: null, salt: randomSalt() });
    const existing = 'vault:tok_existing';
    expect(d.put('val', existing)).toBe(existing);
    const sentinel = d.put('val');
    expect(sentinel.startsWith('vault:tok_locked_')).toBe(true);
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });

  it('serialize returns null while locked', () => {
    const d = new EncryptedDriver({ key: null, salt: randomSalt() });
    expect(d.serialize()).toBeNull();
  });

  it('hydrate while locked is a no-op (warns)', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const d = new EncryptedDriver({ key: null, salt: randomSalt() });
    d.hydrate({ tok_a: 'A' });
    expect(d.serialize()).toBeNull();
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });

  it('round-trips put/get once unlockWithKey decrypts the on-disk blobs', async () => {
    // First seed: build an encrypted store from initialItems with a known key.
    const salt = randomSalt();
    const key = await deriveKey('pw', salt, FAST_ITER);
    const seed = new EncryptedDriver({
      key,
      salt,
      iterations: FAST_ITER,
      initialItems: { tok_seed: 'hello-encrypted' },
    });
    await seed.flushNow();
    expect(hasEncryptedVaultData()).toBe(true);
    const header = readEncryptedHeader();
    expect(header).not.toBeNull();
    expect(decodeSalt(header!.salt).length).toBe(salt.length);

    // Cold-start a fresh driver from the same on-disk blobs, locked, then unlock.
    const cold = new EncryptedDriver({
      key: null,
      salt,
      iterations: FAST_ITER,
    });
    expect(cold.isLocked()).toBe(true);
    const ok = await cold.unlockWithKey(
      await deriveKey('pw', salt, FAST_ITER),
    );
    expect(ok).toBe(true);
    expect(cold.isLocked()).toBe(false);
    expect(cold.get('vault:tok_seed')).toBe('hello-encrypted');

    // put a new value while unlocked
    const ref = cold.put('another');
    await cold.flushNow();
    expect(cold.get(ref)).toBe('another');

    // Re-cold-start, this time unlock with the WRONG key — should fail and re-lock.
    const cold2 = new EncryptedDriver({
      key: null,
      salt,
      iterations: FAST_ITER,
    });
    const okWrong = await cold2.unlockWithKey(
      await deriveKey('WRONG', salt, FAST_ITER),
    );
    expect(okWrong).toBe(false);
    expect(cold2.isLocked()).toBe(true);
  });

  it('lock() drops the in-memory key + plaintext mirror', async () => {
    const salt = randomSalt();
    const key = await deriveKey('pw', salt, FAST_ITER);
    const d = new EncryptedDriver({
      key,
      salt,
      iterations: FAST_ITER,
      initialItems: { tok_a: 'A' },
    });
    await d.flushNow();
    expect(d.get('vault:tok_a')).toBe('A');
    d.lock();
    expect(d.isLocked()).toBe(true);
    expect(d.get('vault:tok_a')).toBeNull();
  });

  it('retainOnly while unlocked drops orphan plaintext entries', async () => {
    const salt = randomSalt();
    const key = await deriveKey('pw', salt, FAST_ITER);
    const d = new EncryptedDriver({
      key,
      salt,
      iterations: FAST_ITER,
      initialItems: { tok_keep: 'K', tok_drop: 'D' },
    });
    await d.flushNow();
    d.retainOnly(new Set(['vault:tok_keep']));
    await d.flushNow();
    expect(d.get('vault:tok_keep')).toBe('K');
    expect(d.get('vault:tok_drop')).toBeNull();
  });

  it('retainOnly while locked GCs ciphertext blobs without unlocking', async () => {
    const salt = randomSalt();
    const key = await deriveKey('pw', salt, FAST_ITER);
    const seed = new EncryptedDriver({
      key,
      salt,
      iterations: FAST_ITER,
      initialItems: { tok_keep: 'K', tok_drop: 'D' },
    });
    await seed.flushNow();
    const lockedView = new EncryptedDriver({
      key: null,
      salt,
      iterations: FAST_ITER,
    });
    expect(lockedView.isLocked()).toBe(true);
    lockedView.retainOnly(new Set(['vault:tok_keep']));
    const persisted = readEncryptedHeader();
    expect(persisted).not.toBeNull();
    expect(Object.keys(persisted!.items)).toEqual(['tok_keep']);
  });

  it('remove while locked deletes the on-disk blob', async () => {
    const salt = randomSalt();
    const key = await deriveKey('pw', salt, FAST_ITER);
    const seed = new EncryptedDriver({
      key,
      salt,
      iterations: FAST_ITER,
      initialItems: { tok_a: 'A', tok_b: 'B' },
    });
    await seed.flushNow();
    const lockedView = new EncryptedDriver({
      key: null,
      salt,
      iterations: FAST_ITER,
    });
    lockedView.remove('vault:tok_a');
    expect(Object.keys(readEncryptedHeader()!.items)).toEqual(['tok_b']);
  });
});

describe('NoopDriver-equivalent (locked-state in vault module)', () => {
  it('ENCRYPTED_STORAGE_KEY is distinct from PLAINTEXT_STORAGE_KEY', () => {
    expect(ENCRYPTED_STORAGE_KEY).not.toBe(PLAINTEXT_STORAGE_KEY);
  });
});
