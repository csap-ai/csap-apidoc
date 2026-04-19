/**
 * Vault drivers — internal storage backends for `vault.ts`.
 *
 * Two drivers, one interface:
 *   - PlaintextDriver: keeps the M3 layout under `csap-apidoc:vault`.
 *     The on-disk shape is `{ version, encrypted: false, items: { id: value } }`.
 *   - EncryptedDriver: separate key `csap-apidoc:vault.encrypted` with shape
 *     `{ version, salt, iterations, items: { id: { iv, ct } } }`.
 *     The AES-GCM key lives only in memory; never persisted.
 *
 * The driver interface is deliberately synchronous from the call site's
 * perspective so we don't have to convert every existing
 * `vault.put(...)` / `vault.get(...)` call to async (that would ripple
 * across AuthSchemesDrawer, authResolver, etc.). The encrypted driver
 * fulfils that by keeping the unlocked plaintext in an in-memory mirror;
 * encryption + persistence happens asynchronously in the background.
 *
 * Locked state behaviour:
 *   - get() returns null
 *   - put() returns the existing ref unchanged (if any) or a sentinel ref
 *     `vault:tok_locked_<n>` whose get() always returns null. This avoids
 *     silently corrupting data when the UI accidentally calls put while
 *     locked. UI should be disabling Save while locked anyway.
 *
 * See docs/features/environment-auth-headers.md §5.4 / §11 for the threat
 * model.
 */

import {
  bytesToBase64,
  base64ToBytes,
  decryptString,
  encryptString,
  EncryptedBlob,
  PBKDF2_ITERATIONS,
} from './vaultCrypto';

export const REF_PREFIX = 'vault:';
export type VaultRef = `${typeof REF_PREFIX}${string}`;

export const PLAINTEXT_STORAGE_KEY = 'csap-apidoc:vault';
export const ENCRYPTED_STORAGE_KEY = 'csap-apidoc:vault.encrypted';

const SCHEMA_VERSION = 1;
const LOCKED_REF_PREFIX = 'tok_locked_';

let lockedRefCounter = 0;

export function genId(): string {
  return 'tok_' + Math.random().toString(36).slice(2, 10);
}

export function refToId(ref: string | null | undefined): string | null {
  if (!ref || typeof ref !== 'string') return null;
  if (!ref.startsWith(REF_PREFIX)) return null;
  const id = ref.slice(REF_PREFIX.length);
  return id || null;
}

export function isVaultRef(s: unknown): s is VaultRef {
  return typeof s === 'string' && s.startsWith(REF_PREFIX);
}

function isLockedRef(ref: string | null | undefined): boolean {
  const id = refToId(ref);
  return !!id && id.startsWith(LOCKED_REF_PREFIX);
}

function nextLockedRef(): VaultRef {
  lockedRefCounter += 1;
  return (REF_PREFIX + LOCKED_REF_PREFIX + lockedRefCounter) as VaultRef;
}

/* ---------------- driver interface ---------------- */

export interface VaultDriver {
  readonly mode: 'plaintext' | 'encrypted';
  isLocked(): boolean;
  put(plaintext: string, existingRef?: string | null): VaultRef;
  get(ref: string | null | undefined): string | null;
  remove(ref: string | null | undefined): void;
  retainOnly(liveRefs: Set<string>): void;
  /** Returns the current plaintext snapshot, or null if locked. */
  serialize(): { items: Record<string, string> } | null;
  /** Replaces all items from a plaintext map (used by import / migration). */
  hydrate(items: Record<string, string>): void;
}

/* ---------------- plaintext driver ---------------- */

interface PlaintextOnDisk {
  version: 1;
  encrypted: false;
  items: Record<string, string>;
}

const DEFAULT_PLAINTEXT: PlaintextOnDisk = {
  version: SCHEMA_VERSION,
  encrypted: false,
  items: {},
};

function isPlaintextOnDisk(value: unknown): value is PlaintextOnDisk {
  if (!value || typeof value !== 'object') return false;
  const v = value as Partial<PlaintextOnDisk>;
  return (
    v.version === SCHEMA_VERSION &&
    v.encrypted === false &&
    !!v.items &&
    typeof v.items === 'object'
  );
}

export function readPlaintextItems(): Record<string, string> {
  if (typeof window === 'undefined') return {};
  try {
    const raw = window.localStorage.getItem(PLAINTEXT_STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    if (!isPlaintextOnDisk(parsed)) return {};
    return { ...parsed.items };
  } catch {
    return {};
  }
}

function writePlaintextItems(items: Record<string, string>): void {
  if (typeof window === 'undefined') return;
  try {
    const payload: PlaintextOnDisk = {
      version: SCHEMA_VERSION,
      encrypted: false,
      items,
    };
    window.localStorage.setItem(PLAINTEXT_STORAGE_KEY, JSON.stringify(payload));
  } catch (err) {
    console.warn('[csap-apidoc] failed to write plaintext vault', err);
  }
}

export function clearPlaintextStorage(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(PLAINTEXT_STORAGE_KEY);
  } catch {
    /* noop */
  }
}

export class PlaintextDriver implements VaultDriver {
  readonly mode = 'plaintext' as const;
  private items: Record<string, string>;

  constructor(initial?: Record<string, string>) {
    this.items = initial ? { ...initial } : readPlaintextItems();
    if (initial) writePlaintextItems(this.items);
  }

  isLocked(): boolean {
    return false;
  }

  put(plaintext: string, existingRef?: string | null): VaultRef {
    let id = refToId(existingRef);
    if (!id || !Object.prototype.hasOwnProperty.call(this.items, id)) {
      id = genId();
    }
    this.items = { ...this.items, [id]: plaintext };
    writePlaintextItems(this.items);
    return (REF_PREFIX + id) as VaultRef;
  }

  get(ref: string | null | undefined): string | null {
    const id = refToId(ref);
    if (!id) return null;
    return Object.prototype.hasOwnProperty.call(this.items, id)
      ? this.items[id]
      : null;
  }

  remove(ref: string | null | undefined): void {
    const id = refToId(ref);
    if (!id) return;
    if (!Object.prototype.hasOwnProperty.call(this.items, id)) return;
    const next = { ...this.items };
    delete next[id];
    this.items = next;
    writePlaintextItems(this.items);
  }

  retainOnly(liveRefs: Set<string>): void {
    const next: Record<string, string> = {};
    for (const id of Object.keys(this.items)) {
      if (liveRefs.has(REF_PREFIX + id)) next[id] = this.items[id];
    }
    if (Object.keys(next).length === Object.keys(this.items).length) return;
    this.items = next;
    writePlaintextItems(this.items);
  }

  serialize(): { items: Record<string, string> } {
    return { items: { ...this.items } };
  }

  hydrate(items: Record<string, string>): void {
    this.items = { ...items };
    writePlaintextItems(this.items);
  }
}

/* ---------------- encrypted driver ---------------- */

export interface EncryptedOnDisk {
  version: 1;
  salt: string; // base64
  iterations: number;
  items: Record<string, EncryptedBlob>;
}

function isEncryptedOnDisk(value: unknown): value is EncryptedOnDisk {
  if (!value || typeof value !== 'object') return false;
  const v = value as Partial<EncryptedOnDisk>;
  return (
    v.version === SCHEMA_VERSION &&
    typeof v.salt === 'string' &&
    typeof v.iterations === 'number' &&
    !!v.items &&
    typeof v.items === 'object'
  );
}

export function readEncryptedHeader(): EncryptedOnDisk | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(ENCRYPTED_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!isEncryptedOnDisk(parsed)) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function hasEncryptedVaultData(): boolean {
  return readEncryptedHeader() !== null;
}

function writeEncryptedOnDisk(payload: EncryptedOnDisk): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(ENCRYPTED_STORAGE_KEY, JSON.stringify(payload));
  } catch (err) {
    console.warn('[csap-apidoc] failed to write encrypted vault', err);
  }
}

export function clearEncryptedStorage(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(ENCRYPTED_STORAGE_KEY);
  } catch {
    /* noop */
  }
}

export interface EncryptedDriverInit {
  /** Required when unlocked; null/undefined means the driver starts locked. */
  key: CryptoKey | null;
  salt: Uint8Array;
  iterations?: number;
  /** Plaintext items to seed (e.g. fresh from PlaintextDriver during migration). */
  initialItems?: Record<string, string>;
}

/**
 * EncryptedDriver keeps two parallel views:
 *   - `plaintext` map (only when unlocked): authoritative for sync get/put
 *   - `blobs` map: encrypted ciphertexts persisted to localStorage
 *
 * Persistence is debounced via microtask to coalesce bursts (e.g. retainOnly
 * after a scheme delete that wipes 4 refs).
 */
export class EncryptedDriver implements VaultDriver {
  readonly mode = 'encrypted' as const;

  private key: CryptoKey | null;
  private readonly salt: Uint8Array;
  private readonly saltB64: string;
  private readonly iterations: number;
  /** Plaintext mirror — populated on unlock, cleared on lock. */
  private plaintext: Record<string, string> = {};
  /** Ciphertexts mirror — kept in sync with localStorage. */
  private blobs: Record<string, EncryptedBlob> = {};
  private flushScheduled = false;

  constructor(init: EncryptedDriverInit) {
    this.key = init.key ?? null;
    this.salt = init.salt;
    this.saltB64 = bytesToBase64(init.salt);
    this.iterations = init.iterations ?? PBKDF2_ITERATIONS;

    const onDisk = readEncryptedHeader();
    if (onDisk) {
      this.blobs = { ...onDisk.items };
    }

    if (init.initialItems && this.key) {
      // Seed migration: encrypt each item, replace any on-disk blobs.
      this.plaintext = { ...init.initialItems };
      this.blobs = {};
      this.scheduleFullEncryptFlush();
    }
    // If created with a key but no initialItems, the caller is expected to
    // call unlockWithKey() (or pass the key fresh from migration). We don't
    // auto-decrypt here to keep the constructor synchronous and predictable.
  }

  isLocked(): boolean {
    return this.key === null;
  }

  /** Drop the in-memory key + plaintext mirror. */
  lock(): void {
    this.key = null;
    this.plaintext = {};
  }

  /** Install a key (post-unlock) and decrypt all blobs into memory. */
  async unlockWithKey(key: CryptoKey): Promise<boolean> {
    this.key = key;
    return this.eagerDecryptAll();
  }

  /** True if all blobs decrypted cleanly. */
  private async eagerDecryptAll(): Promise<boolean> {
    if (!this.key) return false;
    const next: Record<string, string> = {};
    for (const [id, blob] of Object.entries(this.blobs)) {
      const out = await decryptString(blob, this.key);
      if (out === null) {
        // Corrupt or wrong key — abort, leave plaintext empty, treat as locked.
        this.plaintext = {};
        this.key = null;
        return false;
      }
      next[id] = out;
    }
    this.plaintext = next;
    return true;
  }

  /** Fire-and-forget: encrypt the whole plaintext map and write to disk. */
  private scheduleFullEncryptFlush(): void {
    if (this.flushScheduled) return;
    this.flushScheduled = true;
    queueMicrotask(() => {
      this.flushScheduled = false;
      void this.flushAll();
    });
  }

  /** Force a synchronous-await flush. Used by migration so the orchestrator can wait. */
  async flushNow(): Promise<void> {
    this.flushScheduled = false;
    await this.flushAll();
  }

  private async flushAll(): Promise<void> {
    if (!this.key) return;
    const nextBlobs: Record<string, EncryptedBlob> = {};
    for (const [id, value] of Object.entries(this.plaintext)) {
      // Reuse existing blob if value unchanged (avoid burning a fresh IV).
      const existing = this.blobs[id];
      if (existing) {
        const decoded = await decryptString(existing, this.key).catch(() => null);
        if (decoded === value) {
          nextBlobs[id] = existing;
          continue;
        }
      }
      nextBlobs[id] = await encryptString(value, this.key);
    }
    this.blobs = nextBlobs;
    writeEncryptedOnDisk({
      version: SCHEMA_VERSION,
      salt: this.saltB64,
      iterations: this.iterations,
      items: this.blobs,
    });
  }

  put(plaintext: string, existingRef?: string | null): VaultRef {
    if (this.key === null) {
      console.warn(
        '[csap-apidoc] vault.put called while locked; ignoring (set master password first)',
      );
      if (existingRef && isVaultRef(existingRef)) return existingRef as VaultRef;
      return nextLockedRef();
    }
    let id = refToId(existingRef);
    if (!id || isLockedRef(existingRef)) {
      id = genId();
    } else if (!Object.prototype.hasOwnProperty.call(this.plaintext, id)) {
      id = genId();
    }
    this.plaintext = { ...this.plaintext, [id]: plaintext };
    this.scheduleFullEncryptFlush();
    return (REF_PREFIX + id) as VaultRef;
  }

  get(ref: string | null | undefined): string | null {
    if (this.key === null) return null;
    const id = refToId(ref);
    if (!id) return null;
    return Object.prototype.hasOwnProperty.call(this.plaintext, id)
      ? this.plaintext[id]
      : null;
  }

  remove(ref: string | null | undefined): void {
    const id = refToId(ref);
    if (!id) return;
    if (this.key === null) {
      // Remove the blob but leave plaintext map alone (it's empty when locked).
      if (Object.prototype.hasOwnProperty.call(this.blobs, id)) {
        const next = { ...this.blobs };
        delete next[id];
        this.blobs = next;
        writeEncryptedOnDisk({
          version: SCHEMA_VERSION,
          salt: this.saltB64,
          iterations: this.iterations,
          items: this.blobs,
        });
      }
      return;
    }
    if (!Object.prototype.hasOwnProperty.call(this.plaintext, id)) return;
    const next = { ...this.plaintext };
    delete next[id];
    this.plaintext = next;
    this.scheduleFullEncryptFlush();
  }

  retainOnly(liveRefs: Set<string>): void {
    if (this.key === null) {
      // Apply the GC to blobs directly so orphans don't accumulate.
      const next: Record<string, EncryptedBlob> = {};
      for (const id of Object.keys(this.blobs)) {
        if (liveRefs.has(REF_PREFIX + id)) next[id] = this.blobs[id];
      }
      if (Object.keys(next).length === Object.keys(this.blobs).length) return;
      this.blobs = next;
      writeEncryptedOnDisk({
        version: SCHEMA_VERSION,
        salt: this.saltB64,
        iterations: this.iterations,
        items: this.blobs,
      });
      return;
    }
    const next: Record<string, string> = {};
    for (const id of Object.keys(this.plaintext)) {
      if (liveRefs.has(REF_PREFIX + id)) next[id] = this.plaintext[id];
    }
    if (Object.keys(next).length === Object.keys(this.plaintext).length) return;
    this.plaintext = next;
    this.scheduleFullEncryptFlush();
  }

  serialize(): { items: Record<string, string> } | null {
    if (this.key === null) return null;
    return { items: { ...this.plaintext } };
  }

  hydrate(items: Record<string, string>): void {
    if (this.key === null) {
      console.warn('[csap-apidoc] cannot hydrate encrypted vault while locked');
      return;
    }
    this.plaintext = { ...items };
    this.blobs = {};
    this.scheduleFullEncryptFlush();
  }
}

/* ---------------- noop driver (for SSR / never-set) ---------------- */

export class NoopDriver implements VaultDriver {
  readonly mode = 'plaintext' as const;
  isLocked(): boolean {
    return false;
  }
  put(_: string, existingRef?: string | null): VaultRef {
    if (existingRef && isVaultRef(existingRef)) return existingRef as VaultRef;
    return (REF_PREFIX + genId()) as VaultRef;
  }
  get(): string | null {
    return null;
  }
  remove(): void {
    /* noop */
  }
  retainOnly(): void {
    /* noop */
  }
  serialize(): { items: Record<string, string> } {
    return { items: {} };
  }
  hydrate(): void {
    /* noop */
  }
}

/* ---------------- helpers exported for VaultContext ---------------- */

export function decodeSalt(b64: string): Uint8Array {
  return base64ToBytes(b64);
}
