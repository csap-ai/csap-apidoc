/**
 * TryItOutHistoryStore — localStorage-backed ring buffer of past Try-it-out requests (M9.1).
 *
 * Stores the last {@link MAX_ENTRIES} outbound requests (method/url/headers/
 * query/body) alongside a terse response summary (status/latency/byteLength
 * or failure reason). No response body is persisted, by design:
 *
 *   1. Bodies can be large (images, binary, multi-MB JSON) and blow the
 *      5 MB localStorage quota for a handful of requests.
 *   2. Bodies may contain sensitive data (tokens, user PII) that the user
 *      did NOT opt into having persisted to disk; the Vault story covers
 *      credentials but response content is out of scope.
 *
 * The "Replay" action in {@link TryItOutHistoryDrawer} therefore rebuilds
 * the *request* from an entry and re-sends it through the panel — it does
 * not restore the original response.
 *
 * Storage layout mirrors {@link settingsStore.ts} for consistency:
 *   - Single JSON blob under `csap-apidoc:tryItOutHistory`
 *   - `version` field for forward-compat migration
 *   - Parsed eagerly on first access, cached in-memory, written through on
 *     every mutation.
 *
 * Browser-local only. No backend, no accounts.
 */
import type { HttpMethod } from '@/services/tryItOutClient';

const STORAGE_KEY = 'csap-apidoc:tryItOutHistory';
const SCHEMA_VERSION = 1;

/**
 * Keep 50 entries. Chosen so the typical JSON blob stays well under 1 MB
 * even for chatty POST bodies (worst-case ~50 × 10 KB = 500 KB).
 */
export const MAX_ENTRIES = 50;

export interface TryItOutHistoryResponseSummary {
  ok: boolean;
  status?: number;
  statusText?: string;
  latencyMs: number;
  byteLength?: number;
  /** Discriminates failures (`network` / `timeout` / `cancelled` / `config` / `unknown`). */
  failureReason?: string;
  /** Short human message; used as tooltip for failed rows. */
  failureMessage?: string;
}

export interface TryItOutHistoryEntry {
  /** Opaque client-generated id; stable across reloads. */
  id: string;
  /** Wall-clock millis (Date.now()) of when the request was initiated. */
  timestamp: number;
  method: HttpMethod;
  url: string;
  headers: Record<string, string>;
  query: Record<string, string>;
  /**
   * Request body as the user typed it. `null` for GET/HEAD or empty-body
   * sends. We store the raw string (not the parsed JSON) so Replay can
   * round-trip formatting — the panel re-parses on send anyway.
   */
  body: string | null;
  /** Mirrors {@link TryItOutPanel}'s bodyKind so Replay can re-seed the editor. */
  bodyKind: 'json' | 'text' | 'none';
  response: TryItOutHistoryResponseSummary;
}

interface HistoryState {
  version: 1;
  /** Newest first. */
  entries: TryItOutHistoryEntry[];
}

const EMPTY_STATE: HistoryState = {
  version: SCHEMA_VERSION,
  entries: [],
};

function isValidState(value: unknown): value is HistoryState {
  if (!value || typeof value !== 'object') return false;
  const s = value as Partial<HistoryState>;
  if (s.version !== SCHEMA_VERSION) return false;
  if (!Array.isArray(s.entries)) return false;
  // We deliberately don't hard-validate every entry shape — a corrupt row
  // is dropped at read time by `sanitizeEntry` rather than nuking the
  // whole history.
  return true;
}

function sanitizeEntry(raw: unknown): TryItOutHistoryEntry | null {
  if (!raw || typeof raw !== 'object') return null;
  const e = raw as Partial<TryItOutHistoryEntry> & {
    response?: Partial<TryItOutHistoryResponseSummary>;
  };
  if (
    typeof e.id !== 'string' ||
    typeof e.timestamp !== 'number' ||
    typeof e.method !== 'string' ||
    typeof e.url !== 'string' ||
    !e.response ||
    typeof e.response.latencyMs !== 'number' ||
    typeof e.response.ok !== 'boolean'
  ) {
    return null;
  }
  return {
    id: e.id,
    timestamp: e.timestamp,
    method: e.method as HttpMethod,
    url: e.url,
    headers: (e.headers ?? {}) as Record<string, string>,
    query: (e.query ?? {}) as Record<string, string>,
    body: typeof e.body === 'string' ? e.body : null,
    bodyKind:
      e.bodyKind === 'json' || e.bodyKind === 'text' || e.bodyKind === 'none'
        ? e.bodyKind
        : 'none',
    response: {
      ok: e.response.ok,
      status: e.response.status,
      statusText: e.response.statusText,
      latencyMs: e.response.latencyMs,
      byteLength: e.response.byteLength,
      failureReason: e.response.failureReason,
      failureMessage: e.response.failureMessage,
    },
  };
}

function safeRead(): HistoryState {
  if (typeof window === 'undefined') return { ...EMPTY_STATE, entries: [] };
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...EMPTY_STATE, entries: [] };
    const parsed = JSON.parse(raw);
    if (!isValidState(parsed)) return { ...EMPTY_STATE, entries: [] };
    const cleaned: TryItOutHistoryEntry[] = [];
    for (const row of parsed.entries) {
      const ok = sanitizeEntry(row);
      if (ok) cleaned.push(ok);
      if (cleaned.length >= MAX_ENTRIES) break;
    }
    return { version: SCHEMA_VERSION, entries: cleaned };
  } catch (err) {
    console.warn(
      '[csap-apidoc] failed to read try-it-out history from localStorage',
      err,
    );
    return { ...EMPTY_STATE, entries: [] };
  }
}

function safeWrite(state: HistoryState): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (err) {
    // Quota-exceeded is the common case; swallow rather than bubble up —
    // losing one history entry is much better than crashing the panel.
    console.warn(
      '[csap-apidoc] failed to write try-it-out history to localStorage',
      err,
    );
  }
}

type Listener = (state: HistoryState) => void;
const listeners = new Set<Listener>();
let cache: HistoryState | null = null;

function getState(): HistoryState {
  if (cache === null) cache = safeRead();
  return cache;
}

function setState(next: HistoryState): void {
  cache = next;
  safeWrite(next);
  listeners.forEach((l) => {
    try {
      l(next);
    } catch (err) {
      console.warn('[csap-apidoc] try-it-out history listener threw', err);
    }
  });
}

function generateId(): string {
  return (
    'h_' +
    Date.now().toString(36) +
    '_' +
    Math.random().toString(36).slice(2, 8)
  );
}

export interface PushEntryInput
  extends Omit<TryItOutHistoryEntry, 'id' | 'timestamp'> {
  /** Optional overrides used by tests / import flows. */
  id?: string;
  timestamp?: number;
}

export const tryItOutHistoryStore = {
  getState(): HistoryState {
    return getState();
  },

  list(): TryItOutHistoryEntry[] {
    return getState().entries;
  },

  /**
   * Prepend a new entry; evicts oldest beyond {@link MAX_ENTRIES}.
   * Returns the final stored entry (with id/timestamp filled in).
   */
  push(input: PushEntryInput): TryItOutHistoryEntry {
    const entry: TryItOutHistoryEntry = {
      id: input.id ?? generateId(),
      timestamp: input.timestamp ?? Date.now(),
      method: input.method,
      url: input.url,
      headers: { ...input.headers },
      query: { ...input.query },
      body: input.body,
      bodyKind: input.bodyKind,
      response: { ...input.response },
    };
    const current = getState();
    const nextEntries = [entry, ...current.entries].slice(0, MAX_ENTRIES);
    setState({ version: SCHEMA_VERSION, entries: nextEntries });
    return entry;
  },

  remove(id: string): void {
    const current = getState();
    const nextEntries = current.entries.filter((e) => e.id !== id);
    if (nextEntries.length === current.entries.length) return; // no-op
    setState({ version: SCHEMA_VERSION, entries: nextEntries });
  },

  clear(): void {
    setState({ version: SCHEMA_VERSION, entries: [] });
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },

  /**
   * Used by tests to force a reload from localStorage after mutating it
   * directly. Not part of the public API; exposed because the test file
   * lives in the same package and we prefer this over `as any`.
   */
  __resetCacheForTests(): void {
    cache = null;
  },
};

export const TRY_IT_OUT_HISTORY_STORAGE_KEY = STORAGE_KEY;
