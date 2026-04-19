/**
 * Global test setup — runs before every test file.
 *
 * - Ensures `globalThis.crypto.subtle` is available even when a test file
 *   opts into the `jsdom` environment (jsdom does not ship Web Crypto).
 *   Node 18+ exposes `webcrypto` from `node:crypto`; we install it as the
 *   global `crypto` if it isn't already a usable SubtleCrypto provider.
 */

import { webcrypto } from 'node:crypto';

const existing = (globalThis as any).crypto;
if (!existing || !existing.subtle) {
  Object.defineProperty(globalThis, 'crypto', {
    value: webcrypto,
    configurable: true,
    writable: true,
  });
}
