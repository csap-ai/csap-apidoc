/**
 * vaultCrypto — pure Web Crypto helpers for the encrypted vault driver.
 *
 * All routines are stateless. The derived AES-GCM key (a CryptoKey produced
 * by `deriveKey`) lives only in the encrypted driver's in-memory cache and
 * is forgotten on `lock()`. Salt and ciphertext are persisted by the
 * driver (see vaultDriver.ts); this file only knows about bytes.
 *
 * Algorithms (locked in §9 D-2 of docs/features/environment-auth-headers.md):
 *   - KDF:    PBKDF2-SHA256, 200_000 iterations, 16-byte random salt
 *   - Cipher: AES-GCM 256, 12-byte random IV per item
 *
 * No external dependencies — uses globalThis.crypto.subtle only.
 */

export const PBKDF2_ITERATIONS = 200_000;
export const SALT_BYTES = 16;
export const IV_BYTES = 12;
export const AES_KEY_BITS = 256;

const TEXT_ENCODER = typeof TextEncoder !== 'undefined' ? new TextEncoder() : null;
const TEXT_DECODER = typeof TextDecoder !== 'undefined' ? new TextDecoder() : null;

function getSubtle(): SubtleCrypto {
  const c =
    typeof globalThis !== 'undefined' && (globalThis as any).crypto
      ? (globalThis as any).crypto
      : null;
  if (!c || !c.subtle) {
    throw new Error(
      '[csap-apidoc] Web Crypto SubtleCrypto is not available; vault encryption requires a secure context (HTTPS or localhost).',
    );
  }
  return c.subtle as SubtleCrypto;
}

function getRandomBytes(n: number): Uint8Array {
  const c =
    typeof globalThis !== 'undefined' && (globalThis as any).crypto
      ? (globalThis as any).crypto
      : null;
  if (!c || typeof c.getRandomValues !== 'function') {
    throw new Error('[csap-apidoc] crypto.getRandomValues unavailable');
  }
  const out = new Uint8Array(n);
  c.getRandomValues(out);
  return out;
}

export function randomSalt(): Uint8Array {
  return getRandomBytes(SALT_BYTES);
}

export function randomIv(): Uint8Array {
  return getRandomBytes(IV_BYTES);
}

/* ---------------- base64 helpers ---------------- */

export function bytesToBase64(bytes: Uint8Array): string {
  if (typeof btoa === 'function') {
    let s = '';
    for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
    return btoa(s);
  }
  // Fallback for Node test environments.
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  return Buffer.from(bytes).toString('base64');
}

export function base64ToBytes(b64: string): Uint8Array {
  if (typeof atob === 'function') {
    const bin = atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }
  return new Uint8Array(Buffer.from(b64, 'base64'));
}

function encodeUtf8(s: string): Uint8Array {
  if (TEXT_ENCODER) return TEXT_ENCODER.encode(s);
  // Fallback: only ASCII safe; the strings we encrypt may contain UTF-8 so
  // throw rather than silently corrupt.
  throw new Error('[csap-apidoc] TextEncoder unavailable');
}

function decodeUtf8(b: Uint8Array): string {
  if (TEXT_DECODER) return TEXT_DECODER.decode(b);
  throw new Error('[csap-apidoc] TextDecoder unavailable');
}

/* ---------------- KDF + cipher ---------------- */

/**
 * Derive an AES-GCM key from a master password + per-vault salt.
 * Throws if Web Crypto is unavailable (insecure context).
 */
export async function deriveKey(
  password: string,
  salt: Uint8Array,
  iterations: number = PBKDF2_ITERATIONS,
): Promise<CryptoKey> {
  const subtle = getSubtle();
  const baseKey = await subtle.importKey(
    'raw',
    encodeUtf8(password),
    { name: 'PBKDF2' },
    false,
    ['deriveKey'],
  );
  return subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt,
      iterations,
      hash: 'SHA-256',
    },
    baseKey,
    { name: 'AES-GCM', length: AES_KEY_BITS },
    false,
    ['encrypt', 'decrypt'],
  );
}

export interface EncryptedBlob {
  iv: string; // base64
  ct: string; // base64
}

export async function encryptString(
  plaintext: string,
  key: CryptoKey,
): Promise<EncryptedBlob> {
  const subtle = getSubtle();
  const iv = randomIv();
  const ct = await subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    encodeUtf8(plaintext),
  );
  return {
    iv: bytesToBase64(iv),
    ct: bytesToBase64(new Uint8Array(ct)),
  };
}

/**
 * Decrypt a blob. Returns null on any failure (wrong key, corrupt blob).
 * Callers should treat null as "data not recoverable" and not surface raw
 * crypto errors to the user.
 */
export async function decryptString(
  blob: EncryptedBlob,
  key: CryptoKey,
): Promise<string | null> {
  try {
    const subtle = getSubtle();
    const iv = base64ToBytes(blob.iv);
    const ct = base64ToBytes(blob.ct);
    const pt = await subtle.decrypt({ name: 'AES-GCM', iv }, key, ct);
    return decodeUtf8(new Uint8Array(pt));
  } catch (err) {
    // Authentication failures throw OperationError; treat them as "wrong key".
    return null;
  }
}

/**
 * Convenience: verify that a candidate password produces the right key by
 * decrypting any one known blob. Returns true on success, false otherwise.
 * Used by `unlock(pwd)` and `changePassword(oldPwd, ...)`.
 */
export async function verifyPasswordAgainstAnyBlob(
  password: string,
  salt: Uint8Array,
  iterations: number,
  blobs: EncryptedBlob[],
): Promise<{ ok: boolean; key: CryptoKey | null }> {
  const key = await deriveKey(password, salt, iterations);
  if (blobs.length === 0) {
    // No items to verify against — treat the password as accepted; the next
    // successful encrypt will define the key for future unlocks.
    return { ok: true, key };
  }
  for (const b of blobs) {
    const out = await decryptString(b, key);
    if (out !== null) return { ok: true, key };
  }
  return { ok: false, key: null };
}
