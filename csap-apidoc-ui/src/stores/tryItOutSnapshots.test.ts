// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  MAX_SNAPSHOTS,
  TRY_IT_OUT_SNAPSHOTS_STORAGE_KEY,
  tryItOutSnapshotsStore,
  type CreateSnapshotInput,
} from './tryItOutSnapshots';

function makeInput(overrides: Partial<CreateSnapshotInput> = {}): CreateSnapshotInput {
  return {
    name: 'Get order',
    description: 'retrieve a single order',
    method: 'GET',
    url: 'https://api.example.com/orders/1',
    headers: { 'x-trace': 'abc' },
    query: {},
    body: null,
    bodyKind: 'none',
    ...overrides,
  };
}

describe('tryItOutSnapshotsStore', () => {
  beforeEach(() => {
    window.localStorage.clear();
    tryItOutSnapshotsStore.__resetCacheForTests();
  });
  afterEach(() => {
    window.localStorage.clear();
    tryItOutSnapshotsStore.__resetCacheForTests();
  });

  it('starts empty when localStorage is empty', () => {
    expect(tryItOutSnapshotsStore.list()).toEqual([]);
  });

  it('create() fills id + timestamps and prepends', () => {
    const a = tryItOutSnapshotsStore.create(makeInput({ name: 'A' }));
    const b = tryItOutSnapshotsStore.create(makeInput({ name: 'B' }));
    expect(a.id).toMatch(/^s_/);
    expect(a.createdAt).toBeTypeOf('number');
    expect(a.updatedAt).toBe(a.createdAt);
    const list = tryItOutSnapshotsStore.list();
    expect(list.map((s) => s.name)).toEqual(['B', 'A']);
  });

  it('create() trims name + drops empty description', () => {
    const snap = tryItOutSnapshotsStore.create(
      makeInput({ name: '  My call  ', description: '   ' }),
    );
    expect(snap.name).toBe('My call');
    expect(snap.description).toBeUndefined();
  });

  it('create() rejects a blank name', () => {
    expect(() =>
      tryItOutSnapshotsStore.create(makeInput({ name: '   ' })),
    ).toThrow(/name is required/i);
  });

  it('get(id) looks up an existing snapshot', () => {
    const created = tryItOutSnapshotsStore.create(makeInput({ name: 'X' }));
    expect(tryItOutSnapshotsStore.get(created.id)?.name).toBe('X');
    expect(tryItOutSnapshotsStore.get('s_nope')).toBeUndefined();
  });

  it('update() mutates fields, bumps updatedAt, preserves id + createdAt', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-01T00:00:00Z'));
    const snap = tryItOutSnapshotsStore.create(makeInput({ name: 'Before' }));
    vi.setSystemTime(new Date('2026-01-02T00:00:00Z'));
    const updated = tryItOutSnapshotsStore.update(snap.id, {
      name: 'After',
      description: 'new desc',
      url: 'https://api/next',
    })!;
    expect(updated.id).toBe(snap.id);
    expect(updated.createdAt).toBe(snap.createdAt);
    expect(updated.updatedAt).toBeGreaterThan(snap.updatedAt);
    expect(updated.name).toBe('After');
    expect(updated.description).toBe('new desc');
    expect(updated.url).toBe('https://api/next');
    vi.useRealTimers();
  });

  it('update() ignores a blank-name patch (keeps previous name)', () => {
    const snap = tryItOutSnapshotsStore.create(makeInput({ name: 'Stable' }));
    const after = tryItOutSnapshotsStore.update(snap.id, { name: '   ' });
    expect(after?.name).toBe('Stable');
  });

  it('update() returns undefined for a missing id', () => {
    expect(tryItOutSnapshotsStore.update('s_missing', { name: 'x' })).toBeUndefined();
  });

  it('remove() drops the row; no-op for unknown id', () => {
    const a = tryItOutSnapshotsStore.create(makeInput({ name: 'A' }));
    const b = tryItOutSnapshotsStore.create(makeInput({ name: 'B' }));
    tryItOutSnapshotsStore.remove(a.id);
    expect(tryItOutSnapshotsStore.list().map((s) => s.id)).toEqual([b.id]);
    tryItOutSnapshotsStore.remove('s_nope');
    expect(tryItOutSnapshotsStore.list().map((s) => s.id)).toEqual([b.id]);
  });

  it('clear() empties the store', () => {
    tryItOutSnapshotsStore.create(makeInput({ name: 'A' }));
    tryItOutSnapshotsStore.create(makeInput({ name: 'B' }));
    tryItOutSnapshotsStore.clear();
    expect(tryItOutSnapshotsStore.list()).toEqual([]);
  });

  it('persists through localStorage across reloads', () => {
    const snap = tryItOutSnapshotsStore.create(makeInput({ name: 'Persisted' }));
    tryItOutSnapshotsStore.__resetCacheForTests();
    const list = tryItOutSnapshotsStore.list();
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe(snap.id);
    expect(list[0].name).toBe('Persisted');
  });

  it('drops corrupt rows on read', () => {
    window.localStorage.setItem(
      TRY_IT_OUT_SNAPSHOTS_STORAGE_KEY,
      JSON.stringify({
        version: 1,
        snapshots: [
          {
            id: 's_good',
            name: 'Good',
            createdAt: 1,
            updatedAt: 1,
            method: 'GET',
            url: 'https://api/ok',
            headers: {},
            query: {},
            body: null,
            bodyKind: 'none',
          },
          { trash: true },
          null,
          { id: 's_bad_method', name: 'Bad', method: 'TEAPOT', url: 'x' },
        ],
      }),
    );
    tryItOutSnapshotsStore.__resetCacheForTests();
    const list = tryItOutSnapshotsStore.list();
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe('s_good');
  });

  it('returns empty state for wrong schema version', () => {
    window.localStorage.setItem(
      TRY_IT_OUT_SNAPSHOTS_STORAGE_KEY,
      JSON.stringify({ version: 99, snapshots: [{ id: 's_future' }] }),
    );
    tryItOutSnapshotsStore.__resetCacheForTests();
    expect(tryItOutSnapshotsStore.list()).toEqual([]);
  });

  it('exportJson() round-trips through importJson(replace)', () => {
    tryItOutSnapshotsStore.create(makeInput({ name: 'One' }));
    tryItOutSnapshotsStore.create(makeInput({ name: 'Two' }));
    const payload = tryItOutSnapshotsStore.exportJson();
    tryItOutSnapshotsStore.clear();
    const res = tryItOutSnapshotsStore.importJson(payload, 'replace');
    expect(res.error).toBeUndefined();
    expect(res.added).toBe(2);
    expect(res.skipped).toBe(0);
    expect(tryItOutSnapshotsStore.list().map((s) => s.name).sort()).toEqual([
      'One',
      'Two',
    ]);
  });

  it('importJson(merge) regenerates colliding ids so nothing is lost', () => {
    const a = tryItOutSnapshotsStore.create(makeInput({ name: 'Local' }));
    const incoming = {
      version: 1,
      snapshots: [
        {
          // Same id as the local one — must not silently clobber the
          // local row nor be dropped.
          id: a.id,
          name: 'Incoming',
          createdAt: 1,
          updatedAt: 1,
          method: 'GET',
          url: 'https://api/incoming',
          headers: {},
          query: {},
          body: null,
          bodyKind: 'none',
        },
      ],
    };
    const res = tryItOutSnapshotsStore.importJson(JSON.stringify(incoming));
    expect(res.added).toBe(1);
    const list = tryItOutSnapshotsStore.list();
    expect(list).toHaveLength(2);
    expect(list.filter((s) => s.name === 'Local')).toHaveLength(1);
    expect(list.filter((s) => s.name === 'Incoming')).toHaveLength(1);
    // Every id is unique after the rewrite.
    const ids = list.map((s) => s.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('importJson() reports unparseable payloads as `error`', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    try {
      const res = tryItOutSnapshotsStore.importJson('not json at all {');
      expect(res.error).toBeTruthy();
      expect(res.added).toBe(0);
    } finally {
      warn.mockRestore();
    }
  });

  it('importJson() counts skipped rows for partially corrupt payloads', () => {
    const payload = {
      version: 1,
      snapshots: [
        {
          id: 's_import_ok',
          name: 'Valid',
          method: 'GET',
          url: 'https://api/x',
          headers: {},
          query: {},
          body: null,
          bodyKind: 'none',
        },
        { broken: 'row' },
        null,
      ],
    };
    const res = tryItOutSnapshotsStore.importJson(JSON.stringify(payload));
    expect(res.added).toBe(1);
    expect(res.skipped).toBe(2);
    expect(tryItOutSnapshotsStore.list()).toHaveLength(1);
  });

  it('caps `create()` at MAX_SNAPSHOTS', () => {
    // Directly seed localStorage so we don't have to create() 500 times.
    const snaps = Array.from({ length: MAX_SNAPSHOTS }, (_, i) => ({
      id: `s_${i}`,
      name: `n${i}`,
      createdAt: 1,
      updatedAt: 1,
      method: 'GET',
      url: 'https://api/n',
      headers: {},
      query: {},
      body: null,
      bodyKind: 'none',
    }));
    window.localStorage.setItem(
      TRY_IT_OUT_SNAPSHOTS_STORAGE_KEY,
      JSON.stringify({ version: 1, snapshots: snaps }),
    );
    tryItOutSnapshotsStore.__resetCacheForTests();
    expect(tryItOutSnapshotsStore.list()).toHaveLength(MAX_SNAPSHOTS);
    expect(() =>
      tryItOutSnapshotsStore.create(makeInput({ name: 'one-too-many' })),
    ).toThrow(/limit of/i);
  });

  it('notifies subscribers on create / update / remove / clear', () => {
    const seen: number[] = [];
    const unsub = tryItOutSnapshotsStore.subscribe((s) => {
      seen.push(s.snapshots.length);
    });
    const a = tryItOutSnapshotsStore.create(makeInput({ name: 'A' }));
    tryItOutSnapshotsStore.create(makeInput({ name: 'B' }));
    tryItOutSnapshotsStore.update(a.id, { name: 'A2' });
    tryItOutSnapshotsStore.remove(a.id);
    tryItOutSnapshotsStore.clear();
    unsub();
    expect(seen).toEqual([1, 2, 2, 1, 0]);
  });
});
