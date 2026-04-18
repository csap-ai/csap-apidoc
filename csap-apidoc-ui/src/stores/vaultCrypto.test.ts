import { describe, it, expect } from 'vitest';
import {
  PBKDF2_ITERATIONS,
  base64ToBytes,
  bytesToBase64,
  decryptString,
  deriveKey,
  encryptString,
  randomIv,
  randomSalt,
  verifyPasswordAgainstAnyBlob,
} from './vaultCrypto';

// Use a small iteration count in tests to keep them fast; the production
// constant is exercised in a single dedicated test below.
const FAST_ITER = 1000;

describe('base64 helpers', () => {
  it('round-trip arbitrary bytes', () => {
    const input = new Uint8Array([0, 1, 2, 250, 251, 252, 253, 254, 255]);
    const round = base64ToBytes(bytesToBase64(input));
    expect(Array.from(round)).toEqual(Array.from(input));
  });
});

describe('randomSalt / randomIv', () => {
  it('produce non-reused values across calls', () => {
    const a = bytesToBase64(randomSalt());
    const b = bytesToBase64(randomSalt());
    expect(a).not.toBe(b);
    const x = bytesToBase64(randomIv());
    const y = bytesToBase64(randomIv());
    expect(x).not.toBe(y);
  });

  it('return the documented byte lengths', () => {
    expect(randomSalt().length).toBe(16);
    expect(randomIv().length).toBe(12);
  });
});

describe('encryptString / decryptString', () => {
  it('round-trips a UTF-8 plaintext with the same derived key', async () => {
    const salt = randomSalt();
    const key = await deriveKey('correct horse battery staple', salt, FAST_ITER);
    const blob = await encryptString('héllo 世界 🌍', key);
    const out = await decryptString(blob, key);
    expect(out).toBe('héllo 世界 🌍');
  });

  it('returns null when decrypting with the wrong key (different password)', async () => {
    const salt = randomSalt();
    const k1 = await deriveKey('right', salt, FAST_ITER);
    const k2 = await deriveKey('WRONG', salt, FAST_ITER);
    const blob = await encryptString('secret', k1);
    expect(await decryptString(blob, k2)).toBeNull();
  });

  it('returns null when decrypting with the wrong salt-derived key', async () => {
    const k1 = await deriveKey('pwd', randomSalt(), FAST_ITER);
    const k2 = await deriveKey('pwd', randomSalt(), FAST_ITER);
    const blob = await encryptString('secret', k1);
    expect(await decryptString(blob, k2)).toBeNull();
  });

  it('uses a fresh IV per call (no IV reuse for identical plaintexts)', async () => {
    const key = await deriveKey('p', randomSalt(), FAST_ITER);
    const a = await encryptString('same', key);
    const b = await encryptString('same', key);
    expect(a.iv).not.toBe(b.iv);
    expect(a.ct).not.toBe(b.ct);
  });
});

describe('verifyPasswordAgainstAnyBlob', () => {
  it('accepts any password when there are no blobs to verify against', async () => {
    const { ok, key } = await verifyPasswordAgainstAnyBlob(
      'anything',
      randomSalt(),
      FAST_ITER,
      [],
    );
    expect(ok).toBe(true);
    expect(key).not.toBeNull();
  });

  it('returns ok=true when the candidate password matches', async () => {
    const salt = randomSalt();
    const k = await deriveKey('pw', salt, FAST_ITER);
    const blob = await encryptString('hi', k);
    const r = await verifyPasswordAgainstAnyBlob('pw', salt, FAST_ITER, [blob]);
    expect(r.ok).toBe(true);
    expect(r.key).not.toBeNull();
  });

  it('returns ok=false when none of the blobs decrypt', async () => {
    const salt = randomSalt();
    const k = await deriveKey('pw', salt, FAST_ITER);
    const blob = await encryptString('hi', k);
    const r = await verifyPasswordAgainstAnyBlob(
      'wrong-pw',
      salt,
      FAST_ITER,
      [blob],
    );
    expect(r.ok).toBe(false);
    expect(r.key).toBeNull();
  });
});

describe('PBKDF2_ITERATIONS', () => {
  it('exposes the documented locked-in default', () => {
    expect(PBKDF2_ITERATIONS).toBe(200_000);
  });
});
