// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  MAX_ENTRIES,
  TRY_IT_OUT_HISTORY_STORAGE_KEY,
  tryItOutHistoryStore,
  type PushEntryInput,
} from './tryItOutHistory';

function makeInput(overrides: Partial<PushEntryInput> = {}): PushEntryInput {
  return {
    method: 'GET',
    url: 'https://api.example.com/orders/1',
    headers: { 'x-trace': 'abc' },
    query: {},
    body: null,
    bodyKind: 'none',
    response: {
      ok: true,
      status: 200,
      statusText: 'OK',
      latencyMs: 42,
      byteLength: 128,
    },
    ...overrides,
  };
}

describe('tryItOutHistoryStore', () => {
  beforeEach(() => {
    window.localStorage.clear();
    tryItOutHistoryStore.__resetCacheForTests();
  });

  afterEach(() => {
    window.localStorage.clear();
    tryItOutHistoryStore.__resetCacheForTests();
  });

  it('starts empty when localStorage is empty', () => {
    expect(tryItOutHistoryStore.list()).toEqual([]);
  });

  it('push() prepends and auto-fills id + timestamp', () => {
    const before = Date.now();
    const entry = tryItOutHistoryStore.push(makeInput());
    const after = Date.now();
    expect(entry.id).toMatch(/^h_/);
    expect(entry.timestamp).toBeGreaterThanOrEqual(before);
    expect(entry.timestamp).toBeLessThanOrEqual(after);
    expect(tryItOutHistoryStore.list()[0]).toEqual(entry);
  });

  it('push() honours explicit id / timestamp overrides (for import flows)', () => {
    const entry = tryItOutHistoryStore.push(
      makeInput({ id: 'h_fixed', timestamp: 1_700_000_000_000 }),
    );
    expect(entry.id).toBe('h_fixed');
    expect(entry.timestamp).toBe(1_700_000_000_000);
  });

  it('push() is newest-first', () => {
    const a = tryItOutHistoryStore.push(
      makeInput({ url: 'https://api/a', id: 'h_a' }),
    );
    const b = tryItOutHistoryStore.push(
      makeInput({ url: 'https://api/b', id: 'h_b' }),
    );
    const list = tryItOutHistoryStore.list();
    expect(list[0].id).toBe(b.id);
    expect(list[1].id).toBe(a.id);
  });

  it('push() evicts oldest beyond MAX_ENTRIES', () => {
    for (let i = 0; i < MAX_ENTRIES + 5; i += 1) {
      tryItOutHistoryStore.push(
        makeInput({ url: `https://api/${i}`, id: `h_${i}` }),
      );
    }
    const list = tryItOutHistoryStore.list();
    expect(list).toHaveLength(MAX_ENTRIES);
    // Newest (index 0) is the last push; oldest kept entry is #5 (5 older
    // entries were evicted from the tail).
    expect(list[0].id).toBe(`h_${MAX_ENTRIES + 4}`);
    expect(list[list.length - 1].id).toBe('h_5');
  });

  it('remove() drops the matching entry, no-op for unknown ids', () => {
    tryItOutHistoryStore.push(makeInput({ id: 'h_a', url: 'https://api/a' }));
    tryItOutHistoryStore.push(makeInput({ id: 'h_b', url: 'https://api/b' }));
    tryItOutHistoryStore.remove('h_a');
    expect(tryItOutHistoryStore.list().map((e) => e.id)).toEqual(['h_b']);
    tryItOutHistoryStore.remove('h_nonexistent');
    expect(tryItOutHistoryStore.list().map((e) => e.id)).toEqual(['h_b']);
  });

  it('clear() empties the store', () => {
    tryItOutHistoryStore.push(makeInput());
    tryItOutHistoryStore.push(makeInput());
    tryItOutHistoryStore.clear();
    expect(tryItOutHistoryStore.list()).toEqual([]);
  });

  it('persists through localStorage across reloads', () => {
    tryItOutHistoryStore.push(
      makeInput({ id: 'h_persisted', url: 'https://api/x' }),
    );
    tryItOutHistoryStore.__resetCacheForTests();
    expect(tryItOutHistoryStore.list()).toHaveLength(1);
    expect(tryItOutHistoryStore.list()[0].id).toBe('h_persisted');
    expect(tryItOutHistoryStore.list()[0].url).toBe('https://api/x');
  });

  it('drops corrupt entries when reading back', () => {
    window.localStorage.setItem(
      TRY_IT_OUT_HISTORY_STORAGE_KEY,
      JSON.stringify({
        version: 1,
        entries: [
          {
            id: 'h_good',
            timestamp: 1,
            method: 'GET',
            url: 'https://api/ok',
            headers: {},
            query: {},
            body: null,
            bodyKind: 'none',
            response: { ok: true, status: 200, latencyMs: 10 },
          },
          { this: 'is garbage' },
          null,
          {
            id: 'h_missing_response',
            timestamp: 1,
            method: 'GET',
            url: 'https://api/broken',
            headers: {},
            query: {},
            body: null,
            bodyKind: 'none',
          },
        ],
      }),
    );
    tryItOutHistoryStore.__resetCacheForTests();
    const list = tryItOutHistoryStore.list();
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe('h_good');
  });

  it('returns empty state when localStorage blob is wrong schema version', () => {
    window.localStorage.setItem(
      TRY_IT_OUT_HISTORY_STORAGE_KEY,
      JSON.stringify({ version: 99, entries: [{ id: 'h_future' }] }),
    );
    tryItOutHistoryStore.__resetCacheForTests();
    expect(tryItOutHistoryStore.list()).toEqual([]);
  });

  it('returns empty state when localStorage blob is unparseable', () => {
    window.localStorage.setItem(
      TRY_IT_OUT_HISTORY_STORAGE_KEY,
      'this is not json at all {{{',
    );
    tryItOutHistoryStore.__resetCacheForTests();
    // A lot of browsers log to console on recovery; silence it for the test.
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    try {
      expect(tryItOutHistoryStore.list()).toEqual([]);
    } finally {
      warn.mockRestore();
    }
  });

  it('notifies subscribers on push / remove / clear', () => {
    const seen: number[] = [];
    const unsub = tryItOutHistoryStore.subscribe((s) => {
      seen.push(s.entries.length);
    });
    tryItOutHistoryStore.push(makeInput({ id: 'h_1' }));
    tryItOutHistoryStore.push(makeInput({ id: 'h_2' }));
    tryItOutHistoryStore.remove('h_1');
    tryItOutHistoryStore.clear();
    unsub();
    // push→push→remove→clear = sizes 1, 2, 1, 0
    expect(seen).toEqual([1, 2, 1, 0]);
  });
});
