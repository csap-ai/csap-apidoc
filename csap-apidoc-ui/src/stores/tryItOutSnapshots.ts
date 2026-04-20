/**
 * TryItOutSnapshotsStore — localStorage-backed **named** request templates (M9.2).
 *
 * Complements {@link tryItOutHistoryStore} (M9.1):
 *
 *   - History  = auto-recorded, size-bounded ring buffer of the user's last
 *                N sends. Ephemeral: new sends evict old entries.
 *   - Snapshots = user-named, user-described, never-evicted pre-request
 *                 templates. Think "Postman collection / Insomnia request"
 *                 but browser-local. Exportable as JSON so a team can hand
 *                 around canonical debug requests.
 *
 * Snapshots therefore store *only the request side* — method / URL /
 * headers / query / body / bodyKind + name + description. We deliberately
 * do NOT persist any response summary even though the history store does,
 * because a snapshot is a template you re-run, not a reproduction of a
 * past call. (If you want the response-side memory, use M9.1's history
 * drawer — entries there can be "Pin to snapshot" copied to this store.)
 *
 * Schema (v1), stored under `csap-apidoc:tryItOutSnapshots`:
 *
 *   {
 *     version: 1,
 *     snapshots: [ ...{id, name, description?, createdAt, updatedAt,
 *                      method, url, headers, query, body, bodyKind} ]
 *   }
 *
 * Export format == persistence format. That's intentional so users can
 * diff/edit/hand-author JSON and re-import it without schema
 * translation friction.
 *
 * Browser-local only. No backend, no accounts.
 */
import type { HttpMethod } from '@/services/tryItOutClient';

const STORAGE_KEY = 'csap-apidoc:tryItOutSnapshots';
const SCHEMA_VERSION = 1;

/**
 * Hard cap so a malicious/bug-inflated import can't blow the 5 MB
 * localStorage quota. 500 named snapshots is ~1 MB at worst.
 */
export const MAX_SNAPSHOTS = 500;

export interface TryItOutSnapshot {
  /** Opaque client-generated id; stable across reloads. */
  id: string;
  /** User-facing label (required, non-empty). Not a unique key. */
  name: string;
  /** Optional freeform description. */
  description?: string;
  /** Wall-clock millis (Date.now()). */
  createdAt: number;
  /** Wall-clock millis of the last `update()` call. */
  updatedAt: number;
  method: HttpMethod;
  url: string;
  headers: Record<string, string>;
  query: Record<string, string>;
  body: string | null;
  bodyKind: 'json' | 'text' | 'none';
}

interface SnapshotsState {
  version: 1;
  snapshots: TryItOutSnapshot[];
}

const EMPTY_STATE: SnapshotsState = {
  version: SCHEMA_VERSION,
  snapshots: [],
};

export interface ImportResult {
  /** Number of snapshots successfully added to the store. */
  added: number;
  /** Number of entries in the imported payload that failed validation. */
  skipped: number;
  /** Human-readable error when the top-level JSON itself was invalid. */
  error?: string;
}

export type ImportMode = 'merge' | 'replace';

function isHttpMethod(v: unknown): v is HttpMethod {
  return (
    v === 'GET' ||
    v === 'POST' ||
    v === 'PUT' ||
    v === 'DELETE' ||
    v === 'PATCH' ||
    v === 'HEAD' ||
    v === 'OPTIONS'
  );
}

function isBodyKind(v: unknown): v is TryItOutSnapshot['bodyKind'] {
  return v === 'json' || v === 'text' || v === 'none';
}

function sanitizeSnapshot(raw: unknown, now: number): TryItOutSnapshot | null {
  if (!raw || typeof raw !== 'object') return null;
  const s = raw as Partial<TryItOutSnapshot>;
  if (
    typeof s.id !== 'string' ||
    !s.id.length ||
    typeof s.name !== 'string' ||
    !s.name.length ||
    !isHttpMethod(s.method) ||
    typeof s.url !== 'string'
  ) {
    return null;
  }
  return {
    id: s.id,
    name: s.name,
    description:
      typeof s.description === 'string' ? s.description : undefined,
    createdAt: typeof s.createdAt === 'number' ? s.createdAt : now,
    updatedAt: typeof s.updatedAt === 'number' ? s.updatedAt : now,
    method: s.method,
    url: s.url,
    headers:
      s.headers && typeof s.headers === 'object'
        ? { ...(s.headers as Record<string, string>) }
        : {},
    query:
      s.query && typeof s.query === 'object'
        ? { ...(s.query as Record<string, string>) }
        : {},
    body: typeof s.body === 'string' ? s.body : null,
    bodyKind: isBodyKind(s.bodyKind) ? s.bodyKind : 'none',
  };
}

function isValidState(value: unknown): value is SnapshotsState {
  if (!value || typeof value !== 'object') return false;
  const s = value as Partial<SnapshotsState>;
  return s.version === SCHEMA_VERSION && Array.isArray(s.snapshots);
}

function safeRead(): SnapshotsState {
  if (typeof window === 'undefined') return { ...EMPTY_STATE, snapshots: [] };
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...EMPTY_STATE, snapshots: [] };
    const parsed = JSON.parse(raw);
    if (!isValidState(parsed)) return { ...EMPTY_STATE, snapshots: [] };
    const now = Date.now();
    const cleaned: TryItOutSnapshot[] = [];
    for (const row of parsed.snapshots) {
      const ok = sanitizeSnapshot(row, now);
      if (ok) cleaned.push(ok);
      if (cleaned.length >= MAX_SNAPSHOTS) break;
    }
    return { version: SCHEMA_VERSION, snapshots: cleaned };
  } catch (err) {
    console.warn(
      '[csap-apidoc] failed to read try-it-out snapshots from localStorage',
      err,
    );
    return { ...EMPTY_STATE, snapshots: [] };
  }
}

function safeWrite(state: SnapshotsState): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (err) {
    console.warn(
      '[csap-apidoc] failed to write try-it-out snapshots to localStorage',
      err,
    );
  }
}

type Listener = (state: SnapshotsState) => void;
const listeners = new Set<Listener>();
let cache: SnapshotsState | null = null;

function getState(): SnapshotsState {
  if (cache === null) cache = safeRead();
  return cache;
}

function setState(next: SnapshotsState): void {
  cache = next;
  safeWrite(next);
  listeners.forEach((l) => {
    try {
      l(next);
    } catch (err) {
      console.warn('[csap-apidoc] snapshots listener threw', err);
    }
  });
}

function generateId(): string {
  return (
    's_' +
    Date.now().toString(36) +
    '_' +
    Math.random().toString(36).slice(2, 8)
  );
}

/**
 * Shape callers pass to `create()`. All request fields are required;
 * timestamps/id are filled in by the store.
 */
export interface CreateSnapshotInput {
  name: string;
  description?: string;
  method: HttpMethod;
  url: string;
  headers: Record<string, string>;
  query: Record<string, string>;
  body: string | null;
  bodyKind: TryItOutSnapshot['bodyKind'];
}

/**
 * Partial mutation shape for `update()`. Any omitted key is left as-is.
 * `id` comes from a separate argument, not this patch, so the caller
 * can't accidentally re-key a snapshot.
 */
export type UpdateSnapshotPatch = Partial<
  Omit<TryItOutSnapshot, 'id' | 'createdAt' | 'updatedAt'>
>;

export const tryItOutSnapshotsStore = {
  getState(): SnapshotsState {
    return getState();
  },

  list(): TryItOutSnapshot[] {
    return getState().snapshots;
  },

  get(id: string): TryItOutSnapshot | undefined {
    return getState().snapshots.find((s) => s.id === id);
  },

  /**
   * Append a new snapshot. Returns the created record with id/timestamps
   * filled in. Rejects (throws) if `MAX_SNAPSHOTS` is already reached —
   * surfacing a user-visible error is preferable to silently discarding
   * saves.
   */
  create(input: CreateSnapshotInput): TryItOutSnapshot {
    if (!input.name || !input.name.trim()) {
      throw new Error('Snapshot name is required');
    }
    const current = getState();
    if (current.snapshots.length >= MAX_SNAPSHOTS) {
      throw new Error(
        `Cannot create snapshot: limit of ${MAX_SNAPSHOTS} reached`,
      );
    }
    const now = Date.now();
    const snap: TryItOutSnapshot = {
      id: generateId(),
      name: input.name.trim(),
      description: input.description?.trim() || undefined,
      createdAt: now,
      updatedAt: now,
      method: input.method,
      url: input.url,
      headers: { ...input.headers },
      query: { ...input.query },
      body: input.body,
      bodyKind: input.bodyKind,
    };
    // New snapshots prepend so the "most recently saved" surfaces at the
    // top of the drawer — mirrors the history store ordering.
    setState({
      version: SCHEMA_VERSION,
      snapshots: [snap, ...current.snapshots],
    });
    return snap;
  },

  update(id: string, patch: UpdateSnapshotPatch): TryItOutSnapshot | undefined {
    const current = getState();
    const idx = current.snapshots.findIndex((s) => s.id === id);
    if (idx < 0) return undefined;
    const prev = current.snapshots[idx];
    const merged: TryItOutSnapshot = {
      ...prev,
      ...patch,
      // Name is required; reject blank mutations.
      name:
        typeof patch.name === 'string' && patch.name.trim()
          ? patch.name.trim()
          : prev.name,
      description:
        patch.description === undefined
          ? prev.description
          : patch.description?.trim() || undefined,
      headers: patch.headers ? { ...patch.headers } : prev.headers,
      query: patch.query ? { ...patch.query } : prev.query,
      id: prev.id,
      createdAt: prev.createdAt,
      updatedAt: Date.now(),
    };
    const next = current.snapshots.slice();
    next[idx] = merged;
    setState({ version: SCHEMA_VERSION, snapshots: next });
    return merged;
  },

  remove(id: string): void {
    const current = getState();
    const next = current.snapshots.filter((s) => s.id !== id);
    if (next.length === current.snapshots.length) return;
    setState({ version: SCHEMA_VERSION, snapshots: next });
  },

  clear(): void {
    setState({ version: SCHEMA_VERSION, snapshots: [] });
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },

  /** Serialise the current state to a JSON string suitable for sharing. */
  exportJson(): string {
    return JSON.stringify(getState(), null, 2);
  },

  /**
   * Ingest a JSON blob. `merge` mode appends (skipping id-collisions);
   * `replace` mode wipes existing snapshots first. Returns a summary of
   * what happened so the caller can surface it in a toast.
   *
   * Uses the same `sanitizeSnapshot` path as `safeRead()` so a partially
   * corrupted payload still imports the valid rows.
   */
  importJson(text: string, mode: ImportMode = 'merge'): ImportResult {
    let parsed: unknown;
    try {
      parsed = JSON.parse(text);
    } catch (err) {
      return { added: 0, skipped: 0, error: (err as Error).message };
    }
    if (!isValidState(parsed)) {
      return {
        added: 0,
        skipped: 0,
        error: 'not a snapshots export (missing version / snapshots)',
      };
    }
    const now = Date.now();
    const incoming: TryItOutSnapshot[] = [];
    let skipped = 0;
    for (const raw of parsed.snapshots) {
      const ok = sanitizeSnapshot(raw, now);
      if (ok) incoming.push(ok);
      else skipped += 1;
    }

    const existing = mode === 'replace' ? [] : getState().snapshots;
    const seenIds = new Set(existing.map((s) => s.id));
    const merged: TryItOutSnapshot[] = [...existing];
    let added = 0;
    for (const snap of incoming) {
      if (seenIds.has(snap.id)) {
        // ID collision in `merge` mode — rewrite with a fresh id so the
        // user doesn't lose the incoming copy. We keep the rest of the
        // snapshot unchanged.
        const regened: TryItOutSnapshot = { ...snap, id: generateId() };
        merged.unshift(regened);
        seenIds.add(regened.id);
      } else {
        merged.unshift(snap);
        seenIds.add(snap.id);
      }
      added += 1;
      if (merged.length >= MAX_SNAPSHOTS) break;
    }
    setState({ version: SCHEMA_VERSION, snapshots: merged });
    return { added, skipped };
  },

  /** Escape hatch for tests — reset the in-memory cache so a localStorage poke is re-read. */
  __resetCacheForTests(): void {
    cache = null;
  },
};

export const TRY_IT_OUT_SNAPSHOTS_STORAGE_KEY = STORAGE_KEY;
